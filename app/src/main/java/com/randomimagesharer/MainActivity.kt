package com.randomimagesharer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.randomimagesharer.utils.FileUtils

class MainActivity : AppCompatActivity() {

    private lateinit var randomImageView: ImageView
    private lateinit var selectImageButton: Button
    private lateinit var shareButton: Button
    private var currentImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        randomImageView = findViewById(R.id.randomImageView)
        selectImageButton = findViewById(R.id.selectImageButton)
        shareButton = findViewById(R.id.shareButton)

        selectImageButton.setOnClickListener {
            selectRandomImage()
        }

        shareButton.setOnClickListener {
            currentImageUri?.let { uri ->
                shareImage(uri)
            }
        }
    }

    private fun selectRandomImage() {
        currentImageUri = FileUtils.getRandomImageUri(this)
        currentImageUri?.let {
            randomImageView.setImageURI(it)
        }
    }

    private fun shareImage(imageUri: Uri) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, imageUri)
            type = "image/*"
        }
        startActivity(Intent.createChooser(shareIntent, "Share Image via"))
        FileUtils.deleteImageFile(this, imageUri)
    }
}