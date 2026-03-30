package com.aether.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ════════════════════════════════════════════════════════════════════════════
// PALETTE — identique pour toutes les apps Aether
// ════════════════════════════════════════════════════════════════════════════

object AetherColors {
    // ── Primaire violet indigo ────────────────────────────────────────────
    val Primary       = Color(0xFF6B4EFF)
    val PrimaryDim    = Color(0xFF8B70FF)   // dark mode
    val OnPrimary     = Color(0xFFFFFFFF)
    val PrimaryContainer    = Color(0xFFEDE8FF)
    val OnPrimaryContainer  = Color(0xFF1A0063)

    // ── Secondaire (accents par app) ──────────────────────────────────────
    val ContactsTeal  = Color(0xFF00BCD4)   // AetherContacts
    val PhoneGreen    = Color(0xFF4CAF50)   // AetherPhone
    val NotesAmber    = Color(0xFFFFB300)   // AetherNotes
    val FilesBlue     = Color(0xFF1976D2)   // AetherFiles
    val SmsViolet     = Color(0xFF6B4EFF)   // AetherSMS

    // ── Fonds Light ───────────────────────────────────────────────────────
    val BackgroundLight = Color(0xFFF6F5FB)
    val SurfaceLight    = Color(0xFFFFFFFF)
    val SurfaceVarLight = Color(0xFFEFEDF7)
    val OutlineLight    = Color(0xFFCBC8D8)
    val OnBgLight       = Color(0xFF1B1A22)
    val OnSurfaceLight  = Color(0xFF1B1A22)
    val OnSurfaceVarLight = Color(0xFF47454F)

    // ── Fonds Dark ────────────────────────────────────────────────────────
    val BackgroundDark  = Color(0xFF0D0C12)
    val SurfaceDark     = Color(0xFF15141D)
    val SurfaceVarDark  = Color(0xFF1F1D2B)
    val OutlineDark     = Color(0xFF3A3848)
    val OnBgDark        = Color(0xFFE6E1F9)
    val OnSurfaceDark   = Color(0xFFE6E1F9)
    val OnSurfaceVarDark = Color(0xFFCAC5DF)

    // ── Sémantique ────────────────────────────────────────────────────────
    val Error    = Color(0xFFE53935)
    val OnError  = Color(0xFFFFFFFF)
    val Success  = Color(0xFF00C853)
    val Warning  = Color(0xFFFFA726)
}

// ════════════════════════════════════════════════════════════════════════════
// COLOR SCHEMES
// ════════════════════════════════════════════════════════════════════════════

private val LightScheme = lightColorScheme(
    primary              = AetherColors.Primary,
    onPrimary            = AetherColors.OnPrimary,
    primaryContainer     = AetherColors.PrimaryContainer,
    onPrimaryContainer   = AetherColors.OnPrimaryContainer,
    background           = AetherColors.BackgroundLight,
    onBackground         = AetherColors.OnBgLight,
    surface              = AetherColors.SurfaceLight,
    onSurface            = AetherColors.OnSurfaceLight,
    surfaceVariant       = AetherColors.SurfaceVarLight,
    onSurfaceVariant     = AetherColors.OnSurfaceVarLight,
    outline              = AetherColors.OutlineLight,
    error                = AetherColors.Error,
    onError              = AetherColors.OnError,
)

private val DarkScheme = darkColorScheme(
    primary              = AetherColors.PrimaryDim,
    onPrimary            = AetherColors.OnPrimary,
    primaryContainer     = Color(0xFF3D28CC),
    onPrimaryContainer   = Color(0xFFE8E0FF),
    background           = AetherColors.BackgroundDark,
    onBackground         = AetherColors.OnBgDark,
    surface              = AetherColors.SurfaceDark,
    onSurface            = AetherColors.OnSurfaceDark,
    surfaceVariant       = AetherColors.SurfaceVarDark,
    onSurfaceVariant     = AetherColors.OnSurfaceVarDark,
    outline              = AetherColors.OutlineDark,
    error                = AetherColors.Error,
    onError              = AetherColors.OnError,
)

// ════════════════════════════════════════════════════════════════════════════
// TYPOGRAPHY
// ════════════════════════════════════════════════════════════════════════════

val AetherTypography = Typography(
    titleLarge   = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = (-0.2).sp),
    titleMedium  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = (-0.1).sp),
    bodyLarge    = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium   = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall    = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall   = TextStyle(fontWeight = FontWeight.Normal,   fontSize = 11.sp, letterSpacing = 0.3.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,   fontSize = 24.sp, letterSpacing = (-0.3).sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold,     fontSize = 36.sp, letterSpacing = (-0.5).sp),
)

// ════════════════════════════════════════════════════════════════════════════
// THEME COMPOSABLE
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun AetherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content:   @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography  = AetherTypography,
        content     = content,
    )
}
