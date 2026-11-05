package com.metashield.app.data.repository

import android.net.Uri
import com.metashield.app.data.cleaner.MetadataCleaningEngine
import com.metashield.app.data.parser.MetadataScannerEngine
import com.metashield.app.data.storage.PreferencesAndHistoryStore
import com.metashield.app.data.storage.SafFileHandler
import com.metashield.app.domain.model.*
import com.metashield.app.domain.repository.MetaShieldRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MetaShieldRepositoryImpl(
    private val safFileHandler: SafFileHandler,
    private val scannerEngine: MetadataScannerEngine,
    private val cleaningEngine: MetadataCleaningEngine,
    private val preferencesStore: PreferencesAndHistoryStore
) : MetaShieldRepository {

    override suspend fun importImageFromUri(uri: Uri): Result<ImportedImageInfo> {
        return safFileHandler.importUriToReadOnlyCache(uri)
    }

    override suspend fun scanImageMetadata(imageInfo: ImportedImageInfo): Result<MetadataScanReport> =
        withContext(Dispatchers.Default) {
            try {
                val file = File(imageInfo.localCachePath)
                val report = scannerEngine.scanFile(file, imageInfo)
                Result.success(report)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun cleanAndVerifyImage(
        scanReport: MetadataScanReport,
        options: CleaningOptions,
        onStageProgress: suspend (stageIndex: Int, stageName: String, progressFraction: Float) -> Unit
    ): Result<CleaningAndVerificationResult> = withContext(Dispatchers.Default) {
        try {
            val result = cleaningEngine.cleanAndVerify(
                scanReport = scanReport,
                options = options,
                outputDirectory = safFileHandler.cleanedExportCacheDir,
                onStageProgress = onStageProgress
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportCleanedImageToUri(
        cleanedFilePath: String,
        destinationUri: Uri
    ): Result<Long> {
        return safFileHandler.writeCleanedFileToDestinationUri(cleanedFilePath, destinationUri)
    }

    override suspend fun exportCleanedImageToGallery(
        cleanedFilePath: String,
        displayFileName: String,
        mimeType: String
    ): Result<Uri> {
        return safFileHandler.saveCleanedFileToMediaStore(cleanedFilePath, displayFileName, mimeType)
    }

    override fun getRecentOperations(): Flow<List<RecentOperation>> =
        preferencesStore.recentOperationsFlow

    override suspend fun recordOperation(operation: RecentOperation) {
        preferencesStore.appendOperation(operation)
    }

    override suspend fun clearRecentOperations() {
        preferencesStore.clearHistory()
    }

    override fun getDefaultCleaningOptions(): Flow<CleaningOptions> =
        preferencesStore.defaultCleaningOptionsFlow

    override suspend fun updateDefaultCleaningOptions(options: CleaningOptions) {
        preferencesStore.updateDefaultCleaningOptions(options)
    }
}
