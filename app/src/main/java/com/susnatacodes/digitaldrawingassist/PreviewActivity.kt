package com.susnatacodes.digitaldrawingassist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class PreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        val imageView = findViewById<ImageView>(R.id.previewImage)
        val btnDone = findViewById<Button>(R.id.btnDone)
        val btnCancel = findViewById<Button>(R.id.btnCancel)

        val uriString = intent.getStringExtra("imageUri")
        if (uriString != null) {
            imageView.setImageURI(Uri.parse(uriString))
        }

        btnDone.setOnClickListener {
            val result = Intent()
            result.putExtra("imageUri", uriString)
            setResult(RESULT_OK, result)
            finish()
        }

        btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }
}
