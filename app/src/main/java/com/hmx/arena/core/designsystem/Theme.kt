package com.hmx.arena.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * HMX Arena is white-only. There is deliberately no dark scheme: the product
 * reference is a white Liquid Glass interface and the brief forbids a dark
 * theme. Only neutral ink, hairlines and frosted white surfaces are permitted.
 */
object HmxColor {
    val White = Color(0xFFFFFFFF)
    val Canvas = Color(0xFFFDFDFE)
    val CanvasTint = Color(0xFFF6F6F8)
    val Ink = Color(0xFF0B0B0C)
    val InkSecondary = Color(0xFF6E6E74)
    val InkTertiary = Color(0xFF9B9BA2)
    val Hairline = Color(0x1A0B0B0C)
    val HairlineStrong = Color(0x330B0B0C)

    // Liquid Glass surfaces are deliberately much more transparent than a
    // Material card. The visible backdrop, not a grey border, supplies depth.
    val Glass = Color(0x52FFFFFF)
    val GlassStrong = Color(0x8AFFFFFF)
    val GlassMuted = Color(0x24FFFFFF)
    val GlassChrome = Color(0x3DFFFFFF)
    val GlassControl = Color(0x66FFFFFF)
    val GlassEdge = Color(0x99FFFFFF)
    val GlassInk = Color(0xD90B0B0C)

    val Shadow = Color(0x1F000000)
    val ShadowSoft = Color(0x0F000000)
    val Scrim = Color(0x3D000000)
    val Danger = Color(0xFF8C2F2F)
    val Disabled = Color(0xFFBFBFC6)
}

object HmxDimens {
    val hairline = 1.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp

    val screenPadding = 20.dp
    val cardPadding = 18.dp
    val sectionGap = 24.dp
    val itemGap = 12.dp

    val buttonHeight = 54.dp
    val fieldHeight = 56.dp
    val iconButton = 44.dp
    val touchTarget = 48.dp
    val bottomBarHeight = 68.dp

    val radiusField = 16.dp
    val radiusCard = 22.dp
    val radiusLarge = 28.dp
    val radiusSheet = 30.dp
    val radiusPill = 100.dp
}

object HmxShapes {
    val card = RoundedCornerShape(HmxDimens.radiusCard)
    val large = RoundedCornerShape(HmxDimens.radiusLarge)
    val field = RoundedCornerShape(HmxDimens.radiusField)
    val sheet = RoundedCornerShape(
        topStart = HmxDimens.radiusSheet,
        topEnd = HmxDimens.radiusSheet,
        bottomStart = 8.dp,
        bottomEnd = 8.dp,
    )
    val pill = RoundedCornerShape(HmxDimens.radiusPill)
    val small = RoundedCornerShape(12.dp)
    val dialog = RoundedCornerShape(24.dp)
}

object HmxElevation {
    val card = 8.dp
    val floating = 16.dp
    val bar = 20.dp
}

private val HmxColors = lightColorScheme(
    primary = HmxColor.Ink,
    onPrimary = HmxColor.White,
    primaryContainer = HmxColor.GlassStrong,
    onPrimaryContainer = HmxColor.Ink,
    secondary = HmxColor.InkSecondary,
    onSecondary = HmxColor.White,
    background = HmxColor.White,
    onBackground = HmxColor.Ink,
    surface = HmxColor.White,
    onSurface = HmxColor.Ink,
    surfaceVariant = HmxColor.CanvasTint,
    onSurfaceVariant = HmxColor.InkSecondary,
    outline = HmxColor.HairlineStrong,
    outlineVariant = HmxColor.Hairline,
    error = HmxColor.Danger,
    onError = HmxColor.White,
    scrim = HmxColor.Scrim,
)

private val HmxTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.9).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val HmxShapesM3 = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun HMXArenaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HmxColors,
        typography = HmxTypography,
        shapes = HmxShapesM3,
        content = content,
    )
}
