package com.susnatacodes.digitaldrawingassist

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.WindowCompat

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private var progressContainer: FrameLayout? = null
    private var progressAnimator: ValueAnimator? = null
    private var routeRunnable: Runnable? = null
    private var hasRouted = false

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

        val scrollView = findViewById<View>(R.id.splashScrollView)
        val card = findViewById<View>(R.id.splashCard)
        progressContainer = findViewById(R.id.splashProgressContainer)
        val progressBar = findViewById<View>(R.id.splashProgressBar)

        UiEffects.applySafeWindowInsets(scrollView)
        UiEffects.constrainWidthToViewport(card, scrollView, maxWidthDp = 400)

        card.alpha = 0f
        card.scaleX = 0.96f
        card.scaleY = 0.96f
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(360L)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .withEndAction { startProgressAnimation(progressBar) }
            .start()
    }

    private fun startProgressAnimation(progressBar: View) {
        val container = progressContainer ?: return
        container.post {
            if (isFinishing || isDestroyed || hasRouted) return@post

            val fullWidth = (container.width - container.paddingLeft - container.paddingRight)
                .coerceAtLeast(0)
            progressAnimator = ValueAnimator.ofInt(0, fullWidth).apply {
                duration = 900L
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val params = progressBar.layoutParams
                    params.width = animator.animatedValue as Int
                    progressBar.layoutParams = params
                }
                start()
            }

            routeRunnable = Runnable { routeToNextScreen() }.also { runnable ->
                container.postDelayed(runnable, 1050L)
            }
        }
    }

    private fun routeToNextScreen() {
        if (hasRouted || isFinishing || isDestroyed) return
        hasRouted = true

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

    override fun onDestroy() {
        progressAnimator?.cancel()
        routeRunnable?.let { runnable -> progressContainer?.removeCallbacks(runnable) }
        super.onDestroy()
    }
}
