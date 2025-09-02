package io.legato.kazusa.ui.book.manga.config

import android.os.Bundle
import android.view.View
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.databinding.DialogMangaAutoReadBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding

class MangaAutoReadDialog : BaseBottomSheetDialogFragment(R.layout.dialog_manga_auto_read) {

    private val binding by viewBinding(DialogMangaAutoReadBinding::bind)

    var initialAutoPageEnabled: Boolean = false

    var callback: Callback? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
    }

    private fun initView() {

        binding.chipAutoPage.run {
            isChecked = initialAutoPageEnabled
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onAutoPageToggle(isChecked)
                initAutoPageSpeed(isChecked)
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
    }

}
