package com.metashield.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.metashield.app.domain.model.ImportedImageInfo
import com.metashield.app.domain.model.MetadataScanReport
import com.metashield.app.ui.components.*
import com.metashield.app.ui.theme.*
import java.io.File

@Composable
fun ImagePreviewScreen(
    scanReport: MetadataScanReport?,
    onOpenMetadataScanner: () -> Unit,
    onOpenC2paInspection: () -> Unit,
    onOpenMetadataCleaning: () -> Unit,
    onSelectDifferentImage: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "Image Preview & Container Info",
                subtitle = scanReport?.imageInfo?.fileName ?: "No image selected",
                onBack = onBack
            )
        },
        containerColor = ObsidianBackground
    ) { padding ->
        if (scanReport == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = onSelectDifferentImage) {
                    Text("Select an Image First")
                }
            }
            return@Scaffold
        }

        val info = scanReport.imageInfo
        val bitmap = remember(info.localCachePath) {
            runCatching {
                val file = File(info.localCachePath)
                if (file.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                } else null
            }.getOrNull()
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Visual Image Preview Card
            item {
                MetaShieldCard {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = info.fileName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "${info.format.displayName} Preview (${info.dimensionsLabel})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // File Container Properties Card
            item {
                MetaShieldCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "File & Container Attributes",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricBlue
                        )
                        C2paStatusBadge(status = scanReport.c2paReport.status)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    KeyValueRow("Filename", info.fileName)
                    KeyValueRow("Format / MIME", "${info.format.displayName} (${info.format.mimeType})")
                    KeyValueRow("File Size", info.formattedFileSize)
                    KeyValueRow("Dimensions", "${info.dimensionsLabel} (${String.format("%.2f", info.megapixels)} MP)")
                    KeyValueRow(
                        "Embedded Metadata",
                        "${scanReport.fields.size} fields (${ImportedImageInfo.formatByteCount(scanReport.totalMetadataBytes.toLong())})"
                    )
                    KeyValueRow("SHA-256 Digest", info.sha256Hex.take(32) + "…", monospaceValue = true)
                    KeyValueRow("Original File Protection", "Read-Only Source Preserved")
                }
            }

            // Navigation Actions
            item {
                DashboardActionCard(
                    title = "Open Metadata Scanner Report",
                    subtitle = "View all ${scanReport.fields.size} detected tags across C2PA, JUMBF, EXIF, XMP, IPTC & container segments",
                    icon = Icons.Default.ManageSearch,
                    accentColor = ElectricBlue,
                    onClick = onOpenMetadataScanner
                )
            }

            item {
                DashboardActionCard(
                    title = "Inspect C2PA Content Credentials",
                    subtitle = "${scanReport.c2paReport.status.displayTitle} · ${scanReport.c2paReport.jumbfBoxes.size} JUMBF boxes",
                    icon = Icons.Default.VerifiedUser,
                    accentColor = CyberPurple,
                    onClick = onOpenC2paInspection
                )
            }

            item {
                DashboardActionCard(
                    title = "Configure Format-Aware Cleaning",
                    subtitle = "Strip selected C2PA manifests and metadata into a separate lossless copy",
                    icon = Icons.Default.CleaningServices,
                    accentColor = EmeraldValid,
                    onClick = onOpenMetadataCleaning
                )
            }
        }
    }
}
