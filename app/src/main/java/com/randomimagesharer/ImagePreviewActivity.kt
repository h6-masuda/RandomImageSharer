package com.randomimagesharer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.randomimagesharer.utils.FileUtils
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ImagePreviewActivity : AppCompatActivity() {
    
    private lateinit var headerTextView: TextView
    private lateinit var previewImageView: ImageView
    private lateinit var metadataTextView: TextView
    private lateinit var detailedMetadataTextView: TextView
    private lateinit var shareTwitterButton: MaterialButton
    private lateinit var deleteButton: MaterialButton
    private lateinit var backButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    
    private var imageFile: File? = null
    private val TAG = "ImagePreviewActivity"
    
    // Gesture detectors for image zooming and panning
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector
    private var scaleFactor = 1.0f
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)
        
        // Initialize views
        headerTextView = findViewById(R.id.headerTextView)
        previewImageView = findViewById(R.id.previewImageView)
        metadataTextView = findViewById(R.id.metadataTextView)
        detailedMetadataTextView = findViewById(R.id.detailedMetadataTextView)
        shareTwitterButton = findViewById(R.id.shareTwitterButton)
        deleteButton = findViewById(R.id.deleteButton)
        backButton = findViewById(R.id.backButton)
        progressBar = findViewById(R.id.progressBar)
        
        // Initialize gesture detectors
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
        gestureDetector = GestureDetector(this, GestureListener())
        
        // Set up touch listener for image view
        previewImageView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
        
        // Get image path from intent
        val imagePath = intent.getStringExtra("image_path")
        if (imagePath != null) {
            imageFile = File(imagePath)
            
            // Check if we have storage permissions before accessing the file
            if (checkStoragePermissions()) {
                displayImage(imageFile!!)
            } else {
                // Show error message and finish activity if we don't have permissions
                Log.e(TAG, "Storage permissions not granted")
                Toast.makeText(
                    this,
                    "ストレージ権限が必要です",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        } else {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.error_loading_image,
                Snackbar.LENGTH_SHORT
            ).show()
            finish()
        }
        
        // Set up click listeners
        shareTwitterButton.setOnClickListener {
            shareImageToTwitter()
        }
        
        deleteButton.setOnClickListener {
            deleteImage()
        }
        
        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun displayImage(file: File) {
        try {
            // Show progress bar while loading
            progressBar.visibility = View.VISIBLE
            
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )
            
            // Use Picasso for better image loading and caching
            Picasso.get()
                .load(uri)
                .fit()
                .centerInside()
                .into(previewImageView, object : Callback {
                    override fun onSuccess() {
                        progressBar.visibility = View.GONE
                    }
                    
                    override fun onError(e: Exception?) {
                        progressBar.visibility = View.GONE
                        Log.e(TAG, "Error loading image", e)
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.error_loading_image,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                })
            
            // Parse and display metadata
            displayMetadata(file)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying image", e)
            progressBar.visibility = View.GONE
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.error_loading_image,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun displayMetadata(file: File) {
        try {
            // Parse filename metadata
            val metadata = FileUtils.parseMetadataFromFilename(file.name)
            if (metadata != null) {
                val (name, dateStr) = metadata
                val formattedDate = FileUtils.formatDateString(dateStr)
                metadataTextView.text = getString(R.string.image_metadata_format, name, formattedDate)
            } else {
                metadataTextView.text = file.name
            }
            
            // Get additional file metadata
            val fileSize = file.length()
            val formattedSize = when {
                fileSize < 1024 -> "$fileSize B"
                fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
                else -> String.format("%.2f MB", fileSize / (1024.0 * 1024.0))
            }
            
            val lastModified = Date(file.lastModified())
            val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
            val formattedDate = dateFormat.format(lastModified)
            
            // Get image dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val width = options.outWidth
            val height = options.outHeight
            
            // Display detailed metadata
            val detailedMetadata = getString(
                R.string.detailed_metadata_format,
                formattedSize,
                formattedDate,
                "$width x $height"
            )
            detailedMetadataTextView.text = detailedMetadata
            detailedMetadataTextView.visibility = View.VISIBLE
            
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying metadata", e)
            detailedMetadataTextView.visibility = View.GONE
        }
    }
    
    private fun shareImageToTwitter() {
        // First check if the file exists and is valid
        if (imageFile == null || !imageFile!!.exists() || !imageFile!!.canRead()) {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.invalid_image_file,
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        
        // Check network connectivity
        if (!isNetworkAvailable()) {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.no_network_connection,
                Snackbar.LENGTH_LONG
            ).setAction(R.string.retry) {
                shareImageToTwitter()
            }.show()
            return
        }
        
        try {
            // Show progress while preparing to share
            progressBar.visibility = View.VISIBLE
            
            // Get content URI via FileProvider
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                imageFile!!
            )
            
            // Create intent for Twitter
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                // Add optional text if needed
                putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text))
                // Target Twitter app specifically
                setPackage("com.twitter.android")
                // Grant read permission to receiving app
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (isTwitterInstalled()) {
                try {
                    startActivity(intent)
                    // Show success message
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.sharing_to_twitter,
                        Snackbar.LENGTH_SHORT
                    ).show()
                    // Show delete button after sharing
                    deleteButton.visibility = View.VISIBLE
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching Twitter intent", e)
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.error_sharing,
                        Snackbar.LENGTH_LONG
                    ).setAction(R.string.retry) {
                        shareImageToTwitter()
                    }.show()
                }
            } else {
                // Twitter not installed, show notification without alternative sharing option
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.twitter_not_installed,
                    Snackbar.LENGTH_LONG
                ).show()
                
                // Do not show delete button since sharing was not performed
                deleteButton.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing image", e)
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.error_sharing,
                Snackbar.LENGTH_LONG
            ).setAction(R.string.retry) {
                shareImageToTwitter()
            }.show()
        } finally {
            // Hide progress bar
            progressBar.visibility = View.GONE
        }
    }
    
    private fun isTwitterInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("com.twitter.android", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * Check if the device has an active internet connection
     * @return True if connected, false otherwise
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                   capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }
    
    private fun deleteImage() {
        // First check if the file exists and is valid
        if (imageFile == null || !imageFile!!.exists()) {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.file_not_found,
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        
        // Check if we have permission to delete the file
        if (!imageFile!!.canWrite()) {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.permission_denied,
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        
        // Show confirmation dialog before deleting
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                // Show progress while deleting
                progressBar.visibility = View.VISIBLE
                
                // Use a background thread for file deletion
                Thread {
                    try {
                        val deleted = FileUtils.deleteFile(this@ImagePreviewActivity, imageFile!!)
                        
                        // Update UI on the main thread
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            
                            if (deleted) {
                                // Success - show message and finish activity
                                Snackbar.make(
                                    findViewById(android.R.id.content),
                                    R.string.image_deleted,
                                    Snackbar.LENGTH_SHORT
                                ).show()
                                
                                // Clear the image view with fade out animation
                                previewImageView.animate()
                                    .alpha(0f)
                                    .setDuration(500)
                                    .withEndAction {
                                        // Clear the image
                                        previewImageView.setImageDrawable(null)
                                        previewImageView.alpha = 1f
                                        
                                        // Clear metadata
                                        metadataTextView.text = ""
                                        detailedMetadataTextView.text = ""
                                        
                                        // Disable share and delete buttons
                                        shareTwitterButton.isEnabled = false
                                        deleteButton.isEnabled = false
                                        
                                        // Show message that image was deleted
                                        Snackbar.make(
                                            findViewById(android.R.id.content),
                                            R.string.image_deleted,
                                            Snackbar.LENGTH_LONG
                                        ).show()
                                        
                                        // Set imageFile to null
                                        imageFile = null
                                    }
                                    .start()
                            } else {
                                // Error - show message with specific error type if possible
                                val errorMessage = when {
                                    !imageFile!!.exists() -> R.string.file_not_found
                                    !imageFile!!.canWrite() -> R.string.permission_denied
                                    else -> R.string.error_deleting
                                }
                                
                                Snackbar.make(
                                    findViewById(android.R.id.content),
                                    errorMessage,
                                    Snackbar.LENGTH_LONG
                                ).setAction(R.string.retry) {
                                    // Retry deletion
                                    deleteImage()
                                }.show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting file", e)
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            
                            // Determine the type of error for a more specific message
                            val errorMessage = when (e) {
                                is SecurityException -> R.string.permission_denied
                                is java.io.FileNotFoundException -> R.string.file_not_found
                                is java.io.IOException -> R.string.file_access_error
                                else -> R.string.unknown_error
                            }
                            
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                errorMessage,
                                Snackbar.LENGTH_LONG
                            ).setAction(R.string.retry) {
                                deleteImage()
                            }.show()
                        }
                    }
                }.start()
            }
            .setNegativeButton(R.string.no, null)
            .setCancelable(true)
            .show()
    }
    
    // Scale gesture listener for zooming
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f))
            previewImageView.scaleX = scaleFactor
            previewImageView.scaleY = scaleFactor
            return true
        }
    }
    
    // Gesture listener for double tap to reset zoom
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            scaleFactor = 1.0f
            previewImageView.scaleX = scaleFactor
            previewImageView.scaleY = scaleFactor
            return true
        }
    }
    
    /**
     * Check if the app has storage permissions
     * @return True if permissions are granted, false otherwise
     */
    private fun checkStoragePermissions(): Boolean {
        // For Android 11+ (API 30+), check for MANAGE_EXTERNAL_STORAGE permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        } else {
            // For older Android versions, check for READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
