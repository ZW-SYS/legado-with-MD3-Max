package io.legato.kazusa.ui.widget.number

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.NumberPicker
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.legato.kazusa.R


class NumberPickerDialog(context: Context) {
    private val builder = MaterialAlertDialogBuilder(context)
    private var numberPicker: NumberPicker? = null
    private var editText: TextInputEditText? = null
    private var inputLayout: TextInputLayout? = null
    private var tvRange: TextView? = null
    private var maxValue: Int? = null
    private var minValue: Int? = null
    private var value: Int? = null
    private var useInputMode = false

    init {
        builder.setView(R.layout.dialog_number_picker)
    }

    fun setTitle(title: String): NumberPickerDialog {
        builder.setTitle(title)
        return this
    }

    fun setMaxValue(value: Int): NumberPickerDialog {
        maxValue = value
        return this
    }

    fun setMinValue(value: Int): NumberPickerDialog {
        minValue = value
        return this
    }

    fun setValue(value: Int): NumberPickerDialog {
        this.value = value
        return this
    }

    fun setCustomButton(textId: Int, listener: (() -> Unit)?): NumberPickerDialog {
        builder.setNeutralButton(textId) { _, _ ->
            numberPicker?.clearFocus()
            listener?.invoke()
        }
        return this
    }

    fun show(callBack: ((value: Int) -> Unit)?) {
        builder.setPositiveButton(R.string.ok, null)
        builder.setNegativeButton(R.string.cancel, null)
        val dialog = builder.show()

        numberPicker = dialog.findViewById(R.id.number_picker)
        inputLayout = dialog.findViewById(R.id.til_input)
        editText = dialog.findViewById(R.id.et_input)
        tvRange = dialog.findViewById(R.id.tv_range)
        val btnSwitch = dialog.findViewById<MaterialButton>(R.id.btn_switch_input)

        numberPicker?.let { np ->
            minValue?.let { np.minValue = it }
            maxValue?.let { np.maxValue = it }
            value?.let { np.value = it }
        }
        tvRange?.text = "输入范围: ${numberPicker?.minValue} - ${numberPicker?.maxValue}"

        btnSwitch?.setOnClickListener {
            useInputMode = !useInputMode
            if (useInputMode) {
                numberPicker?.visibility = View.GONE
                inputLayout?.visibility = View.VISIBLE
                tvRange?.visibility = View.VISIBLE
                editText?.setText(numberPicker?.value?.toString() ?: value?.toString())
            } else {
                numberPicker?.visibility = View.VISIBLE
                inputLayout?.visibility = View.GONE
                tvRange?.visibility = View.GONE
                inputLayout?.error = null
            }
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            if (useInputMode) {
                val num = editText?.text?.toString()?.toIntOrNull()
                val min = minValue ?: numberPicker?.minValue ?: Int.MIN_VALUE
                val max = maxValue ?: numberPicker?.maxValue ?: Int.MAX_VALUE
                if (num == null || num !in min..max) {
                    inputLayout?.error = "请输入 $min - $max 范围内的数字"
                } else {
                    inputLayout?.error = null
                    callBack?.invoke(num)
                    dialog.dismiss()
                }
            } else {
                numberPicker?.let {
                    it.clearFocus()
                    callBack?.invoke(it.value)
                    dialog.dismiss()
                }
            }
        }
    }
}
