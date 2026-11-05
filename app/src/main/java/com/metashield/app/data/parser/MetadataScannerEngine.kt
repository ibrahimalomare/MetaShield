package com.metashield.app.data.parser

import com.metashield.app.data.c2pa.C2paSdkBridge
import com.metashield.app.data.c2pa.CborCoseInspector
import com.metashield.app.data.c2pa.JumbfBinaryParser
import com.metashield.app.domain.model.*
import java.io.File

/**
 * Unified Metadata & C2PA Content Credentials Scanner Engine.
 * Automatically scans imported images across all 7 required detection layers:
 * 1. C2PA Content Credentials
 * 2. Embedded C2PA manifests
 * 3. JUMBF structures associated with C2PA
 * 4. EXIF metadata
 * 5. XMP metadata
 * 6. IPTC metadata
 * 7. Other identifiable embedded metadata (COM, ICC_PROFILE, tEXt, iTXt, tIME, etc.)
 */
class MetadataScannerEngine {

    fun scanFile(file: File, importedInfo: ImportedImageInfo? = null): MetadataScanReport {
        val startTime = System.currentTimeMillis()
        val bytes = if (file.exists()) file.readBytes() else ByteArray(0)
        return scanBytes(bytes, file, importedInfo, startTime)
    }

    fun scanBytes(
        bytes: ByteArray,
        backingFile: File? = null,
        importedInfo: ImportedImageInfo? = null,
        startTimeMs: Long = System.currentTimeMillis()
    ): MetadataScanReport {
        val header = FormatDetector.inspectBytes(bytes, importedInfo?.fileName ?: backingFile?.name)
        val info = importedInfo?.copy(
            format = header.format,
            width = if (header.width > 0) header.width else importedInfo.width,
            height = if (header.height > 0) header.height else importedInfo.height,
            fileSizeBytes = bytes.size.toLong()
        ) ?: ImportedImageInfo(
            uriString = backingFile?.toURI()?.toString() ?: "memory://buffer",
            localCachePath = backingFile?.absolutePath ?: "",
            fileName = backingFile?.name ?: "image.${header.format.defaultExtension}",
            fileSizeBytes = bytes.size.toLong(),
            width = header.width,
            height = header.height,
            format = header.format,
            sha256Hex = if (bytes.isNotEmpty()) FormatDetector.sha256Hex(bytes) else ""
        )

        if (!header.isStructurallyValid || header.format == ImageFormat.UNSUPPORTED) {
            val emptyC2pa = C2paInspectionReport(
                status = C2paValidationStatus.NO_CREDENTIALS_FOUND,
                manifestExists = false,
                jumbfDetected = false,
                xmpProvenanceDetected = false,
                inspectedViaOfficialSdk = false,
                sdkVersionInfo = "Skipped (Corrupted or Unsupported Container)",
                activeManifestId = null,
                manifests = emptyList(),
                jumbfBoxes = emptyList(),
                totalC2paBytes = 0,
                carrierLocations = emptyList(),
                validationErrors = listOfNotNull(header.corruptionReason),
                rawManifestJson = null
            )
            return MetadataScanReport(
                imageInfo = info,
                isCorrupted = true,
                corruptionReason = header.corruptionReason,
                c2paReport = emptyC2pa,
                fields = emptyList(),
                segments = emptyList(),
                categoryByteCounts = MetadataCategory.entries.associateWith { 0 },
                categoryFieldCounts = MetadataCategory.entries.associateWith { 0 },
                hasGpsCoordinates = false,
                hasDeviceIdentifiers = false,
                totalMetadataBytes = 0,
                scanDurationMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
            )
        }

        // 1. Extract XMP first so XMP dcterms:provenance URIs are passed to C2PA inspector
        val xmpResult = XmpXmlParser.extractXmp(bytes, header.format)

        // 2. Extract JUMBF binary structures & query official C2PA Android SDK Bridge
        val jumbfPayload = JumbfBinaryParser.extractFromContainer(bytes, header.format)
        val sdkOutput = if (backingFile != null && backingFile.exists()) {
            C2paSdkBridge.readManifestWithOfficialSdk(backingFile, header.format.mimeType)
        } else {
            C2paSdkBridge.SdkInspectionOutput(
                sdkAvailableOnClasspath = false,
                invokedSuccessfully = false,
                manifestStoreJson = null,
                sdkStatusDescription = "MetaShield On-Device JUMBF / ISO-19566-5 / COSE Inspector Engine"
            )
        }
        val c2paReport = CborCoseInspector.inspectJumbfPayload(
            jumbfPayload = jumbfPayload,
            xmpProvenanceUri = xmpResult.c2paProvenanceUri,
            sdkJsonResult = sdkOutput
        )

        // 3. Extract EXIF, IPTC, and Other Embedded Metadata + Segment Map
        val exifFields = ExifBinaryReader.extractExifFields(bytes, header.format)
        val iptcResult = IptcIimParser.extractIptc(bytes, header.format)
        val (otherFields, segments, exifBytesCount, otherBytesCount) = inspectContainerSegments(bytes, header.format)

        // 4. Populate C2PA & JUMBF MetadataFieldItems for unified scanner display
        val c2paFields = mutableListOf<MetadataFieldItem>()
        if (c2paReport.hasC2paData) {
            c2paFields.add(
                MetadataFieldItem(
                    category = MetadataCategory.C2PA_CREDENTIALS,
                    key = "Validation Status",
                    value = "${c2paReport.status.displayTitle} (${c2paReport.status.badgeLabel})",
                    containerLocation = c2paReport.carrierLocations.firstOrNull() ?: "Container Header",
                    byteLength = c2paReport.totalC2paBytes,
                    sensitivity = PrivacySensitivity.CRITICAL
                )
            )
            c2paReport.primaryManifest?.let { m ->
                c2paFields.add(
                    MetadataFieldItem(
                        category = MetadataCategory.C2PA_MANIFEST,
                        key = "Active Manifest ID",
                        value = m.manifestId,
                        containerLocation = "c2pa.claim",
                        sensitivity = PrivacySensitivity.CRITICAL
                    )
                )
                m.claimGenerator?.let { cg ->
                    c2paFields.add(
                        MetadataFieldItem(
                            category = MetadataCategory.C2PA_MANIFEST,
                            key = "Claim Generator",
                            value = cg,
                            containerLocation = "c2pa.claim",
                            sensitivity = PrivacySensitivity.HIGH
                        )
                    )
                }
                m.signerIssuer?.let { iss ->
                    c2paFields.add(
                        MetadataFieldItem(
                            category = MetadataCategory.C2PA_CREDENTIALS,
                            key = "Signer / Certificate Issuer",
                            value = iss,
                            containerLocation = "c2pa.signature (COSE_Sign1)",
                            sensitivity = PrivacySensitivity.CRITICAL
                        )
                    )
                }
                m.actions.forEachIndexed { idx, act ->
                    c2paFields.add(
                        MetadataFieldItem(
                            category = MetadataCategory.C2PA_MANIFEST,
                            key = "Recorded Action #${idx + 1}",
                            value = buildString {
                                append(act.action)
                                act.softwareAgent?.let { append(" via $it") }
                                act.digitalSourceType?.let { append(" [${it.substringAfterLast('/')}]") }
                            },
                            containerLocation = "c2pa.assertions / c2pa.actions",
                            sensitivity = PrivacySensitivity.HIGH
                        )
                    )
                }
            }
        }

        val jumbfFields = c2paReport.jumbfBoxes.map { box ->
            MetadataFieldItem(
                category = MetadataCategory.JUMBF_STRUCTURE,
                key = "Box '${box.boxType}'" + (box.label?.let { " ($it)" } ?: ""),
                value = "${box.description} — ${box.length} B",
                tagIdHex = box.uuidHex?.take(16),
                containerLocation = "Offset 0x${box.offset.toString(16).uppercase()}",
                byteOffset = box.offset,
                byteLength = box.length,
                sensitivity = PrivacySensitivity.HIGH
            )
        }

        val allFields = c2paFields + jumbfFields + exifFields + xmpResult.fields + iptcResult.fields + otherFields

        val categoryByteCounts = mapOf(
            MetadataCategory.C2PA_CREDENTIALS to c2paReport.totalC2paBytes,
            MetadataCategory.C2PA_MANIFEST to c2paReport.totalC2paBytes,
            MetadataCategory.JUMBF_STRUCTURE to jumbfPayload.reassembledJumbfBytes.size,
            MetadataCategory.EXIF to exifBytesCount,
            MetadataCategory.XMP to xmpResult.totalXmpBytes,
            MetadataCategory.IPTC to iptcResult.totalIptcBytes,
            MetadataCategory.OTHER to otherBytesCount
        )

        val categoryFieldCounts = MetadataCategory.entries.associateWith { cat ->
            allFields.count { it.category == cat }
        }

        val hasGps = exifFields.any { it.key.startsWith("GPS", ignoreCase = true) } ||
            xmpResult.fields.any { it.key.contains("GPS", ignoreCase = true) }
        val hasDevice = exifFields.any {
            it.key in listOf("Make", "Model", "BodySerialNumber", "LensSerialNumber", "CameraOwnerName", "ImageUniqueID")
        }

        val totalMetaBytes = c2paReport.totalC2paBytes + exifBytesCount + xmpResult.totalXmpBytes +
            iptcResult.totalIptcBytes + otherBytesCount

        return MetadataScanReport(
            imageInfo = info,
            isCorrupted = false,
            corruptionReason = null,
            c2paReport = c2paReport,
            fields = allFields,
            segments = segments,
            categoryByteCounts = categoryByteCounts,
            categoryFieldCounts = categoryFieldCounts,
            hasGpsCoordinates = hasGps,
            hasDeviceIdentifiers = hasDevice,
            totalMetadataBytes = totalMetaBytes,
            scanDurationMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
        )
    }

