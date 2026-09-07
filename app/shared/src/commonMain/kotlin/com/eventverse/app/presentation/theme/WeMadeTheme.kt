package com.eventverse.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * WeMade ERP Theme Tokens based on ui-ux-pro-max B2B SaaS Design System
 */
object WeMadeColors {
    val Primary = Color(0xFF2563EB)         // Trust Blue
    val PrimaryDark = Color(0xFF1D4ED8)
    val PrimaryContainer = Color(0xFFEFF6FF)
    val Secondary = Color(0xFF3B82F6)
    val Accent = Color(0xFFEA580C)          // Garment Safety Orange
    val AccentLight = Color(0xFFFFF7ED)
    val Background = Color(0xFFF8FAFC)      // Slate-50 Background
    val Surface = Color(0xFFFFFFFF)         // Pure Card Surface
    val OnSurface = Color(0xFF1E293B)       // Slate-800 Body Text
    val OnSurfaceMuted = Color(0xFF64748B)  // Slate-500 Secondary Text
    val Border = Color(0xFFE2E8F0)          // Slate-200 Border
    val BorderFocus = Color(0xFF2563EB)     // Accessible Focus Ring
    val Success = Color(0xFF16A34A)         // Emerald-600
    val SuccessBg = Color(0xFFF0FDF4)
    val Error = Color(0xFFDC2626)           // Red-600
    val ErrorBg = Color(0xFFFEF2F2)
}

val WeMadeLightColorScheme = lightColorScheme(
    primary = WeMadeColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = WeMadeColors.PrimaryDark,
    secondary = WeMadeColors.Secondary,
    background = WeMadeColors.Background,
    onBackground = WeMadeColors.OnSurface,
    surface = WeMadeColors.Surface,
    onSurface = WeMadeColors.OnSurface,
    error = WeMadeColors.Error,
    onError = Color.White
)

val WeMadeTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = WeMadeColors.OnSurface
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = WeMadeColors.OnSurface
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = WeMadeColors.OnSurface
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = WeMadeColors.OnSurface
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = WeMadeColors.OnSurface
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = WeMadeColors.OnSurfaceMuted
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

@Composable
fun WeMadeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WeMadeLightColorScheme,
        typography = WeMadeTypography,
        content = content
    )
}
