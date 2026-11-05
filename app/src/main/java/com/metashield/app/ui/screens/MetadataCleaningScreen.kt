package com.metashield.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metashield.app.domain.model.CleaningOptions
import com.metashield.app.domain.model.MetadataCategory
import com.metashield.app.domain.model.MetadataScanReport
import com.metashield.app.ui.components.MetaShieldCard
import com.metashield.app.ui.components.MetaShieldTopBar
import com.metashield.app.ui.theme.*

@Composable
fun MetadataCleaningScreen(
    scanReport: MetadataScanReport?,
    options: CleaningOptions,
    onUpdateOptions: ((CleaningOptions) -> CleaningOptions) -> Unit,
    onStartCleaning: () -> Unit,
    onBack: () -> Unit
) {
    var showProvenanceAuthDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "Metadata Cleaning Engine",
                subtitle = scanReport?.let { "Format-Aware ${it.imageInfo.format.displayName} Lossless Sanitizer" },
                onBack = onBack
            )
        },
        containerColor = ObsidianBackground
    ) { padding ->
        if (scanReport == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Select an image first.", color = TextSecondary)
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
            // Format-Aware Lossless Strategy Card
            item {
                MetaShieldCard(borderColor = ElectricBlue.copy(alpha = 0.5f)) {
                    Text(
                        text = "Format-Aware Binary Surgery (${scanReport.imageInfo.format.displayName})",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "MetaShield parses exact container markers/chunks and excises only selected metadata structures without re-compressing image pixels. Your original file is never modified.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Section 1: Dedicated C2PA & JUMBF Provenance Cleaning Option
            item {
                Text(
                    text = "1. CONTENT CREDENTIALS & PROVENANCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberPurple
                )
            }

            item {
                MetaShieldCard(borderColor = if (options.removeC2pa) CyberPurple else CardBorderSubtle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remove C2PA Content Credentials & JUMBF",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Strips embedded C2PA manifests, COSE signatures, and ISO 19566-5 JUMBF superboxes (${scanReport.c2paReport.totalC2paBytes} B detected)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = options.removeC2pa,
                            onCheckedChange = { checked ->
                                onUpdateOptions { it.copy(removeC2pa = checked) }
                            }
                        )
                    }

                    if (options.removeC2pa) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = options.userAuthorizedProvenanceRemoval,
                                onCheckedChange = { checked ->
                                    onUpdateOptions { it.copy(userAuthorizedProvenanceRemoval = checked) }
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I own or am authorized to process this image and understand that removing Content Credentials deletes embedded provenance history from the exported copy.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Section 2: Ordinary Embedded Metadata Cleaning Options
            item {
                Text(
                    text = "2. ORDINARY EMBEDDED METADATA",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricBlue
                )
            }

            item {
                CleaningToggleCard(
                    title = "Remove EXIF Metadata",
                    subtitle = "Strips camera model, serial numbers, timestamps, thumbnail IFDs & GPS coordinates (${scanReport.categoryFieldCounts[MetadataCategory.EXIF] ?: 0} tags, ${scanReport.categoryByteCounts[MetadataCategory.EXIF] ?: 0} B)",
                    checked = options.removeExif,
                    onCheckedChange = { v -> onUpdateOptions { it.copy(removeExif = v) } }
                )
            }

            item {
                CleaningToggleCard(
                    title = "Remove XMP Metadata",
                    subtitle = "Strips Adobe XMP packets, editing history, CreatorTool & dcterms:provenance (${scanReport.categoryFieldCounts[MetadataCategory.XMP] ?: 0} tags, ${scanReport.categoryByteCounts[MetadataCategory.XMP] ?: 0} B)",
                    checked = options.removeXmp,
                    onCheckedChange = { v -> onUpdateOptions { it.copy(removeXmp = v) } }
                )
            }

            item {
                CleaningToggleCard(
                    title = "Remove IPTC / Photoshop 8BIM Metadata",
                    subtitle = "Strips press credits, author by-lines, copyright notices & 8BIM resources (${scanReport.categoryFieldCounts[MetadataCategory.IPTC] ?: 0} tags, ${scanReport.categoryByteCounts[MetadataCategory.IPTC] ?: 0} B)",
                    checked = options.removeIptc,
                    onCheckedChange = { v -> onUpdateOptions { it.copy(removeIptc = v) } }
                )
            }

            item {
                CleaningToggleCard(
                    title = "Remove Comments & Ancillary Text",
                    subtitle = "Strips JPEG COM comments and PNG tEXt/zTXt/iTXt/tIME chunks (${scanReport.categoryFieldCounts[MetadataCategory.OTHER] ?: 0} items)",
                    checked = options.removeOtherMetadata,
                    onCheckedChange = { v -> onUpdateOptions { it.copy(removeOtherMetadata = v) } }
                )
            }

            item {
                CleaningToggleCard(
                    title = "Preserve ICC Color Profile",
                    subtitle = "Keeps APP2 ICC_PROFILE / iCCP / ICCP color space profiles intact so image colors do not shift",
                    checked = options.preserveIccColorProfile,
                    onCheckedChange = { v -> onUpdateOptions { it.copy(preserveIccColorProfile = v) } }
                )
            }

            // Execute CTA
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        if (options.removeC2pa && scanReport.c2paReport.hasC2paData && !options.userAuthorizedProvenanceRemoval) {
                            showProvenanceAuthDialog = true
                        } else {
                            onStartCleaning()
                        }
                    },
                    enabled = options.hasAnySelection && !scanReport.isCorrupted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldValid, contentColor = ObsidianBackground)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate Cleaned Copy & Verify", style = MaterialTheme.typography.titleMedium, color = ObsidianBackground)
                }
            }
        }
    }

    if (showProvenanceAuthDialog) {
        AlertDialog(
            onDismissRequest = { showProvenanceAuthDialog = false },
            title = { Text("Confirm Provenance Removal") },
            text = {
                Text(
                    "This image contains C2PA Content Credentials. Removing them strips cryptographic provenance information from the newly created copy and may prevent future verification of the original image history.

Your original image file will remain untouched. Confirm that you are authorized to sanitize this image?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showProvenanceAuthDialog = false
                        onUpdateOptions { it.copy(userAuthorizedProvenanceRemoval = true) }
                        onStartCleaning()
                    }
                ) {
                    Text("Authorize & Clean")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProvenanceAuthDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CleaningToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    MetaShieldCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
