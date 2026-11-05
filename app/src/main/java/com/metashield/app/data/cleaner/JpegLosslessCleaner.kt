package com.metashield.app.data.cleaner

import com.metashield.app.data.parser.XmpXmlParser
import com.metashield.app.domain.model.CleaningOptions
import java.io.ByteArrayOutputStream

/**
 * Lossless Format-Aware JPEG Binary Segment Surgeon.
 *
 * Excises selected metadata markers (`APP11` C2PA/JUMBF, `APP1` EXIF, `APP1` XMP, `APP13` IPTC/8BIM,
 * `COM` Comments, and optionally `APP2` ICC_PROFILE) while copying quantization tables (`DQT`),
 * Huffman tables (`DHT`), Start-of-Frame (`SOF*`), and the entropy-coded Start-of-Scan (`SOS` -> `EOI`)
 * bitstream byte-for-byte with ZERO DCT re-compression or visual quality loss.
 */
object JpegLosslessCleaner {

    data class SurgeryOutcome(
        val cleanedBytes: ByteArray,
        val removedSegments: List<String>,
        val preservedNotes: List<String>
    )

    fun cleanJpeg(sourceBytes: ByteArray, options: CleaningOptions): Result<SurgeryOutcome> {
        if (sourceBytes.size < 4 || (sourceBytes[0].toInt() and 0xFF) != 0xFF || (sourceBytes[1].toInt() and 0xFF) != 0xD8) {
            return Result.failure(IllegalArgumentException("Source buffer does not start with valid JPEG SOI (0xFFD8) marker."))
        }

        val out = ByteArrayOutputStream(sourceBytes.size)
        val removed = mutableListOf<String>()
        val preserved = mutableListOf<String>()

        // Write SOI marker (0xFF 0xD8)
        out.write(0xFF)
        out.write(0xD8)

        var pos = 2
        var copiedEntropyScan = false

        while (pos < sourceBytes.size) {
            if ((sourceBytes[pos].toInt() and 0xFF) != 0xFF) {
                return Result.failure(IllegalStateException("Malformed JPEG marker framing at offset $pos."))
            }
            val markerStart = pos
            while (pos < sourceBytes.size && (sourceBytes[pos].toInt() and 0xFF) == 0xFF) {
                pos++
            }
            if (pos >= sourceBytes.size) break
            val marker = sourceBytes[pos].toInt() and 0xFF
            pos++

            if (marker == 0xD9) {
                out.write(0xFF)
                out.write(0xD9)
                break
            }

            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) {
                out.write(0xFF)
                out.write(marker)
                continue
            }

            if (pos + 2 > sourceBytes.size) {
                return Result.failure(IllegalStateException("Truncated JPEG marker 0x${marker.toString(16)} at offset $markerStart."))
            }
            val segLen = ((sourceBytes[pos].toInt() and 0xFF) shl 8) or (sourceBytes[pos + 1].toInt() and 0xFF)
            if (segLen < 2 || pos + segLen > sourceBytes.size) {
                return Result.failure(IllegalStateException("Invalid JPEG segment length ($segLen) at offset $markerStart."))
            }

            val payloadStart = pos + 2
            val payloadLen = segLen - 2
            val fullSegmentBytes = segLen + 2

            // If we hit SOS (0xFFDA), copy the SOS header and the entire entropy-coded scan until EOI verbatim
            if (marker == 0xDA) {
                out.write(0xFF)
                out.write(0xDA)
                out.write(sourceBytes, pos, sourceBytes.size - pos)
                copiedEntropyScan = true
                break
            }

            var shouldRemove = false
            var segmentDescription = ""

            when (marker) {
                0xEB -> { // APP11: JPEG XT / JUMBF (C2PA)
                    val isJp = payloadLen >= 2 &&
                        sourceBytes[payloadStart] == 'J'.code.toByte() &&
                        sourceBytes[payloadStart + 1] == 'P'.code.toByte()
                    if (isJp) {
                        if (options.removeC2pa) {
                            shouldRemove = true
                            segmentDescription = "Removed JPEG APP11 (0xFFEB) C2PA/JUMBF segment ($fullSegmentBytes B)"
                        } else {
                            preserved.add("Preserved JPEG APP11 C2PA/JUMBF segment ($fullSegmentBytes B) per user selection.")
                        }
                    }
                }
                0xE1 -> { // APP1: EXIF or XMP
                    val isExif = payloadLen >= 6 &&
                        sourceBytes[payloadStart] == 'E'.code.toByte() &&
                        sourceBytes[payloadStart + 1] == 'x'.code.toByte() &&
                        sourceBytes[payloadStart + 2] == 'i'.code.toByte() &&
                        sourceBytes[payloadStart + 3] == 'f'.code.toByte()

                    val isXmp = payloadLen >= 29 &&
                        String(sourceBytes, payloadStart, 29, Charsets.US_ASCII) == XmpXmlParser.JPEG_XMP_HEADER
                    val isExtXmp = payloadLen >= 35 &&
                        String(sourceBytes, payloadStart, 35, Charsets.US_ASCII) == XmpXmlParser.JPEG_XMP_EXT_HEADER

                    when {
                        isExif -> {
                            if (options.removeExif) {
                                shouldRemove = true
                                segmentDescription = "Removed JPEG APP1 (0xFFE1) EXIF segment ($fullSegmentBytes B)"
                            } else {
                                preserved.add("Preserved JPEG APP1 EXIF segment ($fullSegmentBytes B) per user selection.")
                            }
                        }
                        isXmp || isExtXmp -> {
                            if (options.removeXmp) {
                                shouldRemove = true
                                segmentDescription = "Removed JPEG APP1 (0xFFE1) Adobe XMP packet ($fullSegmentBytes B)"
                            } else {
                                preserved.add("Preserved JPEG APP1 XMP segment ($fullSegmentBytes B) per user selection.")
                            }
                        }
                    }
                }
                0xED -> { // APP13: Photoshop IRB / IPTC
                    if (options.removeIptc) {
                        shouldRemove = true
                        segmentDescription = "Removed JPEG APP13 (0xFFED) IPTC / Photoshop 8BIM segment ($fullSegmentBytes B)"
                    } else {
                        preserved.add("Preserved JPEG APP13 IPTC segment ($fullSegmentBytes B) per user selection.")
                    }
                }
                0xFE -> { // COM: Comment
                    if (options.removeOtherMetadata) {
                        shouldRemove = true
                        segmentDescription = "Removed JPEG COM (0xFFFE) Comment segment ($fullSegmentBytes B)"
                    } else {
                        preserved.add("Preserved JPEG COM Comment segment ($fullSegmentBytes B) per user selection.")
                    }
                }
                0xE2 -> { // APP2: ICC Profile or FlashPix
                    val isIcc = payloadLen >= 12 &&
                        String(sourceBytes, payloadStart, 11, Charsets.US_ASCII) == "ICC_PROFILE"
                    if (isIcc) {
                        if (!options.preserveIccColorProfile && options.removeOtherMetadata) {
                            shouldRemove = true
                            segmentDescription = "Removed JPEG APP2 ICC Color Profile ($fullSegmentBytes B)"
                        } else {
                            preserved.add("Preserved APP2 ICC Color Profile ($fullSegmentBytes B) to maintain color accuracy.")
                        }
                    } else if (options.removeOtherMetadata) {
                        shouldRemove = true
                        segmentDescription = "Removed JPEG APP2 vendor extension segment ($fullSegmentBytes B)"
                    }
                }
            }

            if (shouldRemove) {
                removed.add(segmentDescription)
            } else {
                out.write(0xFF)
                out.write(marker)
                out.write(sourceBytes, pos, segLen)
            }
            pos += segLen
        }

        if (!copiedEntropyScan) {
            return Result.failure(IllegalStateException("JPEG image is missing Start-of-Scan (0xFFDA) bitstream; aborted to prevent corruption."))
        }

        return Result.success(
            SurgeryOutcome(
                cleanedBytes = out.toByteArray(),
                removedSegments = removed,
                preservedNotes = preserved
            )
        )
    }
}
