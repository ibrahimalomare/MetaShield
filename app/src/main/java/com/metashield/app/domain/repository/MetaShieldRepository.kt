package com.metashield.app.domain.repository

import android.net.Uri
import com.metashield.app.domain.model.*
import kotlinx.coroutines.flow.Flow

interface MetaShieldRepository {
    suspend fun importImageFromUri(uri: Uri): Result<ImportedImageInfo>
    suspend fun scanImageMetadata(imageInfo: ImportedImageInfo): Result<MetadataScanReport>
    suspend fun cleanAndVerifyImage(
        scanReport: MetadataScanReport,
        options: CleaningOptions,
        onStageProgress: suspend (stageIndex: Int, stageName: String, progressFraction: Float) -> Unit = { _, _, _ -> }
    ): Result<CleaningAndVerificationResult>
    suspend fun exportCleanedImageToUri(
        cleanedFilePath: String,
        destinationUri: Uri
    ): Result<Long>
    suspend fun exportCleanedImageToGallery(
        cleanedFilePath: String,
        displayFileName: String,
        mimeType: String
    ): Result<Uri>
    fun getRecentOperations(): Flow<List<RecentOperation>>
    suspend fun recordOperation(operation: RecentOperation)
    suspend fun clearRecentOperations()
    fun getDefaultCleaningOptions(): Flow<CleaningOptions>
    suspend fun updateDefaultCleaningOptions(options: CleaningOptions)
}
