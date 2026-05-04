package com.susnatacodes.digitaldrawingassist

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSelect = findViewById<Button>(R.id.btnSelect)
        val btnStart = findViewById<Button>(R.id.btnStart)

        btnSelect.setOnClickListener {
            // Open Gallery
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, 100)
        }

        btnStart.setOnClickListener {
            if (selectedImageUri == null) {
                Toast.makeText(this, getString(R.string.msg_select_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
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
        finish() // Close Main Activity so user sees overlay immediately
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // 1. Image Selected from Gallery
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // Persist permission to read this URI later
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                // Go to Preview
                val intent = Intent(this, PreviewActivity::class.java)
                intent.putExtra("imageUri", uri.toString())
                startActivityForResult(intent, 101)
            }
        }

        // 2. Returned from Preview
        if (requestCode == 101 && resultCode == Activity.RESULT_OK) {
            val uriString = data?.getStringExtra("imageUri")
            if (uriString != null) {
                selectedImageUri = Uri.parse(uriString)
                Toast.makeText(this, "Image Ready", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
