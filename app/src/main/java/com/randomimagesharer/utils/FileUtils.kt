package com.randomimagesharer.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.random.Random

object FileUtils {
    private const val TAG = "FileUtils"
    private val IMAGE_EXTENSIONS = arrayOf("jpg", "jpeg", "png")
    private const val TEST_FOLDER_NAME = "TestImages"
    private const val PREF_NAME = "RandomImageViewerPrefs"
    private const val PREF_FOLDER_PATH = "folderPath"
    private const val PREF_FOLDER_URI = "folderUri"
    
    /**
     * Get a random image file from the specified folder
     * @param context The application context
     * @param folderPath The path to the folder containing images
     * @return A random image file or null if no images are found
     */
    fun getRandomImageFile(context: Context, folderPath: String): File? {
        try {
            Log.d(TAG, "Getting random image file from folder: $folderPath, Android version: ${Build.VERSION.SDK_INT}")
            
            // First check if we have a saved folder URI
            val folderUri = getImageFolderUri(context)
            if (folderUri != null) {
                Log.d(TAG, "Using folder URI: $folderUri")
                
                try {
                    // If we have a folder URI, use DocumentFile API
                    val documentFile = getRandomImageFileFromUri(context, folderUri)
                    if (documentFile != null) {
                        Log.d(TAG, "Found document file: ${documentFile.name}, URI: ${documentFile.uri}")
                        
                        // Create a temporary file from the document file
                        try {
                            // Create a unique filename to avoid conflicts
                            val fileName = documentFile.name ?: "temp_image_${System.currentTimeMillis()}.jpg"
                            val cacheDir = context.cacheDir
                            val tempFile = File(cacheDir, fileName)
                            
                            // Delete any existing file with the same name
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }
                            
                            var inputStream: java.io.InputStream? = null
                            var outputStream: java.io.OutputStream? = null
                            
                            try {
                                // Open input stream with explicit content resolver (better for Android 15)
                                inputStream = context.contentResolver.openInputStream(documentFile.uri)
                                if (inputStream == null) {
                                    Log.e(TAG, "Failed to open input stream for document file: ${documentFile.uri}")
                                    throw java.io.IOException("Could not open input stream")
                                }
                                
                                // Open output stream with explicit buffer
                                outputStream = java.io.BufferedOutputStream(java.io.FileOutputStream(tempFile))
                                
                                // Copy data with buffer
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                var totalBytes = 0L
                                
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                    totalBytes += bytesRead
                                }
                                outputStream.flush()
                                
                                Log.d(TAG, "Created temporary file: ${tempFile.absolutePath}, size: $totalBytes bytes")
                                
                                // Verify the file exists and is readable
                                if (!tempFile.exists()) {
                                    Log.e(TAG, "Temporary file does not exist after creation: ${tempFile.absolutePath}")
                                    throw java.io.FileNotFoundException("Temporary file does not exist after creation")
                                }
                                
                                if (!tempFile.canRead()) {
                                    Log.e(TAG, "Temporary file is not readable: ${tempFile.absolutePath}")
                                    throw SecurityException("Temporary file is not readable")
                                }
                                
                                // Check file size to ensure content was properly copied
                                if (tempFile.length() == 0L) {
                                    Log.e(TAG, "Temporary file is empty: ${tempFile.absolutePath}")
                                    throw java.io.IOException("Temporary file is empty after copying")
                                }
                                
                                return tempFile
                            } finally {
                                // Explicit close of streams
                                try {
                                    inputStream?.close()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error closing input stream", e)
                                }
                                try {
                                    outputStream?.close()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error closing output stream", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error creating temporary file from document file", e)
                            return null
                        }
                    } else {
                        Log.e(TAG, "No document file found from URI: $folderUri")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error using DocumentFile API with URI: $folderUri", e)
                }
            } else {
                Log.d(TAG, "No folder URI found, using folder path directly")
            }
            
            // Fallback to legacy methods if URI approach fails
            Log.d(TAG, "Falling back to legacy methods")
            try {
                val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    getRandomImageFileApi29(context, folderPath)
                } else {
                    getRandomImageFileLegacy(context, folderPath)
                }
                
                if (file != null) {
                    // Verify the file exists and is readable
                    if (!file.exists()) {
                        Log.e(TAG, "Legacy file does not exist: ${file.absolutePath}")
                        return null
                    }
                    
                    if (!file.canRead()) {
                        Log.e(TAG, "Legacy file is not readable: ${file.absolutePath}")
                        return null
                    }
                    
                    return file
                } else {
                    Log.e(TAG, "Legacy methods returned null file")
                    return null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in legacy file methods", e)
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getRandomImageFile", e)
            return null
        }
    }
    
    /**
     * Get a random image file from a folder URI using DocumentFile
     * @param context The application context
     * @param folderUri The URI of the folder containing images
     * @return A random image file or null if no images are found
     */
    private fun getRandomImageFileFromUri(context: Context, folderUri: Uri): DocumentFile? {
        try {
            Log.d(TAG, "Getting image from folder URI: $folderUri, SDK: ${Build.VERSION.SDK_INT}")
            
            val documentFolder = DocumentFile.fromTreeUri(context, folderUri)
            if (documentFolder == null) {
                Log.e(TAG, "Invalid document folder: null returned from fromTreeUri")
                return null
            }
            
            if (!documentFolder.exists()) {
                Log.e(TAG, "Document folder does not exist: $folderUri")
                return null
            }
            
            if (!documentFolder.isDirectory) {
                Log.e(TAG, "URI is not a directory: $folderUri")
                return null
            }
            
            // Get all files from the folder
            val allFiles = documentFolder.listFiles()
            Log.d(TAG, "Found ${allFiles.size} files in folder")
            
            // Filter image files only
            val imageFiles = allFiles.filter { file ->
                val isFile = file.isFile
                val isImageType = file.type?.startsWith("image/") == true
                val hasValidExtension = IMAGE_EXTENSIONS.any { ext ->
                    file.name?.lowercase(Locale.getDefault())?.endsWith(".$ext") == true
                }
                
                if (!isFile) {
                    Log.d(TAG, "Skipping non-file: ${file.name}")
                }
                
                if (!isImageType && isFile) {
                    Log.d(TAG, "Skipping non-image type: ${file.type} for ${file.name}")
                }
                
                isFile && (isImageType || hasValidExtension)
            }
            
            if (imageFiles.isEmpty()) {
                Log.e(TAG, "No image files found in folder URI: $folderUri")
                return null
            }
            
            // Use Random with a time-based seed for better randomness
            val randomSeed = System.currentTimeMillis()
            val random = Random(randomSeed)
            val randomIndex = random.nextInt(imageFiles.size)
            
            val selectedFile = imageFiles[randomIndex]
            Log.d(TAG, "Selected random image ${randomIndex + 1} of ${imageFiles.size}: ${selectedFile.name}, type: ${selectedFile.type}, size: ${selectedFile.length()}")
            return selectedFile
        } catch (e: Exception) {
            Log.e(TAG, "Error getting random image from URI: $folderUri", e)
            return null
        }
    }
    
    /**
     * Get a random image file using legacy File API (pre-Android 10)
     * @param context The application context
     * @param folderPath The path to the folder containing images
     * @return A random image file or null if no images are found
     */
    private fun getRandomImageFileLegacy(context: Context, folderPath: String): File? {
        try {
            val folder = File(folderPath)
            
            if (!folder.exists()) {
                Log.e(TAG, "Folder does not exist: $folderPath")
                try {
                    val created = folder.mkdirs()
                    if (created) {
                        Log.d(TAG, "Created folder: $folderPath")
                    } else {
                        Log.e(TAG, "Failed to create folder: $folderPath")
                        return null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating folder: $folderPath", e)
                    return null
                }
            } else if (!folder.isDirectory) {
                Log.e(TAG, "Path exists but is not a directory: $folderPath")
                return null
            }
            
            // Get all files from the folder
            val allFiles = folder.listFiles()
            if (allFiles == null) {
                Log.e(TAG, "Error accessing files in folder: $folderPath")
                return null
            }
            
            Log.d(TAG, "Found ${allFiles.size} files in folder")
            
            // Filter image files only
            val imageFiles = allFiles.filter { file ->
                val isFile = file.isFile
                val hasValidExtension = IMAGE_EXTENSIONS.any { ext ->
                    file.name.lowercase(Locale.getDefault()).endsWith(".$ext")
                }
                
                isFile && hasValidExtension
            }.toTypedArray()
            
            if (imageFiles.isEmpty()) {
                Log.e(TAG, "No image files found in folder: $folderPath")
                return null
            }
            
            // Use Random with a time-based seed for better randomness
            val randomSeed = System.currentTimeMillis()
            val random = Random(randomSeed)
            val randomIndex = random.nextInt(imageFiles.size)
            
            val selectedFile = imageFiles[randomIndex]
            Log.d(TAG, "Selected random image ${randomIndex + 1} of ${imageFiles.size}: ${selectedFile.name}, size: ${selectedFile.length()}")
            
            // Verify the file exists and is readable
            if (!selectedFile.exists()) {
                Log.e(TAG, "Selected file does not exist: ${selectedFile.absolutePath}")
                return null
            }
            
            if (!selectedFile.canRead()) {
                Log.e(TAG, "Selected file is not readable: ${selectedFile.absolutePath}")
                return null
            }
            
            if (selectedFile.length() == 0L) {
                Log.e(TAG, "Selected file is empty: ${selectedFile.absolutePath}")
                return null
            }
            
            return selectedFile
        } catch (e: Exception) {
            Log.e(TAG, "Error in getRandomImageFileLegacy", e)
            return null
        }
    }
    
    /**
     * Get a random image file using MediaStore API (Android 10+)
     * @param context The application context
     * @param folderPath The path to the folder containing images
     * @return A random image file or null if no images are found
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getRandomImageFileApi29(context: Context, folderPath: String): File? {
        val contentResolver = context.contentResolver
        val folderName = File(folderPath).name
        
        // Create the folder if it doesn't exist (for Android 10+)
        createFolderApi29(context, folderName)
        
        // Query for images in the specified folder
        val imageList = mutableListOf<Pair<Uri, String>>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )
        
        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%/$folderName/%")
        
        var cursor: Cursor? = null
        
        try {
            cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val data = it.getString(dataColumn)
                    
                    // Filter by file extension
                    if (IMAGE_EXTENSIONS.any { ext -> name.lowercase(Locale.getDefault()).endsWith(".$ext") }) {
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        imageList.add(Pair(contentUri, data))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore", e)
        } finally {
            cursor?.close()
        }
        
        if (imageList.isEmpty()) {
            Log.e(TAG, "No image files found in folder: $folderPath")
            return null
        }
        
        // Use Random with a time-based seed for better randomness
        val randomSeed = System.currentTimeMillis()
        val random = Random(randomSeed)
        val randomIndex = random.nextInt(imageList.size)
        
        val selectedImage = imageList[randomIndex]
        Log.d(TAG, "Selected random image ${randomIndex + 1} of ${imageList.size}: ${selectedImage.second}")
        
        return File(selectedImage.second)
    }
    
    /**
     * Create a folder in MediaStore (Android 10+)
     * @param context The application context
     * @param folderName The name of the folder to create
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createFolderApi29(context: Context, folderName: String) {
        try {
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, ".nomedia")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/$folderName")
            }
            
            val uri = contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            )
            
            uri?.let {
                contentResolver.delete(it, null, null)
                Log.d(TAG, "Created folder: DCIM/$folderName")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error creating folder: $folderName", e)
        }
    }
    
    /**
     * Get the user's preferred image folder path, or the default if not set
     * @param context The application context
     * @return The path for storing images
     */
    fun getImageFolderPath(context: Context): String {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedPath = sharedPrefs.getString(PREF_FOLDER_PATH, null)
        
        return if (savedPath != null) {
            savedPath
        } else {
            // Return default path if no user preference is set
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+, we'll use a relative path with MediaStore
                "DCIM/$TEST_FOLDER_NAME"
            } else {
                // For older Android versions, use the legacy path
                "${Environment.getExternalStorageDirectory().path}/DCIM/$TEST_FOLDER_NAME"
            }
        }
    }
    
    /**
     * Save the user's preferred image folder path
     * @param context The application context
     * @param folderPath The folder path to save
     */
    fun saveImageFolderPath(context: Context, folderPath: String) {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putString(PREF_FOLDER_PATH, folderPath).apply()
        Log.d(TAG, "Saved user folder path: $folderPath")
    }
    
    /**
     * Get the user's preferred image folder URI, or null if not set
     * @param context The application context
     * @return The URI for the selected folder, or null if not set
     */
    fun getImageFolderUri(context: Context): Uri? {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedUri = sharedPrefs.getString(PREF_FOLDER_URI, null)
        
        return if (savedUri != null) {
            try {
                Uri.parse(savedUri)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing saved URI: $savedUri", e)
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Save the user's preferred image folder URI
     * @param context The application context
     * @param folderUriString The folder URI string to save
     */
    fun saveImageFolderUri(context: Context, folderUriString: String) {
        val sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putString(PREF_FOLDER_URI, folderUriString).apply()
        
        // Also update the folder path for backward compatibility
        try {
            val uri = Uri.parse(folderUriString)
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            documentFile?.let {
                val displayName = it.name ?: "SelectedFolder"
                saveImageFolderPath(context, "DCIM/$displayName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating folder path from URI: $folderUriString", e)
        }
        
        Log.d(TAG, "Saved user folder URI: $folderUriString")
    }
    
    /**
     * Get the default image folder path (for backward compatibility)
     * @return The default path for storing test images
     */
    fun getDefaultImageFolderPath(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, we'll use a relative path with MediaStore
            "DCIM/$TEST_FOLDER_NAME"
        } else {
            // For older Android versions, use the legacy path
            "${Environment.getExternalStorageDirectory().path}/DCIM/$TEST_FOLDER_NAME"
        }
    }
    
    /**
     * Log system state for diagnostics
     * @param context The application context
     */
    fun logSystemState(context: Context) {
        try {
            Log.d(TAG, "======= SYSTEM DIAGNOSTIC INFO =======")
            Log.d(TAG, "Android SDK Version: ${Build.VERSION.SDK_INT}")
            Log.d(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            Log.d(TAG, "App Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
            
            // Log storage paths
            Log.d(TAG, "External Storage Directory: ${Environment.getExternalStorageDirectory().absolutePath}")
            Log.d(TAG, "External Storage State: ${Environment.getExternalStorageState()}")
            Log.d(TAG, "Cache Directory: ${context.cacheDir.absolutePath}")
            
            // Log storage permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d(TAG, "MANAGE_EXTERNAL_STORAGE permission: ${Environment.isExternalStorageManager()}")
            } else {
                val readPermission = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                val writePermission = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                Log.d(TAG, "READ_EXTERNAL_STORAGE permission: ${readPermission == android.content.pm.PackageManager.PERMISSION_GRANTED}")
                Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission: ${writePermission == android.content.pm.PackageManager.PERMISSION_GRANTED}")
            }
            
            // Log persisted URI permissions
            val persistedUris = context.contentResolver.persistedUriPermissions
            Log.d(TAG, "Persisted URI permissions count: ${persistedUris.size}")
            persistedUris.forEachIndexed { index, uriPermission ->
                Log.d(TAG, "URI $index: ${uriPermission.uri}")
                Log.d(TAG, "  - Read permission: ${uriPermission.isReadPermission}")
                Log.d(TAG, "  - Write permission: ${uriPermission.isWritePermission}")
            }
            
            // Log saved folder path and URI
            val folderPath = getImageFolderPath(context)
            val folderUri = getImageFolderUri(context)
            Log.d(TAG, "Saved folder path: $folderPath")
            Log.d(TAG, "Saved folder URI: $folderUri")
            
            Log.d(TAG, "======= END DIAGNOSTIC INFO =======")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging system state", e)
        }
    }
    
    /**
     * Delete a file from storage
     * @param context The application context
     * @param file The file to delete
     * @return True if the file was deleted successfully, false otherwise
     */
    fun deleteFile(context: Context, file: File): Boolean {
        // First check if we have a saved folder URI
        val folderUri = getImageFolderUri(context)
        if (folderUri != null) {
            // Try to delete using DocumentFile API
            val deleted = deleteFileFromUri(context, folderUri, file)
            if (deleted) {
                return true
            }
        }
        
        // Fallback to legacy methods if URI approach fails
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            deleteFileApi29(context, file)
        } else {
            deleteFileLegacy(file)
        }
    }
    
    /**
     * Delete a file using DocumentFile API
     * @param context The application context
     * @param folderUri The URI of the folder containing the file
     * @param file The file to delete
     * @return True if the file was deleted successfully, false otherwise
     */
    private fun deleteFileFromUri(context: Context, folderUri: Uri, file: File): Boolean {
        try {
            val documentFolder = DocumentFile.fromTreeUri(context, folderUri)
            if (documentFolder == null || !documentFolder.exists() || !documentFolder.isDirectory) {
                Log.e(TAG, "Invalid folder URI: $folderUri")
                return false
            }
            
            // Find the file in the folder
            val fileName = file.name
            val documentFile = documentFolder.listFiles().find { it.name == fileName }
            
            if (documentFile != null) {
                val deleted = documentFile.delete()
                if (deleted) {
                    Log.d(TAG, "File deleted successfully via DocumentFile: ${file.absolutePath}")
                    return true
                } else {
                    Log.e(TAG, "Failed to delete file via DocumentFile: ${file.absolutePath}")
                }
            } else {
                Log.e(TAG, "File not found in DocumentFile folder: ${file.absolutePath}")
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file via DocumentFile: ${file.absolutePath}", e)
            return false
        }
    }
    
    /**
     * Delete a file using legacy File API (pre-Android 10)
     * @param file The file to delete
     * @return True if the file was deleted successfully, false otherwise
     */
    private fun deleteFileLegacy(file: File): Boolean {
        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                Log.d(TAG, "File deleted successfully: ${file.absolutePath}")
            } else {
                Log.e(TAG, "Failed to delete file: ${file.absolutePath}")
            }
            deleted
        } else {
            Log.e(TAG, "File does not exist: ${file.absolutePath}")
            false
        }
    }
    
    /**
     * Delete a file using MediaStore API (Android 10+)
     * @param context The application context
     * @param file The file to delete
     * @return True if the file was deleted successfully, false otherwise
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteFileApi29(context: Context, file: File): Boolean {
        try {
            // For Android 10+, we need to use MediaStore to delete files
            val contentResolver = context.contentResolver
            
            // Try to find the file in MediaStore
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(file.absolutePath)
            
            val deletedRows = contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                selection,
                selectionArgs
            )
            
            if (deletedRows > 0) {
                Log.d(TAG, "File deleted successfully via MediaStore: ${file.absolutePath}")
                return true
            }
            
            // If MediaStore deletion failed, try using FileProvider
            try {
                val uri = getUriForFile(context, file)
                if (uri != null) {
                    // Try to delete the file using the URI
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(TAG, "File deleted successfully via FileProvider: ${file.absolutePath}")
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting file via FileProvider: ${file.absolutePath}", e)
            }
            
            // Fallback to legacy method if all else fails
            return deleteFileLegacy(file)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file via MediaStore: ${file.absolutePath}", e)
            // Try legacy method as fallback
            return deleteFileLegacy(file)
        }
    }
    
    /**
     * Get a content URI for a file using FileProvider
     * @param context The application context
     * @param file The file to get a URI for
     * @return A content URI for the file, or null if an error occurs
     */
    fun getUriForFile(context: Context, file: File): Uri? {
        return try {
            val authority = "${context.packageName}.provider"
            androidx.core.content.FileProvider.getUriForFile(
                context,
                authority,
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting URI for file: ${file.absolutePath}", e)
            null
        }
    }
    
    /**
     * Parse metadata from filename in format NAME_YYYYMMDD_NUM
     * @param filename The filename to parse
     * @return A Pair containing the name and date, or null if the format is invalid
     */
    fun parseMetadataFromFilename(filename: String): Pair<String, String>? {
        val regex = "([A-Za-z0-9]+)_(\\d{8})_\\d+".toRegex()
        val matchResult = regex.find(filename) ?: return null
        
        val (name, date) = matchResult.destructured
        return Pair(name, date)
    }
    
    /**
     * Format date string from YYYYMMDD to a more readable format
     * @param dateStr The date string in YYYYMMDD format
     * @return The formatted date string
     */
    fun formatDateString(dateStr: String): String {
        if (dateStr.length != 8) return dateStr
        
        val year = dateStr.substring(0, 4)
        val month = dateStr.substring(4, 6)
        val day = dateStr.substring(6, 8)
        
        return "$year/$month/$day"
    /**
     * Log system and app state for diagnostic purposes
     * @param context The application context
     */
    fun logSystemState(context: Context) {
        Log.d(TAG, "============ SYSTEM DIAGNOSTICS ============")
        Log.d(TAG, "Android SDK: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.d(TAG, "App version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
        
        // Check storage permissions
        val hasReadPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasWritePermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasManagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else false
        
        Log.d(TAG, "READ_EXTERNAL_STORAGE: $hasReadPermission")
        Log.d(TAG, "WRITE_EXTERNAL_STORAGE: $hasWritePermission")
        Log.d(TAG, "MANAGE_EXTERNAL_STORAGE: $hasManagePermission")
        
        // Log folder URI status
        val folderUri = getImageFolderUri(context)
        Log.d(TAG, "Folder URI: $folderUri")
        Log.d(TAG, "============================================")
    }

    }
    /**
     * Get a user-friendly display name for a folder path or URI
     * @param context The application context
     * @return A user-friendly display name for the folder
     */
    fun getFolderDisplayName(context: Context): String {
        // First check if we have a saved folder URI
        val folderUri = getImageFolderUri(context)
        if (folderUri != null) {
            try {
                val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                if (documentFile != null && documentFile.exists()) {
                    return documentFile.name ?: "Selected Folder"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting folder display name from URI", e)
            }
        }
        
        // Fallback to path-based display name
        val folderPath = getImageFolderPath(context)
        val parts = folderPath.split("/")
        return if (parts.isNotEmpty()) {
            parts.last()
        } else {
            folderPath
        }
    }

}
