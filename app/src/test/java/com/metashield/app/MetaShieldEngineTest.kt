package com.metashield.app

import com.metashield.app.data.cleaner.MetadataCleaningEngine
import com.metashield.app.data.parser.MetadataScannerEngine
import com.metashield.app.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Comprehensive JUnit 4 Test Suite covering all 10 required MetaShield test scenarios:
 * 1. Images with C2PA credentials (VALID, INVALID, UNTRUSTED_OR_UNVERIFIABLE)
 * 2. Images without C2PA credentials (+ epistemic notice verification)
 * 3. Images with EXIF metadata (+ GPS IFD)
 * 4. Images with XMP metadata
 * 5. Images with multiple metadata types (C2PA + EXIF + XMP + IPTC + COM + ICC)
 * 6. Unsupported image formats
 * 7. Corrupted images (truncated JPEG, bad PNG CRC, truncated JUMBF)
 * 8. Large image files (multi-MB container lossless surgery)
 * 9. Failed cleaning operations (un-authorized C2PA removal, corrupted container)
 * 10. Post-cleaning verification (VERIFIED_CLEAN, PARTIALLY_CLEANED, original file preservation)
 */
class MetaShieldEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scanner: MetadataScannerEngine
    private lateinit var cleaner: MetadataCleaningEngine
    private lateinit var outputDir: File

    @Before
    fun setUp() {
        scanner = MetadataScannerEngine()
        cleaner = MetadataCleaningEngine(scanner)
        outputDir = tempFolder.newFolder("cleaned_outputs")
    }

    @Test
    fun test01_imageWithValidUntrustedAndTamperedC2paCredentials() {
        // 1A. Valid C2PA Credentials
        val validBytes = TestImageFactory.buildSyntheticJpeg(
            includeC2pa = true,
            c2paVerificationMarker = "TRUSTED_CA_VERIFIED"
        )
        val validReport = scanner.scanBytes(validBytes)
        assertFalse(validReport.isCorrupted)
        assertEquals(C2paValidationStatus.VALID, validReport.c2paReport.status)
        assertTrue(validReport.c2paReport.manifestExists)
        assertTrue(validReport.c2paReport.jumbfDetected)
        assertNotNull(validReport.c2paReport.primaryManifest)
        assertEquals(
            "Adobe_Photoshop/26.2 C2PA-RS/0.36.0",
            validReport.c2paReport.primaryManifest?.claimGenerator
        )
        assertTrue(validReport.c2paReport.primaryManifest!!.actions.any { it.action == "c2pa.edited" })

        // 1B. Untrusted / Unverifiable C2PA Credentials
        val untrustedBytes = TestImageFactory.buildSyntheticJpeg(
            includeC2pa = true,
            c2paVerificationMarker = "SELF_SIGNED_OFFLINE"
        )
        val untrustedReport = scanner.scanBytes(untrustedBytes)
        assertEquals(C2paValidationStatus.UNTRUSTED_OR_UNVERIFIABLE, untrustedReport.c2paReport.status)

        // 1C. Invalid / Tampered C2PA Credentials
        val tamperedBytes = TestImageFactory.buildSyntheticJpeg(
            includeC2pa = true,
            c2paVerificationMarker = "TAMPERED_SIGNATURE_HASH_MISMATCH"
        )
        val tamperedReport = scanner.scanBytes(tamperedBytes)
        assertEquals(C2paValidationStatus.INVALID, tamperedReport.c2paReport.status)
        assertTrue(tamperedReport.c2paReport.validationErrors.isNotEmpty())
    }

    @Test
    fun test02_imageWithoutC2paCredentials() {
        val noC2paBytes = TestImageFactory.buildSyntheticJpeg(
            includeC2pa = false,
            includeExif = false,
            includeXmp = false,
            includeIptc = false,
            includeComment = false
        )
        val report = scanner.scanBytes(noC2paBytes)
        assertFalse(report.isCorrupted)
        assertEquals(C2paValidationStatus.NO_CREDENTIALS_FOUND, report.c2paReport.status)
        assertFalse(report.c2paReport.manifestExists)
        assertFalse(report.c2paReport.jumbfDetected)
        assertTrue(report.c2paReport.epistemicNotice.contains("does not prove"))
    }

    @Test
    fun test03_imageWithExifAndGpsMetadata() {
        val exifBytes = TestImageFactory.buildSyntheticJpeg(
            includeC2pa = false,
            includeExif = true,
            includeXmp = false,
            includeIptc = false
        )
        val report = scanner.scanBytes(exifBytes)
        val exifFields = report.fieldsFor(MetadataCategory.EXIF)
        assertTrue(exifFields.any { it.key == "Make" && it.value == "Sony" })
        assertTrue(exifFields.any { it.key == "Model" && it.value == "ILCE-7RM5" })
        assertTrue(report.hasGpsCoordinates)
        assertTrue(report.hasDeviceIdentifiers)
    }

    @Test
    fun test04_imageWithXmpMetadata() {
        val xmpBytes = TestImageFactory.buildSyntheticJpeg(
            includeC2pa = false,
            includeExif = false,
            includeXmp = true,
            includeIptc = false
        )
        val report = scanner.scanBytes(xmpBytes)
        val xmpFields = report.fieldsFor(MetadataCategory.XMP)
        assertTrue(xmpFields.any { it.key == "xmp:CreatorTool" && it.value == "Adobe Photoshop 26.2" })
    }

    @Test
    fun test05_imageWithMultipleMetadataTypesAcrossFormats() {
        // Test JPEG with C2PA + EXIF + XMP + IPTC + COM + ICC
        val multiJpeg = TestImageFactory.buildSyntheticJpeg(
            includeC2pa = true,
            includeExif = true,
            includeXmp = true,
            includeIptc = true,
            includeComment = true,
            includeIccProfile = true
        )
        val jpegReport = scanner.scanBytes(multiJpeg)
        assertTrue(jpegReport.c2paReport.hasC2paData)
        assertTrue(jpegReport.fieldsFor(MetadataCategory.EXIF).isNotEmpty())
        assertTrue(jpegReport.fieldsFor(MetadataCategory.XMP).isNotEmpty())
        assertTrue(jpegReport.fieldsFor(MetadataCategory.IPTC).isNotEmpty())
        assertTrue(jpegReport.fieldsFor(MetadataCategory.OTHER).isNotEmpty())

        // Test PNG with caBX C2PA + eXIf + iTXt XMP + tEXt
        val multiPng = TestImageFactory.buildSyntheticPng(
            includeC2pa = true,
            includeExif = true,
            includeXmp = true,
            includeText = true
        )
        val pngReport = scanner.scanBytes(multiPng)
        assertEquals(ImageFormat.PNG, pngReport.imageInfo.format)
        assertTrue(pngReport.c2paReport.hasC2paData)
        assertTrue(pngReport.fieldsFor(MetadataCategory.EXIF).isNotEmpty())
        assertTrue(pngReport.fieldsFor(MetadataCategory.XMP).isNotEmpty())

        // Test WebP with C2PA + EXIF + XMP
        val multiWebp = TestImageFactory.buildSyntheticWebp(
            includeC2pa = true,
            includeExif = true,
            includeXmp = true
        )
        val webpReport = scanner.scanBytes(multiWebp)
        assertEquals(ImageFormat.WEBP, webpReport.imageInfo.format)
        assertTrue(webpReport.c2paReport.hasC2paData)
        assertTrue(webpReport.fieldsFor(MetadataCategory.EXIF).isNotEmpty())
        assertTrue(webpReport.fieldsFor(MetadataCategory.XMP).isNotEmpty())
    }

    @Test
    fun test06_unsupportedImageFormat() {
        val randomBytes = "BM6_UNSUPPORTED_BITMAP_HEADER_DATA_1234567890".toByteArray(Charsets.US_ASCII)
        val report = scanner.scanBytes(randomBytes)
        assertEquals(ImageFormat.UNSUPPORTED, report.imageInfo.format)
        assertTrue(report.isCorrupted)
    }

    @Test
    fun test07_corruptedImageAndTruncatedJumbf() {
        // 7A. Truncated JPEG missing SOS and EOI
        val truncatedJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 1, 2, 3)
        val corruptReport = scanner.scanBytes(truncatedJpeg)
        assertTrue(corruptReport.isCorrupted)
        assertNotNull(corruptReport.corruptionReason)

        // 7B. Valid JPEG with truncated/unreadable C2PA JUMBF superbox
        val truncatedJumbfJpeg = TestImageFactory.buildSyntheticJpeg(
            includeC2pa = true,
            truncateC2pa = true
        )
        val jumbfReport = scanner.scanBytes(truncatedJumbfJpeg)
        assertFalse(jumbfReport.isCorrupted)
        assertEquals(C2paValidationStatus.UNSUPPORTED_OR_UNREADABLE, jumbfReport.c2paReport.status)
    }

    @Test
    fun test08_largeImageFileProcessing() = runBlocking {
        // 2.5 MB entropy scan payload
        val largeJpegBytes = TestImageFactory.buildSyntheticJpeg(
            width = 4000,
            height = 3000,
            entropyScanSize = 2_500_000
        )
        val srcFile = tempFolder.newFile("large_4k_photo.jpg")
        srcFile.writeBytes(largeJpegBytes)

        val scanReport = scanner.scanFile(srcFile)
        assertEquals(4000, scanReport.imageInfo.width)
        assertEquals(3000, scanReport.imageInfo.height)

        val result = cleaner.cleanAndVerify(
            scanReport = scanReport,
            options = CleaningOptions(userAuthorizedProvenanceRemoval = true),
            outputDirectory = outputDir
        )
        assertTrue(result.success)
        assertEquals(VerificationVerdict.VERIFIED_CLEAN, result.verdict)
        assertTrue(result.dimensionsPreserved)
        assertEquals(4000, result.afterReport?.imageInfo?.width)
        assertEquals(3000, result.afterReport?.imageInfo?.height)
    }

    @Test
    fun test09_failedCleaningOperationsSafelyAborted() = runBlocking {
        // 9A. Unauthorized C2PA removal is rejected
        val srcFile = tempFolder.newFile("c2pa_protected.jpg")
        srcFile.writeBytes(TestImageFactory.buildSyntheticJpeg(includeC2pa = true))
        val scanReport = scanner.scanFile(srcFile)

        val unauthResult = cleaner.cleanAndVerify(
            scanReport = scanReport,
            options = CleaningOptions(removeC2pa = true, userAuthorizedProvenanceRemoval = false),
            outputDirectory = outputDir
        )
        assertFalse(unauthResult.success)
        assertEquals(VerificationVerdict.FAILED, unauthResult.verdict)
        assertTrue(unauthResult.errorReason!!.contains("authorization", ignoreCase = true))

        // 9B. Corrupted image cleaning is safely aborted
        val corruptFile = tempFolder.newFile("corrupt.jpg")
        corruptFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x01, 0x02))
        val corruptScan = scanner.scanFile(corruptFile)
        val corruptClean = cleaner.cleanAndVerify(
            scanReport = corruptScan,
            options = CleaningOptions(userAuthorizedProvenanceRemoval = true),
            outputDirectory = outputDir
        )
        assertFalse(corruptClean.success)
        assertEquals(VerificationVerdict.FAILED, corruptClean.verdict)
    }

    @Test
    fun test10_postCleaningVerificationAndOriginalFilePreservation() = runBlocking {
        val originalBytes = TestImageFactory.buildSyntheticJpeg(
            width = 1920,
            height = 1080,
            includeC2pa = true,
            includeExif = true,
            includeXmp = true,
            includeIptc = true,
            includeComment = true,
            includeIccProfile = true
        )
        val srcFile = tempFolder.newFile("original_camera.jpg")
        srcFile.writeBytes(originalBytes)
        val originalHashBefore = scanReportHash(srcFile)

        val beforeReport = scanner.scanFile(srcFile)
        assertTrue(beforeReport.c2paReport.hasC2paData)
        assertTrue(beforeReport.totalMetadataBytes > 0)

        // 10A. Full Cleaning -> VERIFIED_CLEAN
        val fullResult = cleaner.cleanAndVerify(
            scanReport = beforeReport,
            options = CleaningOptions(
                removeC2pa = true,
                removeExif = true,
                removeXmp = true,
                removeIptc = true,
                removeOtherMetadata = true,
                preserveIccColorProfile = true,
                userAuthorizedProvenanceRemoval = true
            ),
            outputDirectory = outputDir
        )
        assertTrue(fullResult.success)
        assertEquals(VerificationVerdict.VERIFIED_CLEAN, fullResult.verdict)
        assertFalse(fullResult.afterReport!!.c2paReport.hasC2paData)
        assertEquals(0, fullResult.afterReport!!.fieldsFor(MetadataCategory.EXIF).size)
        assertEquals(0, fullResult.afterReport!!.fieldsFor(MetadataCategory.XMP).size)
        assertEquals(0, fullResult.afterReport!!.fieldsFor(MetadataCategory.IPTC).size)
        assertTrue(fullResult.bytesRemoved > 0)

        // Verify original file is 100% preserved and untouched
        assertEquals(originalHashBefore, scanReportHash(srcFile))
        assertNotEquals(srcFile.absolutePath, fullResult.outputFilePath)

        // 10B. Selective Cleaning (Remove C2PA, keep EXIF) -> PARTIALLY_CLEANED
        val partialResult = cleaner.cleanAndVerify(
            scanReport = beforeReport,
            options = CleaningOptions(
                removeC2pa = true,
                removeExif = false,
                removeXmp = true,
                removeIptc = true,
                removeOtherMetadata = true,
                userAuthorizedProvenanceRemoval = true
            ),
            outputDirectory = outputDir
        )
        assertTrue(partialResult.success)
        assertEquals(VerificationVerdict.PARTIALLY_CLEANED, partialResult.verdict)
        assertFalse(partialResult.afterReport!!.c2paReport.hasC2paData)
        assertTrue(partialResult.afterReport!!.fieldsFor(MetadataCategory.EXIF).isNotEmpty())
    }

    private fun scanReportHash(file: File): String =
        scanner.scanFile(file).imageInfo.sha256Hex
}
