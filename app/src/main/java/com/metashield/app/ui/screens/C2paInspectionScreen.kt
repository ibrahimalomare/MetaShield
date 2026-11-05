package com.metashield.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.metashield.app.domain.model.C2paInspectionReport
import com.metashield.app.domain.model.C2paValidationStatus
import com.metashield.app.ui.components.*
import com.metashield.app.ui.theme.*

@Composable
fun C2paInspectionScreen(
    c2paReport: C2paInspectionReport?,
    onProceedToClean: () -> Unit,
    onBack: () -> Unit
) {
    var showRawJson by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "C2PA Content Credentials",
                subtitle = "ISO/IEC 19566-5 JUMBF & COSE_Sign1 Inspector",
                onBack = onBack
            )
        },
        containerColor = ObsidianBackground
    ) { padding ->
        if (c2paReport == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No C2PA inspection report available.", color = TextSecondary)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // 1. Validation Status Hero Card (Distinguishes all 5 required C2PA states)
            item {
                val borderCol = when (c2paReport.status) {
                    C2paValidationStatus.VALID -> EmeraldValid
                    C2paValidationStatus.INVALID -> CrimsonInvalid
                    C2paValidationStatus.UNTRUSTED_OR_UNVERIFIABLE -> AmberWarn
                    C2paValidationStatus.NO_CREDENTIALS_FOUND -> CardBorderSubtle
                    C2paValidationStatus.UNSUPPORTED_OR_UNREADABLE -> CrimsonInvalid
                }
                MetaShieldCard(borderColor = borderCol) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = c2paReport.status.displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        C2paStatusBadge(status = c2paReport.status)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = c2paReport.status.summaryExplanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    KeyValueRow("Manifest Exists", if (c2paReport.manifestExists) "Yes" else "No")
                    KeyValueRow("JUMBF Superbox Detected", if (c2paReport.jumbfDetected) "Yes (${c2paReport.jumbfBoxes.size} boxes)" else "No")
                    KeyValueRow("XMP Provenance Reference", if (c2paReport.xmpProvenanceDetected) "Present" else "None")
                    KeyValueRow("Embedded C2PA Size", "${c2paReport.totalC2paBytes} bytes")
                    KeyValueRow("Inspection Engine", c2paReport.sdkVersionInfo)
                }
            }

            // 2. Mandatory Epistemic Notice (Missing credentials != proof of human creation)
            item {
                MetaShieldCard(borderColor = ElectricBlue.copy(alpha = 0.5f), containerColor = ElectricBlueDim) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = c2paReport.epistemicNotice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            // 3. Validation Errors (if any)
            if (c2paReport.validationErrors.isNotEmpty()) {
                item {
                    MetaShieldCard(borderColor = CrimsonInvalid, containerColor = CrimsonDim) {
                        Text(
                            text = "Cryptographic / Structural Validation Findings",
                            style = MaterialTheme.typography.titleMedium,
                            color = CrimsonInvalid
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        c2paReport.validationErrors.forEach { err ->
                            Text("• $err", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
            }

            // 4. Active Manifest & Claim Generator Details
            c2paReport.primaryManifest?.let { manifest ->
                item {
                    MetaShieldCard {
                        Text(
                            text = "Active C2PA Manifest & Claim Generator",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricBlue
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        KeyValueRow("Manifest ID / URN", manifest.manifestId, monospaceValue = true)
                        manifest.title?.let { KeyValueRow("Asset Title", it) }
                        manifest.format?.let { KeyValueRow("Declared Format", it) }
                        KeyValueRow("Claim Generator", manifest.claimGenerator ?: "Not specified")
                        manifest.claimGeneratorVersion?.let { KeyValueRow("Generator Version", it) }
                        KeyValueRow(
                            "Software & App Agents",
                            manifest.softwareAgents.ifEmpty { listOf("None recorded") }.joinToString(", ")
                        )
                        KeyValueRow("Hash Algorithm", manifest.hashAlgorithm ?: "SHA-256")
                        KeyValueRow("Declared Assertions", manifest.assertions.joinToString(", "), monospaceValue = true)
                    }
                }

                // 5. Cryptographic Signature & Provenance Signer Details
                item {
                    MetaShieldCard {
                        Text(
                            text = "Cryptographic Signature & Certificate Provenance",
                            style = MaterialTheme.typography.titleMedium,
                            color = CyberPurple
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        KeyValueRow("Validation State", c2paReport.status.displayTitle)
                        KeyValueRow("Certificate Issuer", manifest.signerIssuer ?: "Unknown")
                        KeyValueRow("Signer Common Name", manifest.signerCommonName ?: "Unknown")
                        manifest.signerOrganization?.let { KeyValueRow("Organization", it) }
                        KeyValueRow("COSE Signature Algorithm", manifest.signatureAlgorithm ?: "ES256")
                        manifest.signingTime?.let { KeyValueRow("Signing Timestamp", it) }
                        if (manifest.ingredients.isNotEmpty()) {
                            KeyValueRow("Parent Ingredients", manifest.ingredients.joinToString(", "))
                        }
                    }
                }

                // 6. Recorded Editing Actions (c2pa.actions)
                item {
                    Text(
                        text = "RECORDED EDITING & CREATION ACTIONS (${manifest.actions.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                if (manifest.actions.isEmpty()) {
                    item {
                        MetaShieldCard {
                            Text("No explicit c2pa.actions assertions listed in manifest.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    items(manifest.actions) { act ->
                        MetaShieldCard {
                            Text(
                                text = act.action,
                                style = MaterialTheme.typography.titleMedium,
                                color = EmeraldValid
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            act.softwareAgent?.let { KeyValueRow("Software Agent", it) }
                            act.digitalSourceType?.let { KeyValueRow("Digital Source Type", it) }
                            act.whenTimestamp?.let { KeyValueRow("Timestamp", it) }
                            act.parametersSummary?.let { KeyValueRow("Parameters", it) }
                        }
                    }
                }
            }

            // 7. JUMBF Box Hierarchy
            if (c2paReport.jumbfBoxes.isNotEmpty()) {
                item {
                    Text(
                        text = "EMBEDDED JUMBF BOX HIERARCHY (${c2paReport.jumbfBoxes.size} BOXES)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                items(c2paReport.jumbfBoxes) { box ->
                    MetaShieldCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "[${box.boxType}] ${box.label ?: ""}",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                color = ElectricBlue
                            )
                            Text(
                                text = "${box.length} B @ 0x${box.offset.toString(16).uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = box.description, style = MaterialTheme.typography.bodyMedium)
                        box.uuidHex?.let {
                            Text(
                                text = "UUID: $it",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // 8. Raw Manifest Store JSON Toggle
            if (!c2paReport.rawManifestJson.isNullOrBlank()) {
                item {
                    OutlinedButton(
                        onClick = { showRawJson = !showRawJson },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showRawJson) "Hide Raw C2PA Manifest JSON" else "Show Raw C2PA Manifest JSON")
                    }
                }
                if (showRawJson) {
                    item {
                        MetaShieldCard {
                            Text(
                                text = c2paReport.rawManifestJson,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Action CTA to Clean C2PA
            if (c2paReport.hasC2paData) {
                item {
                    Button(
                        onClick = onProceedToClean,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPurple)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Configure C2PA & Metadata Removal")
                    }
                }
            }
        }
    }
}
