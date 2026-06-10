package com.xscan.app.presentation.enhance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.xscan.app.domain.model.ColorMode
import com.xscan.app.presentation.scan.EnhanceEffect
import com.xscan.app.presentation.scan.EnhanceEvent
import com.xscan.app.presentation.scan.EnhanceUiState
import com.xscan.app.presentation.scan.EnhanceViewModel
import com.xscan.app.ui.components.Eyebrow
import com.xscan.app.ui.components.GoldThreadLoading
import com.xscan.app.ui.components.IconActionButton
import com.xscan.app.ui.components.PrimaryButton
import com.xscan.app.ui.components.ScreenHeader
import com.xscan.app.ui.components.SegmentedControl
import com.xscan.app.ui.theme.AppTheme

@Composable
fun EnhanceRoute(
    viewModel: EnhanceViewModel,
    onNavigateToDocument: (String) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EnhanceEffect.NavigateToDocument -> onNavigateToDocument(effect.documentId)
                is EnhanceEffect.ShowMessage -> onShowMessage(effect.text)
            }
        }
    }

    EnhanceScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun EnhanceScreen(
    state: EnhanceUiState,
    onEvent: (EnhanceEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(AppTheme.colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(AppTheme.spacing.lg),
    ) {
        ScreenHeader(title = "Refine", eyebrow = "Step 3 of 3")
        Spacer(Modifier.height(AppTheme.spacing.lg))

        // Preview — dims gently while a re-render settles.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.shapes.card))
                .background(AppTheme.colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = state.previewUri,
                contentDescription = "Scan preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (state.isRendering) 0.35f else 1f),
            )
            if (state.isRendering) {
                GoldThreadLoading(label = "Updating preview")
            }
        }

        Spacer(Modifier.height(AppTheme.spacing.lg))

        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentedControl(
                options = listOf(ColorMode.COLOR, ColorMode.BLACK_AND_WHITE),
                selected = state.adjustments.colorMode,
                onSelect = { onEvent(EnhanceEvent.ColorModeToggled) },
                label = { if (it == ColorMode.COLOR) "Color" else "Black & white" },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(AppTheme.spacing.sm))
            IconActionButton(
                icon = Icons.Outlined.RotateRight,
                contentDescription = "Rotate 90 degrees",
                onClick = { onEvent(EnhanceEvent.RotateClicked) },
            )
        }

        Spacer(Modifier.height(AppTheme.spacing.md))

        AdjustmentSlider(
            label = "Brightness",
            value = state.adjustments.brightness,
            onValueChange = { onEvent(EnhanceEvent.BrightnessChanged(it)) },
        )
        AdjustmentSlider(
            label = "Contrast",
            value = state.adjustments.contrast,
            onValueChange = { onEvent(EnhanceEvent.ContrastChanged(it)) },
        )

        Spacer(Modifier.height(AppTheme.spacing.md))

        PrimaryButton(
            text = if (state.isSaving) "Saving\u2026" else "Save to library",
            onClick = { onEvent(EnhanceEvent.SaveClicked) },
            enabled = !state.isSaving,
        )
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow(label)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = AppTheme.colors.accent,
                activeTrackColor = AppTheme.colors.accent,
                inactiveTrackColor = AppTheme.colors.outline,
            ),
            modifier = Modifier.height(36.dp),
        )
    }
}
