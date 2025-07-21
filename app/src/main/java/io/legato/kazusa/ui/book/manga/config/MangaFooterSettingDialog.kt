package io.legato.kazusa.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.databinding.DialogMangaFooterSettingBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.ui.widget.ReaderInfoBarView
import io.legato.kazusa.utils.GSON
import io.legato.kazusa.utils.fromJsonObject
import io.legato.kazusa.utils.postEvent
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding

class MangaFooterSettingDialog :
    BaseBottomSheetDialogFragment(R.layout.dialog_manga_footer_setting) {

    val config = GSON.fromJsonObject<MangaFooterConfig>(AppConfig.mangaFooterConfig).getOrNull()
        ?: MangaFooterConfig()

    var callback: Callback? = null

    var initialAutoPageEnabled: Boolean = false

    private val binding by viewBinding(DialogMangaFooterSettingBinding::bind)

    override fun onStart() {
        super.onStart()

    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {

        binding.cbChapterLabel.run {
            isChecked = !config.hideChapterLabel
            setOnCheckedChangeListener { _, isChecked ->
                config.hideChapterLabel = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (isChecked) binding.cbChapter.isChecked = true
            }
        }

        binding.cbChapter.run {
            isChecked = !config.hideChapter
            setOnCheckedChangeListener { _, isChecked ->
                config.hideChapter = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (!isChecked) binding.cbChapterLabel.isChecked = false
            }
        }

        binding.cbChapterName.run {
            isChecked = !config.hideChapterName
            setOnCheckedChangeListener { _, isChecked ->
                config.hideChapterName = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
            }
        }

        binding.cbPageNumberLabel.run {
            isChecked = !config.hidePageNumberLabel
            setOnCheckedChangeListener { _, isChecked ->
                config.hidePageNumberLabel = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (isChecked) binding.cbPageNumber.isChecked = true
            }
        }

        binding.cbPageNumber.run {
            isChecked = !config.hidePageNumber
            setOnCheckedChangeListener { _, isChecked ->
                config.hidePageNumber = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (!isChecked) binding.cbPageNumberLabel.isChecked = false
            }
        }

        binding.cbProgressRatioLabel.run {
            isChecked = !config.hideProgressRatioLabel
            setOnCheckedChangeListener { _, isChecked ->
                config.hideProgressRatioLabel = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (isChecked) binding.cbProgressRatio.isChecked = true
            }
        }

        binding.cbProgressRatio.run {
            isChecked = !config.hideProgressRatio
            setOnCheckedChangeListener { _, isChecked ->
                config.hideProgressRatio = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (!isChecked) binding.cbProgressRatioLabel.isChecked = false
            }
        }

        binding.rgFooterOrientation.check(
            when {
                config.hideFooter -> R.id.rb_hide
                config.footerOrientation == ReaderInfoBarView.ALIGN_CENTER -> R.id.rb_center
                else -> R.id.rb_left
            }
        )

        binding.rgFooterOrientation.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.rb_left -> {
                    config.hideFooter = false
                    config.footerOrientation = ReaderInfoBarView.ALIGN_LEFT
                }
                R.id.rb_center -> {
                    config.hideFooter = false
                    config.footerOrientation = ReaderInfoBarView.ALIGN_CENTER
                }
                R.id.rb_hide -> {
                    config.hideFooter = true
                }
            }
            updateChapterText()
            postEvent(EventBus.UP_MANGA_CONFIG, config)
        }

        binding.btnColorFilter.setOnClickListener {
            callback?.showColorFilterConfig()
            dismiss()
        }

        updateChapterText()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        AppConfig.mangaFooterConfig = GSON.toJson(config)
    }

    private fun updateChapterText() {

        if (config.hideFooter) {
            binding.tvChapter.visibility = View.INVISIBLE
            return
        } else {
            binding.tvChapter.visibility = View.VISIBLE
        }

        binding.tvChapter.gravity = when (config.footerOrientation) {
            ReaderInfoBarView.ALIGN_LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
            ReaderInfoBarView.ALIGN_CENTER -> Gravity.CENTER
            else -> Gravity.START
        }

        val label = if (!config.hideChapterLabel) "章节" else ""
        val chapter = if (!config.hideChapter) "1/45 " else ""
        val name = if (!config.hideChapterName) "第三话 " else ""

        val progressLabel = if (!config.hideProgressRatioLabel) "总进度" else ""
        val progress = if (!config.hideProgressRatio) "2.1%" else ""

        val pageLabel = if (!config.hidePageNumberLabel) "页数" else ""
        val page = if (!config.hidePageNumber) "4/30 " else ""

        val parts = listOf(
            name,
            pageLabel,
            page,
            label,
            chapter,
            progressLabel,
            progress
        ).filter { it.isNotEmpty() }
        binding.tvChapter.text = parts.joinToString(" ")
    }

    interface Callback {
        fun onAutoPageToggle(enable: Boolean)
        fun onAutoPageSpeedChanged(speed: Int)
        fun showColorFilterConfig()
    }

}