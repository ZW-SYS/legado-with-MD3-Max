package io.legato.kazusa.ui.welcome

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.legato.kazusa.base.BaseActivity
import io.legato.kazusa.constant.PreferKey
import io.legato.kazusa.databinding.ActivitySplashBinding
import io.legato.kazusa.help.config.LocalConfig
import io.legato.kazusa.ui.book.read.ReadBookActivity
import io.legato.kazusa.ui.main.MainActivity
import io.legato.kazusa.utils.getPrefBoolean
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity<ActivitySplashBinding>() {
    override val binding by viewBinding(ActivitySplashBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { true }
        routeNext()
    }

    private fun routeNext() {
        when {
            LocalConfig.isFirstOpenApp -> {
                go(WelcomeActivity::class.java)
            }
            getPrefBoolean(PreferKey.defaultToRead) -> {
                go(MainActivity::class.java)
                go(ReadBookActivity::class.java)
            }
            else -> {
                go(MainActivity::class.java)
            }
        }
        finish()
    }

    private fun go(target: Class<*>) {
        startActivity(
            Intent(this, target),
            ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle()
        )
    }
}
