package com.metashield.app.domain.model

data class ImportedImageInfo(
    val uriString: String,
    val localCachePath: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val sha256Hex: String,
    val importedAtEpochMs: Long = System.currentTimeMillis()
) {
    val megapixels: Double
        get() = if (width > 0 && height > 0) (width.toLong() * height.toLong()) / 1_000_000.0 else 0.0

    val dimensionsLabel: String
        get() = if (width > 0 && height > 0) "${width} × ${height} px" else "Unknown dimensions"

    val formattedFileSize: String
        get() = formatByteCount(fileSizeBytes)

    companion object {
        fun formatByteCount(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format("%.2f KB (%d B)", kb, bytes)
            val mb = kb / 1024.0
            return String.format("%.2f MB (%d B)", mb, bytes)
        }
    }
}
