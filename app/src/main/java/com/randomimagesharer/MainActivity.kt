package com.randomimagesharer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.randomimagesharer.utils.FileUtils
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var randomImageView: ImageView
    private lateinit var metadataTextView: TextView
    private lateinit var randomImageButton: MaterialButton
    private lateinit var shareButton: MaterialButton
    private lateinit var folderPathTextView: TextView
    private lateinit var changeFolderButton: MaterialButton
    
    private var currentImageFile: File? = null
    private val STORAGE_PERMISSION_REQUEST_CODE = 1001
    private val TAG = "MainActivity"
    
    private val FOLDER_PICKER_REQUEST_CODE = 1002
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize views
        randomImageView = findViewById(R.id.randomImageView)
        metadataTextView = findViewById(R.id.metadataTextView)
        randomImageButton = findViewById(R.id.randomImageButton)
        shareButton = findViewById(R.id.shareButton)
        folderPathTextView = findViewById(R.id.folderPathTextView)
        changeFolderButton = findViewById(R.id.changeFolderButton)
        
        // Display current folder path
        updateFolderPathDisplay()
        
        // Request storage permissions when app starts - force request on first launch
        requestStoragePermissions()
        
        // Log system state for diagnostics
        FileUtils.logSystemState(this)
        
        // Set up click listeners
        changeFolderButton.setOnClickListener {
            showFolderPathDialog()
        }
        randomImageButton.setOnClickListener {
            if (checkStoragePermissions()) {
                randomImageButton.isEnabled = false
                randomImageButton.text = getString(R.string.loading)
                
                // Use a separate thread for file operations
                Thread {
                    selectRandomImage()
                    
                    // Update UI on the main thread
                    runOnUiThread {
                        randomImageButton.isEnabled = true
                        randomImageButton.text = getString(R.string.random_image_button)
                    }
                }.start()
            } else {
                requestStoragePermissions()
            }
        }
        
        shareButton.setOnClickListener {
            currentImageFile?.let { file ->
                val intent = Intent(this, ImagePreviewActivity::class.java)
                intent.putExtra("image_path", file.absolutePath)
                startActivity(intent)
            } ?: run {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.no_images_found,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
        
        // Initially disable share button and hide metadata until an image is selected
        shareButton.isEnabled = false
        metadataTextView.visibility = View.GONE
    }
    
    /**
     * Update the folder path display in the UI
     */
    private fun updateFolderPathDisplay() {
        // Get a user-friendly display name for the folder
        val displayName = FileUtils.getFolderDisplayName(this)
        folderPathTextView.text = displayName
    }
    
    /**
     * Show folder picker for changing the folder path
     */
    private fun showFolderPathDialog() {
        // Use Storage Access Framework to let user select a folder
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        
        // Set explicit flags for Android 15 compatibility
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        
        // For Android 15+, ensure we're using the most compatible mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("*/*"))
        }
        
        try {
            Log.d(TAG, "Launching folder picker with flags: ${intent.flags}")
            startActivityForResult(intent, FOLDER_PICKER_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching folder picker", e)
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.folder_picker_error,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == FOLDER_PICKER_REQUEST_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    // Persist permission for this URI with explicit flags for Android 15
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    
                    // Log the URI and flags for debugging
                    Log.d(TAG, "Taking persistable URI permission for: $uri with flags: $takeFlags")
                    
                    // Take persistable URI permission
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    
                    // Verify the permission was granted
                    val persistedUris = contentResolver.persistedUriPermissions
                    val hasPersistedPermission = persistedUris.any { it.uri == uri }
                    Log.d(TAG, "URI permission persisted successfully: $hasPersistedPermission")
                    
                    // Save the selected folder URI
                    FileUtils.saveImageFolderUri(this, uri.toString())
                    
                    // Update UI
                    updateFolderPathDisplay()
                    
                    // Show confirmation
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.folder_path_saved,
                        Snackbar.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error persisting URI permission: ${e.message}", e)
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        getString(R.string.folder_permission_error, e.message),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            } ?: run {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.folder_selection_failed,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun selectRandomImage() {
        try {
            val imageFolderPath = FileUtils.getImageFolderPath(this)
            Log.d(TAG, "Selecting random image from folder: $imageFolderPath")
            
            currentImageFile = FileUtils.getRandomImageFile(this, imageFolderPath)
            
            runOnUiThread {
                if (currentImageFile != null) {
                    try {
                        Log.d(TAG, "Found image file: ${currentImageFile!!.absolutePath}")
                        
                        // Verify the file exists and is readable
                        if (!currentImageFile!!.exists()) {
                            Log.e(TAG, "File does not exist: ${currentImageFile!!.absolutePath}")
                            throw java.io.FileNotFoundException("File does not exist: ${currentImageFile!!.absolutePath}")
                        }
                        
                        if (!currentImageFile!!.canRead()) {
                            Log.e(TAG, "File is not readable: ${currentImageFile!!.absolutePath}")
                            throw SecurityException("File is not readable: ${currentImageFile!!.absolutePath}")
                        }
                        
                        // Get URI for the file using FileProvider
                        val uri = FileProvider.getUriForFile(
                            this,
                            "${applicationContext.packageName}.provider",
                            currentImageFile!!
                        )
                        
                        Log.d(TAG, "Created URI for file: $uri")
                        randomImageView.setImageURI(uri)
                        shareButton.isEnabled = true
                        
                        // Display metadata
                        displayMetadata(currentImageFile!!)
                        
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            getString(R.string.image_selected, currentImageFile!!.name),
                            Snackbar.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing selected image file", e)
                        
                        // Reset UI state
                        randomImageView.setImageDrawable(null)
                        shareButton.isEnabled = false
                        metadataTextView.visibility = View.GONE
                        currentImageFile = null
                        
                        // Show error message
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            R.string.error_loading_image,
                            Snackbar.LENGTH_LONG
                        ).setAction(R.string.retry) {
                            if (checkStoragePermissions()) {
                                selectRandomImage()
                            } else {
                                requestStoragePermissions()
                            }
                        }.show()
                    }
                } else {
                    randomImageView.setImageDrawable(null)
                    shareButton.isEnabled = false
                    metadataTextView.visibility = View.GONE
                    
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.no_images_found,
                        Snackbar.LENGTH_LONG
                    ).setAction(R.string.create_test_folder) {
                        createTestFolder()
                    }.show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting random image", e)
            runOnUiThread {
                // Reset UI state
                randomImageView.setImageDrawable(null)
                shareButton.isEnabled = false
                metadataTextView.visibility = View.GONE
                currentImageFile = null
                
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.error_selecting_image,
                    Snackbar.LENGTH_LONG
                ).setAction(R.string.retry) {
                    if (checkStoragePermissions()) {
                        selectRandomImage()
                    } else {
                        requestStoragePermissions()
                    }
                }.show()
            }
        }
    }
    
    private fun createTestFolder() {
        if (checkStoragePermissions()) {
            Thread {
                try {
                    val folderPath = FileUtils.getImageFolderPath(this)
                    val folder = File(folderPath)
                    val success = folder.mkdirs()
                    
                    runOnUiThread {
                        if (success) {
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                R.string.folder_created,
                                Snackbar.LENGTH_LONG
                            ).show()
                        } else {
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                R.string.folder_creation_failed,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating test folder", e)
                    runOnUiThread {
                        Toast.makeText(this, R.string.error_creating_folder, Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        } else {
            requestStoragePermissions()
        }
    }
    
    private fun displayMetadata(file: File) {
        val metadata = FileUtils.parseMetadataFromFilename(file.name)
        if (metadata != null) {
            val (name, dateStr) = metadata
            val formattedDate = FileUtils.formatDateString(dateStr)
            metadataTextView.text = getString(R.string.image_metadata_format, name, formattedDate)
            metadataTextView.visibility = View.VISIBLE
        } else {
            metadataTextView.text = file.name
            metadataTextView.visibility = View.VISIBLE
        }
    }
    
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
    
    private fun requestStoragePermissions() {
        Log.d(TAG, "Requesting storage permissions")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+ (API 30+), request MANAGE_EXTERNAL_STORAGE permission
                try {
                    Log.d(TAG, "Requesting MANAGE_EXTERNAL_STORAGE permission for Android 11+")
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                    
                    Toast.makeText(
                        this,
                        "すべてのファイルへのアクセス権限を許可してください",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Error requesting MANAGE_EXTERNAL_STORAGE permission", e)
                    Toast.makeText(
                        this,
                        "権限リクエストエラー: " + e.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // For older Android versions, request READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE
                Log.d(TAG, "Requesting READ/WRITE_EXTERNAL_STORAGE permissions for Android 10-")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permissions", e)
            Toast.makeText(
                this,
                "権限リクエストエラー: " + e.message,
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            // Log the permission results for debugging
            for (i in permissions.indices) {
                val permission = permissions[i]
                val result = if (i < grantResults.size) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
                } else {
                    "UNKNOWN"
                }
                Log.d(TAG, "Permission result: $permission = $result")
            }
            
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Permissions granted, proceed with operation
                Log.d(TAG, "All storage permissions granted")
                Toast.makeText(
                    this,
                    "ストレージ権限が許可されました",
                    Toast.LENGTH_SHORT
                ).show()
                
                // Update folder path display since we now have permissions
                updateFolderPathDisplay()
            } else {
                // Permissions denied, show message with option to open settings
                Log.d(TAG, "Some storage permissions denied")
                Toast.makeText(
                    this,
                    "ストレージ権限が必要です",
                    Toast.LENGTH_LONG
                ).show()
                
                Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.storage_permissions_required,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.settings) {
                    // Open app settings
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }.show()
            }
        }
    }
}
