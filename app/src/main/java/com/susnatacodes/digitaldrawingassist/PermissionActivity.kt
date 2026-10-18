package com.susnatacodes.digitaldrawingassist

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat

class PermissionActivity : AppCompatActivity() {

    private var hasOpenedStudio = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val root = findViewById<View>(android.R.id.content)
        val content = findViewById<View>(R.id.permissionContentContainer)
        val btnGrant = findViewById<Button>(R.id.btnGrantPermission)

        UiEffects.applySafeWindowInsets(content)
        UiEffects.constrainWidthToViewport(content, root, maxWidthDp = 600)
        UiEffects.applyPressAnimation(btnGrant)

        btnGrant.setOnClickListener { openOverlaySettings() }
    }

    override fun onResume() {
        super.onResume()
        openStudioIfPermissionGranted()
    }

    private fun openOverlaySettings() {
        val appSettingsIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri(),
        )

        try {
            startActivity(appSettingsIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.permission_settings_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openStudioIfPermissionGranted() {
        if (hasOpenedStudio || !Settings.canDrawOverlays(this)) return
        hasOpenedStudio = true

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
