package com.metashield.app.domain.model

data class CleaningOptions(
    val removeC2pa: Boolean = true,
    val removeExif: Boolean = true,
    val removeXmp: Boolean = true,
    val removeIptc: Boolean = true,
    val removeOtherMetadata: Boolean = true,
    val preserveIccColorProfile: Boolean = true,
    val zeroFillSlackSpace: Boolean = true,
    val userAuthorizedProvenanceRemoval: Boolean = false
) {
    val hasAnySelection: Boolean
        get() = removeC2pa || removeExif || removeXmp || removeIptc || removeOtherMetadata
}

enum class VerificationVerdict(
    val title: String,
    val description: String
) {
    VERIFIED_CLEAN(
        title = "Verified Clean — All Selected Metadata Removed",
        description = "Post-processing re-scan confirmed that zero targeted metadata structures remain in the output file and image dimensions/bitstream are intact."
    ),
    PARTIALLY_CLEANED(
        title = "Partially Cleaned — Residual or Preserved Metadata Detected",
        description = "Targeted structures were processed, but some unselected or format-constrained metadata structures remain in the output file."
    ),
    UNCHANGED(
        title = "No Targeted Metadata Found to Remove",
        description = "The source image did not contain any of the selected metadata categories; a verified clean copy was produced."
    ),
    FAILED(
        title = "Cleaning Failed or Aborted Safely",
        description = "The operation was aborted to prevent file corruption or because the format/container structure is unsupported."
    )
}

data class CategoryComparisonRow(
    val category: MetadataCategory,
    val wasSelectedForRemoval: Boolean,
    val beforeFieldCount: Int,
    val beforeBytes: Int,
    val afterFieldCount: Int,
    val afterBytes: Int,
    val removedSuccessfully: Boolean,
    val statusNote: String
)

data class CleaningAndVerificationResult(
    val success: Boolean,
    val verdict: VerificationVerdict,
    val optionsUsed: CleaningOptions,
    val beforeReport: MetadataScanReport,
    val afterReport: MetadataScanReport?,
    val outputFilePath: String?,
    val outputFileName: String?,
    val outputFileSizeBytes: Long,
    val bytesRemoved: Long,
    val dimensionsPreserved: Boolean,
    val losslessBitstreamPreserved: Boolean,
    val categoryComparisons: List<CategoryComparisonRow>,
    val removedSegmentsSummary: List<String>,
    val remainingOrUnsupportedNotes: List<String>,
    val errorReason: String? = null,
    val processingDurationMs: Long = 0L,
    val externalProvenanceAndWatermarkScopeNotice: String = SCOPE_LIMITATION_NOTICE,
    val provenanceRemovalWarning: String = PROVENANCE_REMOVAL_WARNING
) {
    companion object {
        const val SCOPE_LIMITATION_NOTICE =
            "Scope & Technical Limitation Notice: External manifests stored in remote provenance registries (such as cloud-based Content Credentials matched via perceptual hash) and imperceptible pixel-level watermarks (such as Google SynthID or frequency-domain steganography embedded directly into pixel values) exist outside container metadata headers and are NOT removed by metadata sanitization."

        const val PROVENANCE_REMOVAL_WARNING =
            "Provenance Notice: Removing C2PA Content Credentials deletes embedded cryptographic provenance history from the exported copy and may prevent future verification of the original image's creation or editing history. The original source file on your device has been preserved untouched."
    }
}
