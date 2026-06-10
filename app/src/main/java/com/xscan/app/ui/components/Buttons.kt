package com.xscan.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xscan.app.ui.theme.AppTheme
import com.xscan.app.ui.theme.pressFeedback

/**
 * The one loud element on any screen. Champagne fill, dark ink,
 * full-width by default. Use once per screen — twice is a design bug.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppTheme.spacing.touchTarget + AppTheme.spacing.xs)   // 56dp
            .pressFeedback(interaction)
            .clip(RoundedCornerShape(AppTheme.shapes.control))
            .background(AppTheme.colors.accent)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = AppTheme.colors.onAccent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(AppTheme.spacing.xs))
        }
        Text(text = text, style = AppTheme.typography.label, color = AppTheme.colors.onAccent)
    }
}

/** Quiet secondary action: hairline-stroked, surface-toned. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(AppTheme.shapes.control)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(AppTheme.spacing.touchTarget + AppTheme.spacing.xs)
            .pressFeedback(interaction)
            .clip(shape)
            .border(AppTheme.shapes.hairline, AppTheme.colors.outline, shape)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AppTheme.typography.label, color = AppTheme.colors.inkPrimary)
    }
}

/** Circular 48dp icon action — toolbar back, flash, overflow. */
@Composable
fun IconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,   // e.g. flash ON — the icon takes the accent
    onScrim: Boolean = false,  // For use over the camera feed
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(AppTheme.spacing.touchTarget)
            .pressFeedback(interaction)
            .clip(CircleShape)
            .background(if (onScrim) AppTheme.colors.scrim else AppTheme.colors.surface)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) AppTheme.colors.accent else AppTheme.colors.inkPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

