package com.susnatacodes.digitaldrawingassist

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri

class PreviewActivity : AppCompatActivity() {

    private var imageUriString: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val imageView = findViewById<ImageView>(R.id.previewImage)
        val btnDone = findViewById<Button>(R.id.btnDone)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        // Get image
        imageUriString = intent.getStringExtra("imageUri")

        // Safe loading
        imageUriString?.let {
            imageView.setImageURI(it.toUri())
        } ?: run {
            Toast.makeText(this, "Image load failed", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Done → return to MainActivity
        btnDone.setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra("imageUri", imageUriString)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        // Cancel
        btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }
}