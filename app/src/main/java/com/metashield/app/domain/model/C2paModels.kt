package com.metashield.app.domain.model

/**
 * Explicitly distinguishes the 5 required C2PA Content Credential validation states.
 * Never interprets NO_CREDENTIALS_FOUND as proof that an image is authentic or human-created.
 */
enum class C2paValidationStatus(
    val displayTitle: String,
    val badgeLabel: String,
    val summaryExplanation: String
) {
    VALID(
        displayTitle = "Valid Content Credentials",
        badgeLabel = "VALID",
        summaryExplanation = "The embedded C2PA manifest store, cryptographic COSE signature, and asset hash bindings were verified with zero validation errors."
    ),
    INVALID(
        displayTitle = "Invalid or Tampered Credentials",
        badgeLabel = "INVALID",
        summaryExplanation = "A C2PA manifest is embedded, but cryptographic signature verification or payload hash binding failed (image bytes or claim were modified after signing)."
    ),
    UNTRUSTED_OR_UNVERIFIABLE(
        displayTitle = "Untrusted or Unverifiable Credentials",
        badgeLabel = "UNTRUSTED / UNVERIFIABLE",
        summaryExplanation = "A structurally valid C2PA JUMBF manifest and COSE_Sign1 signature block are present, but the signing certificate could not be chained to a trusted root authority offline."
    ),
    NO_CREDENTIALS_FOUND(
        displayTitle = "No C2PA Content Credentials Found",
        badgeLabel = "NONE FOUND",
        summaryExplanation = "No embedded C2PA manifests, JUMBF 'c2pa' boxes, or XMP provenance references were detected in this image. Missing credentials do NOT prove an image is authentic or human-created."
    ),
    UNSUPPORTED_OR_UNREADABLE(
        displayTitle = "Unsupported or Unreadable Credentials",
        badgeLabel = "UNREADABLE",
        summaryExplanation = "A C2PA or JUMBF marker was detected, but the superbox structure is truncated, corrupted, or uses an unsupported specification version."
    )
}

data class C2paRecordedAction(
    val action: String,
    val softwareAgent: String? = null,
    val digitalSourceType: String? = null,
    val whenTimestamp: String? = null,
    val parametersSummary: String? = null
)

data class JumbfBoxNode(
    val boxType: String,
    val label: String?,
    val uuidHex: String?,
    val offset: Long,
    val length: Int,
    val description: String
)

data class C2paManifestDetail(
    val manifestId: String,
    val title: String?,
    val format: String?,
    val claimGenerator: String?,
    val claimGeneratorVersion: String?,
    val softwareAgents: List<String>,
    val signerIssuer: String?,
    val signerCommonName: String?,
    val signerOrganization: String?,
    val signatureAlgorithm: String?,
    val signingTime: String?,
    val actions: List<C2paRecordedAction>,
    val assertions: List<String>,
    val ingredients: List<String>,
    val hashAlgorithm: String?,
    val validationStateCode: String?
)

data class C2paInspectionReport(
    val status: C2paValidationStatus,
    val manifestExists: Boolean,
    val jumbfDetected: Boolean,
    val xmpProvenanceDetected: Boolean,
    val inspectedViaOfficialSdk: Boolean,
    val sdkVersionInfo: String,
    val activeManifestId: String?,
    val manifests: List<C2paManifestDetail>,
    val jumbfBoxes: List<JumbfBoxNode>,
    val totalC2paBytes: Int,
    val carrierLocations: List<String>,
    val validationErrors: List<String>,
    val rawManifestJson: String?,
    val epistemicNotice: String = EPISTEMIC_AUTHENTICITY_NOTICE
) {
    val hasC2paData: Boolean
        get() = manifestExists || jumbfDetected || xmpProvenanceDetected

    val primaryManifest: C2paManifestDetail?
        get() = manifests.firstOrNull { it.manifestId == activeManifestId } ?: manifests.firstOrNull()

    companion object {
        const val EPISTEMIC_AUTHENTICITY_NOTICE =
            "Important: The absence of C2PA Content Credentials does not prove that an image is authentic, unedited, or human-created. Conversely, valid credentials confirm who signed the manifest and what edits were declared, not whether the underlying scene is real."
    }
}
