package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

object KsbColors {
    val HeaderStartLight = Color(0xFF0B2A74)
    val HeaderEndLight = Color(0xFF1D4ED8)
    val HeaderStartDark = Color(0xFF0A1230)
    val HeaderEndDark = Color(0xFF16306D)

    val BrandOrange = Color(0xFFFF5722)
    val BrandOrangeHover = Color(0xFFFF6A3D)
    val TabYellow = Color(0xFFFFC916)
    val TabYellowText = Color(0xFF0B2A74)

    val PrimaryLight = Color(0xFF2563EB)
    val PrimaryDark = Color(0xFF4F8BFF)
    val OkGreen = Color(0xFF16A34A)
    val DangerRed = Color(0xFFDC2626)
    val PendingAmber = Color(0xFFD97706)

    val PageLight = Color(0xFFF3F5FA)
    val CardLight = Color(0xFFFFFFFF)
    val TextLight = Color(0xFF0F172A)
    val MutedLight = Color(0xFF64748B)
    val LineLight = Color(0xFFE2E8F0)
    val SoftLight = Color(0xFFEEF3FF)
    val TableHeaderLight = Color(0xFF12307A)
    val UnsureRowLight = Color(0xFFFFF6C7)

    val PageDark = Color(0xFF0B1020)
    val CardDark = Color(0xFF141B2F)
    val TextDark = Color(0xFFE6EAF6)
    val MutedDark = Color(0xFF93A1BB)
    val LineDark = Color(0xFF263250)
    val SoftDark = Color(0xFF1A2644)
    val InputDark = Color(0xFF0E1427)
    val UnsureRowDark = Color(0xFF3A3315)

    val AuthBg = Color(0xFF08080A)
    val AuthCard = Color(0xFF151518)
    val AuthBorder = Color(0xFF232328)
    val AuthInputBg = Color(0xFF0E0E11)
    val AuthTabBg = Color(0xFF0D0D10)
    val AuthTabActive = Color(0xFF2B272D)
    val AuthMuted = Color(0xFFA1A1AA)

    val VideoGradStart = Color(0xFFE11D74)
    val VideoGradEnd = Color(0xFF7C3AED)
}

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
    Font(R.font.inter, FontWeight.ExtraBold)
)

private val KsbTypography = Typography(
    headlineLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
)

private val LightColorScheme = lightColorScheme(
    primary = KsbColors.PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = KsbColors.SoftLight,
    onPrimaryContainer = KsbColors.TextLight,
    secondary = KsbColors.BrandOrange,
    onSecondary = Color.White,
    tertiary = KsbColors.OkGreen,
    background = KsbColors.PageLight,
    onBackground = KsbColors.TextLight,
    surface = KsbColors.CardLight,
    onSurface = KsbColors.TextLight,
    surfaceVariant = KsbColors.SoftLight,
    onSurfaceVariant = KsbColors.MutedLight,
    outline = KsbColors.LineLight,
    error = KsbColors.DangerRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = KsbColors.PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = KsbColors.SoftDark,
    onPrimaryContainer = KsbColors.TextDark,
    secondary = KsbColors.BrandOrange,
    onSecondary = Color.White,
    tertiary = KsbColors.OkGreen,
    background = KsbColors.PageDark,
    onBackground = KsbColors.TextDark,
    surface = KsbColors.CardDark,
    onSurface = KsbColors.TextDark,
    surfaceVariant = KsbColors.SoftDark,
    onSurfaceVariant = KsbColors.MutedDark,
    outline = KsbColors.LineDark,
    error = KsbColors.DangerRed,
    onError = Color.White
)

@Composable
fun KsbTheme(
    uiThemeMode: String,
    content: @Composable () -> Unit
) {
    val isDark = when (uiThemeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        typography = KsbTypography,
        content = content
    )
}
