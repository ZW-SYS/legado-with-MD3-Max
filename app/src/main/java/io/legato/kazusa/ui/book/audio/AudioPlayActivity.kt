package io.legato.kazusa.ui.book.audio

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import com.google.android.material.slider.Slider
import io.legato.kazusa.R
import io.legato.kazusa.base.VMBaseActivity
import io.legato.kazusa.constant.BookType
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.constant.Status
import io.legato.kazusa.constant.Theme
import io.legato.kazusa.data.appDb
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookChapter
import io.legato.kazusa.data.entities.BookSource
import io.legato.kazusa.databinding.ActivityAudioPlayBinding
import io.legato.kazusa.help.book.isAudio
import io.legato.kazusa.help.book.removeType
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.lib.dialogs.alert
import io.legato.kazusa.model.AudioPlay
import io.legato.kazusa.model.BookCover
import io.legato.kazusa.service.AudioPlayService
import io.legato.kazusa.ui.about.AppLogDialog
import io.legato.kazusa.ui.book.changesource.ChangeBookSourceDialog
import io.legato.kazusa.ui.book.source.edit.BookSourceEditActivity
import io.legato.kazusa.ui.book.toc.TocActivityResult
import io.legato.kazusa.ui.login.SourceLoginActivity
import io.legato.kazusa.utils.StartActivityContract
import io.legato.kazusa.utils.applyNavigationBarPadding
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.observeEvent
import io.legato.kazusa.utils.observeEventSticky
import io.legato.kazusa.utils.sendToClip
import io.legato.kazusa.utils.showDialogFragment
import io.legato.kazusa.utils.startActivity
import io.legato.kazusa.utils.startActivityForBook
import io.legato.kazusa.utils.startAnimation
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import io.legato.kazusa.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.onLongClick
import java.util.Locale

/**
 * 音频播放
 */
@SuppressLint("ObsoleteSdkInt")
class AudioPlayActivity :
    VMBaseActivity<ActivityAudioPlayBinding, AudioPlayViewModel>(toolBarTheme = Theme.Dark),
    ChangeBookSourceDialog.CallBack,
    AudioPlay.CallBack {

    override val binding by viewBinding(ActivityAudioPlayBinding::inflate)
    override val viewModel by viewModels<AudioPlayViewModel>()
    private var adjustProgress = false
    private var playMode = AudioPlay.PlayMode.LIST_END_STOP
    private var playSpeed = 1f

    private val progressTimeFormat by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SimpleDateFormat("mm:ss", Locale.getDefault())
        } else {
            java.text.SimpleDateFormat("mm:ss", Locale.getDefault())
        }
    }
    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            if (it.first != AudioPlay.book?.durChapterIndex
                || it.second == 0
            ) {
                AudioPlay.skipTo(it.first)
            }
        }
    }
    private val sourceEditResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                viewModel.upSource()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.titleBar.setBackgroundResource(R.color.transparent)
        binding.titleBar.title = ""
        AudioPlay.register(this)
        viewModel.titleData.observe(this) {
            binding.tvTitle.text = it
        }
        viewModel.coverData.observe(this) {
            upCover(it)
        }
        viewModel.initData(intent)
        initView()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.audio_play, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !AudioPlay.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_wake_lock)?.isChecked = AppConfig.audioPlayUseWakeLock
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_change_source -> AudioPlay.book?.let {
                showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
            }

            R.id.menu_login -> AudioPlay.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                }
            }

            R.id.menu_wake_lock -> AppConfig.audioPlayUseWakeLock = !AppConfig.audioPlayUseWakeLock
            R.id.menu_copy_audio_url -> sendToClip(AudioPlayService.url)
            R.id.menu_edit_source -> AudioPlay.bookSource?.let {
                sourceEditResult.launch {
                    putExtra("sourceUrl", it.bookSourceUrl)
                }
            }

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.ivTimer.isEnabled = false
        binding.ivFastForward.isEnabled = false

        binding.ivPlayMode.setOnClickListener {
            AudioPlay.changePlayMode()
        }

        observeEventSticky<AudioPlay.PlayMode>(EventBus.PLAY_MODE_CHANGED) {
            playMode = it
            updatePlayModeIcon()
        }
        binding.fabPlayStop.setOnClickListener {
            playButton()
        }
        binding.fabPlayStop.onLongClick {
            AudioPlay.stop()
        }
        binding.ivSkipNext.setOnClickListener {
            AudioPlay.next()
        }
        binding.ivSkipPrevious.setOnClickListener {
            AudioPlay.prev()
        }

