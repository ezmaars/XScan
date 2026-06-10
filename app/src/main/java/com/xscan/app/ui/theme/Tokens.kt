package com.xscan.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spatial system — 4dp base grid, but the named tokens skew generous.
 * Screens breathe: 24dp gutters, 32–48dp between sections.
 */
@Immutable
data class CustomSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,    // Default screen gutter
    val xl: Dp = 32.dp,    // Section spacing
    val xxl: Dp = 48.dp,   // Hero / empty-state spacing
    val touchTarget: Dp = 48.dp,
)

@Immutable
data class CustomShapes(
    val control: Dp = 14.dp,   // Buttons, inputs
    val card: Dp = 20.dp,      // Document cards
    val sheet: Dp = 28.dp,     // Bottom sheets, dialogs
    val hairline: Dp = 1.dp,   // Divider thickness — never thicker
)

/**
 * Motion — restrained and purposeful. Two easings, three durations.
 * Press feedback is scale 0.97 + alpha 0.88; nothing bounces.
 */
@Immutable
data class CustomMotion(
    val fast: Int = 140,        // Press feedback, icon swaps
    val standard: Int = 240,    // State changes, content fades
    val emphasized: Int = 420,  // Screen transitions, sheet entrance
    val easeOut: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val easeInOut: Easing = CubicBezierEasing(0.45f, 0f, 0.15f, 1f),
    val pressedScale: Float = 0.97f,
    val pressedAlpha: Float = 0.88f,
)

val LocalCustomSpacing = staticCompositionLocalOf { CustomSpacing() }
val LocalCustomShapes = staticCompositionLocalOf { CustomShapes() }
val LocalCustomMotion = staticCompositionLocalOf { CustomMotion() }
