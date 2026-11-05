package com.metashield.app.data.c2pa

import com.metashield.app.data.parser.FormatDetector
import com.metashield.app.domain.model.ImageFormat
import com.metashield.app.domain.model.JumbfBoxNode
import java.io.ByteArrayOutputStream

/**
 * Format-aware ISO/IEC 19566-5 (JUMBF) binary parser for C2PA Content Credentials.
 * Extracts JUMBF superboxes (`jumb`), description boxes (`jumd`), CBOR claims (`cbor`),
 * JSON assertions (`json`), and COSE signatures (`c2cs`) across JPEG (APP11 JP),
 * PNG (`caBX`), WebP (`C2PA`/`jumb`), and TIFF (Tag 0xCD41).
 */
object JumbfBinaryParser {

    // Official C2PA UUIDs per C2PA Specification
    const val C2PA_MANIFEST_STORE_UUID = "d8fec3d61b0e483c92975828877ec481"
    const val C2PA_MANIFEST_UUID_PREFIX = "63327061" // "c2pa"
    const val C2PA_CLAIM_UUID_PREFIX = "6332636c"    // "c2cl"
    const val C2PA_ASSERTIONS_UUID_PREFIX = "63326173" // "c2as"
    const val C2PA_SIGNATURE_UUID_PREFIX = "63326373"  // "c2cs"

    data class ExtractedJumbfPayload(
        val carrierLocations: List<String>,
        val totalCarrierBytes: Int,
        val reassembledJumbfBytes: ByteArray,
        val boxes: List<JumbfBoxNode>,
        val isStructuralError: Boolean,
        val structuralErrorReason: String?
    )

    fun extractFromContainer(bytes: ByteArray, format: ImageFormat): ExtractedJumbfPayload {
        return when (format) {
            ImageFormat.JPEG -> extractFromJpeg(bytes)
            ImageFormat.PNG -> extractFromPng(bytes)
            ImageFormat.WEBP -> extractFromWebp(bytes)
            ImageFormat.TIFF -> extractFromTiff(bytes)
            ImageFormat.UNSUPPORTED -> ExtractedJumbfPayload(emptyList(), 0, ByteArray(0), emptyList(), false, null)
        }
    }

    private fun extractFromJpeg(bytes: ByteArray): ExtractedJumbfPayload {
        val carriers = mutableListOf<String>()
        var totalCarrierBytes = 0
        val reassembled = ByteArrayOutputStream()
        var structuralError = false
        var errorReason: String? = null

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

            // APP11 (0xFFEB) carries JPEG XT / JUMBF boxes starting with "JP" (0x4A 0x50)
            if (marker == 0xEB && segLen >= 10) {
                val payloadStart = pos + 2
                val payloadLen = segLen - 2
                if (bytes[payloadStart] == 'J'.code.toByte() && bytes[payloadStart + 1] == 'P'.code.toByte()) {
                    val segmentOffset = (pos - 2).toLong()
                    carriers.add("JPEG APP11 (0xFFEB) 'JP' segment @ 0x${segmentOffset.toString(16).uppercase()} (${segLen + 2} B)")
                    totalCarrierBytes += (segLen + 2)
                    // JPEG XT APP11 header: 2B 'JP', 2B En (instance), 4B Z (sequence number), then BMFF box stream
                    val zSeq = FormatDetector.readIntBE(bytes, payloadStart + 4)
                    val xtHeaderSize = 8
                    if (payloadLen > xtHeaderSize) {
                        val boxDataStart = payloadStart + xtHeaderSize
                        val boxDataLen = payloadLen - xtHeaderSize
                        // For continuation packets (Z > 1), JPEG XT repeats the 8-byte LBox+TBox header
                        if (zSeq <= 1) {
                            reassembled.write(bytes, boxDataStart, boxDataLen)
                        } else if (boxDataLen > 8) {
                            reassembled.write(bytes, boxDataStart + 8, boxDataLen - 8)
                        }
                    } else {
                        structuralError = true
                        errorReason = "Truncated JPEG APP11 'JP' header at offset $segmentOffset."
                    }
                }
            }
            pos += segLen
        }

        val jumbfBytes = reassembled.toByteArray()
        val parsedBoxes = if (jumbfBytes.isNotEmpty()) {
            parseIsoBmffBoxes(jumbfBytes)
        } else {
            ParseBoxesResult(emptyList(), false, null)
        }

