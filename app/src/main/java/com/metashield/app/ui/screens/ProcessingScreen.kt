package com.metashield.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metashield.app.ui.components.MetaShieldCard
import com.metashield.app.ui.components.MetaShieldTopBar
import com.metashield.app.ui.theme.*

@Composable
fun ProcessingScreen(
    stageIndex: Int,
    stageName: String,
    progressFraction: Float
) {
    val stages = listOf(
        1 to "Stage 1: Container Header & Authorization Validation",
        2 to "Stage 2: Format-Aware Lossless Binary Surgery",
        3 to "Stage 3: Separate Clean Output Container Generation",
        4 to "Stage 4: Independent Post-Cleaning Re-Scan & Verification"
    )

    Scaffold(
        topBar = {
            MetaShieldTopBar(
                title = "Sanitizing & Verifying Image",
                subtitle = "On-Device Format-Aware Engine"
            )
        },
        containerColor = ObsidianBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                progress = { progressFraction.coerceIn(0.05f, 1f) },
                modifier = Modifier.size(88.dp),
                color = CyberPurple,
                trackColor = CharcoalElevated,
                strokeWidth = 6.dp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stageName,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${(progressFraction * 100).toInt()}% Complete · Preserving Original Image",
                style = MaterialTheme.typography.labelMedium,
                color = ElectricBlue
            )
            Spacer(modifier = Modifier.height(24.dp))

            MetaShieldCard {
                stages.forEach { (idx, label) ->
                    val done = stageIndex > idx || progressFraction >= 1f
                    val active = stageIndex == idx && progressFraction < 1f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                done -> Icons.Default.CheckCircle
                                active -> Icons.Default.Sync
                                else -> Icons.Default.Pending
                            },
                            contentDescription = null,
                            tint = when {
                                done -> EmeraldValid
                                active -> ElectricBlue
                                else -> TextMuted
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (done || active) TextPrimary else TextMuted
                        )
                    }
                }
            }
        }
    }
}
