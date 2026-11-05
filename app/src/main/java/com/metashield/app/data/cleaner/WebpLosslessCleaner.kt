package com.metashield.app.data.cleaner

import com.metashield.app.data.parser.FormatDetector
import com.metashield.app.domain.model.CleaningOptions
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lossless Format-Aware WebP RIFF Container Surgeon.
 *
 * Excises `C2PA`/`jumb`, `EXIF`, and `XMP ` chunks from the RIFF `WEBP` container, updates the `VP8X`
 * feature bitmask (clearing EXIF bit `0x08` and XMP bit `0x04` when removed), and recalculates the
 * 32-bit little-endian RIFF container size while keeping the `VP8 `/`VP8L` compressed image bitstream untouched.
 */
object WebpLosslessCleaner {

    private data class RiffChunk(
        val fourCC: String,
        val payload: ByteArray
    )

    fun cleanWebp(sourceBytes: ByteArray, options: CleaningOptions): Result<JpegLosslessCleaner.SurgeryOutcome> {
        if (sourceBytes.size < 20 ||
            String(sourceBytes, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(sourceBytes, 8, 4, Charsets.US_ASCII) != "WEBP"
        ) {
            return Result.failure(IllegalArgumentException("Source buffer is not a valid RIFF WEBP container."))
        }

        val keptChunks = mutableListOf<RiffChunk>()
        val removed = mutableListOf<String>()
        val preserved = mutableListOf<String>()

        var pos = 12
        var hasExifRemaining = false
        var hasXmpRemaining = false
        var hasIccpRemaining = false
        var hasBitstream = false

        while (pos + 8 <= sourceBytes.size) {
            val fourCC = String(sourceBytes, pos, 4, Charsets.US_ASCII)
            val chunkSize = FormatDetector.readIntLE(sourceBytes, pos + 4)
            if (chunkSize < 0 || pos + 8L + chunkSize > sourceBytes.size) {
                return Result.failure(IllegalStateException("Truncated WebP chunk '$fourCC' at offset $pos."))
            }
            val payload = sourceBytes.sliceArray((pos + 8) until (pos + 8 + chunkSize))
            val fullSize = 8 + chunkSize + (chunkSize and 1)

            var shouldRemove = false
            when (fourCC) {
                "C2PA", "jumb" -> {
                    if (options.removeC2pa) {
                        shouldRemove = true
                        removed.add("Removed WebP '$fourCC' C2PA JUMBF chunk ($fullSize B)")
                    } else {
                        preserved.add("Preserved WebP '$fourCC' C2PA chunk ($fullSize B) per user selection.")
                    }
                }
                "EXIF" -> {
                    if (options.removeExif) {
                        shouldRemove = true
                        removed.add("Removed WebP 'EXIF' chunk ($fullSize B)")
                    } else {
                        hasExifRemaining = true
                        preserved.add("Preserved WebP 'EXIF' chunk ($fullSize B) per user selection.")
                    }
                }
                "XMP " -> {
                    if (options.removeXmp) {
                        shouldRemove = true
                        removed.add("Removed WebP 'XMP ' chunk ($fullSize B)")
                    } else {
                        hasXmpRemaining = true
                        preserved.add("Preserved WebP 'XMP ' chunk ($fullSize B) per user selection.")
                    }
                }
                "ICCP" -> {
                    if (!options.preserveIccColorProfile && options.removeOtherMetadata) {
                        shouldRemove = true
                        removed.add("Removed WebP 'ICCP' color profile chunk ($fullSize B)")
                    } else {
                        hasIccpRemaining = true
                        preserved.add("Preserved WebP 'ICCP' color profile ($fullSize B).")
                    }
                }
                "VP8 ", "VP8L" -> {
                    hasBitstream = true
                }
            }

            if (!shouldRemove) {
                keptChunks.add(RiffChunk(fourCC, payload))
            }
            pos += fullSize
        }

        if (!hasBitstream) {
            return Result.failure(IllegalStateException("WebP container has no VP8/VP8L image bitstream."))
        }

        // Update VP8X flags if VP8X chunk is present
        val updatedChunks = keptChunks.map { chunk ->
            if (chunk.fourCC == "VP8X" && chunk.payload.size >= 10) {
                val newPayload = chunk.payload.copyOf()
                var flags = newPayload[0].toInt() and 0xFF
                flags = if (hasExifRemaining) (flags or 0x08) else (flags and 0x08.inv())
                flags = if (hasXmpRemaining) (flags or 0x04) else (flags and 0x04.inv())
                flags = if (hasIccpRemaining) (flags or 0x20) else (flags and 0x20.inv())
                newPayload[0] = flags.toByte()
                RiffChunk("VP8X", newPayload)
            } else {
                chunk
            }
        }

        val bodyOut = ByteArrayOutputStream()
        bodyOut.write("WEBP".toByteArray(Charsets.US_ASCII))
        for (chunk in updatedChunks) {
            bodyOut.write(chunk.fourCC.toByteArray(Charsets.US_ASCII))
            val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunk.payload.size).array()
            bodyOut.write(lenBytes)
            bodyOut.write(chunk.payload)
            if ((chunk.payload.size and 1) == 1) {
                bodyOut.write(0)
            }
        }
        val bodyBytes = bodyOut.toByteArray()

        val finalOut = ByteArrayOutputStream(8 + bodyBytes.size)
        finalOut.write("RIFF".toByteArray(Charsets.US_ASCII))
        val riffSizeBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(bodyBytes.size).array()
        finalOut.write(riffSizeBytes)
        finalOut.write(bodyBytes)

        return Result.success(
            JpegLosslessCleaner.SurgeryOutcome(
                cleanedBytes = finalOut.toByteArray(),
                removedSegments = removed,
                preservedNotes = preserved
            )
        )
    }
}
