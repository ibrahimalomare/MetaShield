package com.metashield.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metashield.app.domain.model.CleaningOptions
import com.metashield.app.ui.components.MetaShieldCard
import com.metashield.app.ui.components.MetaShieldTopBar
import com.metashield.app.ui.theme.*

@Composable
fun SettingsScreen(
    defaultOptions: CleaningOptions,
    onUpdateDefaults: (CleaningOptions) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "Settings & Default Policy",
                subtitle = "Configure Default Sanitization Presets",
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Text(
                    text = "DEFAULT METADATA CLEANING PRESETS",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricBlue
                )
            }

            item {
                SettingSwitchRow(
                    title = "Strip C2PA Content Credentials by Default",
                    subtitle = "Pre-select C2PA & JUMBF removal (still requires authorization confirmation per image)",
                    checked = defaultOptions.removeC2pa,
                    onCheckedChange = { onUpdateDefaults(defaultOptions.copy(removeC2pa = it)) }
                )
            }

            item {
                SettingSwitchRow(
                    title = "Strip EXIF Metadata by Default",
                    subtitle = "Remove IFD0, ExifSubIFD, and GPS IFD camera/location tags",
                    checked = defaultOptions.removeExif,
                    onCheckedChange = { onUpdateDefaults(defaultOptions.copy(removeExif = it)) }
                )
            }

            item {
                SettingSwitchRow(
                    title = "Strip XMP Metadata by Default",
                    subtitle = "Remove Adobe XMP XML packets and editing history",
                    checked = defaultOptions.removeXmp,
                    onCheckedChange = { onUpdateDefaults(defaultOptions.copy(removeXmp = it)) }
                )
            }

            item {
                SettingSwitchRow(
                    title = "Strip IPTC / Photoshop 8BIM by Default",
                    subtitle = "Remove press/agency datasets and 8BIM resources",
                    checked = defaultOptions.removeIptc,
                    onCheckedChange = { onUpdateDefaults(defaultOptions.copy(removeIptc = it)) }
                )
            }

            item {
                SettingSwitchRow(
                    title = "Preserve ICC Color Profiles",
                    subtitle = "Keep APP2 ICC_PROFILE / PNG iCCP / WebP ICCP chunks for accurate display colors",
                    checked = defaultOptions.preserveIccColorProfile,
                    onCheckedChange = { onUpdateDefaults(defaultOptions.copy(preserveIccColorProfile = it)) }
                )
            }

            item {
                SettingSwitchRow(
                    title = "Zero-Fill TIFF Metadata Slack Space",
                    subtitle = "Overwrite excised TIFF tag payload offsets with 0x00 bytes",
                    checked = defaultOptions.zeroFillSlackSpace,
                    onCheckedChange = { onUpdateDefaults(defaultOptions.copy(zeroFillSlackSpace = it)) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onClearHistory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Recent Operations History")
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
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
                Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
