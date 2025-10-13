package io.legato.kazusa.ui.dict

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.fragment.app.viewModels
import androidx.transition.TransitionManager
import com.google.android.material.tabs.TabLayout
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.data.entities.DictRule
import io.legato.kazusa.databinding.DialogDictBinding
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.setHtml
import io.legato.kazusa.utils.toastOnUi
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import io.legato.kazusa.utils.visible

/**
 * 词典
 */
class DictDialog() : BaseBottomSheetDialogFragment(R.layout.dialog_dict) {

    constructor(word: String) : this() {
        arguments = Bundle().apply {
            putString("word", word)
        }
    }

    private val viewModel by viewModels<DictViewModel>()
    private val binding by viewBinding(DialogDictBinding::bind)

    private var word: String? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvDict.movementMethod = LinkMovementMethod()
        word = arguments?.getString("word")
        if (word.isNullOrEmpty()) {
            toastOnUi(R.string.cannot_empty)
            dismiss()
            return
        }

        setupTabs()
        observeDicts()
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabSelected(tab: TabLayout.Tab) {
                val dictRule = tab.tag as DictRule
                loadDict(dictRule)
            }
        })
    }

    private fun observeDicts() {
        viewModel.initData { dictRules ->
            if (dictRules.isEmpty()) {
                showEmptyView("暂无可用词典")
                return@initData
            }

            binding.emptyMessageView.gone()
            dictRules.forEach {
                binding.tabLayout.addTab(binding.tabLayout.newTab().apply {
                    text = it.name
                    tag = it
                })
            }
            setupTabLayoutMode(dictRules.size)


            binding.tabLayout.getTabAt(0)?.select()
        }
    }

    private fun loadDict(dictRule: DictRule) {

        binding.tvDict.gone()
        binding.emptyMessageView.gone()
        binding.rotateLoading.visible()

        viewModel.dict(dictRule, word!!) { result ->
            binding.rotateLoading.gone()
            if (result.isBlank()) {
                showEmptyView("没有查询到结果")
            } else {
                binding.tvDict.visible()
                binding.tvDict.setHtml(result)
            }
        }
    }

    private fun setupTabLayoutMode(dictCount: Int) {
        if (dictCount <= 4) {
            binding.tabLayout.tabMode = TabLayout.MODE_FIXED
            binding.tabLayout.tabGravity = TabLayout.GRAVITY_FILL
        } else {
            binding.tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
            binding.tabLayout.tabGravity = TabLayout.GRAVITY_CENTER
        }
    }

    private fun showEmptyView(message: String) {
        binding.tvDict.gone()
        binding.rotateLoading.gone()
        binding.emptyMessageView.visible()
        binding.emptyMessageView.setMessage(message)
    }
}
