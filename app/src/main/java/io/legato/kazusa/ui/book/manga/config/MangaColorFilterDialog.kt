package io.legato.kazusa.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.isVisible
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.databinding.DialogMangaColorFilterBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.utils.GSON
import io.legato.kazusa.utils.fromJsonObject
import io.legato.kazusa.utils.invisible
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import io.legato.kazusa.utils.visible

class MangaColorFilterDialog : BaseBottomSheetDialogFragment(R.layout.dialog_manga_color_filter) {
    private val binding by viewBinding(DialogMangaColorFilterBinding::bind)
    private val mConfig =
        GSON.fromJsonObject<MangaColorFilterConfig>(AppConfig.mangaColorFilter).getOrNull()
            ?: MangaColorFilterConfig()
    private val callback: Callback? get() = activity as? Callback

    private var mMangaEInkThreshold = AppConfig.mangaEInkThreshold


    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initData()
        initView()
    }

    private fun initData() {
        binding.run {
            chipAutoBrightness.isChecked = mConfig.autoBrightness

            if (AppConfig.enableMangaEInk) dsbEpaper.visible()
            else dsbEpaper.invisible()

            dsbEpaper.progress = mMangaEInkThreshold

            cpEpaper.isChecked = AppConfig.enableMangaEInk
            cpEnableGray.isChecked = AppConfig.enableMangaGray

            dsbBrightness.isEnabled = !mConfig.autoBrightness
            dsbBrightness.progress = mConfig.l
            dsbR.progress = mConfig.r
            dsbG.progress = mConfig.g
            dsbB.progress = mConfig.b
            dsbA.progress = mConfig.a
        }
    }

    private fun initView() {
        binding.run {
            chipAutoBrightness.setOnCheckedChangeListener { _, isChecked ->
                mConfig.autoBrightness = isChecked
                dsbBrightness.isEnabled = !isChecked
                callback?.updateColorFilter(mConfig)
            }

            cpEpaper.setOnCheckedChangeListener { _, isChecked ->
                binding.dsbEpaper.isVisible = isChecked
                if (isChecked) {
                    cpEnableGray.isChecked = false
                    callback?.updateGrayMode(false)
                    dsbEpaper.visible()
                } else dsbEpaper.invisible()
                callback?.updateEpaperMode(isChecked, mMangaEInkThreshold)
            }

            cpEnableGray.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    binding.dsbEpaper.isVisible = false
                    cpEpaper.isChecked = false
                    callback?.updateEpaperMode(false, mMangaEInkThreshold)
                }
                dsbEpaper.invisible()
                callback?.updateGrayMode(isChecked)
            }

            dsbEpaper.onChanged = {
                mMangaEInkThreshold = it
                callback?.updateEpaperMode(true, it)
            }

            dsbBrightness.onChanged = {
                mConfig.l = it
                callback?.updateColorFilter(mConfig)
            }
            dsbR.onChanged = {
                mConfig.r = it
                callback?.updateColorFilter(mConfig)
            }
            dsbG.onChanged = {
                mConfig.g = it
                callback?.updateColorFilter(mConfig)
            }
            dsbB.onChanged = {
                mConfig.b = it
                callback?.updateColorFilter(mConfig)
            }
            dsbA.onChanged = {
                mConfig.a = it
                callback?.updateColorFilter(mConfig)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        AppConfig.mangaColorFilter = mConfig.toJson()
        AppConfig.mangaEInkThreshold = mMangaEInkThreshold
    }

    interface Callback {
        fun updateColorFilter(config: MangaColorFilterConfig)
        fun updateEpaperMode(enabled: Boolean, threshold: Int)
        fun updateGrayMode(enabled: Boolean)
    }

}