    private data class SegmentInspectionQuad(
        val otherFields: List<MetadataFieldItem>,
        val segments: List<ContainerSegmentInfo>,
        val exifByteTotal: Int,
        val otherByteTotal: Int
    )

    private fun inspectContainerSegments(bytes: ByteArray, format: ImageFormat): SegmentInspectionQuad {
        val otherFields = mutableListOf<MetadataFieldItem>()
        val segments = mutableListOf<ContainerSegmentInfo>()
        var exifBytes = 0
        var otherBytes = 0

        when (format) {
            ImageFormat.JPEG -> {
                var pos = 2
                while (pos + 4 <= bytes.size) {
                    if ((bytes[pos].toInt() and 0xFF) != 0xFF) break
                    while (pos < bytes.size && (bytes[pos].toInt() and 0xFF) == 0xFF) pos++
                    if (pos >= bytes.size) break
                    val marker = bytes[pos].toInt() and 0xFF
                    pos++
                    if (marker == 0xD9 || marker == 0xDA) break
                    if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) continue
                    if (pos + 2 > bytes.size) break
                    val segLen = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
                    if (segLen < 2 || pos + segLen > bytes.size) break
                    val segOffset = (pos - 2).toLong()
                    val fullLen = segLen + 2
                    val start = pos + 2

                    when (marker) {
                        0xE1 -> {
                            val isExif = segLen >= 8 &&
                                bytes[start] == 'E'.code.toByte() &&
                                bytes[start + 1] == 'x'.code.toByte() &&
                                bytes[start + 2] == 'i'.code.toByte() &&
                                bytes[start + 3] == 'f'.code.toByte()
                            if (isExif) {
                                exifBytes += fullLen
                                segments.add(
                                    ContainerSegmentInfo("APP1 (Exif)", MetadataCategory.EXIF, segOffset, fullLen, "EXIF TIFF IFD segment", true)
                                )
                            } else {
                                segments.add(
                                    ContainerSegmentInfo("APP1 (XMP)", MetadataCategory.XMP, segOffset, fullLen, "Adobe XMP XML packet", true)
                                )
                            }
                        }
                        0xEB -> {
                            segments.add(
                                ContainerSegmentInfo("APP11 (JUMBF/C2PA)", MetadataCategory.C2PA_CREDENTIALS, segOffset, fullLen, "JPEG XT 'JP' C2PA JUMBF superbox", true)
                            )
                        }
                        0xED -> {
                            segments.add(
                                ContainerSegmentInfo("APP13 (IPTC/8BIM)", MetadataCategory.IPTC, segOffset, fullLen, "Photoshop IRB / IPTC-NAA datasets", true)
                            )
                        }
                        0xFE -> { // COM (JPEG Comment)
                            val commentText = String(bytes, start, segLen - 2, Charsets.UTF_8).trim()
                            otherBytes += fullLen
                            otherFields.add(
                                MetadataFieldItem(
                                    category = MetadataCategory.OTHER,
                                    key = "JPEG Comment (COM)",
                                    value = commentText,
                                    tagIdHex = "0xFFFE",
                                    containerLocation = "JPEG COM Segment",
                                    byteOffset = segOffset,
                                    byteLength = fullLen,
                                    sensitivity = PrivacySensitivity.HIGH
                                )
                            )
                            segments.add(
                                ContainerSegmentInfo("COM (0xFFFE)", MetadataCategory.OTHER, segOffset, fullLen, "Embedded JPEG text comment", true)
                            )
                        }
                        0xE2 -> { // APP2 (ICC_PROFILE)
                            val isIcc = segLen >= 14 && String(bytes, start, 11, Charsets.US_ASCII) == "ICC_PROFILE"
                            if (isIcc) {
                                otherFields.add(
                                    MetadataFieldItem(
                                        category = MetadataCategory.OTHER,
                                        key = "ICC Color Profile",
                                        value = "${segLen - 16} bytes color space profile (Preserved by default)",
                                        tagIdHex = "0xFFE2",
                                        containerLocation = "JPEG APP2",
                                        byteOffset = segOffset,
                                        byteLength = fullLen,
                                        sensitivity = PrivacySensitivity.LOW
                                    )
                                )
                            }
                        }
                    }
                    pos += segLen
                }
            }
            ImageFormat.PNG -> {
                var pos = 8
                while (pos + 8 <= bytes.size) {
                    val length = FormatDetector.readIntBE(bytes, pos)
                    val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
                    if (length < 0 || pos + 12L + length > bytes.size) break
                    val fullLen = length + 12
                    when (type) {
                        "caBX" -> segments.add(
                            ContainerSegmentInfo("PNG caBX", MetadataCategory.C2PA_CREDENTIALS, pos.toLong(), fullLen, "C2PA JUMBF superbox chunk", true)
                        )
                        "eXIf" -> {
                            exifBytes += fullLen
                            segments.add(
                                ContainerSegmentInfo("PNG eXIf", MetadataCategory.EXIF, pos.toLong(), fullLen, "EXIF metadata chunk", true)
                            )
                        }
                        "tEXt", "zTXt", "iTXt" -> {
                            val rawStr = String(bytes, pos + 8, length, Charsets.UTF_8)
                            if (!rawStr.startsWith(XmpXmlParser.PNG_XMP_KEYWORD)) {
                                otherBytes += fullLen
                                val parts = rawStr.split(' ', limit = 2)
                                val k = parts.getOrNull(0)?.ifBlank { type } ?: type
                                val v = parts.getOrNull(1)?.replace(' ', ' ')?.trim() ?: rawStr
                                otherFields.add(
                                    MetadataFieldItem(
                                        category = MetadataCategory.OTHER,
                                        key = "PNG $type ($k)",
                                        value = v.take(160),
                                        containerLocation = "PNG '$type' Chunk",
                                        byteOffset = pos.toLong(),
                                        byteLength = fullLen,
                                        sensitivity = PrivacySensitivity.HIGH
                                    )
                                )
                                segments.add(
                                    ContainerSegmentInfo("PNG $type", MetadataCategory.OTHER, pos.toLong(), fullLen, "Ancillary text metadata ($k)", true)
                                )
                            } else {
                                segments.add(
                                    ContainerSegmentInfo("PNG iTXt (XMP)", MetadataCategory.XMP, pos.toLong(), fullLen, "Adobe XMP XML chunk", true)
                                )
                            }
                        }
                        "tIME" -> {
                            otherBytes += fullLen
                            val valueStr = if (length == 7) {
                                val y = ((bytes[pos + 8].toInt() and 0xFF) shl 8) or (bytes[pos + 9].toInt() and 0xFF)
                                val m = bytes[pos + 10].toInt() and 0xFF
                                val d = bytes[pos + 11].toInt() and 0xFF
                                val hh = bytes[pos + 12].toInt() and 0xFF
                                val mm = bytes[pos + 13].toInt() and 0xFF
                                val ss = bytes[pos + 14].toInt() and 0xFF
                                "%04d-%02d-%02d %02d:%02d:%02d UTC".format(y, m, d, hh, mm, ss)
                            } else "$length bytes"
                            otherFields.add(
                                MetadataFieldItem(
                                    category = MetadataCategory.OTHER,
                                    key = "PNG Last-Modification Time (tIME)",
                                    value = valueStr,
                                    containerLocation = "PNG 'tIME' Chunk",
                                    byteOffset = pos.toLong(),
                                    byteLength = fullLen,
                                    sensitivity = PrivacySensitivity.HIGH
                                )
                            )
                            segments.add(
                                ContainerSegmentInfo("PNG tIME", MetadataCategory.OTHER, pos.toLong(), fullLen, "Last modification timestamp", true)
                            )
                        }
                    }
                    if (type == "IEND") break
                    pos += fullLen
                }
            }
            ImageFormat.WEBP -> {
                var pos = 12
                while (pos + 8 <= bytes.size) {
                    val fourCC = String(bytes, pos, 4, Charsets.US_ASCII)
                    val chunkSize = FormatDetector.readIntLE(bytes, pos + 4)
                    if (chunkSize < 0 || pos + 8L + chunkSize > bytes.size) break
                    val fullLen = 8 + chunkSize + (chunkSize and 1)
                    when (fourCC) {
                        "C2PA", "jumb" -> segments.add(
                            ContainerSegmentInfo("WebP $fourCC", MetadataCategory.C2PA_CREDENTIALS, pos.toLong(), fullLen, "WebP C2PA JUMBF chunk", true)
                        )
                        "EXIF" -> {
                            exifBytes += fullLen
                            segments.add(
                                ContainerSegmentInfo("WebP EXIF", MetadataCategory.EXIF, pos.toLong(), fullLen, "WebP EXIF chunk", true)
                            )
                        }
                        "XMP " -> segments.add(
                            ContainerSegmentInfo("WebP XMP", MetadataCategory.XMP, pos.toLong(), fullLen, "WebP XMP chunk", true)
                        )
                    }
                    pos += fullLen
                }
            }
            ImageFormat.TIFF -> {
                // Estimate EXIF byte footprint from extracted EXIF tags on TIFF
                val exifCount = ExifBinaryReader.extractExifFields(bytes, ImageFormat.TIFF).size
                exifBytes = exifCount * 24
                if (exifCount > 0) {
                    segments.add(
                        ContainerSegmentInfo("TIFF IFD Metadata Tags", MetadataCategory.EXIF, 8L, exifBytes, "TIFF IFD0 / ExifSubIFD / GPS IFD tags", true)
                    )
                }
            }
            ImageFormat.UNSUPPORTED -> {}
        }

        return SegmentInspectionQuad(otherFields, segments, exifBytes, otherBytes)
    }
}
