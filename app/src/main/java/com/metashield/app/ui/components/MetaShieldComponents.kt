package com.metashield.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metashield.app.domain.model.C2paValidationStatus
import com.metashield.app.domain.model.PrivacySensitivity
import com.metashield.app.domain.model.VerificationVerdict
import com.metashield.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaShieldTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricBlue
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ObsidianBackground
        )
    )
}

@Composable
fun MetaShieldCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CardBorderSubtle,
    containerColor: Color = CharcoalSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun C2paStatusBadge(status: C2paValidationStatus) {
    val (bg, fg, icon) = when (status) {
        C2paValidationStatus.VALID -> Triple(EmeraldDim, EmeraldValid, Icons.Default.VerifiedUser)
        C2paValidationStatus.INVALID -> Triple(CrimsonDim, CrimsonInvalid, Icons.Default.GppBad)
        C2paValidationStatus.UNTRUSTED_OR_UNVERIFIABLE -> Triple(AmberDim, AmberWarn, Icons.Default.WarningAmber)
        C2paValidationStatus.NO_CREDENTIALS_FOUND -> Triple(CharcoalElevated, TextSecondary, Icons.Default.Info)
        C2paValidationStatus.UNSUPPORTED_OR_UNREADABLE -> Triple(CrimsonDim, AmberWarn, Icons.Default.ErrorOutline)
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.badgeLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = fg
            )
        }
    }
}

@Composable
fun VerdictBadge(verdict: VerificationVerdict) {
    val (bg, fg) = when (verdict) {
        VerificationVerdict.VERIFIED_CLEAN -> EmeraldDim to EmeraldValid
        VerificationVerdict.PARTIALLY_CLEANED -> AmberDim to AmberWarn
        VerificationVerdict.UNCHANGED -> ElectricBlueDim to ElectricBlue
        VerificationVerdict.FAILED -> CrimsonDim to CrimsonInvalid
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.5f))
    ) {
        Text(
            text = verdict.name.replace('_', ' '),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
fun SensitivityPill(sensitivity: PrivacySensitivity) {
    val (bg, fg) = when (sensitivity) {
        PrivacySensitivity.CRITICAL -> CrimsonDim to CrimsonInvalid
        PrivacySensitivity.HIGH -> AmberDim to AmberWarn
        PrivacySensitivity.MODERATE -> ElectricBlueDim to ElectricBlue
        PrivacySensitivity.LOW -> CharcoalElevated to TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = sensitivity.name,
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    monospaceValue: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(0.4f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (monospaceValue) FontFamily.Monospace else FontFamily.SansSerif,
                fontWeight = FontWeight.Medium
            ),
            color = TextPrimary,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
fun DashboardActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    MetaShieldCard(
        borderColor = if (enabled) accentColor.copy(alpha = 0.45f) else CardBorderSubtle,
        containerColor = if (enabled) CharcoalSurface else CharcoalSurface.copy(alpha = 0.5f),
        onClick = if (enabled) onClick else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (enabled) accentColor else TextMuted,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) TextPrimary else TextMuted
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) TextSecondary else TextMuted
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (enabled) accentColor else TextMuted
            )
        }
    }
}
