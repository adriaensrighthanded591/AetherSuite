package com.aethersms.ui.theme

// ── Alias vers AetherTheme partagé ────────────────────────────────────────
// AetherSMS fait partie d'AetherSuite et réutilise le design system commun.
// Pour les nouvelles fonctionnalités, utiliser directement com.aether.core.ui.theme.AetherTheme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary              = AetherPrimary,
    onPrimary            = AetherOnPrimary,
    primaryContainer     = AetherPrimaryContainer,
    onPrimaryContainer   = AetherOnPrimaryContainer,
    background           = AetherBackgroundLight,
    onBackground         = AetherOnBgLight,
    surface              = AetherSurfaceLight,
    onSurface            = AetherOnSurfaceLight,
    surfaceVariant       = AetherSurfaceVarLight,
    onSurfaceVariant     = AetherOnSurfaceVarLight,
    outline              = AetherOutlineLight,
    error                = AetherError,
    onError              = AetherOnError,
    secondaryContainer   = AetherPrimaryContainer,
    onSecondaryContainer = AetherOnPrimaryContainer,
)

private val DarkColors = darkColorScheme(
    primary              = AetherPrimaryDim,
    onPrimary            = AetherOnPrimary,
    primaryContainer     = Color(0xFF3D28CC),
    onPrimaryContainer   = Color(0xFFE8E0FF),
    background           = AetherBackgroundDark,
    onBackground         = AetherOnBgDark,
    surface              = AetherSurfaceDark,
    onSurface            = AetherOnSurfaceDark,
    surfaceVariant       = AetherSurfaceVarDark,
    onSurfaceVariant     = AetherOnSurfaceVarDark,
    outline              = AetherOutlineDark,
    error                = AetherError,
    onError              = AetherOnError,
    secondaryContainer   = Color(0xFF3D28CC),
    onSecondaryContainer = Color(0xFFE8E0FF),
)

@Composable
fun AetherSMSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AetherTypography,
        content     = content,
    )
}

/** Couleurs de bulles selon le thème courant */
@Composable
fun bubbleSentColor()   = if (isSystemInDarkTheme()) BubbleSentDark   else BubbleSentLight
@Composable
fun bubbleRecvColor()   = if (isSystemInDarkTheme()) BubbleReceivedDark else BubbleReceivedLight
@Composable
fun bubbleSentText()    = if (isSystemInDarkTheme()) BubbleSentTextDark  else BubbleSentTextLight
@Composable
fun bubbleRecvText()    = if (isSystemInDarkTheme()) BubbleReceivedTextDark else BubbleReceivedTextLight
