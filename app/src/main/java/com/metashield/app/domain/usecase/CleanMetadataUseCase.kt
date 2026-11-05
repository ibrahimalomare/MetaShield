package com.metashield.app.domain.usecase

import com.metashield.app.domain.model.CleaningAndVerificationResult
import com.metashield.app.domain.model.CleaningOptions
import com.metashield.app.domain.model.MetadataScanReport
import com.metashield.app.domain.model.RecentOperation
import com.metashield.app.domain.repository.MetaShieldRepository
import java.util.UUID

class CleanMetadataUseCase(
    private val repository: MetaShieldRepository
) {
    suspend operator fun invoke(
        scanReport: MetadataScanReport,
        options: CleaningOptions,
        onStageProgress: suspend (Int, String, Float) -> Unit = { _, _, _ -> }
    ): Result<CleaningAndVerificationResult> {
        if (options.removeC2pa && scanReport.c2paReport.hasC2paData && !options.userAuthorizedProvenanceRemoval) {
            return Result.failure(
                IllegalStateException("User authorization is required before removing C2PA Content Credentials.")
            )
        }
        val result = repository.cleanAndVerifyImage(scanReport, options, onStageProgress)
        result.onSuccess { res ->
            repository.recordOperation(
                RecentOperation(
                    id = UUID.randomUUID().toString(),
                    fileName = res.outputFileName ?: scanReport.imageInfo.fileName,
                    format = scanReport.imageInfo.format,
                    originalSizeBytes = scanReport.imageInfo.fileSizeBytes,
                    outputSizeBytes = if (res.outputFileSizeBytes > 0) res.outputFileSizeBytes else null,
                    bytesRemoved = res.bytesRemoved,
                    c2paStatusBefore = scanReport.c2paReport.status,
                    c2paRemoved = options.removeC2pa && (res.afterReport?.c2paReport?.hasC2paData == false),
                    exifRemoved = options.removeExif,
                    xmpRemoved = options.removeXmp,
                    iptcRemoved = options.removeIptc,
                    verdict = res.verdict,
                    timestampEpochMs = System.currentTimeMillis(),
                    outputFilePath = res.outputFilePath
                )
            )
        }
        return result
    }
}
