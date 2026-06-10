package com.xscan.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xscan.app.ui.theme.AppTheme
import com.xscan.app.ui.theme.pressFeedback

/**
 * The card surface every list item and panel sits on: raised tone,
 * hairline edge, and a single very soft shadow. Optional onClick wires
 * the shared press micro-interaction.
 */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(AppTheme.shapes.card)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressFeedback(interaction) else Modifier)
            .shadow(elevation = 2.dp, shape = shape, spotColor = AppTheme.colors.canvas)
            .clip(shape)
            .background(AppTheme.colors.surfaceRaised)
            .border(AppTheme.shapes.hairline, AppTheme.colors.divider, shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ) else Modifier,
            ),
    ) {
        content()
    }
}

/* ─────────────────────────── Loading state ─────────────────────────── */

/**
 * "Gold thread" — a single hairline that breathes in the accent tone.
 * The app's only loading motif; no spinners anywhere.
 */
@Composable
fun GoldThreadLoading(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val transition = rememberInfiniteTransition(label = "goldThread")
    val breathe by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = AppTheme.motion.easeInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.md),
    ) {
        Box(
            Modifier
                .width(AppTheme.spacing.xxl)
                .height(2.dp)
                .alpha(breathe)
                .background(AppTheme.colors.accent),
        )
        if (label != null) {
            Text(
                text = label,
                style = AppTheme.typography.bodySecondary,
                color = AppTheme.colors.inkTertiary,
            )
        }
    }
}

/** Full-screen centered variant for route-level loading. */
@Composable
fun LoadingState(label: String? = null, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GoldThreadLoading(label = label)
    }
}

/* ─────────────────────── Empty & error states ──────────────────────── */

/**
 * Editorial empty state: an icon in a hairline ring, serif headline,
 * one quiet sentence, one clear action. Errors reuse the same anatomy
 * via [ErrorState] so failure never looks like a different app.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    iconTint: androidx.compose.ui.graphics.Color = AppTheme.colors.accent,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(percent = 50))
                .border(
                    AppTheme.shapes.hairline,
                    AppTheme.colors.outline,
                    RoundedCornerShape(percent = 50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp),
            )
        }
        Box(Modifier.height(AppTheme.spacing.xl))
        Text(
            text = title,
            style = AppTheme.typography.titleScreen,
            color = AppTheme.colors.inkPrimary,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.height(AppTheme.spacing.sm))
        Text(
            text = body,
            style = AppTheme.typography.body,
            color = AppTheme.colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Box(Modifier.height(AppTheme.spacing.xl))
            PrimaryButton(text = actionText, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    retryText: String = "Try again",
    onRetry: (() -> Unit)? = null,
    icon: ImageVector,
) {
    EmptyState(
        icon = icon,
        title = title,
        body = body,
        modifier = modifier,
        actionText = onRetry?.let { retryText },
        onAction = onRetry,
        iconTint = AppTheme.colors.danger,
    )
}
