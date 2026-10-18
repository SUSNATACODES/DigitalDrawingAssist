package com.susnatacodes.digitaldrawingassist

import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private var selectedImageUri: Uri? = null
    private var pendingOverlayUri: Uri? = null
    private lateinit var previewImage: ImageView
    private lateinit var imageStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var mainScrollView: NestedScrollView

    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Document provider granted temporary access only.
            }

            val intent = Intent(this, PreviewActivity::class.java)
                .putExtra(EXTRA_IMAGE_URI, uri.toString())
            previewLauncher.launch(intent)
        }

    private val previewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val uriString = result.data?.getStringExtra(EXTRA_IMAGE_URI) ?: return@registerForActivityResult
            selectedImageUri = uriString.toUri()
            updateSelectedImage()
            Toast.makeText(this, getString(R.string.msg_image_ready), Toast.LENGTH_SHORT).show()
        }

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
        setContentView(R.layout.activity_main)

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        setupWindowInsets()

        previewImage = findViewById(R.id.selectedPreview)
        imageStatus = findViewById(R.id.imageStatus)
        btnStart = findViewById(R.id.btnStart)
        bottomNavigationView = findViewById(R.id.bottomNavigationView)
        mainScrollView = findViewById(R.id.mainScrollView)

        val btnSelect = findViewById<Button>(R.id.btnSelect)

        val animatedViews = listOf<View>(
            findViewById(R.id.mainEyebrow),
            findViewById(R.id.mainTitle),
            findViewById(R.id.mainSubtitle),
            findViewById(R.id.heroPanel),
            btnSelect,
            btnStart,
        )

        selectedImageUri = savedInstanceState?.getString(STATE_IMAGE_URI)?.toUri()
        pendingOverlayUri = savedInstanceState?.getString(STATE_PENDING_OVERLAY_URI)?.toUri()

        updateSelectedImage()
        UiEffects.applyPressAnimation(btnSelect, btnStart)
        UiEffects.playStaggeredEntrance(animatedViews)
        UiEffects.playPulseAnimation(btnStart)

        setupBottomNavigation()

        btnSelect.setOnClickListener {
            selectImageLauncher.launch(arrayOf("image/*"))
        }

        btnStart.setOnClickListener {
            val uri = selectedImageUri
            if (uri == null) {
                Toast.makeText(this, getString(R.string.msg_select_first), Toast.LENGTH_SHORT).show()
                UiEffects.playShakeAnimation(previewImage)
                return@setOnClickListener
            }

            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(this, PermissionActivity::class.java)
                startActivity(intent)
                return@setOnClickListener
            }

            startOverlayService(uri)
        }
    }

    private fun navigateTo(activityClass: Class<*>) {
        val intent = Intent(this, activityClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out,
        )
        startActivity(intent, options.toBundle())
    }

    private fun setupWindowInsets() {
        val root = findViewById<View>(R.id.mainRootLayout) ?: return
        val appBar = findViewById<AppBarLayout>(R.id.mainAppBarLayout)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar?.setPadding(0, systemBars.top, 0, 0)
            bottomNav?.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigationView.selectedItemId = R.id.nav_studio
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_studio -> {
                    mainScrollView.smoothScrollTo(0, 0)
                    true
                }
                R.id.nav_canvas -> {
                    navigateTo(DrawingEdgeActivity::class.java)
                    true
                }
                R.id.nav_guide -> {
                    navigateTo(CreativeGuideActivity::class.java)
                    true
                }
                R.id.nav_developer -> {
                    navigateTo(DeveloperInfoActivity::class.java)
                    true
                }
                else -> false
            }
        }
        bottomNavigationView.setOnItemReselectedListener { item ->
            when (item.itemId) {
                R.id.nav_studio -> mainScrollView.smoothScrollTo(0, 0)
                R.id.nav_canvas -> navigateTo(DrawingEdgeActivity::class.java)
                R.id.nav_guide -> navigateTo(CreativeGuideActivity::class.java)
                R.id.nav_developer -> navigateTo(DeveloperInfoActivity::class.java)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::bottomNavigationView.isInitialized) {
            bottomNavigationView.selectedItemId = R.id.nav_studio
        }
    }

    private fun updateSelectedImage() {
        val uri = selectedImageUri
        if (uri == null) {
            previewImage.setImageResource(android.R.drawable.ic_menu_gallery)
            previewImage.alpha = 0.44f
            imageStatus.text = getString(R.string.reference_empty)
            btnStart.alpha = 0.76f
            return
        }

        previewImage.alpha = 1f
        previewImage.setImageURI(uri)
        imageStatus.text = getString(R.string.reference_ready)
        btnStart.alpha = 1f
    }

    private fun startOverlayService(uri: Uri) {
        val intent = Intent(this, OverlayService::class.java)
            .putExtra(EXTRA_IMAGE_URI, uri.toString())
            .setData(uri)
            .apply {
                clipData = ClipData.newUri(contentResolver, getString(R.string.preview_title), uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        startService(intent)
        pendingOverlayUri = null
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedImageUri?.let { outState.putString(STATE_IMAGE_URI, it.toString()) }
        pendingOverlayUri?.let { outState.putString(STATE_PENDING_OVERLAY_URI, it.toString()) }
    }

    companion object {
        const val EXTRA_IMAGE_URI = "imageUri"
        private const val STATE_IMAGE_URI = "selectedImageUri"
        private const val STATE_PENDING_OVERLAY_URI = "pendingOverlayUri"
    }
}
