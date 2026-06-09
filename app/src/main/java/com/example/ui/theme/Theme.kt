package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WarMachineOrange,
    secondary = WarMachineBlue,
    background = WarMachineBg,
    surface = WarMachineCardBg,
    surfaceVariant = Color(0xFF1E293B),
    onBackground = Color.White,
    onSurface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    primaryContainer = WarMachineOrange.copy(alpha = 0.2f),
    secondaryContainer = WarMachineBlue.copy(alpha = 0.2f),
    outline = WarMachineGray,
    outlineVariant = WarMachineGray.copy(alpha = 0.5f)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme to match the dark web aesthetics of War Machine
    dynamicColor: Boolean = false, // Disable dynamic colors to stick strictly to the requested branding
    content: @Composable () -> Unit,
) {
    // We enforce the customized DarkColorScheme
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
