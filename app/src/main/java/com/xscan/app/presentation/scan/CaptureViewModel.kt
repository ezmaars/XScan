package com.xscan.app.presentation.scan

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.xscan.app.domain.model.CaptureMode
import com.xscan.app.domain.model.FlashMode
import com.xscan.app.domain.model.SURE_SCAN_FRAMES
import com.xscan.app.domain.repository.ScanProcessor
import com.xscan.app.presentation.common.MviViewModel
import kotlinx.coroutines.launch

class CaptureViewModel(
    private val processor: ScanProcessor,
) : MviViewModel<CaptureUiState, CaptureEvent, CaptureEffect>(CaptureUiState()) {

    private val frames = mutableListOf<Uri>()

    override fun onEvent(event: CaptureEvent) {
        when (event) {
            is CaptureEvent.ShutterClicked -> beginCapture()
            is CaptureEvent.FrameCaptured -> onFrame(event.uri)
            is CaptureEvent.CaptureFailed ->
                setState { it.copy(phase = CapturePhase.Failed()) }
            is CaptureEvent.FlashToggled -> setState {
                it.copy(
                    flashMode = when (it.flashMode) {
                        FlashMode.OFF -> FlashMode.ON
                        FlashMode.ON -> FlashMode.AUTO
                        FlashMode.AUTO -> FlashMode.OFF
                    },
                )
            }
            is CaptureEvent.SureScanToggled -> setState {
                if (it.phase !is CapturePhase.Ready) it
                else it.copy(
                    captureMode = when (it.captureMode) {
                        CaptureMode.SINGLE -> CaptureMode.SURE_SCAN
                        CaptureMode.SURE_SCAN -> CaptureMode.SINGLE
                    },
                )
            }
            is CaptureEvent.PermissionResult -> setState {
                it.copy(permissionGranted = event.granted, permissionDenied = !event.granted)
            }
            is CaptureEvent.RetryClicked -> {
                frames.clear()
                setState { it.copy(phase = CapturePhase.Ready) }
            }
            is CaptureEvent.CloseClicked -> sendEffect(CaptureEffect.NavigateBack)
        }
    }

    private fun framesNeeded() = when (currentState.captureMode) {
        CaptureMode.SINGLE -> 1
        CaptureMode.SURE_SCAN -> SURE_SCAN_FRAMES
    }

    private fun beginCapture() {
        if (currentState.phase !is CapturePhase.Ready) return
        frames.clear()
        setState { it.copy(phase = CapturePhase.Capturing(frame = 1, of = framesNeeded())) }
        sendEffect(CaptureEffect.TakePicture)
    }

    private fun onFrame(uri: Uri) {
        frames += uri
        val needed = framesNeeded()
        if (frames.size < needed) {
            setState { it.copy(phase = CapturePhase.Capturing(frames.size + 1, needed)) }
            sendEffect(CaptureEffect.TakePicture)
            return
        }
        analyze(frames.toList())
    }

    /** SureScan: score each frame's sharpness, keep the best, find its edges. */
    private fun analyze(candidates: List<Uri>) {
        setState { it.copy(phase = CapturePhase.Analyzing) }
        viewModelScope.launch {
            try {
                val best = candidates
                    .map { uri -> uri to processor.sharpnessScore(uri) }
                    .maxByOrNull { it.second }
                    ?.first
                    ?: candidates.first()
                val quad = processor.detectEdges(best)
                setState { it.copy(phase = CapturePhase.Ready) }
                sendEffect(CaptureEffect.NavigateToCrop(best, quad))
            } catch (t: Throwable) {
                setState { it.copy(phase = CapturePhase.Failed()) }
            }
        }
    }
}
