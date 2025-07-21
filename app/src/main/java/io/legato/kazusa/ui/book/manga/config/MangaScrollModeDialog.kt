package io.legato.kazusa.ui.book.manga.config

import android.os.Bundle
import android.view.View
import com.google.android.material.chip.Chip
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.databinding.DialogMangaScrollModeBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding

class MangaScrollModeDialog : BaseBottomSheetDialogFragment(R.layout.dialog_manga_scroll_mode) {

    private val binding by viewBinding(DialogMangaScrollModeBinding::bind)

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
                isChecked = (mode == AppConfig.mangaScrollMode)

                setOnClickListener {
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
                callback?.onClickScrollDisabledChanged(isChecked)
            }
        }

        binding.checkboxDisableMangaScale.apply {
            isChecked = AppConfig.disableMangaScale
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onMangaScaleDisabledChanged(isChecked)
            }
        }

        binding.checkboxHideMangaTitle.apply {
            isChecked = AppConfig.hideMangaTitle
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onHideMangaTitleChanged(isChecked)
            }
        }

        binding.scvPadding.apply {
            valueFormat = { "$it %" }
            progress = AppConfig.webtoonSidePaddingDp
            onChanged = { newValue ->
                callback?.upSidePadding(newValue)
            }
        }

        initAutoPageSpeed(initialAutoPageEnabled)
    }

    private fun initAutoPageSpeed(boolean: Boolean) {
        binding.scvAutoPageSpeed.run {
            isEnabled = boolean
            valueFormat = { "$it 秒" }
            progress = AppConfig.mangaAutoPageSpeed

            onChanged = { newValue ->
                callback?.onAutoPageSpeedChanged(newValue)
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
        fun onHideMangaTitleChanged(hide: Boolean)
    }

}
