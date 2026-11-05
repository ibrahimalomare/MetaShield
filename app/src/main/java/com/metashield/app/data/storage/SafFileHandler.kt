package com.metashield.app.data.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.metashield.app.data.parser.FormatDetector
import com.metashield.app.domain.model.ImageFormat
import com.metashield.app.domain.model.ImportedImageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Handles all Android Storage Access Framework (SAF), Photo Picker, and MediaStore file I/O.
 * Always copies selected URIs into an isolated internal cache file so original user images
 * are NEVER modified or overwritten.
 */
class SafFileHandler(
    private val context: Context
) {
    val importCacheDir: File
        get() = File(context.cacheDir, "imported_originals").apply { mkdirs() }

    val cleanedExportCacheDir: File
        get() = File(context.cacheDir, "cleaned_exports").apply { mkdirs() }

    suspend fun importUriToReadOnlyCache(uri: Uri): Result<ImportedImageInfo> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)
            var displayName = "imported_image"
            var declaredSize = -1L

            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            displayName = cursor.getString(nameIdx)
                        }
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            declaredSize = cursor.getLong(sizeIdx)
                        }
                    }
                }

            val safeName = displayName.replace(Regex("[^a-zA-Z0-9._\-]"), "_").ifBlank { "image.jpg" }
            val destFile = File(importCacheDir, "${System.currentTimeMillis()}_$safeName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: return@withContext Result.failure(IOException("Unable to open input stream for selected URI."))

            val bytes = destFile.readBytes()
            val header = FormatDetector.inspectBytes(bytes, displayName)
            val resolvedFormat = if (header.format != ImageFormat.UNSUPPORTED) {
                header.format
            } else {
                ImageFormat.fromMimeOrExtension(mimeType, displayName)
            }

            Result.success(
                ImportedImageInfo(
                    uriString = uri.toString(),
                    localCachePath = destFile.absolutePath,
                    fileName = displayName,
                    fileSizeBytes = if (destFile.length() > 0) destFile.length() else declaredSize.coerceAtLeast(0L),
                    width = header.width,
                    height = header.height,
                    format = resolvedFormat,
                    sha256Hex = FormatDetector.sha256Hex(bytes)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeCleanedFileToDestinationUri(
        cleanedFilePath: String,
        destinationUri: Uri
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val src = File(cleanedFilePath)
            if (!src.exists()) return@withContext Result.failure(IOException("Cleaned output file does not exist."))
            context.contentResolver.openOutputStream(destinationUri, "wt")?.use { out ->
                FileInputStream(src).use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            } ?: return@withContext Result.failure(IOException("Could not open destination URI for writing."))
            Result.success(src.length())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveCleanedFileToMediaStore(
        cleanedFilePath: String,
        displayFileName: String,
        mimeType: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val src = File(cleanedFilePath)
            if (!src.exists()) return@withContext Result.failure(IOException("Cleaned output file does not exist."))

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayFileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MetaShield")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val insertedUri = resolver.insert(collection, values)
                ?: return@withContext Result.failure(IOException("MediaStore failed to allocate new image entry."))

            resolver.openOutputStream(insertedUri)?.use { out ->
                FileInputStream(src).use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            } ?: return@withContext Result.failure(IOException("Failed to open MediaStore output stream."))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(insertedUri, completeValues, null, null)
            }

            Result.success(insertedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
