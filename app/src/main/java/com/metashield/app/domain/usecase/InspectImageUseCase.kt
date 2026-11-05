package com.metashield.app.domain.usecase

import android.net.Uri
import com.metashield.app.domain.model.ImportedImageInfo
import com.metashield.app.domain.model.MetadataScanReport
import com.metashield.app.domain.repository.MetaShieldRepository

class InspectImageUseCase(
    private val repository: MetaShieldRepository
) {
    suspend fun importAndScan(uri: Uri): Result<MetadataScanReport> {
        val importResult = repository.importImageFromUri(uri)
        val imported = importResult.getOrElse { return Result.failure(it) }
        return repository.scanImageMetadata(imported)
    }

    suspend fun rescanExisting(imageInfo: ImportedImageInfo): Result<MetadataScanReport> {
        return repository.scanImageMetadata(imageInfo)
    }
}
