package com.metashield.app.data.cleaner

import com.metashield.app.data.parser.FormatDetector
import com.metashield.app.domain.model.CleaningOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Format-Aware TIFF Metadata Surgeon.
 *
 * Rebuilds TIFF IFD0 in-place (preserving exact file offsets so `StripOffsets` `0x0111` and `TileOffsets` `0x0144`
 * remain 100% valid without touching compressed or uncompressed image strips), removes selected metadata tags:
 * - C2PA: `0xCD41` (52545 `C2PA_MANIFEST`)
 * - EXIF & Descriptive: `0x8769` (`ExifIFDPointer`), `0x8825` (`GPSInfoIFDPointer`), `0xA005` (`InteroperabilityIFD`),
 *   `0x010E` (`ImageDescription`), `0x010F` (`Make`), `0x0110` (`Model`), `0x0131` (`Software`), `0x0132` (`DateTime`),
 *   `0x013B` (`Artist`), `0x8298` (`Copyright`), `0xA430` (`CameraOwnerName`), `0xA431` (`BodySerialNumber`)
 * - XMP: `0x02BC` (700 `XMLPacket`)
 * - IPTC / Photoshop: `0x83BB` (33723 `IPTC-NAA`), `0x8649` (34377 `PhotoshopImageResources`)
 * And zero-fills the external value ranges of excised tags so no residual metadata bytes remain in file slack space!
 */
object TiffFormatCleaner {

    private val EXIF_AND_DESCRIPTIVE_TIFF_TAGS = setOf(
        0x010E, 0x010F, 0x0110, 0x0131, 0x0132, 0x013B, 0x8298,
        0x8769, 0x8825, 0xA005, 0x9286, 0xA420, 0xA430, 0xA431, 0xA434, 0xA435
    )

