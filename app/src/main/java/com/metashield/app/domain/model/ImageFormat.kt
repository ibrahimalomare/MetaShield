package com.metashield.app.domain.model

enum class ImageFormat(
    val displayName: String,
    val mimeType: String,
    val defaultExtension: String,
    val supportsLosslessCleaning: Boolean,
    val c2paCarrierDescription: String
) {
    JPEG(
        displayName = "JPEG",
        mimeType = "image/jpeg",
        defaultExtension = "jpg",
        supportsLosslessCleaning = true,
        c2paCarrierDescription = "APP11 (0xFFEB) JPEG XT 'JP' Box Segment containing JUMBF superbox"
    ),
    PNG(
        displayName = "PNG",
        mimeType = "image/png",
        defaultExtension = "png",
        supportsLosslessCleaning = true,
        c2paCarrierDescription = "Ancillary 'caBX' PNG Chunk containing JUMBF superbox"
    ),
    WEBP(
        displayName = "WebP",
        mimeType = "image/webp",
        defaultExtension = "webp",
        supportsLosslessCleaning = true,
        c2paCarrierDescription = "RIFF 'C2PA' / 'jumb' FourCC Chunk + VP8X extended flags"
    ),
    TIFF(
        displayName = "TIFF",
        mimeType = "image/tiff",
        defaultExtension = "tiff",
        supportsLosslessCleaning = true,
        c2paCarrierDescription = "IFD0 Tag 0xCD41 (52545) C2PA_MANIFEST UNDEFINED byte array"
    ),
    UNSUPPORTED(
        displayName = "Unsupported Format",
        mimeType = "application/octet-stream",
        defaultExtension = "bin",
        supportsLosslessCleaning = false,
        c2paCarrierDescription = "Unknown or unsupported image container"
    );

    companion object {
        fun fromMimeOrExtension(mimeType: String?, fileName: String?): ImageFormat {
            val lowerMime = mimeType?.lowercase() ?: ""
            val lowerName = fileName?.lowercase() ?: ""
            return when {
                lowerMime == "image/jpeg" || lowerMime == "image/jpg" ||
                    lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> JPEG
                lowerMime == "image/png" || lowerName.endsWith(".png") -> PNG
                lowerMime == "image/webp" || lowerName.endsWith(".webp") -> WEBP
                lowerMime == "image/tiff" || lowerMime == "image/tif" ||
                    lowerName.endsWith(".tif") || lowerName.endsWith(".tiff") -> TIFF
                else -> UNSUPPORTED
            }
        }
    }
}
