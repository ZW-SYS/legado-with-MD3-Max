package io.legato.kazusa.utils

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

object FirebaseManager {

    private const val PREF_KEY = "firebaseEnabled"
    private var isEnabled = false

    fun initFromPreferences(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = prefs.getBoolean(PREF_KEY, true)
        applyState(context, enabled)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit { putBoolean(PREF_KEY, enabled) }
        applyState(context, enabled)
    }

    private fun applyState(context: Context, enabled: Boolean) {
        if (enabled) {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(true)
        } else {
            try {
                if (FirebaseApp.getApps(context).isNotEmpty()) {
                    FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false)
                    FirebaseApp.getInstance().delete()
                }
            } catch (_: Exception) {
            }
        }
        isEnabled = enabled
    }

    fun isFirebaseEnabled(): Boolean = isEnabled
}