package com.susnatacodes.digitaldrawingassist

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

class PreviewActivity : AppCompatActivity() {

    private var uriString: String? = null

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
        setContentView(R.layout.activity_preview)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setupWindowInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.previewToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val imageView = findViewById<ImageView>(R.id.previewImage)
        val previewStage = findViewById<View>(R.id.previewStage)
        val previewStatus = findViewById<TextView>(R.id.previewStatus)
        val btnDone = findViewById<Button>(R.id.btnDone)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        uriString = intent.getStringExtra(MainActivity.EXTRA_IMAGE_URI)

        if (uriString != null) {
            imageView.setImageURI(uriString?.toUri())
            previewStatus.text = getString(R.string.preview_ready)
            btnDone.isEnabled = true
            btnDone.alpha = 1f
        } else {
            previewStatus.text = getString(R.string.preview_missing)
            btnDone.isEnabled = false
            btnDone.alpha = 0.55f
        }

        UiEffects.applyPressAnimation(btnDone, btnCancel)
        UiEffects.playStaggeredEntrance(
            listOf(previewStage, previewStatus, btnCancel, btnDone),
            distance = 22f,
            delayStep = 70L,
        )

        btnDone.setOnClickListener {
            val result = Intent().putExtra(MainActivity.EXTRA_IMAGE_URI, uriString)
            setResult(RESULT_OK, result)
            finish()
        }

        btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun setupWindowInsets() {
        val root = findViewById<View>(R.id.previewRootLayout) ?: return
        val appBar = findViewById<AppBarLayout>(R.id.previewAppBarLayout)
        val content = findViewById<View>(R.id.previewContentContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar?.setPadding(0, systemBars.top, 0, 0)
            content?.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }
}
