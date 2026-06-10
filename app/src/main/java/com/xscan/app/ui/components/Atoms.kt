package com.xscan.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Text
import com.xscan.app.ui.theme.AppTheme

/** 1dp rule in the divider tone. The only divider weight in the app. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(AppTheme.shapes.hairline)
            .background(AppTheme.colors.divider),
    )
}

/** Tracked-out eyebrow: "3 PAGES · PDF", "TODAY". Always uppercase. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Text(
        text = text.uppercase(),
        style = AppTheme.typography.overline,
        color = if (accent) AppTheme.colors.accent else AppTheme.colors.inkTertiary,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Serif screen title with optional eyebrow above — the editorial masthead. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Eyebrow(eyebrow)
                Spacer(Modifier.height(AppTheme.spacing.xs))
            }
            Text(
                text = title,
                style = AppTheme.typography.titleScreen,
                color = AppTheme.colors.inkPrimary,
            )
        }
        trailing?.invoke()
    }
}

/**
 * Quiet segmented control — used for Color/B&W, PDF/JPEG, attachment size.
 * Selection is marked by a raised fill and ink shift, not a colored pill;
 * the accent stays reserved for primary actions.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppTheme.shapes.control)
    Row(
        modifier = modifier
            .clip(shape)
            .background(AppTheme.colors.surface)
            .padding(AppTheme.spacing.xxs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val fill by animateColorAsState(
                targetValue = if (isSelected) AppTheme.colors.surfacePressed
                else AppTheme.colors.surface,
                animationSpec = tween(AppTheme.motion.standard, easing = AppTheme.motion.easeInOut),
                label = "segmentFill",
            )
            val ink by animateColorAsState(
                targetValue = if (isSelected) AppTheme.colors.inkPrimary
                else AppTheme.colors.inkTertiary,
                animationSpec = tween(AppTheme.motion.standard, easing = AppTheme.motion.easeInOut),
                label = "segmentInk",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(AppTheme.shapes.control - AppTheme.spacing.xxs))
                    .background(fill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(option) }
                    .padding(vertical = AppTheme.spacing.sm),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = label(option), style = AppTheme.typography.label, color = ink)
            }
        }
    }
}
