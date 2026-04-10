package com.krunventures.meetingrecorder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Light colors
val SidebarBg = Color(0xFF2C3E50)
val Accent = Color(0xFF3498DB)
val AccentDark = Color(0xFF2980B9)
val Success = Color(0xFF27AE60)
val Danger = Color(0xFFE74C3C)
val Warning = Color(0xFFF39C12)
val TextDark = Color(0xFF2C3E50)
val TextLight = Color(0xFF7F8C8D)
val CardBg = Color(0xFFFFFFFF)
val Background = Color(0xFFF5F6FA)

// Dark colors
val DarkSidebarBg = Color(0xFF1A252F)
val DarkAccent = Color(0xFF5DADE2)
val DarkSuccess = Color(0xFF2ECC71)
val DarkDanger = Color(0xFFE74C3C)
val DarkWarning = Color(0xFFF5B041)
val DarkTextPrimary = Color(0xFFECF0F1)
val DarkTextSecondary = Color(0xFFBDC3C7)
val DarkCardBg = Color(0xFF2C3E50)
val DarkBackground = Color(0xFF1A1A2E)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = SidebarBg,
    onSecondary = Color.White,
    tertiary = Success,
    background = Background,
    surface = CardBg,
    surfaceVariant = Color(0xFFEEF0F3),
    error = Danger,
    onBackground = TextDark,
    onSurface = TextDark,
    onSurfaceVariant = TextLight,
    outline = Color(0xFFE0E0E0),
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = Color.White,
    secondary = DarkSidebarBg,
    onSecondary = Color.White,
    tertiary = DarkSuccess,
    background = DarkBackground,
    surface = DarkCardBg,
    surfaceVariant = Color(0xFF34495E),
    error = DarkDanger,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF4A6274),
)

@Composable
fun MeetingRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
