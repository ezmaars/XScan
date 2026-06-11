package com.xscan.app.presentation.scan

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.xscan.app.domain.model.CornerQuad
import com.xscan.app.domain.model.NPoint
import com.xscan.app.ui.components.ErrorState
import com.xscan.app.ui.components.GhostButton
import com.xscan.app.ui.components.GoldThreadLoading
import com.xscan.app.ui.components.LoadingState
import com.xscan.app.ui.components.PrimaryButton
import com.xscan.app.ui.components.ScreenHeader
import com.xscan.app.ui.theme.AppTheme
import kotlin.math.hypot

@Composable
fun CropRoute(
    viewModel: CropViewModel,
    onNavigateToEnhance: (Uri) -> Unit,
    onNavigateBackToCamera: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CropEffect.NavigateToEnhance -> onNavigateToEnhance(effect.processedUri)
                is CropEffect.NavigateBackToCamera -> onNavigateBackToCamera()
                is CropEffect.ShowMessage -> onShowMessage(effect.text)
            }
        }
    }

    CropScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun CropScreen(
    state: CropUiState,
    onEvent: (CropEvent) -> Unit,
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
        ScreenHeader(title = "Adjust the frame", eyebrow = "Step 2 of 3")
        Spacer(Modifier.height(AppTheme.spacing.lg))

        when (state) {
            is CropUiState.Detecting -> LoadingState(label = "Finding the page edges")

            is CropUiState.Error -> ErrorState(
                icon = Icons.Outlined.ImageNotSupported,
                title = "Couldn't read the photo",
                body = "The capture didn't load. Retake the photo to continue.",
                retryText = "Retake photo",
                onRetry = { onEvent(CropEvent.RetakeClicked) },
            )

            is CropUiState.Adjusting -> AdjustingContent(state, onEvent)
        }
    }
}

@Composable
private fun AdjustingContent(
    state: CropUiState.Adjusting,
    onEvent: (CropEvent) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            CropCanvas(
                imageUri = state.imageUri,
                quad = state.quad,
                activeCorner = state.activeCorner,
                isProcessing = state.isProcessing,
                onEvent = onEvent,
            )
            if (state.isProcessing) {
                GoldThreadLoading(label = "Straightening the page")
            }
        }

        Spacer(Modifier.height(AppTheme.spacing.lg))

        Text(
            text = "Drag the corners to match the page. " +
                "The magnifier follows your finger for precision.",
            style = AppTheme.typography.bodySecondary,
            color = AppTheme.colors.inkTertiary,
        )

        Spacer(Modifier.height(AppTheme.spacing.lg))

        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
            GhostButton(
                text = "Retake",
                onClick = { onEvent(CropEvent.RetakeClicked) },
                enabled = !state.isProcessing,
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                text = "Reset",
                onClick = { onEvent(CropEvent.ResetToFullFrame) },
                enabled = !state.isProcessing,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(AppTheme.spacing.sm))
        PrimaryButton(
            text = "Looks right",
            onClick = { onEvent(CropEvent.ConfirmClicked) },
            enabled = !state.isProcessing,
        )
    }
}

/* ───────────────────────── The crop surface ────────────────────────── */

private const val HANDLE_TOUCH_RADIUS_PX = 96f

@Composable
private fun CropCanvas(
    imageUri: Uri,
    quad: CornerQuad,
    activeCorner: Corner?,
    isProcessing: Boolean,
    onEvent: (CropEvent) -> Unit,
) {
    val painter = rememberAsyncImagePainter(model = imageUri)
    var containerSize by remember { mutableStateOf(Size.Zero) }
    val currentQuad by androidx.compose.runtime.rememberUpdatedState(quad)
    val currentCorner by androidx.compose.runtime.rememberUpdatedState(activeCorner)

    // Where the Fit-scaled image actually draws inside the container.
    val imageRect = remember(containerSize, painter.intrinsicSize) {
        fitRect(content = painter.intrinsicSize, container = containerSize)
    }

    val accent = AppTheme.colors.accent
    val scrim = AppTheme.colors.scrim
    val handleFill = AppTheme.colors.inkPrimary

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it.toSize() }
            .alpha(if (isProcessing) 0.35f else 1f),
    ) {
        Image(
            painter = painter,
            contentDescription = "Captured document",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(imageRect, isProcessing) {
                    if (isProcessing) return@pointerInput
                    detectDragGestures(
                        onDragStart = { position ->
                            nearestCorner(position, quadToScreen(currentQuad, imageRect))
                                ?.let { onEvent(CropEvent.CornerDragStarted(it)) }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val n = screenToNormalized(change.position, imageRect)
                            currentCorner?.let {
                                onEvent(CropEvent.CornerDragged(it, n.x, n.y))
                            }
                        },
                        onDragEnd = { onEvent(CropEvent.CornerDragEnded) },
                        onDragCancel = { onEvent(CropEvent.CornerDragEnded) },
                    )
                },
        ) {
            val corners = quadToScreen(quad, imageRect)

            // Dim everything outside the quad (even-odd: outer rect minus quad).
            val mask = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                moveTo(corners[0].x, corners[0].y)
                lineTo(corners[1].x, corners[1].y)
                lineTo(corners[2].x, corners[2].y)
                lineTo(corners[3].x, corners[3].y)
                close()
            }
            drawPath(mask, color = scrim)

            // The champagne frame.
            val frame = Path().apply {
                moveTo(corners[0].x, corners[0].y)
                lineTo(corners[1].x, corners[1].y)
                lineTo(corners[2].x, corners[2].y)
                lineTo(corners[3].x, corners[3].y)
                close()
            }
            drawPath(frame, color = accent, style = Stroke(width = 2.dp.toPx()))

            // Corner handles — paper-white dot in an accent ring.
            corners.forEach { corner ->
                drawCircle(color = accent, radius = 12.dp.toPx(), center = corner)
                drawCircle(color = handleFill, radius = 6.dp.toPx(), center = corner)
            }
        }

        if (activeCorner != null) {
            CornerLoupe(
                painter = painter,
                imageRect = imageRect,
                focus = quad.point(activeCorner),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AppTheme.spacing.md),
            )
        }
    }
}

