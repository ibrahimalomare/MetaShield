package com.metashield.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metashield.app.domain.model.CleaningAndVerificationResult
import com.metashield.app.domain.model.ImportedImageInfo
import com.metashield.app.ui.components.*
import com.metashield.app.ui.theme.*

@Composable
fun ExportSaveScreen(
    result: CleaningAndVerificationResult?,
    exportStatusMessage: String?,
    onSaveToCustomDocumentLocation: (suggestedFileName: String, mimeType: String) -> Unit,
    onSaveToGallery: () -> Unit,
    onShareFile: (filePath: String, mimeType: String) -> Unit,
    onReturnHome: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "Export & Share Cleaned Image",
                subtitle = "Original Source File Preserved Untouched",
                onBack = onBack
            )
        },
        containerColor = ObsidianBackground
    ) { padding ->
        if (result == null || result.outputFilePath == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No cleaned output file ready for export.", color = TextSecondary)
            }
            return@Scaffold
        }

        val outName = result.outputFileName ?: "metashield_clean.${result.beforeReport.imageInfo.format.defaultExtension}"
        val mime = result.beforeReport.imageInfo.format.mimeType

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (exportStatusMessage != null) {
                item {
                    MetaShieldCard(borderColor = EmeraldValid, containerColor = EmeraldDim) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldValid)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = exportStatusMessage, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        }
                    }
                }
            }

            // Output File & Processing Report Summary
            item {
                MetaShieldCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Processing Report Summary",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricBlue
                        )
                        VerdictBadge(verdict = result.verdict)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    KeyValueRow("Output Filename", outName)
                    KeyValueRow("Output File Size", ImportedImageInfo.formatByteCount(result.outputFileSizeBytes))
                    KeyValueRow("Bytes Stripped", ImportedImageInfo.formatByteCount(result.bytesRemoved))
                    KeyValueRow("Output Dimensions", result.afterReport?.imageInfo?.dimensionsLabel ?: "Preserved")
                    KeyValueRow("Output SHA-256", (result.afterReport?.imageInfo?.sha256Hex?.take(32) ?: "") + "…", monospaceValue = true)
                    KeyValueRow("Original Source File", "Preserved Untouched (${result.beforeReport.imageInfo.fileName})")
                    KeyValueRow("Processing Time", "${result.processingDurationMs} ms")
                }
            }

            // Provenance Removal Notice
            item {
                MetaShieldCard(borderColor = AmberWarn.copy(alpha = 0.55f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AmberWarn)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = result.provenanceRemovalWarning,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Export Actions
            item {
                DashboardActionCard(
                    title = "Save to Chosen Location (SAF)",
                    subtitle = "Choose any folder on internal storage or SD card via Android Storage Access Framework",
                    icon = Icons.Default.SaveAlt,
                    accentColor = EmeraldValid,
                    onClick = { onSaveToCustomDocumentLocation(outName, mime) }
                )
            }

            item {
                DashboardActionCard(
                    title = "Save to Pictures / MetaShield Album",
                    subtitle = "Export directly to Android MediaStore Gallery",
                    icon = Icons.Default.Collections,
                    accentColor = ElectricBlue,
                    onClick = onSaveToGallery
                )
            }

            item {
                DashboardActionCard(
                    title = "Share via Android Sharesheet",
                    subtitle = "Send the sanitized image to messaging, email, or cloud apps via secure FileProvider",
                    icon = Icons.Default.Share,
                    accentColor = CyberPurple,
                    onClick = { onShareFile(result.outputFilePath, mime) }
                )
            }

            item {
                OutlinedButton(
                    onClick = onReturnHome,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Return to Home Dashboard")
                }
            }
        }
    }
}
