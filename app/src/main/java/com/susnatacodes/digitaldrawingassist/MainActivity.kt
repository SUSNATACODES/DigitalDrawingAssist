package com.susnatacodes.digitaldrawingassist

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var selectedImageUri: Uri? = null
    private lateinit var previewImage: ImageView
    private lateinit var imageStatus: TextView
    private lateinit var startHint: TextView
    private lateinit var btnStart: Button

    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers grant temporary access only.
            }

            val intent = Intent(this, PreviewActivity::class.java)
                .putExtra(EXTRA_IMAGE_URI, uri.toString())
            previewLauncher.launch(intent)
        }

    private val previewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val uriString = result.data?.getStringExtra(EXTRA_IMAGE_URI) ?: return@registerForActivityResult
            selectedImageUri = Uri.parse(uriString)
            updateSelectedImage()
            Toast.makeText(this, getString(R.string.msg_image_ready), Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewImage = findViewById(R.id.selectedPreview)
        imageStatus = findViewById(R.id.imageStatus)
        startHint = findViewById(R.id.startHint)
        btnStart = findViewById(R.id.btnStart)

        val btnSelect = findViewById<Button>(R.id.btnSelect)
        val btnGuide = findViewById<Button>(R.id.btnGuide)
        val btnDeveloper = findViewById<Button>(R.id.btnDeveloper)
        val animatedViews = listOf<View>(
            findViewById(R.id.mainEyebrow),
            findViewById(R.id.mainTitle),
            findViewById(R.id.mainSubtitle),
            findViewById(R.id.heroPanel),
            findViewById(R.id.toolboxPanel),
            findViewById(R.id.developerSpotlight),
            findViewById(R.id.featureStrip),
            btnSelect,
            btnStart,
            btnGuide,
            btnDeveloper,
            startHint
        )

        selectedImageUri = savedInstanceState
            ?.getString(STATE_IMAGE_URI)
            ?.let(Uri::parse)

        updateSelectedImage()
        UiEffects.applyPressAnimation(btnSelect, btnStart, btnGuide, btnDeveloper)
        UiEffects.playStaggeredEntrance(animatedViews)

        btnSelect.setOnClickListener {
            selectImageLauncher.launch(arrayOf("image/*"))
        }

        btnGuide.setOnClickListener {
            startActivity(Intent(this, CreativeGuideActivity::class.java))
        }

        btnDeveloper.setOnClickListener {
            startActivity(Intent(this, DeveloperInfoActivity::class.java))
        }

        btnStart.setOnClickListener {
            val uri = selectedImageUri
            if (uri == null) {
                Toast.makeText(this, getString(R.string.msg_select_first), Toast.LENGTH_SHORT).show()
                previewImage.animate()
                    .rotationBy(1.8f)
                    .setDuration(70)
                    .withEndAction {
                        previewImage.animate().rotation(0f).setDuration(90).start()
                    }
                    .start()
                return@setOnClickListener
            }

            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                startOverlayService(uri)
            }
        }
    }

    private fun updateSelectedImage() {
        val uri = selectedImageUri
        if (uri == null) {
            previewImage.setImageResource(android.R.drawable.ic_menu_gallery)
            previewImage.alpha = 0.44f
            imageStatus.text = getString(R.string.reference_empty)
            startHint.text = getString(R.string.start_hint_empty)
            btnStart.alpha = 0.76f
            return
        }

        previewImage.alpha = 1f
        previewImage.setImageURI(uri)
        imageStatus.text = getString(R.string.reference_ready)
        startHint.text = getString(R.string.start_hint_ready)
        btnStart.alpha = 1f
    }

    private fun startOverlayService(uri: Uri) {
        val intent = Intent(this, OverlayService::class.java)
            .putExtra(EXTRA_IMAGE_URI, uri.toString())
        startService(intent)
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedImageUri?.let { outState.putString(STATE_IMAGE_URI, it.toString()) }
    }

    companion object {
        const val EXTRA_IMAGE_URI = "imageUri"
        private const val STATE_IMAGE_URI = "selectedImageUri"
    }
}