/**
 * Magnifier loupe: a 112dp circle showing the image at 2.5\u00D7, centered on
 * the corner being dragged, with a crosshair in the accent tone.
 */
@Composable
private fun CornerLoupe(
    painter: androidx.compose.ui.graphics.painter.Painter,
    imageRect: Rect,
    focus: NPoint,
    modifier: Modifier = Modifier,
) {
    val loupeSize = 112.dp
    val zoom = 2.5f
    val accent = AppTheme.colors.accent

    Box(
        modifier
            .size(loupeSize)
            .clip(CircleShape)
            .border(AppTheme.shapes.hairline, AppTheme.colors.outline, CircleShape)
            .background(AppTheme.colors.canvas),
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(loupeSize)
                .graphicsLayer {
                    // Move the focused point to the loupe center, then zoom.
                    val center = size.width / 2f
                    scaleX = zoom
                    scaleY = zoom
                    translationX = (0.5f - focus.x) * size.width * zoom
                    translationY = (0.5f - focus.y) * size.height * zoom
                },
        )
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val arm = 10.dp.toPx()
            drawLine(accent, c.copy(x = c.x - arm), c.copy(x = c.x + arm), 1.5f)
            drawLine(accent, c.copy(y = c.y - arm), c.copy(y = c.y + arm), 1.5f)
        }
    }
}

/* ─────────────────────── Coordinate plumbing ───────────────────────── */

private fun fitRect(content: Size, container: Size): Rect {
    if (container.isEmpty() || content == Size.Unspecified ||
        content.width <= 0f || content.height <= 0f
    ) return Rect(Offset.Zero, container)
    val scale = minOf(container.width / content.width, container.height / content.height)
    val drawn = Size(content.width * scale, content.height * scale)
    val offset = Offset(
        (container.width - drawn.width) / 2f,
        (container.height - drawn.height) / 2f,
    )
    return Rect(offset, drawn)
}

private fun NPoint.toScreen(rect: Rect) =
    Offset(rect.left + x * rect.width, rect.top + y * rect.height)

private fun screenToNormalized(position: Offset, rect: Rect) = NPoint(
    x = ((position.x - rect.left) / rect.width).coerceIn(0f, 1f),
    y = ((position.y - rect.top) / rect.height).coerceIn(0f, 1f),
)

private fun quadToScreen(quad: CornerQuad, rect: Rect): List<Offset> = listOf(
    quad.topLeft.toScreen(rect),
    quad.topRight.toScreen(rect),
    quad.bottomRight.toScreen(rect),
    quad.bottomLeft.toScreen(rect),
)

private fun CornerQuad.point(corner: Corner): NPoint = when (corner) {
    Corner.TOP_LEFT -> topLeft
    Corner.TOP_RIGHT -> topRight
    Corner.BOTTOM_RIGHT -> bottomRight
    Corner.BOTTOM_LEFT -> bottomLeft
}

private fun nearestCorner(position: Offset, corners: List<Offset>): Corner? {
    val order = listOf(
        Corner.TOP_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT, Corner.BOTTOM_LEFT,
    )
    return order.zip(corners)
        .map { (corner, offset) ->
            corner to hypot(position.x - offset.x, position.y - offset.y)
        }
        .filter { it.second <= HANDLE_TOUCH_RADIUS_PX }
        .minByOrNull { it.second }
        ?.first
}