        return ExtractedJumbfPayload(
            carrierLocations = carriers,
            totalCarrierBytes = totalCarrierBytes,
            reassembledJumbfBytes = jumbfBytes,
            boxes = parsedBoxes.boxes,
            isStructuralError = structuralError || parsedBoxes.hasError,
            structuralErrorReason = errorReason ?: parsedBoxes.errorReason
        )
    }

    private fun extractFromPng(bytes: ByteArray): ExtractedJumbfPayload {
        val carriers = mutableListOf<String>()
        var totalCarrierBytes = 0
        val reassembled = ByteArrayOutputStream()
        var pos = 8
        while (pos + 8 <= bytes.size) {
            val length = FormatDetector.readIntBE(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            if (length < 0 || pos + 12L + length > bytes.size) break
            if (type == "caBX") {
                carriers.add("PNG 'caBX' chunk @ 0x${pos.toString(16).uppercase()} (${length + 12} B)")
                totalCarrierBytes += (length + 12)
                if (length > 0) {
                    reassembled.write(bytes, pos + 8, length)
                }
            }
            if (type == "IEND") break
            pos += 12 + length
        }
        val jumbfBytes = reassembled.toByteArray()
        val parsed = if (jumbfBytes.isNotEmpty()) parseIsoBmffBoxes(jumbfBytes) else ParseBoxesResult(emptyList(), false, null)
        return ExtractedJumbfPayload(
            carrierLocations = carriers,
            totalCarrierBytes = totalCarrierBytes,
            reassembledJumbfBytes = jumbfBytes,
            boxes = parsed.boxes,
            isStructuralError = parsed.hasError && carriers.isNotEmpty(),
            structuralErrorReason = parsed.errorReason
        )
    }

    private fun extractFromWebp(bytes: ByteArray): ExtractedJumbfPayload {
        val carriers = mutableListOf<String>()
        var totalCarrierBytes = 0
        val reassembled = ByteArrayOutputStream()
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val fourCC = String(bytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = FormatDetector.readIntLE(bytes, pos + 4)
            if (chunkSize < 0 || pos + 8L + chunkSize > bytes.size) break
            if (fourCC == "C2PA" || fourCC == "jumb") {
                carriers.add("WebP RIFF '$fourCC' chunk @ 0x${pos.toString(16).uppercase()} (${chunkSize + 8} B)")
                totalCarrierBytes += (chunkSize + 8 + (chunkSize and 1))
                if (chunkSize > 0) {
                    reassembled.write(bytes, pos + 8, chunkSize)
                }
            }
            pos += 8 + chunkSize + (chunkSize and 1)
        }
        val jumbfBytes = reassembled.toByteArray()
        val parsed = if (jumbfBytes.isNotEmpty()) parseIsoBmffBoxes(jumbfBytes) else ParseBoxesResult(emptyList(), false, null)
        return ExtractedJumbfPayload(
            carrierLocations = carriers,
            totalCarrierBytes = totalCarrierBytes,
            reassembledJumbfBytes = jumbfBytes,
            boxes = parsed.boxes,
            isStructuralError = parsed.hasError && carriers.isNotEmpty(),
            structuralErrorReason = parsed.errorReason
        )
    }

    private fun extractFromTiff(bytes: ByteArray): ExtractedJumbfPayload {
        if (bytes.size < 8) return ExtractedJumbfPayload(emptyList(), 0, ByteArray(0), emptyList(), false, null)
        val littleEndian = bytes[0] == 'I'.code.toByte()
        val ifdOffset = FormatDetector.readInt(bytes, 4, littleEndian)
        if (ifdOffset < 8 || ifdOffset + 2 > bytes.size) {
            return ExtractedJumbfPayload(emptyList(), 0, ByteArray(0), emptyList(), false, null)
        }
        val entryCount = FormatDetector.readUShort(bytes, ifdOffset, littleEndian)
        if (ifdOffset + 2 + entryCount * 12 > bytes.size) {
            return ExtractedJumbfPayload(emptyList(), 0, ByteArray(0), emptyList(), false, null)
        }

        val carriers = mutableListOf<String>()
        var totalCarrierBytes = 0
        val reassembled = ByteArrayOutputStream()
        var structuralError = false
        var errorReason: String? = null

        for (i in 0 until entryCount) {
            val entryPos = ifdOffset + 2 + i * 12
            val tag = FormatDetector.readUShort(bytes, entryPos, littleEndian)
            val count = FormatDetector.readInt(bytes, entryPos + 4, littleEndian)
            val valueOrOffset = FormatDetector.readInt(bytes, entryPos + 8, littleEndian)
            // Tag 0xCD41 (52545) is the official C2PA_MANIFEST TIFF tag
            if (tag == 0xCD41) {
                carriers.add("TIFF IFD0 Tag 0xCD41 (C2PA_MANIFEST) @ 0x${valueOrOffset.toString(16).uppercase()} ($count B)")
                totalCarrierBytes += count + 12
                if (count > 4 && valueOrOffset >= 8 && valueOrOffset + count <= bytes.size) {
                    reassembled.write(bytes, valueOrOffset, count)
                } else {
                    structuralError = true
                    errorReason = "TIFF Tag 0xCD41 offset ($valueOrOffset) or count ($count) is out of bounds."
                }
            }
        }
        val jumbfBytes = reassembled.toByteArray()
        val parsed = if (jumbfBytes.isNotEmpty()) parseIsoBmffBoxes(jumbfBytes) else ParseBoxesResult(emptyList(), false, null)
        return ExtractedJumbfPayload(
            carrierLocations = carriers,
            totalCarrierBytes = totalCarrierBytes,
            reassembledJumbfBytes = jumbfBytes,
            boxes = parsed.boxes,
            isStructuralError = structuralError || parsed.hasError,
            structuralErrorReason = errorReason ?: parsed.errorReason
        )
    }

    data class ParseBoxesResult(
        val boxes: List<JumbfBoxNode>,
        val hasError: Boolean,
        val errorReason: String?
    )

    fun parseIsoBmffBoxes(
        data: ByteArray,
        startOffset: Int = 0,
        endOffset: Int = data.size,
        baseFileOffset: Long = 0L
    ): ParseBoxesResult {
        val boxes = mutableListOf<JumbfBoxNode>()
        var pos = startOffset
        var hasError = false
        var errorReason: String? = null

        if (endOffset - startOffset < 8) {
            return ParseBoxesResult(emptyList(), true, "JUMBF payload is smaller than minimum 8-byte ISO BMFF box header.")
        }

        while (pos + 8 <= endOffset) {
            val rawLen = FormatDetector.readIntBE(data, pos).toLong() and 0xFFFFFFFFL
            val boxType = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            val boxLen: Int = when {
                rawLen == 1L -> {
                    if (pos + 16 > endOffset) {
                        hasError = true
                        errorReason = "Truncated 64-bit XLBox header for '$boxType' at offset ${baseFileOffset + pos}."
                        break
                    }
                    headerSize = 16
                    val high = FormatDetector.readIntBE(data, pos + 8).toLong() and 0xFFFFFFFFL
                    val low = FormatDetector.readIntBE(data, pos + 12).toLong() and 0xFFFFFFFFL
                    ((high shl 32) or low).toInt()
                }
                rawLen == 0L -> endOffset - pos
                else -> rawLen.toInt()
            }

            if (boxLen < headerSize || pos + boxLen > endOffset) {
                hasError = true
                errorReason = "Truncated or malformed JUMBF box '$boxType' (declared length $boxLen exceeds remaining ${endOffset - pos} bytes)."
                break
            }

            val payloadStart = pos + headerSize
            val payloadEnd = pos + boxLen

            when (boxType) {
                "jumb" -> {
                    // Superbox: inspect first child if it's `jumd` (JUMBF Description Box)
                    var label: String? = null
                    var uuidHex: String? = null
                    if (payloadStart + 24 <= payloadEnd) {
                        val childLen = FormatDetector.readIntBE(data, payloadStart)
                        val childType = String(data, payloadStart + 4, 4, Charsets.ISO_8859_1)
                        if (childType == "jumd" && childLen >= 24 && payloadStart + childLen <= payloadEnd) {
                            uuidHex = data.sliceArray((payloadStart + 8) until (payloadStart + 24))
                                .joinToString("") { "%02x".format(it) }
                            val toggles = data[payloadStart + 24].toInt() and 0xFF
                            if ((toggles and 0x02) != 0 && payloadStart + 25 < payloadStart + childLen) {
                                val strBytes = data.sliceArray((payloadStart + 25) until (payloadStart + childLen))
                                val nullIdx = strBytes.indexOf(0.toByte())
                                label = if (nullIdx >= 0) {
                                    String(strBytes, 0, nullIdx, Charsets.UTF_8)
                                } else {
                                    String(strBytes, Charsets.UTF_8)
                                }
                            }
                        }
                    }
                    boxes.add(
                        JumbfBoxNode(
                            boxType = "jumb",
                            label = label,
                            uuidHex = uuidHex,
                            offset = baseFileOffset + pos,
                            length = boxLen,
                            description = describeJumbfSuperbox(uuidHex, label)
                        )
                    )
                    val childResult = parseIsoBmffBoxes(data, payloadStart, payloadEnd, baseFileOffset)
                    boxes.addAll(childResult.boxes)
                    if (childResult.hasError) {
                        hasError = true
                        errorReason = childResult.errorReason
                    }
                }
                "jumd" -> {
                    var uuidHex: String? = null
                    var label: String? = null
                    if (payloadEnd - payloadStart >= 16) {
                        uuidHex = data.sliceArray(payloadStart until (payloadStart + 16))
                            .joinToString("") { "%02x".format(it) }
                        if (payloadEnd - payloadStart >= 17) {
                            val toggles = data[payloadStart + 16].toInt() and 0xFF
                            if ((toggles and 0x02) != 0 && payloadStart + 17 < payloadEnd) {
                                val strBytes = data.sliceArray((payloadStart + 17) until payloadEnd)
                                val nullIdx = strBytes.indexOf(0.toByte())
                                label = if (nullIdx >= 0) {
                                    String(strBytes, 0, nullIdx, Charsets.UTF_8)
                                } else {
                                    String(strBytes, Charsets.UTF_8)
                                }
                            }
                        }
                    }
                    boxes.add(
                        JumbfBoxNode(
                            boxType = "jumd",
                            label = label,
                            uuidHex = uuidHex,
                            offset = baseFileOffset + pos,
                            length = boxLen,
                            description = "JUMBF Description Box (${label ?: uuidHex ?: "unlabeled"})"
                        )
                    )
                }
                "cbor", "json", "bfdb", "uuid", "c2sh", "c2cs" -> {
                    boxes.add(
                        JumbfBoxNode(
                            boxType = boxType,
                            label = null,
                            uuidHex = null,
                            offset = baseFileOffset + pos,
                            length = boxLen,
                            description = when (boxType) {
                                "cbor" -> "CBOR Content Box (Claim / Assertion / Actions payload)"
                                "json" -> "JSON Content Box (Structured assertion payload)"
                                "c2cs" -> "C2PA COSE_Sign1 Cryptographic Signature Box"
                                "c2sh" -> "C2PA Salted Assertion Hash Box"
                                "uuid" -> "UUID Box (C2PA Signature or Credential Store)"
                                else -> "Embedded Binary Data Box ($boxType)"
                            }
                        )
                    )
                }
                else -> {
                    // Check if boxType is Printable ASCII
                    if (boxType.all { it.code in 0x20..0x7E }) {
                        boxes.add(
                            JumbfBoxNode(
                                boxType = boxType,
                                label = null,
                                uuidHex = null,
                                offset = baseFileOffset + pos,
                                length = boxLen,
                                description = "ISO BMFF Box '$boxType'"
                            )
                        )
                    } else {
                        hasError = true
                        errorReason = "Non-ASCII or corrupted JUMBF box type at offset ${baseFileOffset + pos}."
                        break
                    }
                }
            }
            pos += boxLen
        }

        val hasJumbfBoxes = boxes.any { it.boxType == "jumb" || it.boxType == "jumd" }
        if (!hasJumbfBoxes && !hasError) {
            hasError = true
            errorReason = "C2PA carrier segment did not contain a valid 'jumb'/'jumd' superbox hierarchy."
        }
        return ParseBoxesResult(boxes, hasError, errorReason)
    }

    private fun describeJumbfSuperbox(uuidHex: String?, label: String?): String {
        val lowerUuid = uuidHex?.lowercase() ?: ""
        return when {
            lowerUuid == C2PA_MANIFEST_STORE_UUID || label == "c2pa" ->
                "C2PA Manifest Store Superbox (Root)"
            lowerUuid.startsWith(C2PA_CLAIM_UUID_PREFIX) || label?.contains("c2pa.claim") == true ->
                "C2PA Claim Superbox ($label)"
            lowerUuid.startsWith(C2PA_ASSERTIONS_UUID_PREFIX) || label?.contains("c2pa.assertions") == true ->
                "C2PA Assertion Store Superbox"
            lowerUuid.startsWith(C2PA_SIGNATURE_UUID_PREFIX) || label?.contains("c2pa.signature") == true ->
                "C2PA Cryptographic Signature Superbox"
            lowerUuid.startsWith(C2PA_MANIFEST_UUID_PREFIX) ->
                "C2PA Manifest Superbox (${label ?: "Active Manifest"})"
            else -> "JUMBF Superbox (${label ?: uuidHex ?: "Generic"})"
        }
    }
}
