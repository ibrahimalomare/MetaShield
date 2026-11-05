package com.metashield.app.domain.model

enum class MetadataCategory(
    val title: String,
    val shortCode: String,
    val description: String
) {
    C2PA_CREDENTIALS(
        title = "C2PA Content Credentials",
        shortCode = "C2PA",
        description = "Coalition for Content Provenance and Authenticity cryptographic manifest store"
    ),
    C2PA_MANIFEST(
        title = "Embedded C2PA Manifests",
        shortCode = "MANIFEST",
        description = "Active and ingredient C2PA claims, assertions, and COSE signatures"
    ),
    JUMBF_STRUCTURE(
        title = "JUMBF Box Structures",
        shortCode = "JUMBF",
        description = "ISO/IEC 19566-5 JPEG Universal Metadata Box Format superboxes (jumb / jumd)"
    ),
    EXIF(
        title = "EXIF Metadata",
        shortCode = "EXIF",
        description = "Camera settings, timestamps, device serials, thumbnail IFDs, and GPS coordinates"
    ),
    XMP(
        title = "XMP Metadata",
        shortCode = "XMP",
        description = "Extensible Metadata Platform XML packets, Adobe editing history, and provenance URIs"
    ),
    IPTC(
        title = "IPTC / Photoshop 8BIM",
        shortCode = "IPTC",
        description = "Press/agency creator credits, copyright notices, captions, and 8BIM resources"
    ),
    OTHER(
        title = "Other Embedded Metadata",
        shortCode = "OTHER",
        description = "Comments (COM), PNG text/time chunks (tEXt/iTXt/tIME), and ICC color profiles"
    )
}

enum class PrivacySensitivity(val label: String) {
    CRITICAL("Critical (GPS / Provenance Identity / Serial)"),
    HIGH("High (Device / Software / Author / Timestamps)"),
    MODERATE("Moderate (Technical / Editing Parameters)"),
    LOW("Low (Color Space / Container Framing)")
}

data class MetadataFieldItem(
    val category: MetadataCategory,
    val key: String,
    val value: String,
    val tagIdHex: String? = null,
    val containerLocation: String = "",
    val byteOffset: Long? = null,
    val byteLength: Int? = null,
    val sensitivity: PrivacySensitivity = PrivacySensitivity.MODERATE
)

data class ContainerSegmentInfo(
    val markerOrChunkName: String,
    val category: MetadataCategory?,
    val offset: Long,
    val length: Int,
    val description: String,
    val removableLosslessly: Boolean
)

data class MetadataScanReport(
    val imageInfo: ImportedImageInfo,
    val isCorrupted: Boolean,
    val corruptionReason: String? = null,
    val c2paReport: C2paInspectionReport,
    val fields: List<MetadataFieldItem>,
    val segments: List<ContainerSegmentInfo>,
    val categoryByteCounts: Map<MetadataCategory, Int>,
    val categoryFieldCounts: Map<MetadataCategory, Int>,
    val hasGpsCoordinates: Boolean,
    val hasDeviceIdentifiers: Boolean,
    val totalMetadataBytes: Int,
    val scanDurationMs: Long
) {
    fun fieldsFor(category: MetadataCategory): List<MetadataFieldItem> =
        fields.filter { it.category == category }

    val hasAnyMetadata: Boolean
        get() = totalMetadataBytes > 0 || fields.isNotEmpty() || c2paReport.hasC2paData
}