//        binding.playerProgress.setOnSeekBarChangeListener(object : SeekBarChangeListener {
//            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
//                binding.tvDurTime.text = progressTimeFormat.format(progress.toLong())
//            }
//
//            override fun onStartTrackingTouch(seekBar: SeekBar) {
//                adjustProgress = true
//            }
//
//            override fun onStopTrackingTouch(seekBar: SeekBar) {
//                adjustProgress = false
//                AudioPlay.adjustProgress(seekBar.progress)
//            }
//        })

        // 替换原来的 SeekBar 监听器
        binding.playerProgress.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.tvDurTime.text = progressTimeFormat.format(value.toLong())
            }
        }

        binding.playerProgress.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                adjustProgress = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                adjustProgress = false
                AudioPlay.adjustProgress(slider.value.toInt())
            }
        })

        binding.playerProgress.setLabelFormatter { value ->
            progressTimeFormat.format(value.toLong())
        }

        binding.ivChapter.setOnClickListener {
            AudioPlay.book?.let {
                tocActivityResult.launch(it.bookUrl)
            }
        }

        binding.ivFastForward.setOnClickListener { toggleFastForward() }
        binding.ivTimer.setOnClickListener { toggleTimer() }

        binding.llSubMenu.applyNavigationBarPadding()
    }


    private fun toggleFastForward() {
        TransitionManager.beginDelayedTransition(binding.root)
        if (binding.llSet.isVisible) {
            if (binding.ivTimer.isChecked) {
                initSlider(true)
            } else {
                binding.llSet.gone()
            }
            binding.ivTimer.isChecked = false
        } else {
            initSlider(true)
            binding.llSet.visible()
        }
    }

    private fun toggleTimer() {
        TransitionManager.beginDelayedTransition(binding.root)
        if (binding.llSet.isVisible) {
            if (binding.ivFastForward.isChecked) {
                initSlider(false)
            } else {
                binding.llSet.gone()
            }
            binding.ivFastForward.isChecked = false
        } else {
            initSlider(false)
            binding.llSet.visible()
        }
    }

    private fun initSlider(isAudioPlay: Boolean) {
        binding.settingSlider.clearOnChangeListeners()
        binding.settingSlider.clearOnSliderTouchListeners()
        binding.btnReset.clearOnCheckedChangeListeners()

        if (isAudioPlay) {
            binding.settingSlider.isEnabled = AudioPlay.status != Status.STOP
            binding.btnReset.setOnClickListener {
                binding.settingSlider.value = 1f
                AudioPlay.adjustSpeed(1f)
            }
            binding.settingSlider.apply {
                valueFrom = 0.5f
                valueTo = 5.0f
                stepSize = 0.1f
                value = playSpeed

                addOnChangeListener { _, newValue, fromUser ->
                    if (fromUser) {
                        AudioPlay.adjustSpeed(newValue)
                    }
                }

                addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {
                        slider.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }

                    override fun onStopTrackingTouch(slider: Slider) {}
                })

                setLabelFormatter { value -> String.format(Locale.ROOT, "%.1fX", value) }
            }
        } else {
            binding.settingSlider.isEnabled = true
            binding.btnReset.setOnClickListener {
                binding.settingSlider.value = 0f
                AudioPlay.setTimer(0)
            }
            binding.settingSlider.apply {
                valueFrom = 0f
                valueTo = 180f
                stepSize = 1f
                value = AudioPlayService.timeMinute.toFloat()

                addOnChangeListener { _, newValue, fromUser ->
                    if (fromUser) AudioPlay.setTimer(newValue.toInt())
                }

                addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {
                        slider.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }

                    override fun onStopTrackingTouch(slider: Slider) {}
                })

                setLabelFormatter { v -> "${v.toInt()}m" }
            }
        }
    }

    private fun updatePlayModeIcon() {
        binding.ivPlayMode.setIconResource(playMode.iconRes)
    }

    private fun upCover(path: String?) {
        BookCover.load(this, path, sourceOrigin = AudioPlay.bookSource?.bookSourceUrl) {
            if (!AppConfig.isEInkMode) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BookCover.load(this, path, sourceOrigin = AudioPlay.bookSource?.bookSourceUrl)
                        .into(binding.ivBg)
                    val blurEffect =
                        RenderEffect.createBlurEffect(120f, 120f, Shader.TileMode.CLAMP)
                    binding.ivBg.setRenderEffect(blurEffect)
                } else {
                    // 低版本直接加载模糊图
                    BookCover.loadBlur(
                        this,
                        path,
                        sourceOrigin = AudioPlay.bookSource?.bookSourceUrl
                    )
                        .into(binding.ivBg)
                }
            }
        }.into(binding.ivCover)
    }

    private fun playButton() {
        when (AudioPlay.status) {
            Status.PLAY -> AudioPlay.pause(this)
            Status.PAUSE -> AudioPlay.resume(this)
            else -> AudioPlay.loadOrUpPlayUrl()
        }
    }

    override val oldBook: Book?
        get() = AudioPlay.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (book.isAudio) {
            viewModel.changeTo(source, book, toc)
        } else {
            AudioPlay.stop()
            lifecycleScope.launch {
                withContext(IO) {
                    AudioPlay.book?.migrateTo(book, toc)
                    book.removeType(BookType.updateError)
                    AudioPlay.book?.delete()
                    appDb.bookDao.insert(book)
                }
                startActivityForBook(book)
                finish()
            }
        }
    }

    override fun finish() {
        val book = AudioPlay.book ?: return super.finish()

        if (AudioPlay.inBookshelf) {
            return super.finish()
        }

        if (!AppConfig.showAddToShelfAlert) {
            viewModel.removeFromBookshelf { super.finish() }
        } else {
            alert(title = getString(R.string.add_to_bookshelf)) {
                setMessage(getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    AudioPlay.book?.removeType(BookType.notShelf)
                    AudioPlay.book?.save()
                    AudioPlay.inBookshelf = true
                    setResult(RESULT_OK)
                }
                noButton { viewModel.removeFromBookshelf { super.finish() } }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (AudioPlay.status != Status.PLAY) {
            AudioPlay.stop()
        }
        AudioPlay.unregister(this)
    }

    @SuppressLint("SetTextI18n")
    override fun observeLiveBus() {
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                playButton()
            }
        }
        observeEventSticky<Int>(EventBus.AUDIO_STATE) { state ->
            AudioPlay.status = state

            val iconDrawable = if (state == Status.PLAY) {
                binding.ivTimer.isEnabled = true
                binding.ivFastForward.isEnabled = true
                AppCompatResources.getDrawable(this, R.drawable.play_anim)
            } else {
                AppCompatResources.getDrawable(this, R.drawable.pause_anim)
            }

            val bgDrawable = if (state == Status.PLAY) {
                AppCompatResources.getDrawable(this, R.drawable.bg_play_anim)
            } else {
                AppCompatResources.getDrawable(this, R.drawable.bg_pause_anim)
            }

            binding.fabPlayStop.icon = iconDrawable
            binding.fabPlayStop.background = bgDrawable

            iconDrawable?.startAnimation()
            bgDrawable?.startAnimation()
        }

        observeEventSticky<String>(EventBus.AUDIO_SUB_TITLE) {
            binding.tvSubTitle.text = it
            binding.ivSkipPrevious.isEnabled = AudioPlay.durChapterIndex > 0
            binding.ivSkipNext.isEnabled =
                AudioPlay.durChapterIndex < AudioPlay.simulatedChapterSize - 1
        }
//        observeEventSticky<Int>(EventBus.AUDIO_SIZE) {
//            binding.playerProgress.max = it
//            binding.tvAllTime.text = progressTimeFormat.format(it.toLong())
//        }
//        observeEventSticky<Int>(EventBus.AUDIO_PROGRESS) {
//            if (!adjustProgress) binding.playerProgress.progress = it
//            binding.tvDurTime.text = progressTimeFormat.format(it.toLong())
//        }
//        observeEventSticky<Int>(EventBus.AUDIO_BUFFER_PROGRESS) {
//            binding.playerProgress.secondaryProgress = it
//
//        }
        observeEventSticky<Int>(EventBus.AUDIO_SIZE) {
            binding.playerProgress.valueTo = maxOf(1f, it.toFloat())
            binding.tvAllTime.text = progressTimeFormat.format(it.toLong())
        }

        observeEventSticky<Int>(EventBus.AUDIO_PROGRESS) {
            if (!adjustProgress) binding.playerProgress.value = it.toFloat()
            binding.tvDurTime.text = progressTimeFormat.format(it.toLong())
        }

        observeEventSticky<Int>(EventBus.AUDIO_BUFFER_PROGRESS) {
            //updateBufferProgress(it)
        }

        observeEventSticky<Float>(EventBus.AUDIO_SPEED) {
            TransitionManager.beginDelayedTransition(binding.root)
            playSpeed = it
            binding.tvSpeed.text = String.format(Locale.ROOT, "%.1fX", it)
            if (it != 1f) {
                binding.cdSpeed.visible()
            } else
                binding.cdSpeed.gone()
        }
        observeEventSticky<Int>(EventBus.AUDIO_DS) {
            TransitionManager.beginDelayedTransition(binding.root)
            binding.tvTimer.text = "${it}m"
            if (it > 0) {
                binding.cdTimer.visible()
            } else
                binding.cdTimer.gone()
        }
    }

    override fun upLoading(loading: Boolean) {
        runOnUiThread {
            binding.progressLoading.visible(loading)
        }
    }

}