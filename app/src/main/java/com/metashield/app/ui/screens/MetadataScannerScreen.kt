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
import androidx.compose.ui.unit.dp
import com.metashield.app.domain.model.ImportedImageInfo
import com.metashield.app.domain.model.MetadataCategory
import com.metashield.app.domain.model.MetadataScanReport
import com.metashield.app.ui.components.*
import com.metashield.app.ui.theme.*

@Composable
fun MetadataScannerScreen(
    scanReport: MetadataScanReport?,
    onOpenC2paInspection: () -> Unit,
    onOpenCleaning: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<MetadataCategory?>(null) }

    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "Metadata Scanner",
                subtitle = scanReport?.let { "${it.fields.size} tags · ${ImportedImageInfo.formatByteCount(it.totalMetadataBytes.toLong())} metadata" },
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRescan) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-scan", tint = ElectricBlue)
                    }
                }
            )
        },
        containerColor = ObsidianBackground
    ) { padding ->
        if (scanReport == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No scan report available.", color = TextSecondary)
            }
            return@Scaffold
        }

        val filteredFields = remember(scanReport, selectedCategory) {
            if (selectedCategory == null) scanReport.fields
            else scanReport.fieldsFor(selectedCategory!!)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (scanReport.isCorrupted) {
                item {
                    MetaShieldCard(borderColor = CrimsonInvalid, containerColor = CrimsonDim) {
                        Text(
                            text = "Container Integrity Error",
                            style = MaterialTheme.typography.titleMedium,
                            color = CrimsonInvalid
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = scanReport.corruptionReason ?: "Corrupted or unsupported image container.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Privacy Risk Highlights
            if (scanReport.hasGpsCoordinates || scanReport.hasDeviceIdentifiers || scanReport.c2paReport.hasC2paData) {
                item {
                    MetaShieldCard(borderColor = AmberWarn.copy(alpha = 0.6f)) {
                        Text(
                            text = "Privacy & Provenance Alerts Detected",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberWarn
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (scanReport.c2paReport.hasC2paData) {
                            Text("• Embedded C2PA Content Credentials / JUMBF superboxes (${scanReport.c2paReport.totalC2paBytes} B)", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (scanReport.hasGpsCoordinates) {
                            Text("• Precise GPS Geolocation coordinates embedded in EXIF/XMP", style = MaterialTheme.typography.bodyMedium, color = CrimsonInvalid)
                        }
                        if (scanReport.hasDeviceIdentifiers) {
                            Text("• Camera Make, Model, Owner, or Hardware Serial Number present", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // 7-Layer Detection Summary Grid
            item {
                Text(
                    text = "7-LAYER AUTOMATIC DETECTION MATRIX (TAP TO FILTER)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            items(MetadataCategory.entries) { category ->
                val count = scanReport.categoryFieldCounts[category] ?: 0
                val bytes = scanReport.categoryByteCounts[category] ?: 0
                val isSelected = selectedCategory == category
                MetaShieldCard(
                    borderColor = when {
                        isSelected -> ElectricBlue
                        count > 0 || bytes > 0 -> CyberPurple.copy(alpha = 0.45f)
                        else -> CardBorderSubtle
                    },
                    onClick = {
                        selectedCategory = if (selectedCategory == category) null else category
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (count > 0 || bytes > 0) TextPrimary else TextSecondary
                            )
                            Text(
                                text = category.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (count > 0) "$count tags" else "0 tags",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (count > 0) ElectricBlue else TextMuted
                            )
                            Text(
                                text = "$bytes B",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Quick Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenC2paInspection,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Inspect C2PA")
                    }
                    Button(
                        onClick = onOpenCleaning,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPurple)
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clean Metadata")
                    }
                }
            }

            // Detailed Tag Inspector List
            item {
                Text(
                    text = if (selectedCategory != null) {
                        "INSPECTING ${selectedCategory!!.title.uppercase()} (${filteredFields.size})"
                    } else {
                        "ALL DETECTED METADATA FIELDS (${filteredFields.size})"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricBlue
                )
            }

            if (filteredFields.isEmpty()) {
                item {
                    MetaShieldCard {
                        Text(
                            text = "No embedded metadata fields found in this category.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(filteredFields) { item ->
                    MetaShieldCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.key + (item.tagIdHex?.let { " ($it)" } ?: ""),
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            SensitivityPill(sensitivity = item.sensitivity)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.bodyLarge,
                            color = ElectricBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val locText = buildString {
                            append(item.category.shortCode)
                            if (item.containerLocation.isNotBlank()) append(" · ${item.containerLocation}")
                            item.byteOffset?.let { append(" · Offset 0x${it.toString(16).uppercase()}") }
                            item.byteLength?.let { append(" ($it B)") }
                        }
                        Text(
                            text = locText,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}
