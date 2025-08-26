package io.legato.kazusa.ui.welcome

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import io.legato.kazusa.R
import io.legato.kazusa.constant.PreferKey
import io.legato.kazusa.lib.prefs.ThemeCardPreference
import io.legato.kazusa.lib.prefs.ThemeModePreference

class ThemeFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_strat_theme, rootKey)

        findPreference<ThemeModePreference>(PreferKey.themeMode)?.let {
            it.setOnPreferenceChangeListener { _, _ ->
                true
            }
        }

        findPreference<ThemeCardPreference>(PreferKey.themePref)?.let {
            it.setOnPreferenceChangeListener { _, _ ->
                true
            }
        }
    }
}
