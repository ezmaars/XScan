package com.xscan.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * XScan palette — "Dark Atelier".
 *
 * A deep charcoal neutral stack with exactly one accent: Champagne (#C9A86A),
 * a warm brass tone borrowed from print-shop foil stamping. It is reserved for
 * primary actions, focus, and selection. Everything else is rendered in ink
 * and paper tones so scanned documents remain the brightest object on screen.
 */
@Immutable
data class CustomColors(
    // Canvas
    val canvas: Color,          // App background
    val surface: Color,         // Cards, sheets
    val surfaceRaised: Color,   // Elevated cards, dialogs
    val surfacePressed: Color,  // Pressed-state fill

    // Ink (foreground)
    val inkPrimary: Color,      // Headlines, primary copy
    val inkSecondary: Color,    // Supporting copy
    val inkTertiary: Color,     // Captions, placeholders
    val inkInverse: Color,      // Text on accent fills

    // The one accent
    val accent: Color,
    val accentMuted: Color,     // Accent at rest (chips, traces)
    val onAccent: Color,

    // Lines
    val divider: Color,         // Hairline rules
    val outline: Color,         // Input strokes, crop handles' ring

    // Functional (status, not decoration)
    val danger: Color,
    val scrim: Color,
)

val DarkAtelierColors = CustomColors(
    canvas = Color(0xFF0D0D0F),
    surface = Color(0xFF151517),
    surfaceRaised = Color(0xFF1C1C1F),
    surfacePressed = Color(0xFF232327),

    inkPrimary = Color(0xFFF4F1EA),   // Warm paper-white
    inkSecondary = Color(0xFFA8A49B),
    inkTertiary = Color(0xFF6C6962),
    inkInverse = Color(0xFF121212),

    accent = Color(0xFFC9A86A),       // Champagne brass
    accentMuted = Color(0x33C9A86A),  // 20% — selection traces, focus halos
    onAccent = Color(0xFF14110A),

    divider = Color(0xFF26262A),
    outline = Color(0xFF3A3A40),

    danger = Color(0xFFD96A5F),
    scrim = Color(0xCC0A0A0C),
)

val LocalCustomColors = staticCompositionLocalOf { DarkAtelierColors }
