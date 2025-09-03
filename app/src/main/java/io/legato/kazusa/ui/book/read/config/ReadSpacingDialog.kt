package io.legato.kazusa.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.databinding.DialogReadBookSpacingBinding
import io.legato.kazusa.help.config.ReadBookConfig
import io.legato.kazusa.ui.book.read.ReadBookActivity
import io.legato.kazusa.utils.postEvent
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding

class ReadSpacingDialog : BaseBottomSheetDialogFragment(R.layout.dialog_read_book_spacing) {

    private val binding by viewBinding(DialogReadBookSpacingBinding::bind)
    private val callBack get() = activity as? ReadBookActivity

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        initView()
        upView()
        initViewEvent()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun initView() = binding.run {
        dsbTextLetterSpacing.valueFormat = {
            ((it - 50) / 100f).toString()
        }
        dsbLineSize.valueFormat = { ((it - 10) / 10f).toString() }
        dsbParagraphSpacing.valueFormat = { (it / 10f).toString() }
    }

    private fun initViewEvent() = binding.run {
        dsbTextLetterSpacing.onChanged = {
            ReadBookConfig.letterSpacing = (it - 50) / 100f
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbLineSize.onChanged = {
            ReadBookConfig.lineSpacingExtra = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbParagraphSpacing.onChanged = {
            ReadBookConfig.paragraphSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }

    private fun upView() = binding.run {
        ReadBookConfig.let {
            dsbTextLetterSpacing.progress = (it.letterSpacing * 100).toInt() + 50
            dsbLineSize.progress = it.lineSpacingExtra
            dsbParagraphSpacing.progress = it.paragraphSpacing
        }
    }
}