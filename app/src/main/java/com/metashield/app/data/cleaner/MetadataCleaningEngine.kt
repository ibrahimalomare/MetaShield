package com.metashield.app.data.cleaner

import com.metashield.app.data.parser.MetadataScannerEngine
import com.metashield.app.domain.model.*
import java.io.File

/**
 * Master Format-Aware Metadata & C2PA Cleaning Engine.
 *
 * Guarantees:
 * 1. Never modifies or overwrites the original source file.
 * 2. Generates a separate verified output file (`*_metashield_clean.<ext>`).
 * 3. Uses format-aware lossless binary surgery for JPEG, PNG, WebP, and TIFF.
 * 4. Runs `PostCleaningVerifier` on the output file before reporting results.
 */
class MetadataCleaningEngine(
    private val scannerEngine: MetadataScannerEngine = MetadataScannerEngine(),
    private val postVerifier: PostCleaningVerifier = PostCleaningVerifier(scannerEngine)
) {

    suspend fun cleanAndVerify(
        scanReport: MetadataScanReport,
        options: CleaningOptions,
        outputDirectory: File,
        onStageProgress: suspend (stageIndex: Int, stageName: String, progressFraction: Float) -> Unit = { _, _, _ -> }
    ): CleaningAndVerificationResult {
        val startMs = System.currentTimeMillis()

        // Stage 1: Validate source container and options
        onStageProgress(1, "Validating container integrity & authorization", 0.15f)
        if (scanReport.isCorrupted || scanReport.imageInfo.format == ImageFormat.UNSUPPORTED) {
            val reason = scanReport.corruptionReason
                ?: "Format '${scanReport.imageInfo.format.displayName}' is not supported for safe format-aware metadata cleaning."
            return failedResult(scanReport, options, reason, startMs)
        }

        if (options.removeC2pa && scanReport.c2paReport.hasC2paData && !options.userAuthorizedProvenanceRemoval) {
            return failedResult(
                scanReport,
                options,
                "User authorization is required before removing C2PA Content Credentials.",
                startMs
            )
        }

        val sourceFile = File(scanReport.imageInfo.localCachePath)
        if (!sourceFile.exists() || !sourceFile.canRead()) {
            return failedResult(scanReport, options, "Source image cache file is not accessible.", startMs)
        }

        val sourceBytes = try {
            sourceFile.readBytes()
        } catch (oom: OutOfMemoryError) {
            return failedResult(scanReport, options, "Image exceeds available memory buffer.", startMs)
        } catch (e: Exception) {
            return failedResult(scanReport, options, "Failed to read source file: ${e.message}", startMs)
        }

        // Stage 2: Execute Format-Aware Binary Surgery
        onStageProgress(2, "Executing lossless ${scanReport.imageInfo.format.displayName} binary surgery", 0.45f)
        val surgeryResult = when (scanReport.imageInfo.format) {
            ImageFormat.JPEG -> JpegLosslessCleaner.cleanJpeg(sourceBytes, options)
            ImageFormat.PNG -> PngLosslessCleaner.cleanPng(sourceBytes, options)
            ImageFormat.WEBP -> WebpLosslessCleaner.cleanWebp(sourceBytes, options)
            ImageFormat.TIFF -> TiffFormatCleaner.cleanTiff(sourceBytes, options)
            ImageFormat.UNSUPPORTED -> Result.failure(
                UnsupportedOperationException("Unsupported image container format.")
            )
        }

        val outcome = surgeryResult.getOrElse { err ->
            return failedResult(
                scanReport,
                options,
                "Safe cleaning aborted: ${err.message ?: "Container structure could not be safely modified."}",
                startMs
            )
        }

        // Stage 3: Write separate output file (never overwrite sourceFile)
        onStageProgress(3, "Writing separate sanitized image container", 0.75f)
        outputDirectory.mkdirs()
        val baseName = scanReport.imageInfo.fileName
            .substringBeforeLast('.')
            .replace(Regex("[^a-zA-Z0-9_\-]"), "_")
            .ifBlank { "image" }
        val ext = scanReport.imageInfo.format.defaultExtension
        var outFile = File(outputDirectory, "${baseName}_metashield_clean.$ext")
        if (outFile.absolutePath == sourceFile.absolutePath) {
            outFile = File(outputDirectory, "${baseName}_metashield_clean_${System.currentTimeMillis()}.$ext")
        }

        try {
            outFile.writeBytes(outcome.cleanedBytes)
        } catch (e: Exception) {
            return failedResult(scanReport, options, "Failed to write cleaned output file: ${e.message}", startMs)
        }

        // Stage 4: Independent Post-Processing Re-Scan & Verification
        onStageProgress(4, "Re-scanning output file & verifying metadata removal", 0.95f)
        val verified = postVerifier.verifyOutput(
            beforeReport = scanReport,
            outputFile = outFile,
            options = options,
            surgeryOutcome = outcome,
            startTimeMs = startMs
        )
        onStageProgress(4, "Verification complete", 1.0f)
        return verified
    }

    private fun failedResult(
        beforeReport: MetadataScanReport,
        options: CleaningOptions,
        reason: String,
        startMs: Long
    ): CleaningAndVerificationResult {
        return CleaningAndVerificationResult(
            success = false,
            verdict = VerificationVerdict.FAILED,
            optionsUsed = options,
            beforeReport = beforeReport,
            afterReport = null,
            outputFilePath = null,
            outputFileName = null,
            outputFileSizeBytes = 0L,
            bytesRemoved = 0L,
            dimensionsPreserved = false,
            losslessBitstreamPreserved = false,
            categoryComparisons = emptyList(),
            removedSegmentsSummary = emptyList(),
            remainingOrUnsupportedNotes = listOf(reason),
            errorReason = reason,
            processingDurationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        )
    }
}
