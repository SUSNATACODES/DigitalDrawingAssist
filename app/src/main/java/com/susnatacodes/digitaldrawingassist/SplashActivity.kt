package com.susnatacodes.digitaldrawingassist

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.WindowCompat

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val card = findViewById<View>(R.id.splashCard)
        val progressContainer = findViewById<FrameLayout>(R.id.splashProgressContainer)
        val progressBar = findViewById<View>(R.id.splashProgressBar)

        // Pop card entrance animation
        card.alpha = 0f
        card.scaleX = 0.82f
        card.scaleY = 0.82f
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(450L)
            .setInterpolator(OvershootInterpolator(1.3f))
            .withEndAction {
                // Animate loading progress bar
                progressContainer.post {
                    val fullWidth = progressContainer.width
                    val animator = ValueAnimator.ofInt(0, fullWidth).apply {
                        duration = 1100L
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { anim ->
                            val params = progressBar.layoutParams
                            params.width = anim.animatedValue as Int
                            progressBar.layoutParams = params
                        }
                    }
                    animator.start()

                    progressContainer.postDelayed(
                        { routeToNextScreen() },
                        1250L,
                    )
                }
            }
            .start()
    }

    private fun routeToNextScreen() {
        val nextClass = if (Settings.canDrawOverlays(this)) {
            MainActivity::class.java
        } else {
            PermissionActivity::class.java
        }

        val intent = Intent(this, nextClass).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out,
        )
        startActivity(intent, options.toBundle())
        finish()
    }
}
