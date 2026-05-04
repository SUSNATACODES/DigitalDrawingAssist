package com.susnatacodes.digitaldrawingassist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var selectedImageUri: Uri? = null

    // 📂 Image picker (modern way)
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                // Go to preview
                val intent = Intent(this, PreviewActivity::class.java)
                intent.putExtra("imageUri", it.toString())
                previewLauncher.launch(intent)
            }
        }

    // 👁 Preview result
    private val previewLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uriString = result.data?.getStringExtra("imageUri")
                uriString?.let {
                    selectedImageUri = Uri.parse(it)
                    Toast.makeText(this, "Image Ready ✔", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSelect = findViewById<Button>(R.id.btnSelect)
        val btnStart = findViewById<Button>(R.id.btnStart)

        // 📂 Select Image
        btnSelect.setOnClickListener {
            pickImage.launch(arrayOf("image/*"))
        }

        // ▶ Start Overlay
        btnStart.setOnClickListener {

            if (selectedImageUri == null) {
                Toast.makeText(this, getString(R.string.msg_select_first), Toast.LENGTH_SHORT).show()
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
                startOverlayService()
            }
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("imageUri", selectedImageUri.toString())
        startService(intent)

        // Close app so overlay is visible immediately
        finish()
    }

}
