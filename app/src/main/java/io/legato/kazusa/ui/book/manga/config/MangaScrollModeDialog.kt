package io.legato.kazusa.ui.book.manga.config

import android.os.Bundle
import android.view.View
import androidx.transition.TransitionManager
import com.google.android.material.chip.Chip
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.databinding.DialogMangaScrollModeBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding

class MangaScrollModeDialog : BaseBottomSheetDialogFragment(R.layout.dialog_manga_scroll_mode) {

    private val binding by viewBinding(DialogMangaScrollModeBinding::bind)

    var initialScrollMode: Int = AppConfig.mangaScrollMode
    var initialAutoPageSpeed: Int = AppConfig.mangaAutoPageSpeed
    var initialSidePadding: Int = AppConfig.webtoonSidePaddingDp
    var initialAutoPageEnabled: Boolean = false
    var callback: Callback? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
    }

    private fun initView() {
        binding.chipGroupScrollMode.removeAllViews()

        MangaScrollMode.ALL.forEach { mode ->
            binding.chipGroupScrollMode.addView(Chip(requireContext()).apply {
                text = MangaScrollMode.labelOf(mode)
                isCheckable = true
                isChecked = (mode == initialScrollMode)

                setOnClickListener {
                    AppConfig.mangaScrollMode = mode
                    callback?.onScrollModeChanged(mode)
                    binding.chipAutoPage.isChecked = false
                }
            })
        }

        binding.chipAutoPage.run {
            isChecked = initialAutoPageEnabled
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onAutoPageToggle(isChecked)
                initAutoPageSpeed(isChecked)
            }
        }

        binding.checkboxDisableClickScroll.apply {
            isChecked = AppConfig.disableClickScroll
            setOnCheckedChangeListener { _, isChecked ->
                AppConfig.disableClickScroll = isChecked
                callback?.onClickScrollDisabledChanged(isChecked)
            }
        }

        binding.checkboxDisableMangaScale.apply {
            isChecked = AppConfig.disableMangaScale
            setOnCheckedChangeListener { _, isChecked ->
                AppConfig.disableMangaScale = isChecked
                callback?.onMangaScaleDisabledChanged(isChecked)
            }
        }

        binding.scvPadding.apply {
            valueFormat = { "$it %" }
            progress = initialSidePadding
            onChanged = { newValue ->
                callback?.upSidePadding(newValue)
                initialSidePadding = newValue
            }
        }

        initAutoPageSpeed(initialAutoPageEnabled)
    }

    private fun initAutoPageSpeed(boolean: Boolean) {
        TransitionManager.beginDelayedTransition(binding.rootView)
        binding.scvAutoPageSpeed.run {
            isEnabled = boolean
            valueFormat = { "$it 秒" }
            progress = initialAutoPageSpeed

            onChanged = { newValue ->
                callback?.onAutoPageSpeedChanged(newValue)
                initialAutoPageSpeed = newValue
            }
        }
    }

    interface Callback {
        fun onAutoPageToggle(enable: Boolean)
        fun onAutoPageSpeedChanged(speed: Int)
        fun onScrollModeChanged(mode: Int)
        fun onClickScrollDisabledChanged(disabled: Boolean)
        fun onMangaScaleDisabledChanged(disabled: Boolean)
        fun upSidePadding(padding: Int)
    }

}
