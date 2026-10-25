package com.tubetune.downloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.tubetune.downloader.data.Prefs

/* 品牌色：紫 → 粉的「極光」漸層，深色為主 */
val BrandViolet = Color(0xFF8B7CFF)
val BrandPink = Color(0xFFFF5C8A)
val BrandCyan = Color(0xFF3DD6E0)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4A6FF),
    onPrimary = Color(0xFF1B1140),
    primaryContainer = Color(0xFF3B2A78),
    onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = Color(0xFFFF9EB8),
    onSecondary = Color(0xFF3A0E20),
    secondaryContainer = Color(0xFF6E2340),
    onSecondaryContainer = Color(0xFFFFDCE6),
    tertiary = Color(0xFF7FE3EC),
    onTertiary = Color(0xFF00363B),
    background = Color(0xFF0A0A0F),
    onBackground = Color(0xFFECEBF2),
    surface = Color(0xFF12121A),
    onSurface = Color(0xFFECEBF2),
    surfaceVariant = Color(0xFF1B1B25),
    onSurfaceVariant = Color(0xFFA3A2B4),
    surfaceContainer = Color(0xFF15151E),
    surfaceContainerHigh = Color(0xFF1B1B25),
    surfaceContainerHighest = Color(0xFF232330),
    outline = Color(0xFF3A3A4A),
    outlineVariant = Color(0xFF26262F),
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF43090B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6741D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF22065C),
    secondary = Color(0xFFD6336C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDEE9),
    onSecondaryContainer = Color(0xFF3D0A1E),
    tertiary = Color(0xFF0E8E99),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F7FC),
    onBackground = Color(0xFF17161D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17161D),
    surfaceVariant = Color(0xFFF0EEF7),
    onSurfaceVariant = Color(0xFF5A5866),
    surfaceContainer = Color(0xFFF3F1FA),
    surfaceContainerHigh = Color(0xFFEDEBF6),
    surfaceContainerHighest = Color(0xFFE6E3F1),
    outline = Color(0xFFC9C5D8),
    outlineVariant = Color(0xFFE2DFEC),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val BaseTypography = Typography()

private val AppTypography = BaseTypography.copy(
    headlineSmall = BaseTypography.headlineSmall.copy(
        fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(
        fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp
    ),
    titleLarge = BaseTypography.titleLarge.copy(
        fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp
    ),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = BaseTypography.bodyMedium.copy(letterSpacing = 0.sp)
)

/** 主色漸層：主按鈕、正在播放的封面、資料夾卡片都用它 */
object Gradients {
    val accent: Brush
        @Composable get() = Brush.linearGradient(listOf(BrandViolet, BrandPink))

    /** 半透明版（做背景、佔位圖用） */
    @Composable
    fun soft(alpha: Float): Brush = Brush.linearGradient(
        listOf(BrandViolet.copy(alpha = alpha), BrandPink.copy(alpha = alpha))
    )

    @Composable
    fun hero(): Brush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
            MaterialTheme.colorScheme.background
        )
    )
}

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
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
