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
import com.metashield.app.domain.model.ImageFormat
import com.metashield.app.ui.components.DashboardActionCard
import com.metashield.app.ui.components.MetaShieldCard
import com.metashield.app.ui.components.MetaShieldTopBar
import com.metashield.app.ui.theme.*

@Composable
fun ImageSelectionScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLaunchPhotoPicker: () -> Unit,
    onLaunchDocumentPicker: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "Select Image",
                subtitle = "Native Android Photo Picker & SAF File Picker",
                onBack = onBack
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
            if (isLoading) {
                item {
                    MetaShieldCard(borderColor = ElectricBlue) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = ElectricBlue,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Copying image to read-only sandbox cache & scanning binary headers...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    MetaShieldCard(borderColor = CrimsonInvalid, containerColor = CrimsonDim) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            item {
                DashboardActionCard(
                    title = "Android Native Photo Picker",
                    subtitle = "Select directly from your photo library without granting broad storage permissions",
                    icon = Icons.Default.PhotoLibrary,
                    accentColor = CyberPurple,
                    enabled = !isLoading,
                    onClick = onLaunchPhotoPicker
                )
            }

            item {
                DashboardActionCard(
                    title = "Storage Access Framework File Picker",
                    subtitle = "Browse device folders, downloads, or SD card for raw JPG, JPEG, PNG, WebP, and TIFF files",
                    icon = Icons.Default.FolderOpen,
                    accentColor = ElectricBlue,
                    enabled = !isLoading,
                    onClick = onLaunchDocumentPicker
                )
            }

            item {
                Text(
                    text = "SUPPORTED IMAGE CONTAINERS & C2PA CARRIERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            items(
                listOf(ImageFormat.JPEG, ImageFormat.PNG, ImageFormat.WEBP, ImageFormat.TIFF)
            ) { fmt ->
                MetaShieldCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${fmt.displayName} (.${fmt.defaultExtension})",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricBlue
                        )
                        Text(
                            text = fmt.mimeType,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "C2PA Carrier: ${fmt.c2paCarrierDescription}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
