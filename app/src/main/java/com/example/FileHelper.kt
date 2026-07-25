package com.example

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileHelper {

    fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun readBase64FromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val outputStream = ByteArrayOutputStream()
            val base64OutputStream = android.util.Base64OutputStream(outputStream, android.util.Base64.NO_WRAP)

            inputStream?.use { input ->
                base64OutputStream.use { base64Output ->
                    input.copyTo(base64Output)
                }
            }
            outputStream.toString("UTF-8")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = ""
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (name.isEmpty()) {
            name = uri.lastPathSegment ?: "image.jpg"
        }
        return name
    }

    fun createZipOfBytes(filesMap: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, bytes) in filesMap) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    suspend fun saveStreamToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        writer: suspend (java.io.OutputStream) -> Unit
    ): Uri? {
        val resolver = context.contentResolver

        val cleanFileName = if (!fileName.contains(".")) {
            if (mimeType == "application/zip") "$fileName.zip" else "$fileName.jpg"
        } else {
            fileName
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, cleanFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/WarMachineHybrid")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "WarMachineHybrid"
            )
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, cleanFileName)
            return try {
                file.outputStream().buffered().use { out ->
                    writer(out)
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, file.absolutePath)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                }
                resolver.insert(MediaStore.Files.getContentUri("external"), values)
                Uri.fromFile(file)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        val collectionUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val itemUri = resolver.insert(collectionUri, contentValues)
        if (itemUri != null) {
            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    writer(out)
                }
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    resolver.delete(itemUri, null, null)
                } catch (_: Exception) {}
                return null
            }
        }
        return itemUri
    }

    suspend fun saveToDownloads(context: Context, fileName: String, mimeType: String, bytes: ByteArray): Uri? {
        return saveStreamToDownloads(context, fileName, mimeType) { out ->
            out.write(bytes)
        }
    }
}

