package com.xscan.app.presentation.scan

import android.net.Uri
import com.xscan.app.domain.model.Adjustments
import com.xscan.app.domain.model.CaptureMode
import com.xscan.app.domain.model.CornerQuad
import com.xscan.app.domain.model.FlashMode
import com.xscan.app.domain.model.SURE_SCAN_FRAMES
import com.xscan.app.presentation.common.UiEffect
import com.xscan.app.presentation.common.UiEvent
import com.xscan.app.presentation.common.UiState

/* ───────────────────────────── Capture ─────────────────────────────── */

data class CaptureUiState(
    val flashMode: FlashMode = FlashMode.OFF,
    val captureMode: CaptureMode = CaptureMode.SINGLE,
    val phase: CapturePhase = CapturePhase.Ready,
    val permissionGranted: Boolean = false,
    val permissionDenied: Boolean = false,
) : UiState

sealed interface CapturePhase {
    data object Ready : CapturePhase

    /** SureScan in flight: "Hold steady · 2 of 3". */
    data class Capturing(val frame: Int, val of: Int = SURE_SCAN_FRAMES) : CapturePhase

    /** Frames taken, sharpness scoring / edge detection running. */
    data object Analyzing : CapturePhase

    data class Failed(val retryable: Boolean = true) : CapturePhase
}

sealed interface CaptureEvent : UiEvent {
    data object ShutterClicked : CaptureEvent
    data object FlashToggled : CaptureEvent
    data object SureScanToggled : CaptureEvent
    data class FrameCaptured(val uri: Uri) : CaptureEvent   // From CameraX callback
    data class CaptureFailed(val throwable: Throwable) : CaptureEvent
    data class PermissionResult(val granted: Boolean) : CaptureEvent
    data object RetryClicked : CaptureEvent
    data object CloseClicked : CaptureEvent
}

sealed interface CaptureEffect : UiEffect {
    /** Ask the camera controller for one still; result returns as [CaptureEvent.FrameCaptured]. */
    data object TakePicture : CaptureEffect
    data class NavigateToCrop(val imageUri: Uri, val detectedQuad: CornerQuad?) : CaptureEffect
    data object NavigateBack : CaptureEffect
}

/* ─────────────────────────────── Crop ──────────────────────────────── */

sealed interface CropUiState : UiState {
    data object Detecting : CropUiState

    data class Adjusting(
        val imageUri: Uri,
        val quad: CornerQuad,
        val activeCorner: Corner? = null,   // Drives the loupe/magnifier
        val isProcessing: Boolean = false,  // Confirm pressed, warp running
    ) : CropUiState

    data class Error(val imageUri: Uri) : CropUiState
}

enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

sealed interface CropEvent : UiEvent {
    data class CornerDragStarted(val corner: Corner) : CropEvent
    data class CornerDragged(val corner: Corner, val x: Float, val y: Float) : CropEvent
    data object CornerDragEnded : CropEvent
    data object ResetToFullFrame : CropEvent
    data object ConfirmClicked : CropEvent
    data object RetakeClicked : CropEvent
}

sealed interface CropEffect : UiEffect {
    data class NavigateToEnhance(val processedUri: Uri) : CropEffect
    data object NavigateBackToCamera : CropEffect
    data class ShowMessage(val text: String) : CropEffect
}

/* ───────────────────────────── Enhance ─────────────────────────────── */

data class EnhanceUiState(
    val previewUri: Uri? = null,
    val adjustments: Adjustments = Adjustments(),
    val isRendering: Boolean = false,   // Re-process in flight; preview dims
    val isSaving: Boolean = false,
) : UiState

sealed interface EnhanceEvent : UiEvent {
    data class BrightnessChanged(val value: Float) : EnhanceEvent
    data class ContrastChanged(val value: Float) : EnhanceEvent
    data object RotateClicked : EnhanceEvent
    data object ColorModeToggled : EnhanceEvent
    data object SaveClicked : EnhanceEvent
}

sealed interface EnhanceEffect : UiEffect {
    data class NavigateToDocument(val documentId: String) : EnhanceEffect
    data class ShowMessage(val text: String) : EnhanceEffect
}
