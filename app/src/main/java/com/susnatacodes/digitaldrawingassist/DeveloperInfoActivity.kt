package com.susnatacodes.digitaldrawingassist

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

class DeveloperInfoActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_developer_info)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setupWindowInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.developerToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val btnWebsite = findViewById<Button>(R.id.btnDeveloperWebsite)
        val btnGithub = findViewById<Button>(R.id.btnDeveloperGithub)
        val btnShare = findViewById<Button>(R.id.btnDeveloperShare)
        val btnCopy = findViewById<Button>(R.id.btnDeveloperCopy)
        val btnBack = findViewById<Button>(R.id.btnDeveloperBack)

        val animatedViews = listOf<View>(
            findViewById(R.id.developerEyebrow),
            findViewById(R.id.developerHero),
            findViewById(R.id.developerLinksCard),
            findViewById(R.id.developerQualityCard),
            btnWebsite,
            btnGithub,
            btnShare,
            btnCopy,
            btnBack,
        )

        UiEffects.applyPressAnimation(btnWebsite, btnGithub, btnShare, btnCopy, btnBack)
        UiEffects.playStaggeredEntrance(animatedViews, distance = 24f, delayStep = 45L)

        btnWebsite.setOnClickListener { openUrl(getString(R.string.developer_website_url)) }
        btnGithub.setOnClickListener { openUrl(getString(R.string.developer_github_url)) }
        btnShare.setOnClickListener { shareDeveloperProfile() }
        btnCopy.setOnClickListener { copyDeveloperLinks() }
        btnBack.setOnClickListener { finish() }
    }

    private fun setupWindowInsets() {
        val root = findViewById<View>(R.id.developerRootLayout) ?: return
        val appBar = findViewById<AppBarLayout>(R.id.developerAppBarLayout)
        val scrollView = findViewById<View>(R.id.developerScrollView)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar?.setPadding(0, systemBars.top, 0, 0)
            scrollView?.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.msg_no_browser), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareDeveloperProfile() {
        val shareBody = getString(
            R.string.developer_share_body,
            getString(R.string.developer_website_url),
            getString(R.string.developer_github_url),
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.developer_share_subject))
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.developer_share_title)))
    }

    private fun copyDeveloperLinks() {
        val links = getString(
            R.string.developer_copy_body,
            getString(R.string.developer_website_url),
            getString(R.string.developer_github_url),
        )
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.developer_links_label), links))
        Toast.makeText(this, getString(R.string.msg_links_copied), Toast.LENGTH_SHORT).show()
    }
}
