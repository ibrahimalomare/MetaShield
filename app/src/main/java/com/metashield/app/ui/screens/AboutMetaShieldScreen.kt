package com.metashield.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metashield.app.ui.components.KeyValueRow
import com.metashield.app.ui.components.MetaShieldCard
import com.metashield.app.ui.components.MetaShieldTopBar
import com.metashield.app.ui.theme.*

@Composable
fun AboutMetaShieldScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "About MetaShield",
                subtitle = "Architecture, Standards & Privacy Specification",
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
            item {
                MetaShieldCard(borderColor = CyberPurple) {
                    Text("MetaShield v1.0.0", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "MetaShield is an on-device digital image metadata and C2PA Content Credentials inspection and lossless sanitization application engineered with Kotlin, Jetpack Compose, and Clean Architecture + MVVM.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            item {
                MetaShieldCard {
                    Text("Technical Specifications", style = MaterialTheme.typography.titleMedium, color = ElectricBlue)
                    Spacer(modifier = Modifier.height(8.dp))
                    KeyValueRow("Minimum Android SDK", "Android 9.0 Pie (API 28)")
                    KeyValueRow("Target Android SDK", "Android 15 (API 35)")
                    KeyValueRow("C2PA Integration", "Official CAI org.contentauth.c2pa SDK Bridge + Native ISO/IEC 19566-5 JUMBF & COSE Inspector")
                    KeyValueRow("Supported Containers", "JPEG, PNG, WebP, TIFF")
                    KeyValueRow("Network Permissions", "None (Zero external uploads possible)")
                }
            }

            item {
                MetaShieldCard {
                    Text("Important Technical & Legal Boundaries", style = MaterialTheme.typography.titleMedium, color = AmberWarn)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "1. Missing C2PA Credentials ≠ Proof of Authenticity: The absence of C2PA Content Credentials never proves that an image is authentic, unedited, or human-created.

" +
                            "2. External Provenance & Pixel Watermarks: Remote provenance registries (cloud-stored manifests matched by perceptual hashing) and pixel-level steganographic watermarks (such as Google SynthID) are embedded outside container metadata headers and are not removed by file metadata cleaning.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
