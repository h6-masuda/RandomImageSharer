package com.randomimagesharer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.randomimagesharer.utils.FileUtils
import java.io.File

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var metadataTextView: TextView
    private var imageFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        imageView = findViewById(R.id.imageView)
        metadataTextView = findViewById(R.id.metadataTextView)

        // Get the image file passed from MainActivity
        imageFile = intent.getSerializableExtra("imageFile") as? File
        imageFile?.let {
            displayImage(it)
        }

        findViewById<Button>(R.id.shareButton).setOnClickListener {
            shareImage()
        }
    }

    private fun displayImage(file: File) {
        imageView.setImageURI(Uri.fromFile(file))
        metadataTextView.text = "Image: ${file.name}"
    }

    private fun shareImage() {
        imageFile?.let { file ->
            val uri = Uri.fromFile(file)
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "image/*"
            }
            startActivity(Intent.createChooser(shareIntent, "Share Image via"))

            // Delete the image file after sharing
            if (file.delete()) {
                // Optionally handle successful deletion
            }
        }
    }
}