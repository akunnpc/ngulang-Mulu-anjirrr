package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = TextDark,
    primaryContainer = CyberCyanMuted,
    onPrimaryContainer = CyberCyan,
    
    secondary = EmeraldReady,
    onSecondary = TextDark,
    secondaryContainer = EmeraldReadyMuted,
    onSecondaryContainer = EmeraldReady,
    
    tertiary = WarningGold,
    onTertiary = TextDark,
    tertiaryContainer = WarningGoldMuted,
    onTertiaryContainer = WarningGold,
    
    background = ObsidianBg,
    onBackground = TextLight,
    
    surface = SlateCardBg,
    onSurface = TextLight,
    surfaceVariant = SlateCardBg,
    onSurfaceVariant = TextMuted,
    
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0x26EF4444),
    onErrorContainer = Color(0xFFF87171),
    
    outline = BorderSlate
)

// We want our custom cybertech immersive UI theme to shine, so we default to dark theme
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default for immersive cinematic feel
    dynamicColor: Boolean = false, // Disable dynamic colors so our cybertech colors are guaranteed
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme // Always enforce our gorgeous cyber scheme!

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
