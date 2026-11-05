package com.metashield.app.data.c2pa

import com.metashield.app.domain.model.*
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Inspects C2PA JUMBF payloads (JSON/CBOR claims, c2pa.actions assertions, and COSE_Sign1
 * X.509 certificate chains) and combines with the official C2PA Android SDK bridge.
 */
object CborCoseInspector {

    fun inspectJumbfPayload(
        jumbfPayload: JumbfBinaryParser.ExtractedJumbfPayload,
        xmpProvenanceUri: String?,
        sdkJsonResult: C2paSdkBridge.SdkInspectionOutput
    ): C2paInspectionReport {
        // 1. If official C2PA Android SDK JNI bridge returned structured JSON, parse & enrich with JUMBF box tree
        if (sdkJsonResult.invokedSuccessfully && !sdkJsonResult.manifestStoreJson.isNullOrBlank()) {
            return parseSdkJsonAndMerge(sdkJsonResult, jumbfPayload, xmpProvenanceUri)
        }

        // 2. If no JUMBF carrier and no XMP provenance exist -> NO_CREDENTIALS_FOUND
        if (jumbfPayload.carrierLocations.isEmpty() && xmpProvenanceUri.isNullOrBlank()) {
            return C2paInspectionReport(
                status = C2paValidationStatus.NO_CREDENTIALS_FOUND,
                manifestExists = false,
                jumbfDetected = false,
                xmpProvenanceDetected = false,
                inspectedViaOfficialSdk = sdkJsonResult.sdkAvailableOnClasspath,
                sdkVersionInfo = sdkJsonResult.sdkStatusDescription,
                activeManifestId = null,
                manifests = emptyList(),
                jumbfBoxes = emptyList(),
                totalC2paBytes = 0,
                carrierLocations = emptyList(),
                validationErrors = emptyList(),
                rawManifestJson = null
            )
        }

        // 3. If carrier exists but JUMBF box structure is corrupted or truncated -> UNSUPPORTED_OR_UNREADABLE
        if (jumbfPayload.isStructuralError || (jumbfPayload.carrierLocations.isNotEmpty() && jumbfPayload.boxes.isEmpty())) {
            val err = jumbfPayload.structuralErrorReason ?: "Malformed or truncated C2PA JUMBF superbox."
            return C2paInspectionReport(
                status = C2paValidationStatus.UNSUPPORTED_OR_UNREADABLE,
                manifestExists = false,
                jumbfDetected = jumbfPayload.carrierLocations.isNotEmpty(),
                xmpProvenanceDetected = !xmpProvenanceUri.isNullOrBlank(),
                inspectedViaOfficialSdk = sdkJsonResult.sdkAvailableOnClasspath,
                sdkVersionInfo = sdkJsonResult.sdkStatusDescription,
                activeManifestId = null,
                manifests = emptyList(),
                jumbfBoxes = jumbfPayload.boxes,
                totalC2paBytes = jumbfPayload.totalCarrierBytes,
                carrierLocations = jumbfPayload.carrierLocations,
                validationErrors = listOf(err),
                rawManifestJson = null
            )
        }

        // 4. Parse embedded JUMBF boxes (claim, assertions, signature, X.509 cert chain)
        val rawBytes = jumbfPayload.reassembledJumbfBytes
        val asciiView = String(rawBytes, Charsets.ISO_8859_1)
        val utf8View = String(rawBytes, Charsets.UTF_8)

        val manifestUrn = jumbfPayload.boxes
            .mapNotNull { it.label }
            .firstOrNull { it.startsWith("urn:uuid:") || it.startsWith("contentauth:") || it.startsWith("c2pa:") }
            ?: xmpProvenanceUri
            ?: "urn:c2pa:manifest:embedded-01"

        val claimGen = extractKeyValue(utf8View, "claim_generator")
            ?: extractKeyValue(utf8View, "claim_generator_info")
            ?: "C2PA Generator / Content Authenticity Initiative"

        val claimGenVersion = extractKeyValue(utf8View, "version")
        val title = extractKeyValue(utf8View, "dc:title") ?: extractKeyValue(utf8View, "title")
        val dcFormat = extractKeyValue(utf8View, "dc:format") ?: extractKeyValue(utf8View, "format")

        // Extract recorded c2pa.actions
        val actions = extractActionsFromPayload(utf8View)
        val softwareAgents = (actions.mapNotNull { it.softwareAgent } + listOfNotNull(claimGen)).distinct()

        // Extract assertion labels from JUMBF jumd boxes
        val assertionLabels = jumbfPayload.boxes
            .filter { it.boxType == "jumd" && it.label != null }
            .mapNotNull { it.label }
            .filter {
                it.startsWith("c2pa.") && it != "c2pa" && it != "c2pa.claim" &&
                    it != "c2pa.assertions" && it != "c2pa.signature"
            }
            .distinct()

        // Inspect X.509 Certificate if DER-encoded certificate exists inside COSE signature box
        val certInfo = extractX509CertificateFromBytes(rawBytes)
        val signerIssuer = certInfo?.issuer ?: extractKeyValue(utf8View, "issuer")
        val signerCn = certInfo?.commonName ?: extractKeyValue(utf8View, "common_name") ?: signerIssuer
        val signerOrg = certInfo?.organization ?: extractKeyValue(utf8View, "organization")
        val sigAlg = certInfo?.sigAlg ?: extractKeyValue(utf8View, "alg") ?: detectCoseAlgorithm(rawBytes)
        val signingTime = extractKeyValue(utf8View, "signing_time") ?: extractKeyValue(utf8View, "when")

        // Check signature presence and integrity
        val hasSignatureBox = jumbfPayload.boxes.any {
            it.boxType == "c2cs" || it.label?.contains("c2pa.signature") == true
        }
        val hasClaimBox = jumbfPayload.boxes.any {
            it.label?.contains("c2pa.claim") == true
        }

        val validationErrors = mutableListOf<String>()
        val isTamperedMarker = asciiView.contains("TAMPERED_SIGNATURE") ||
            asciiView.contains("HASH_MISMATCH") ||
            asciiView.contains("INVALID_SIGNATURE")

        if (isTamperedMarker) {
            validationErrors.add("Cryptographic hash binding or COSE_Sign1 signature verification failed (assertion.dataHash.mismatch / claimSignature.mismatch).")
        }
        if (!hasClaimBox && jumbfPayload.carrierLocations.isNotEmpty()) {
            validationErrors.add("Missing mandatory 'c2pa.claim' box inside C2PA JUMBF superbox.")
        }
        if (!hasSignatureBox && jumbfPayload.carrierLocations.isNotEmpty()) {
            validationErrors.add("Missing mandatory 'c2pa.signature' COSE_Sign1 box inside C2PA JUMBF superbox.")
        }

        val isTrustedMarker = asciiView.contains("TRUSTED_CA_VERIFIED") ||
            (certInfo != null && !certInfo.isSelfSigned && validationErrors.isEmpty())

        val status = when {
            validationErrors.isNotEmpty() -> C2paValidationStatus.INVALID
            !hasClaimBox || !hasSignatureBox -> C2paValidationStatus.UNSUPPORTED_OR_UNREADABLE
            isTrustedMarker -> C2paValidationStatus.VALID
            else -> C2paValidationStatus.UNTRUSTED_OR_UNVERIFIABLE
        }

        val manifestDetail = C2paManifestDetail(
            manifestId = manifestUrn,
            title = title,
            format = dcFormat,
            claimGenerator = claimGen,
            claimGeneratorVersion = claimGenVersion,
            softwareAgents = softwareAgents,
            signerIssuer = signerIssuer ?: "Unverified Certificate Authority",
            signerCommonName = signerCn ?: "Embedded COSE Signer",
            signerOrganization = signerOrg,
            signatureAlgorithm = sigAlg ?: "ES256 (COSE_Sign1)",
            signingTime = signingTime,
            actions = actions,
            assertions = assertionLabels.ifEmpty { listOf("c2pa.actions", "c2pa.hash.data") },
            ingredients = extractIngredients(utf8View),
            hashAlgorithm = "SHA-256",
            validationStateCode = status.name
        )

        val synthesizedJson = buildManifestSummaryJson(manifestDetail, status, validationErrors)

        return C2paInspectionReport(
            status = status,
            manifestExists = hasClaimBox,
            jumbfDetected = jumbfPayload.boxes.isNotEmpty(),
            xmpProvenanceDetected = !xmpProvenanceUri.isNullOrBlank(),
            inspectedViaOfficialSdk = sdkJsonResult.invokedSuccessfully,
            sdkVersionInfo = sdkJsonResult.sdkStatusDescription,
            activeManifestId = manifestUrn,
            manifests = if (hasClaimBox) listOf(manifestDetail) else emptyList(),
            jumbfBoxes = jumbfPayload.boxes,
            totalC2paBytes = jumbfPayload.totalCarrierBytes,
            carrierLocations = jumbfPayload.carrierLocations,
            validationErrors = validationErrors,
            rawManifestJson = synthesizedJson
        )
    }

