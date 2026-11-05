package com.metashield.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MetaShieldDarkScheme = darkColorScheme(
    primary = CyberPurple,
    onPrimary = TextPrimary,
    primaryContainer = CyberPurpleDim,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricBlue,
    onSecondary = ObsidianBackground,
    secondaryContainer = ElectricBlueDim,
    onSecondaryContainer = ElectricBlue,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = TextSecondary,
    outline = CardBorderSubtle,
    error = CrimsonInvalid,
    onError = TextPrimary
)

@Composable
fun MetaShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MetaShieldDarkScheme,
        typography = MetaShieldTypography,
        content = content
    )
}
