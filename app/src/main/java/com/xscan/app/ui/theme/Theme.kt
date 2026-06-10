package com.xscan.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Single entry point for design tokens. Usage at call sites:
 *
 *   val c = AppTheme.colors
 *   Text("Library", style = AppTheme.typography.titleScreen, color = c.inkPrimary)
 */
object AppTheme {
    val colors: CustomColors
        @Composable @ReadOnlyComposable get() = LocalCustomColors.current
    val typography: CustomTypography
        @Composable @ReadOnlyComposable get() = LocalCustomTypography.current
    val spacing: CustomSpacing
        @Composable @ReadOnlyComposable get() = LocalCustomSpacing.current
    val shapes: CustomShapes
        @Composable @ReadOnlyComposable get() = LocalCustomShapes.current
    val motion: CustomMotion
        @Composable @ReadOnlyComposable get() = LocalCustomMotion.current
}

/**
 * Material3 bridge: we don't design with M3 tokens, but system-level
 * components (ripples, text selection, modal scrims) still read from
 * MaterialTheme. Mapping our palette in keeps those consistent.
 */
private fun materialBridge(c: CustomColors): ColorScheme = darkColorScheme(
    primary = c.accent,
    onPrimary = c.onAccent,
    background = c.canvas,
    onBackground = c.inkPrimary,
    surface = c.surface,
    onSurface = c.inkPrimary,
    surfaceVariant = c.surfaceRaised,
    onSurfaceVariant = c.inkSecondary,
    outline = c.outline,
    error = c.danger,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = DarkAtelierColors
    CompositionLocalProvider(
        LocalCustomColors provides colors,
        LocalCustomTypography provides XScanTypography,
        LocalCustomSpacing provides CustomSpacing(),
        LocalCustomShapes provides CustomShapes(),
        LocalCustomMotion provides CustomMotion(),
    ) {
        MaterialTheme(
            colorScheme = materialBridge(colors),
            content = content,
        )
    }
}

/**
 * The signature micro-interaction: every pressable surface in the app
 * settles to 97% scale / 88% alpha with a fast ease-out. Apply alongside
 * a clickable that shares the same [MutableInteractionSource].
 */
fun Modifier.pressFeedback(interactionSource: MutableInteractionSource): Modifier =
    composed {
        val motion = LocalCustomMotion.current
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) motion.pressedScale else 1f,
            animationSpec = tween(motion.fast, easing = motion.easeOut),
            label = "pressScale",
        )
        val alpha by animateFloatAsState(
            targetValue = if (pressed) motion.pressedAlpha else 1f,
            animationSpec = tween(motion.fast, easing = motion.easeOut),
            label = "pressAlpha",
        )
        graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    }
