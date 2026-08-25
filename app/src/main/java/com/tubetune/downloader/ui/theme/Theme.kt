package com.tubetune.downloader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.tubetune.downloader.data.Prefs

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A3DE8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF23005C),
    secondary = Color(0xFFE91E8C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E6),
    onSecondaryContainer = Color(0xFF3E0021),
    tertiary = Color(0xFF0091A7),
    background = Color(0xFFFDF8FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE8E0EB),
    onSurfaceVariant = Color(0xFF49454E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD3BBFF),
    onPrimary = Color(0xFF3A0090),
    primaryContainer = Color(0xFF5236B8),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFFFB0CB),
    onSecondary = Color(0xFF5B1138),
    secondaryContainer = Color(0xFF7B2950),
    onSecondaryContainer = Color(0xFFFFD9E6),
    tertiary = Color(0xFF4FD8EB),
    background = Color(0xFF12121A),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF1A1A24),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF2B2933),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

@Composable
fun TubeTuneTheme(
    darkThemeOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val pref = Prefs.theme(context)
    val darkTheme = darkThemeOverride ?: when (pref) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
