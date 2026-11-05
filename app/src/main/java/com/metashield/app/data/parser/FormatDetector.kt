package com.metashield.app.data.parser

import com.metashield.app.domain.model.ImageFormat
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

object FormatDetector {

    data class HeaderInspection(
        val format: ImageFormat,
        val isStructurallyValid: Boolean,
        val corruptionReason: String?,
        val width: Int,
        val height: Int
    )

    fun inspectBytes(bytes: ByteArray, fileNameHint: String? = null): HeaderInspection {
        if (bytes.size < 12) {
            return HeaderInspection(
                format = ImageFormat.fromMimeOrExtension(null, fileNameHint),
                isStructurallyValid = false,
                corruptionReason = "File is truncated (${bytes.size} bytes < minimum 12-byte header).",
                width = 0,
                height = 0
            )
        }

        // 1. Check JPEG Magic: FF D8 FF
        if ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8) {
            return inspectJpeg(bytes)
        }

        // 2. Check PNG Magic: 89 50 4E 47 0D 0A 1A 0A
        val pngMagic = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        if (bytes.take(8).toByteArray().contentEquals(pngMagic)) {
            return inspectPng(bytes)
        }

        // 3. Check WebP Magic: "RIFF" [4B size] "WEBP"
        if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return inspectWebp(bytes)
        }

        // 4. Check TIFF Magic: "II" 0x002A (little-endian) or "MM" 0x002A (big-endian)
        val isLittleTiff = bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            (bytes[2].toInt() and 0xFF) == 0x2A && (bytes[3].toInt() and 0xFF) == 0x00
        val isBigTiff = bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte() &&
            (bytes[2].toInt() and 0xFF) == 0x00 && (bytes[3].toInt() and 0xFF) == 0x2A
        if (isLittleTiff || isBigTiff) {
            return inspectTiff(bytes, isLittleTiff)
        }

        return HeaderInspection(
            format = ImageFormat.UNSUPPORTED,
            isStructurallyValid = false,
            corruptionReason = "Unrecognized magic header bytes; supported formats are JPEG, PNG, WebP, and TIFF.",
            width = 0,
            height = 0
        )
    }

    private fun inspectJpeg(bytes: ByteArray): HeaderInspection {
        var pos = 2
        var width = 0
        var height = 0
        var foundSos = false
        var foundEoi = false

        while (pos < bytes.size) {
            if ((bytes[pos].toInt() and 0xFF) != 0xFF) {
                return HeaderInspection(
                    ImageFormat.JPEG,
                    false,
                    "Corrupted JPEG marker framing at byte offset $pos (expected 0xFF).",
                    width,
                    height
                )
            }
            while (pos < bytes.size && (bytes[pos].toInt() and 0xFF) == 0xFF) {
                pos++
            }
            if (pos >= bytes.size) break
            val marker = bytes[pos].toInt() and 0xFF
            pos++

            if (marker == 0xD9) {
                foundEoi = true
                break
            }
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) {
                continue
            }
            if (pos + 2 > bytes.size) {
                return HeaderInspection(
                    ImageFormat.JPEG,
                    false,
                    "Truncated JPEG segment length at marker 0x${marker.toString(16).uppercase()}.",
                    width,
                    height
                )
            }
            val segLen = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
            if (segLen < 2 || pos + segLen > bytes.size) {
                return HeaderInspection(
                    ImageFormat.JPEG,
                    false,
                    "Truncated or invalid JPEG segment length ($segLen bytes) at marker 0x${marker.toString(16).uppercase()}.",
                    width,
                    height
                )
            }

            // SOF markers (Baseline, Extended, Progressive, etc.) hold dimensions
            if (marker in listOf(0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF)) {
                if (segLen >= 7 && pos + 7 <= bytes.size) {
                    height = ((bytes[pos + 3].toInt() and 0xFF) shl 8) or (bytes[pos + 4].toInt() and 0xFF)
                    width = ((bytes[pos + 5].toInt() and 0xFF) shl 8) or (bytes[pos + 6].toInt() and 0xFF)
                }
            }

            if (marker == 0xDA) { // Start of Scan (SOS)
                foundSos = true
                // Verify EOI (0xFF 0xD9) exists at or near the end of the stream
                val scanStart = pos + segLen
                for (i in bytes.size - 2 downTo scanStart) {
                    if ((bytes[i].toInt() and 0xFF) == 0xFF && (bytes[i + 1].toInt() and 0xFF) == 0xD9) {
                        foundEoi = true
                        break
                    }
                }
                break
            }
            pos += segLen
        }

        if (!foundSos || !foundEoi || width <= 0 || height <= 0) {
            val reason = when {
                !foundSos -> "Missing JPEG Start-of-Scan (SOS 0xFFDA) bitstream."
                !foundEoi -> "Truncated JPEG bitstream (missing End-of-Image 0xFFD9 marker)."
                else -> "Missing or invalid JPEG Start-of-Frame (SOF) dimensions."
            }
            return HeaderInspection(ImageFormat.JPEG, false, reason, width, height)
        }
        return HeaderInspection(ImageFormat.JPEG, true, null, width, height)
    }

    private fun inspectPng(bytes: ByteArray): HeaderInspection {
        var pos = 8
        var width = 0
        var height = 0
        var seenIhdr = false
        var seenIdat = false
        var seenIend = false

        while (pos + 8 <= bytes.size) {
            val length = readIntBE(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            if (length < 0 || pos + 12L + length > bytes.size) {
                return HeaderInspection(
                    ImageFormat.PNG,
                    false,
                    "Truncated PNG chunk '$type' at offset $pos (declared length $length exceeds file size).",
                    width,
                    height
                )
            }
            val crcExpected = readIntBE(bytes, pos + 8 + length).toLong() and 0xFFFFFFFFL
            val crc = CRC32()
            crc.update(bytes, pos + 4, 4 + length)
            if (crc.value != crcExpected) {
                return HeaderInspection(
                    ImageFormat.PNG,
                    false,
                    "CRC32 checksum mismatch in PNG chunk '$type' at offset $pos.",
                    width,
                    height
                )
            }

            if (type == "IHDR") {
                if (length >= 8) {
                    width = readIntBE(bytes, pos + 8)
                    height = readIntBE(bytes, pos + 12)
                    seenIhdr = true
                }
            } else if (type == "IDAT") {
                seenIdat = true
            } else if (type == "IEND") {
                seenIend = true
                break
            }
            pos += 12 + length
        }

        if (!seenIhdr || !seenIdat || !seenIend || width <= 0 || height <= 0) {
            val reason = when {
                !seenIhdr -> "Missing PNG IHDR header chunk."
                !seenIdat -> "Missing PNG IDAT image data chunk."
                !seenIend -> "Missing PNG IEND trailer chunk."
                else -> "Invalid PNG image dimensions ($width × $height)."
            }
            return HeaderInspection(ImageFormat.PNG, false, reason, width, height)
        }
        return HeaderInspection(ImageFormat.PNG, true, null, width, height)
    }

    private fun inspectWebp(bytes: ByteArray): HeaderInspection {
        val riffPayloadSize = readIntLE(bytes, 4)
        if (riffPayloadSize < 4 || 8L + riffPayloadSize > bytes.size) {
            return HeaderInspection(
                ImageFormat.WEBP,
                false,
                "Truncated RIFF WebP container (declared payload $riffPayloadSize bytes, actual ${bytes.size - 8} bytes).",
                0,
                0
            )
        }
        var pos = 12
        var width = 0
        var height = 0
        var hasBitstream = false

        while (pos + 8 <= bytes.size) {
            val fourCC = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = readIntLE(bytes, pos + 4)
            if (chunkSize < 0 || pos + 8L + chunkSize > bytes.size) {
                return HeaderInspection(
                    ImageFormat.WEBP,
                    false,
                    "Truncated WebP chunk '$fourCC' at offset $pos.",
                    width,
                    height
                )
            }
            val dataOffset = pos + 8
            when (fourCC) {
                "VP8X" -> {
                    if (chunkSize >= 10) {
                        val wMinus1 = (bytes[dataOffset + 4].toInt() and 0xFF) or
                            ((bytes[dataOffset + 5].toInt() and 0xFF) shl 8) or
                            ((bytes[dataOffset + 6].toInt() and 0xFF) shl 16)
                        val hMinus1 = (bytes[dataOffset + 7].toInt() and 0xFF) or
                            ((bytes[dataOffset + 8].toInt() and 0xFF) shl 8) or
                            ((bytes[dataOffset + 9].toInt() and 0xFF) shl 16)
                        width = wMinus1 + 1
                        height = hMinus1 + 1
                    }
                }
                "VP8 " -> {
                    hasBitstream = true
                    if (width == 0 && chunkSize >= 10) {
                        // VP8 keyframe start code: 9D 01 2A at offset +3..+5
                        if ((bytes[dataOffset + 3].toInt() and 0xFF) == 0x9D &&
                            (bytes[dataOffset + 4].toInt() and 0xFF) == 0x01 &&
                            (bytes[dataOffset + 5].toInt() and 0xFF) == 0x2A
                        ) {
                            width = ((bytes[dataOffset + 6].toInt() and 0xFF) or
                                ((bytes[dataOffset + 7].toInt() and 0xFF) shl 8)) and 0x3FFF
                            height = ((bytes[dataOffset + 8].toInt() and 0xFF) or
                                ((bytes[dataOffset + 9].toInt() and 0xFF) shl 8)) and 0x3FFF
                        }
                    }
                }
                "VP8L" -> {
                    hasBitstream = true
                    if (width == 0 && chunkSize >= 5 && (bytes[dataOffset].toInt() and 0xFF) == 0x2F) {
                        val b1 = bytes[dataOffset + 1].toInt() and 0xFF
                        val b2 = bytes[dataOffset + 2].toInt() and 0xFF
                        val b3 = bytes[dataOffset + 3].toInt() and 0xFF
                        val b4 = bytes[dataOffset + 4].toInt() and 0xFF
                        width = 1 + (((b2 and 0x3F) shl 8) or b1)
                        height = 1 + (((b4 and 0x0F) shl 10) or (b3 shl 2) or ((b2 and 0xC0) shr 6))
                    }
                }
            }
            val paddedSize = chunkSize + (chunkSize and 1)
            pos += 8 + paddedSize
        }

        if (!hasBitstream || width <= 0 || height <= 0) {
            return HeaderInspection(
                ImageFormat.WEBP,
                false,
                "Invalid WebP container (missing VP8/VP8L bitstream or invalid dimensions).",
                width,
                height
            )
        }
        return HeaderInspection(ImageFormat.WEBP, true, null, width, height)
    }

    private fun inspectTiff(bytes: ByteArray, isLittleEndian: Boolean): HeaderInspection {
        val ifdOffset = readInt(bytes, 4, isLittleEndian)
        if (ifdOffset < 8 || ifdOffset + 2 > bytes.size) {
            return HeaderInspection(
                ImageFormat.TIFF,
                false,
                "Invalid or truncated TIFF IFD0 offset ($ifdOffset).",
                0,
                0
            )
        }
        val entryCount = readUShort(bytes, ifdOffset, isLittleEndian)
        if (entryCount <= 0 || ifdOffset + 2 + entryCount * 12 > bytes.size) {
            return HeaderInspection(
                ImageFormat.TIFF,
                false,
                "Truncated TIFF IFD0 directory ($entryCount entries exceed file size).",
                0,
                0
            )
        }
        var width = 0
        var height = 0
        for (i in 0 until entryCount) {
            val entryPos = ifdOffset + 2 + i * 12
            val tag = readUShort(bytes, entryPos, isLittleEndian)
            val fieldType = readUShort(bytes, entryPos + 2, isLittleEndian)
            val value = if (fieldType == 3) { // SHORT
                readUShort(bytes, entryPos + 8, isLittleEndian)
            } else {
                readInt(bytes, entryPos + 8, isLittleEndian)
            }
            if (tag == 0x0100) width = value
            if (tag == 0x0101) height = value
        }
        if (width <= 0 || height <= 0) {
            return HeaderInspection(
                ImageFormat.TIFF,
                false,
                "Missing ImageWidth (0x0100) or ImageLength (0x0101) in TIFF IFD0.",
                width,
                height
            )
        }
        return HeaderInspection(ImageFormat.TIFF, true, null, width, height)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(file: File): String = sha256Hex(file.readBytes())

    fun readIntBE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    fun readIntLE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun readUShort(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
        return if (littleEndian) {
            (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        } else {
            ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
        }
    }

    fun readInt(bytes: ByteArray, offset: Int, littleEndian: Boolean): Int {
        return if (littleEndian) readIntLE(bytes, offset) else readIntBE(bytes, offset)
    }
}
