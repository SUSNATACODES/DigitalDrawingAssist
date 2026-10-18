package com.susnatacodes.digitaldrawingassist

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

class CreativeGuideActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_creative_guide)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setupWindowInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.guideToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val btnBack = findViewById<Button>(R.id.btnGuideBack)
        val views = listOf<View>(
            findViewById(R.id.guideEyebrow),
            findViewById(R.id.guideTitle),
            findViewById(R.id.guideSubtitle),
            findViewById(R.id.guideHero),
            findViewById(R.id.guideStepOne),
            findViewById(R.id.guideStepTwo),
            findViewById(R.id.guideStepThree),
            findViewById(R.id.guideStepFour),
            btnBack,
        )

        UiEffects.applyPressAnimation(btnBack)
        UiEffects.playStaggeredEntrance(views, distance = 24f, delayStep = 45L)

        btnBack.setOnClickListener { finish() }
    }

    private fun setupWindowInsets() {
        val root = findViewById<View>(R.id.guideRootLayout) ?: return
        val appBar = findViewById<AppBarLayout>(R.id.guideAppBarLayout)
        val scrollView = findViewById<View>(R.id.guideScrollView)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar?.setPadding(0, systemBars.top, 0, 0)
            scrollView?.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }
}
