package com.metashield.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metashield.app.domain.model.ImportedImageInfo
import com.metashield.app.domain.model.MetadataScanReport
import com.metashield.app.domain.model.RecentOperation
import com.metashield.app.ui.components.*
import com.metashield.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    currentReport: MetadataScanReport?,
    recentOperations: List<RecentOperation>,
    onSelectImageClick: () -> Unit,
    onScanImageClick: () -> Unit,
    onCleanMetadataClick: () -> Unit,
    onInspectC2paClick: () -> Unit,
    onPreviewImageClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "MetaShield",
                subtitle = "100% On-Device C2PA & Metadata Security",
                actions = {
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Default.Info, contentDescription = "About", tint = TextSecondary)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                }
            )
        },
        containerColor = ObsidianBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Security & Privacy Banner
            item {
                MetaShieldCard(
                    borderColor = CyberPurple.copy(alpha = 0.5f),
                    containerColor = CharcoalElevated
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = EmeraldValid,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Air-Gapped Local Sandbox Active",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "No INTERNET permission requested in AndroidManifest. Original files are never modified.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Active Loaded Image Status (if an image is currently imported)
            if (currentReport != null) {
                item {
                    MetaShieldCard(
                        borderColor = ElectricBlue.copy(alpha = 0.6f),
                        onClick = onPreviewImageClick
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ACTIVE IMAGE LOADED",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricBlue
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentReport.imageInfo.fileName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${currentReport.imageInfo.format.displayName} · ${currentReport.imageInfo.dimensionsLabel} · ${currentReport.imageInfo.formattedFileSize}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            C2paStatusBadge(status = currentReport.c2paReport.status)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = onPreviewImageClick,
                                label = { Text("Preview & Hashes") },
                                leadingIcon = { Icon(Icons.Default.Image, null, Modifier.size(16.dp)) }
                            )
                            AssistChip(
                                onClick = onInspectC2paClick,
                                label = { Text("C2PA Details") },
                                leadingIcon = { Icon(Icons.Default.Policy, null, Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
            }

            // Core Prominent Dashboard Actions
            item {
                Text(
                    text = "CORE WORKFLOW",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            item {
                DashboardActionCard(
                    title = "Select Image",
                    subtitle = "Import JPG, JPEG, PNG, WebP, or TIFF via Photo Picker or Storage Access Framework",
                    icon = Icons.Default.AddPhotoAlternate,
                    accentColor = CyberPurple,
                    onClick = onSelectImageClick
                )
            }

            item {
                DashboardActionCard(
                    title = "Scan Image",
                    subtitle = if (currentReport != null) {
                        "Inspect ${currentReport.fields.size} detected metadata fields & C2PA JUMBF boxes"
                    } else {
                        "Automatically detect C2PA Content Credentials, JUMBF, EXIF, XMP & IPTC"
                    },
                    icon = Icons.Default.Radar,
                    accentColor = ElectricBlue,
                    onClick = if (currentReport != null) onScanImageClick else onSelectImageClick
                )
            }

            item {
                DashboardActionCard(
                    title = "Clean Metadata",
                    subtitle = if (currentReport != null) {
                        "Format-aware lossless removal of C2PA, EXIF, XMP & IPTC (${ImportedImageInfo.formatByteCount(currentReport.totalMetadataBytes.toLong())} detected)"
                    } else {
                        "Surgically strip selected C2PA manifests and metadata into a separate verified copy"
                    },
                    icon = Icons.Default.CleaningServices,
                    accentColor = EmeraldValid,
                    onClick = if (currentReport != null) onCleanMetadataClick else onSelectImageClick
                )
            }

            // Recent Operations Section
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT OPERATIONS (${recentOperations.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            if (recentOperations.isEmpty()) {
                item {
                    MetaShieldCard {
                        Text(
                            text = "No recent cleaning operations recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select an image above to inspect its embedded C2PA Content Credentials and metadata headers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            } else {
                items(recentOperations, key = { it.id }) { op ->
                    RecentOperationCard(op)
                }
            }
        }
    }
}

@Composable
private fun RecentOperationCard(op: RecentOperation) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    MetaShieldCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = op.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${op.format.displayName} · Removed ${ImportedImageInfo.formatByteCount(op.bytesRemoved)} · ${dateFormat.format(Date(op.timestampEpochMs))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            VerdictBadge(verdict = op.verdict)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tags = buildList {
                if (op.c2paRemoved) add("C2PA Stripped")
                if (op.exifRemoved) add("EXIF Stripped")
                if (op.xmpRemoved) add("XMP Stripped")
                if (op.iptcRemoved) add("IPTC Stripped")
            }
            tags.forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CharcoalElevated)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = tag, style = MaterialTheme.typography.labelMedium, color = ElectricBlue)
                }
            }
        }
    }
}