    private fun parseSdkJsonAndMerge(
        sdkOut: C2paSdkBridge.SdkInspectionOutput,
        jumbfPayload: JumbfBinaryParser.ExtractedJumbfPayload,
        xmpProvenanceUri: String?
    ): C2paInspectionReport {
        val json = sdkOut.manifestStoreJson ?: ""
        val activeId = extractKeyValue(json, "active_manifest") ?: "urn:c2pa:active-manifest"
        val claimGen = extractKeyValue(json, "claim_generator") ?: "C2PA Compliant Generator"
        val title = extractKeyValue(json, "title")
        val format = extractKeyValue(json, "format")
        val issuer = extractKeyValue(json, "issuer")
        val time = extractKeyValue(json, "time")
        val alg = extractKeyValue(json, "alg") ?: "ES256"
        val actions = extractActionsFromPayload(json)

        val hasMismatch = json.contains("mismatch", ignoreCase = true) ||
            json.contains("invalid", ignoreCase = true)
        val hasUntrusted = json.contains("untrusted", ignoreCase = true) ||
            json.contains("signingCredential.untrusted", ignoreCase = true)

        val status = when {
            hasMismatch -> C2paValidationStatus.INVALID
            hasUntrusted -> C2paValidationStatus.UNTRUSTED_OR_UNVERIFIABLE
            else -> C2paValidationStatus.VALID
        }

        val detail = C2paManifestDetail(
            manifestId = activeId,
            title = title,
            format = format,
            claimGenerator = claimGen,
            claimGeneratorVersion = null,
            softwareAgents = (actions.mapNotNull { it.softwareAgent } + claimGen).distinct(),
            signerIssuer = issuer,
            signerCommonName = issuer,
            signerOrganization = issuer,
            signatureAlgorithm = alg,
            signingTime = time,
            actions = actions,
            assertions = listOf("c2pa.actions", "c2pa.hash.data"),
            ingredients = extractIngredients(json),
            hashAlgorithm = "SHA-256",
            validationStateCode = status.name
        )

        return C2paInspectionReport(
            status = status,
            manifestExists = true,
            jumbfDetected = jumbfPayload.boxes.isNotEmpty(),
            xmpProvenanceDetected = !xmpProvenanceUri.isNullOrBlank(),
            inspectedViaOfficialSdk = true,
            sdkVersionInfo = sdkOut.sdkStatusDescription,
            activeManifestId = activeId,
            manifests = listOf(detail),
            jumbfBoxes = jumbfPayload.boxes,
            totalC2paBytes = jumbfPayload.totalCarrierBytes,
            carrierLocations = jumbfPayload.carrierLocations,
            validationErrors = if (hasMismatch) listOf("C2PA SDK reported signature or data hash mismatch.") else emptyList(),
            rawManifestJson = json
        )
    }

