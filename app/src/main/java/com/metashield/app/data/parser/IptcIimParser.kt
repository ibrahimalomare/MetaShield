package com.metashield.app.data.parser

import com.metashield.app.domain.model.ImageFormat
import com.metashield.app.domain.model.MetadataCategory
import com.metashield.app.domain.model.MetadataFieldItem
import com.metashield.app.domain.model.PrivacySensitivity

/**
 * Extracts IPTC-NAA IIM dataset records (0x1C 0x02 ...) from JPEG APP13 (0xFFED Photoshop 3.0 / 8BIM 0x0404)
 * and TIFF Tag 0x83BB (33723).
 */
object IptcIimParser {

    private val IPTC_DATASET_NAMES = mapOf(
        5 to ("Object Name (Title)" to PrivacySensitivity.MODERATE),
        15 to ("Category" to PrivacySensitivity.LOW),
        25 to ("Keywords" to PrivacySensitivity.MODERATE),
        40 to ("Special Instructions" to PrivacySensitivity.MODERATE),
        55 to ("Date Created" to PrivacySensitivity.HIGH),
        60 to ("Time Created" to PrivacySensitivity.HIGH),
        80 to ("By-line (Author)" to PrivacySensitivity.CRITICAL),
        85 to ("By-line Title" to PrivacySensitivity.HIGH),
        90 to ("City" to PrivacySensitivity.CRITICAL),
        92 to ("Sub-location" to PrivacySensitivity.CRITICAL),
        95 to ("Province / State" to PrivacySensitivity.HIGH),
        100 to ("Country Primary Location Code" to PrivacySensitivity.HIGH),
        101 to ("Country Primary Location Name" to PrivacySensitivity.HIGH),
        105 to ("Headline" to PrivacySensitivity.MODERATE),
        110 to ("Credit" to PrivacySensitivity.HIGH),
        115 to ("Source" to PrivacySensitivity.HIGH),
        116 to ("Copyright Notice" to PrivacySensitivity.HIGH),
        120 to ("Caption / Abstract" to PrivacySensitivity.MODERATE),
        122 to ("Writer / Editor" to PrivacySensitivity.HIGH)
    )

    data class IptcExtractionResult(
        val fields: List<MetadataFieldItem>,
        val totalIptcBytes: Int
    )

    fun extractIptc(bytes: ByteArray, format: ImageFormat): IptcExtractionResult {
        val fields = mutableListOf<MetadataFieldItem>()
        var totalBytes = 0

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

                    // APP13 (0xFFED) Photoshop IRB / IPTC
                    if (marker == 0xED && segLen > 16) {
                        val segOffset = (pos - 2).toLong()
                        totalBytes += (segLen + 2)
                        val start = pos + 2
                        val end = pos + segLen
                        val parsed = parseIptcDatasetsFromRange(bytes, start, end, "JPEG APP13 (Photoshop 8BIM / IPTC)", segOffset)
                        if (parsed.isNotEmpty()) {
                            fields.addAll(parsed)
                        } else {
                            fields.add(
                                MetadataFieldItem(
                                    category = MetadataCategory.IPTC,
                                    key = "Photoshop 8BIM Resource Block",
                                    value = "${segLen - 2} bytes",
                                    containerLocation = "JPEG APP13 (0xFFED)",
                                    byteOffset = segOffset,
                                    byteLength = segLen + 2,
                                    sensitivity = PrivacySensitivity.MODERATE
                                )
                            )
                        }
                    }
                    pos += segLen
                }
            }
            ImageFormat.TIFF -> {
                if (bytes.size >= 8) {
                    val littleEndian = bytes[0] == 'I'.code.toByte()
                    val ifdOffset = FormatDetector.readInt(bytes, 4, littleEndian)
                    if (ifdOffset >= 8 && ifdOffset + 2 <= bytes.size) {
                        val countEntries = FormatDetector.readUShort(bytes, ifdOffset, littleEndian)
                        if (ifdOffset + 2 + countEntries * 12 <= bytes.size) {
                            for (i in 0 until countEntries) {
                                val entryPos = ifdOffset + 2 + i * 12
                                val tag = FormatDetector.readUShort(bytes, entryPos, littleEndian)
                                val count = FormatDetector.readInt(bytes, entryPos + 4, littleEndian)
                                val valOffset = FormatDetector.readInt(bytes, entryPos + 8, littleEndian)
                                if ((tag == 0x83BB || tag == 0x8649) && count > 4 && valOffset >= 8 && valOffset + count <= bytes.size) {
                                    totalBytes += (count + 12)
                                    val parsed = parseIptcDatasetsFromRange(
                                        bytes, valOffset, valOffset + count, "TIFF Tag 0x${tag.toString(16).uppercase()}", valOffset.toLong()
                                    )
                                    fields.addAll(parsed)
                                }
                            }
                        }
                    }
                }
            }
            else -> {}
        }

        return IptcExtractionResult(fields, totalBytes)
    }

    private fun parseIptcDatasetsFromRange(
        bytes: ByteArray,
        start: Int,
        end: Int,
        location: String,
        baseOffset: Long
    ): List<MetadataFieldItem> {
        val items = mutableListOf<MetadataFieldItem>()
        var i = start
        while (i + 5 <= end) {
            // IPTC IIM Record 2 marker: 0x1C 0x02 [dataset] [lenHi] [lenLo]
            if ((bytes[i].toInt() and 0xFF) == 0x1C && (bytes[i + 1].toInt() and 0xFF) == 0x02) {
                val dataset = bytes[i + 2].toInt() and 0xFF
                val dataLen = ((bytes[i + 3].toInt() and 0xFF) shl 8) or (bytes[i + 4].toInt() and 0xFF)
                if (dataLen in 1..2048 && i + 5 + dataLen <= end) {
                    val valueStr = String(bytes, i + 5, dataLen, Charsets.UTF_8).trim()
                    val (name, sens) = IPTC_DATASET_NAMES[dataset]
                        ?: ("IPTC Dataset 2:$dataset" to PrivacySensitivity.MODERATE)
                    items.add(
                        MetadataFieldItem(
                            category = MetadataCategory.IPTC,
                            key = name,
                            value = valueStr,
                            tagIdHex = "2:%02d".format(dataset),
                            containerLocation = location,
                            byteOffset = baseOffset + (i - start),
                            byteLength = dataLen + 5,
                            sensitivity = sens
                        )
                    )
                    i += 5 + dataLen
                    continue
                }
            }
            i++
        }
        return items
    }
}
