package io.legato.kazusa.ui.welcome

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.postDelayed
import com.google.android.material.transition.platform.MaterialSharedAxis
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
        super.onCreate(savedInstanceState)

        window.exitTransition = null
        window.reenterTransition = null

        if (LocalConfig.isFirstOpenApp) {
            startActivity(
                Intent(this, WelcomeActivity::class.java),
                ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle()
            )
            finish()
            return
        }

        if (getPrefBoolean(PreferKey.defaultToRead)) {
            startActivity(
                Intent(this, MainActivity::class.java),
                ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle()
            )
            startActivity(
                Intent(this, ReadBookActivity::class.java),
                ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle()
            )
            finish()
            return
        }

        startActivity(Intent(this, MainActivity::class.java),
            ActivityOptionsCompat.makeSceneTransitionAnimation(this).toBundle())
        finish()
    }
}

