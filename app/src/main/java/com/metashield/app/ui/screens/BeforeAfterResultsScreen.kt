package com.metashield.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metashield.app.domain.model.CleaningAndVerificationResult
import com.metashield.app.domain.model.ImportedImageInfo
import com.metashield.app.domain.model.VerificationVerdict
import com.metashield.app.ui.components.*
import com.metashield.app.ui.theme.*

@Composable
fun BeforeAfterResultsScreen(
    result: CleaningAndVerificationResult?,
    onProceedToExport: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "Post-Cleaning Verification",
                subtitle = "Independent Re-Scan Before & After Comparison",
                onBack = onBack
            )
        },
        containerColor = ObsidianBackground
    ) { padding ->
        if (result == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No verification result available.", color = TextSecondary)
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
            // 1. Verdict Banner
            item {
                val borderCol = when (result.verdict) {
                    VerificationVerdict.VERIFIED_CLEAN -> EmeraldValid
                    VerificationVerdict.PARTIALLY_CLEANED -> AmberWarn
                    VerificationVerdict.UNCHANGED -> ElectricBlue
                    VerificationVerdict.FAILED -> CrimsonInvalid
                }
                MetaShieldCard(borderColor = borderCol) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = result.verdict.title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        VerdictBadge(verdict = result.verdict)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = result.verdict.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }

            // 2. Side-by-Side Before & After Metrics
            item {
                MetaShieldCard {
                    Text(
                        text = "Before vs. After Container Comparison",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricBlue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    KeyValueRow("Original File Size", result.beforeReport.imageInfo.formattedFileSize)
                    KeyValueRow("Cleaned Output Size", ImportedImageInfo.formatByteCount(result.outputFileSizeBytes))
                    KeyValueRow("Total Bytes Removed", ImportedImageInfo.formatByteCount(result.bytesRemoved))
                    KeyValueRow(
                        "Dimensions Before → After",
                        "${result.beforeReport.imageInfo.dimensionsLabel} → ${result.afterReport?.imageInfo?.dimensionsLabel ?: "N/A"}"
                    )
                    KeyValueRow(
                        "Dimensions & Bitstream Intact",
                        if (result.dimensionsPreserved && result.losslessBitstreamPreserved) "Yes (100% Lossless)" else "No"
                    )
                    KeyValueRow(
                        "C2PA Status Before → After",
                        "${result.beforeReport.c2paReport.status.badgeLabel} → ${result.afterReport?.c2paReport?.status?.badgeLabel ?: "N/A"}"
                    )
                }
            }

            // 3. Per-Category Verification Matrix
            item {
                Text(
                    text = "CATEGORY-BY-CATEGORY RE-SCAN VERIFICATION",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            items(result.categoryComparisons) { row ->
                MetaShieldCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.category.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${row.beforeFieldCount} tags (${row.beforeBytes} B) → ${row.afterFieldCount} tags (${row.afterBytes} B)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (row.afterFieldCount == 0) EmeraldValid else AmberWarn
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = row.statusNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.removedSuccessfully) TextSecondary else CrimsonInvalid
                    )
                }
            }

            // 4. Excised Container Segments Log
            if (result.removedSegmentsSummary.isNotEmpty()) {
                item {
                    MetaShieldCard {
                        Text(
                            text = "Excised Binary Segments & Chunks (${result.removedSegmentsSummary.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldValid
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        result.removedSegmentsSummary.forEach { seg ->
                            Text("• $seg", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // 5. Remaining or Preserved Notes
            if (result.remainingOrUnsupportedNotes.isNotEmpty()) {
                item {
                    MetaShieldCard(borderColor = AmberWarn.copy(alpha = 0.5f)) {
                        Text(
                            text = "Remaining / Preserved / Unsupported Notes",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberWarn
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        result.remainingOrUnsupportedNotes.forEach { note ->
                            Text("• $note", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // 6. Mandatory Scope Notice: External Manifests & SynthID Pixel Watermarks
            item {
                MetaShieldCard(borderColor = CyberPurple.copy(alpha = 0.55f), containerColor = CyberPurpleDim.copy(alpha = 0.45f)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = result.externalProvenanceAndWatermarkScopeNotice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            // 7. Proceed to Export & Share Button
            if (result.success && result.outputFilePath != null) {
                item {
                    Button(
                        onClick = onProceedToExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPurple)
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Proceed to Export & Share Cleaned Image")
                    }
                }
            }
        }
    }
}
