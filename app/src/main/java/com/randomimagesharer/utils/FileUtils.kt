package com.randomimagesharer.utils

import android.content.Context
import java.io.File
import java.util.Random

object FileUtils {

    fun getRandomImageFile(context: Context, folderPath: String): File? {
        val folder = File(folderPath)
        val files = folder.listFiles { file -> file.isFile && file.extension in listOf("jpg", "jpeg", "png") }
        return if (files != null && files.isNotEmpty()) {
            files[Random().nextInt(files.size)]
        } else {
            null
        }
    }

    fun deleteFile(file: File): Boolean {
        return file.delete()
    }
}