package com.metashield.app.domain.model

data class RecentOperation(
    val id: String,
    val fileName: String,
    val format: ImageFormat,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long?,
    val bytesRemoved: Long,
    val c2paStatusBefore: C2paValidationStatus,
    val c2paRemoved: Boolean,
    val exifRemoved: Boolean,
    val xmpRemoved: Boolean,
    val iptcRemoved: Boolean,
    val verdict: VerificationVerdict,
    val timestampEpochMs: Long,
    val outputFilePath: String?
)
