package com.xscan.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * XScan type — editorial pairing.
 *
 * Display: a high-contrast serif (ship "Fraunces" in res/font and swap the
 * family below). Body & UI: a quiet grotesque ("Inter"). Meta: monospace for
 * file sizes, page counts, timestamps — the "typesetter's margin notes".
 *
 * The scale is deliberately large with loose leading; density is the enemy
 * of the luxury brief.
 */
private val Display: FontFamily = FontFamily.Serif      // TODO: FontFamily(Font(R.font.fraunces_light), ...)
private val Body: FontFamily = FontFamily.SansSerif     // TODO: FontFamily(Font(R.font.inter_regular), ...)
private val Meta: FontFamily = FontFamily.Monospace     // TODO: FontFamily(Font(R.font.jetbrains_mono_regular))

@Immutable
data class CustomTypography(
    val displayLarge: TextStyle,   // Hero numerals, onboarding
    val titleScreen: TextStyle,    // Screen titles ("Library")
    val titleSection: TextStyle,   // Section headers, sheet titles
    val titleCard: TextStyle,      // Document names on cards
    val body: TextStyle,           // Primary copy
    val bodySecondary: TextStyle,  // Supporting copy, empty-state prose
    val label: TextStyle,          // Buttons, tabs
    val overline: TextStyle,       // Eyebrows ("3 PAGES · PDF")
    val meta: TextStyle,           // Mono metadata
)

val XScanTypography = CustomTypography(
    displayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Light,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleScreen = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleSection = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleCard = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
    ),
    body = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySecondary = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    label = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    overline = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp,   // Tracked-out eyebrow — the editorial tell
    ),
    meta = TextStyle(
        fontFamily = Meta,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
)

val LocalCustomTypography = staticCompositionLocalOf { XScanTypography }
