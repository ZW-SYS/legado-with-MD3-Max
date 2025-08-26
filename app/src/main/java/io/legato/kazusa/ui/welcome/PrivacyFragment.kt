package io.legato.kazusa.ui.welcome

import android.os.Bundle
import android.view.View
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseFragment
import io.legato.kazusa.databinding.FragmentPrivacyBinding
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding

class PrivacyFragment : BaseFragment(R.layout.fragment_privacy) {

    private val binding by viewBinding(FragmentPrivacyBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {

        binding.tvPrivacy.text =
            String(requireContext().assets.open("privacyPolicy.md").readBytes())
        binding.tvDisclaimer.text =
            String(requireContext().assets.open("disclaimer.md").readBytes())

    }

}
