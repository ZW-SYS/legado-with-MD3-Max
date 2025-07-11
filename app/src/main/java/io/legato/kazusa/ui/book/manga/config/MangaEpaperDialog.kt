package io.legato.kazusa.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.databinding.DialogMangaEpaperBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding


class MangaEpaperDialog : BaseBottomSheetDialogFragment(R.layout.dialog_manga_epaper) {
    private val binding by viewBinding(DialogMangaEpaperBinding::bind)
    private val callback get() = activity as? Callback
    private var mMangaEInkThreshold = 150

//    override fun onStart() {
//        super.onStart()
//        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
//        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
//    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initData()
        initView()
    }

    private fun initData() {
        binding.dsbEpaper.progress = AppConfig.mangaEInkThreshold
    }

    private fun initView() {
        binding.dsbEpaper.onChanged = {
            mMangaEInkThreshold = it
            callback?.updateEepaper(it)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        AppConfig.mangaEInkThreshold = mMangaEInkThreshold
    }

    interface Callback {
        fun updateEepaper(value: Int)
    }

}