    fun cleanTiff(sourceBytes: ByteArray, options: CleaningOptions): Result<JpegLosslessCleaner.SurgeryOutcome> {
        if (sourceBytes.size < 16) {
            return Result.failure(IllegalArgumentException("Buffer is too small to be a valid TIFF container."))
        }
        val littleEndian = when {
            sourceBytes[0] == 'I'.code.toByte() && sourceBytes[1] == 'I'.code.toByte() -> true
            sourceBytes[0] == 'M'.code.toByte() && sourceBytes[1] == 'M'.code.toByte() -> false
            else -> return Result.failure(IllegalArgumentException("Invalid TIFF byte-order header."))
        }
        val order = if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN

        val ifdOffset = FormatDetector.readInt(sourceBytes, 4, littleEndian)
        if (ifdOffset < 8 || ifdOffset + 2 > sourceBytes.size) {
            return Result.failure(IllegalStateException("Invalid TIFF IFD0 offset ($ifdOffset)."))
        }
        val entryCount = FormatDetector.readUShort(sourceBytes, ifdOffset, littleEndian)
        val ifdEnd = ifdOffset + 2 + entryCount * 12 + 4
        if (entryCount <= 0 || ifdEnd > sourceBytes.size) {
            return Result.failure(IllegalStateException("Truncated TIFF IFD0 table."))
        }

        val working = sourceBytes.copyOf()
        val keptEntries = mutableListOf<ByteArray>()
        val removed = mutableListOf<String>()
        val preserved = mutableListOf<String>()

        for (i in 0 until entryCount) {
            val entryPos = ifdOffset + 2 + i * 12
            val tag = FormatDetector.readUShort(working, entryPos, littleEndian)
            val fieldType = FormatDetector.readUShort(working, entryPos + 2, littleEndian)
            val count = FormatDetector.readInt(working, entryPos + 4, littleEndian)
            val valOrOffset = FormatDetector.readInt(working, entryPos + 8, littleEndian)

            var shouldRemove = false
            var desc = ""

            when {
                tag == 0xCD41 -> {
                    if (options.removeC2pa) {
                        shouldRemove = true
                        desc = "Removed TIFF Tag 0xCD41 (C2PA_MANIFEST, $count B)"
                    } else {
                        preserved.add("Preserved TIFF Tag 0xCD41 (C2PA_MANIFEST) per user selection.")
                    }
                }
                tag == 0x02BC -> {
                    if (options.removeXmp) {
                        shouldRemove = true
                        desc = "Removed TIFF Tag 0x02BC (Adobe XMP XMLPacket, $count B)"
                    } else {
                        preserved.add("Preserved TIFF Tag 0x02BC (XMP) per user selection.")
                    }
                }
                tag == 0x83BB || tag == 0x8649 -> {
                    if (options.removeIptc) {
                        shouldRemove = true
                        desc = "Removed TIFF Tag 0x${tag.toString(16).uppercase()} (IPTC / Photoshop 8BIM, $count B)"
                    } else {
                        preserved.add("Preserved TIFF Tag 0x${tag.toString(16).uppercase()} (IPTC) per user selection.")
                    }
                }
                tag in EXIF_AND_DESCRIPTIVE_TIFF_TAGS -> {
                    if (options.removeExif) {
                        shouldRemove = true
                        desc = "Removed TIFF EXIF/Identity Tag 0x%04X".format(tag)
                    } else {
                        preserved.add("Preserved TIFF EXIF Tag 0x%04X per user selection.".format(tag))
                    }
                }
            }

            if (shouldRemove) {
                removed.add(desc)
                // Zero-fill external value payload or SubIFD so no sensitive bytes remain in slack space
                if (options.zeroFillSlackSpace) {
                    if (tag == 0x8769 || tag == 0x8825 || tag == 0xA005) {
                        scrubSubIfd(working, valOrOffset, littleEndian)
                    } else {
                        val typeSize = when (fieldType) {
                            1, 2, 6, 7 -> 1
                            3, 8 -> 2
                            4, 9, 11 -> 4
                            5, 10, 12 -> 8
                            else -> 1
                        }
                        val byteLen = count * typeSize
                        if (byteLen > 4 && valOrOffset >= 8 && valOrOffset + byteLen <= working.size) {
                            working.fill(0, valOrOffset, valOrOffset + byteLen)
                        }
                    }
                }
            } else {
                keptEntries.add(working.sliceArray(entryPos until (entryPos + 12)))
            }
        }

        // Write updated IFD0 entry count and entries in-place, then zero-fill remaining IFD0 slots
        val nextIfdOffset = FormatDetector.readInt(working, ifdOffset + 2 + entryCount * 12, littleEndian)
        working.fill(0, ifdOffset, ifdEnd)

        val buf = ByteBuffer.wrap(working).order(order)
        buf.putShort(ifdOffset, keptEntries.size.toShort())
        var writePos = ifdOffset + 2
        for (entry in keptEntries) {
            System.arraycopy(entry, 0, working, writePos, 12)
            writePos += 12
        }
        // Preserve next IFD pointer only if not stripping secondary thumbnail IFD
        if (!options.removeExif && nextIfdOffset > 0) {
            buf.putInt(writePos, nextIfdOffset)
        } else {
            buf.putInt(writePos, 0)
        }

        return Result.success(
            JpegLosslessCleaner.SurgeryOutcome(
                cleanedBytes = working,
                removedSegments = removed,
                preservedNotes = preserved
            )
        )
    }

    private fun scrubSubIfd(bytes: ByteArray, subIfdOffset: Int, littleEndian: Boolean) {
        if (subIfdOffset < 8 || subIfdOffset + 2 > bytes.size) return
        val count = FormatDetector.readUShort(bytes, subIfdOffset, littleEndian)
        if (count <= 0 || count > 256 || subIfdOffset + 2 + count * 12 > bytes.size) return
        for (i in 0 until count) {
            val entryPos = subIfdOffset + 2 + i * 12
            val fieldType = FormatDetector.readUShort(bytes, entryPos + 2, littleEndian)
            val valCount = FormatDetector.readInt(bytes, entryPos + 4, littleEndian)
            val valOffset = FormatDetector.readInt(bytes, entryPos + 8, littleEndian)
            val typeSize = when (fieldType) {
                1, 2, 6, 7 -> 1
                3, 8 -> 2
                4, 9, 11 -> 4
                5, 10, 12 -> 8
                else -> 1
            }
            val totalLen = valCount * typeSize
            if (totalLen > 4 && valOffset >= 8 && valOffset + totalLen <= bytes.size) {
                bytes.fill(0, valOffset, valOffset + totalLen)
            }
        }
        bytes.fill(0, subIfdOffset, (subIfdOffset + 2 + count * 12 + 4).coerceAtMost(bytes.size))
    }
}