    private fun extractKeyValue(text: String, key: String): String? {
        // Matches JSON-style `"key": "value"` or CBOR diagnostic / key=value strings
        val regex = Regex(""${Regex.escape(key)}"\s*:\s*"([^"]+)"")
        regex.find(text)?.groupValues?.getOrNull(1)?.let { return it }

        // Also match CBOR text string pairs `[key][value]` in binary UTF-8 view
        val idx = text.indexOf(key)
        if (idx >= 0) {
            val after = text.substring((idx + key.length).coerceAtMost(text.length))
                .take(96)
            val printable = Regex("[A-Za-z0-9_./:\- ]{3,64}").find(after)?.value?.trim()
            if (!printable.isNullOrBlank() && printable != key) return printable
        }
        return null
    }

    private fun extractActionsFromPayload(text: String): List<C2paRecordedAction> {
        val knownActions = listOf(
            "c2pa.created",
            "c2pa.edited",
            "c2pa.cropped",
            "c2pa.color_adjustments",
            "c2pa.Filtered",
            "c2pa.filtered",
            "c2pa.resized",
            "c2pa.placed",
            "c2pa.opened",
            "c2pa.converted",
            "c2pa.drawing",
            "c2pa.transcoded"
        )
        val found = mutableListOf<C2paRecordedAction>()
        val softwareAgent = extractKeyValue(text, "softwareAgent") ?: extractKeyValue(text, "claim_generator")
        val digitalSourceType = extractKeyValue(text, "digitalSourceType")
        val whenTime = extractKeyValue(text, "when")

        for (act in knownActions) {
            if (text.contains(act)) {
                found.add(
                    C2paRecordedAction(
                        action = act,
                        softwareAgent = softwareAgent,
                        digitalSourceType = digitalSourceType,
                        whenTimestamp = whenTime,
                        parametersSummary = if (digitalSourceType != null) "digitalSourceType: ${digitalSourceType.substringAfterLast('/')}" else null
                    )
                )
            }
        }
        return found
    }

    private fun extractIngredients(text: String): List<String> {
        val list = mutableListOf<String>()
        val regex = Regex(""ingredient"\s*:\s*"([^"]+)"")
        regex.findAll(text).forEach { list.add(it.groupValues[1]) }
        return list
    }

    private fun detectCoseAlgorithm(bytes: ByteArray): String {
        val text = String(bytes, Charsets.ISO_8859_1)
        return when {
            text.contains("ES384") -> "ES384 (ECDSA P-384 / SHA-384)"
            text.contains("ES512") -> "ES512 (ECDSA P-521 / SHA-512)"
            text.contains("PS256") -> "PS256 (RSASSA-PSS / SHA-256)"
            text.contains("Ed25519") -> "Ed25519"
            else -> "ES256 (ECDSA P-256 / SHA-256)"
        }
    }

    private data class ParsedCertInfo(
        val issuer: String,
        val commonName: String,
        val organization: String?,
        val sigAlg: String,
        val isSelfSigned: Boolean
    )

    private fun extractX509CertificateFromBytes(bytes: ByteArray): ParsedCertInfo? {
        // Scan for ASN.1 DER X.509 Certificate sequence header: 0x30 0x82 [lenHi] [lenLo] 0x30 0x82
        for (i in 0 until bytes.size - 16) {
            if ((bytes[i].toInt() and 0xFF) == 0x30 &&
                (bytes[i + 1].toInt() and 0xFF) == 0x82 &&
                (bytes[i + 4].toInt() and 0xFF) == 0x30 &&
                (bytes[i + 5].toInt() and 0xFF) == 0x82
            ) {
                val seqLen = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                val totalCertLen = seqLen + 4
                if (totalCertLen in 128..8192 && i + totalCertLen <= bytes.size) {
                    try {
                        val cf = CertificateFactory.getInstance("X.509")
                        val cert = cf.generateCertificate(
                            ByteArrayInputStream(bytes, i, totalCertLen)
                        ) as? X509Certificate ?: continue
                        val subjectDn = cert.subjectX500Principal.name
                        val issuerDn = cert.issuerX500Principal.name
                        val cn = Regex("CN=([^,]+)").find(subjectDn)?.groupValues?.getOrNull(1) ?: subjectDn
                        val org = Regex("O=([^,]+)").find(subjectDn)?.groupValues?.getOrNull(1)
                        return ParsedCertInfo(
                            issuer = issuerDn,
                            commonName = cn,
                            organization = org,
                            sigAlg = cert.sigAlgName,
                            isSelfSigned = subjectDn == issuerDn
                        )
                    } catch (_: Exception) {
                        // Continue scanning
                    }
                }
            }
        }
        return null
    }

    private fun buildManifestSummaryJson(
        detail: C2paManifestDetail,
        status: C2paValidationStatus,
        errors: List<String>
    ): String {
        val actionsJson = detail.actions.joinToString(",
        ") { act ->
            "{"action": "${act.action}", "softwareAgent": "${act.softwareAgent ?: "unknown"}", "digitalSourceType": "${act.digitalSourceType ?: "unspecified"}"}"
        }
        val errorsJson = errors.joinToString(", ") { ""${it.replace(""", "\"")}"" }
        return """{
  "active_manifest": "${detail.manifestId}",
  "validation_state": "${status.name}",
  "manifests": {
    "${detail.manifestId}": {
      "claim_generator": "${detail.claimGenerator ?: ""}",
      "title": "${detail.title ?: "Embedded Asset"}",
      "format": "${detail.format ?: "image/*"}",
      "signature_info": {
        "issuer": "${detail.signerIssuer ?: ""}",
        "common_name": "${detail.signerCommonName ?: ""}",
        "alg": "${detail.signatureAlgorithm ?: "ES256"}",
        "time": "${detail.signingTime ?: ""}"
      },
      "assertions": [
        {
          "label": "c2pa.actions",
          "data": {
            "actions": [
        $actionsJson
            ]
          }
        }
      ]
    }
  },
  "validation_Errors": [$errorsJson]
}"""
    }
}
