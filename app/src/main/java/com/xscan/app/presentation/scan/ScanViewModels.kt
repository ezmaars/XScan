package com.xscan.app.presentation.scan

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.xscan.app.domain.model.Adjustments
import com.xscan.app.domain.model.ColorMode
import com.xscan.app.domain.model.CornerQuad
import com.xscan.app.domain.model.NPoint
import com.xscan.app.domain.model.ScanPage
import com.xscan.app.domain.repository.DocumentRepository
import com.xscan.app.domain.repository.DomainResult
import com.xscan.app.domain.repository.ScanProcessor
import com.xscan.app.presentation.common.MviViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/* ─────────────────────────────── Crop ──────────────────────────────── */

class CropViewModel(
    imageUriString: String,
    quadString: String?,
    private val processor: ScanProcessor,
) : MviViewModel<CropUiState, CropEvent, CropEffect>(CropUiState.Detecting) {

    private val imageUri: Uri = Uri.parse(imageUriString)

    init {
        // The capture screen may already have a detected quad; otherwise detect here.
        val handedQuad = quadString?.decodeQuad()
        if (handedQuad != null) {
            setState { CropUiState.Adjusting(imageUri = imageUri, quad = handedQuad) }
        } else {
            detect()
        }
    }

    override fun onEvent(event: CropEvent) {
        when (event) {
            is CropEvent.CornerDragStarted ->
                updateAdjusting { it.copy(activeCorner = event.corner) }
            is CropEvent.CornerDragged -> updateAdjusting {
                it.copy(quad = it.quad.withCorner(event.corner, NPoint(event.x, event.y)))
            }
            is CropEvent.CornerDragEnded -> updateAdjusting { it.copy(activeCorner = null) }
            is CropEvent.ResetToFullFrame -> updateAdjusting { it.copy(quad = CornerQuad.FULL) }
            is CropEvent.ConfirmClicked -> confirm()
            is CropEvent.RetakeClicked -> sendEffect(CropEffect.NavigateBackToCamera)
        }
    }

    private fun detect() {
        setState { CropUiState.Detecting }
        viewModelScope.launch {
            try {
                val quad = processor.detectEdges(imageUri) ?: CornerQuad.FULL
                setState { CropUiState.Adjusting(imageUri = imageUri, quad = quad) }
            } catch (t: Throwable) {
                setState { CropUiState.Error(imageUri) }
            }
        }
    }

    private fun confirm() {
        val adjusting = currentState as? CropUiState.Adjusting ?: return
        if (adjusting.isProcessing) return
        setState { adjusting.copy(isProcessing = true) }
        viewModelScope.launch {
            when (val result = processor.process(imageUri, adjusting.quad, Adjustments())) {
                is DomainResult.Success ->
                    sendEffect(CropEffect.NavigateToEnhance(result.value.uri))
                is DomainResult.Failure -> {
                    setState { adjusting.copy(isProcessing = false) }
                    sendEffect(CropEffect.ShowMessage("Couldn't straighten the page. Try again."))
                }
            }
        }
    }

    private inline fun updateAdjusting(
        crossinline transform: (CropUiState.Adjusting) -> CropUiState.Adjusting,
    ) {
        setState { state ->
            if (state is CropUiState.Adjusting && !state.isProcessing) transform(state) else state
        }
    }

    companion object {
        /** "x,y;x,y;x,y;x,y" clockwise from top-left, normalized 0..1. */
        fun encodeQuad(quad: CornerQuad): String = listOf(
            quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft,
        ).joinToString(";") { "${it.x},${it.y}" }
    }
}

private fun CornerQuad.withCorner(corner: Corner, point: NPoint): CornerQuad = when (corner) {
    Corner.TOP_LEFT -> copy(topLeft = point)
    Corner.TOP_RIGHT -> copy(topRight = point)
    Corner.BOTTOM_RIGHT -> copy(bottomRight = point)
    Corner.BOTTOM_LEFT -> copy(bottomLeft = point)
}

private fun String.decodeQuad(): CornerQuad? = runCatching {
    val points = split(";").map { pair ->
        val (x, y) = pair.split(",")
        NPoint(x.toFloat(), y.toFloat())
    }
    CornerQuad(points[0], points[1], points[2], points[3])
}.getOrNull()

/* ───────────────────────────── Enhance ─────────────────────────────── */

@OptIn(FlowPreview::class)
class EnhanceViewModel(
    sourceUriString: String,
    private val appendToDocumentId: String?,
    private val processor: ScanProcessor,
    private val repository: DocumentRepository,
) : MviViewModel<EnhanceUiState, EnhanceEvent, EnhanceEffect>(EnhanceUiState()) {

    /** The crop output — already perspective-corrected; adjustments layer on top. */
    private val sourceUri: Uri = Uri.parse(sourceUriString)

    private val adjustments = MutableStateFlow(Adjustments())

    init {
        setState { it.copy(previewUri = sourceUri) }

        // Re-render the preview as controls settle; drop(1) skips the initial value.
        adjustments
            .drop(1)
            .debounce(RENDER_DEBOUNCE_MS)
            .onEach { render(it) }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: EnhanceEvent) {
        when (event) {
            is EnhanceEvent.BrightnessChanged -> push { it.copy(brightness = event.value) }
            is EnhanceEvent.ContrastChanged -> push { it.copy(contrast = event.value) }
            is EnhanceEvent.RotateClicked -> push { it.rotatedClockwise() }
            is EnhanceEvent.ColorModeToggled -> push {
                it.copy(
                    colorMode = when (it.colorMode) {
                        ColorMode.COLOR -> ColorMode.BLACK_AND_WHITE
                        ColorMode.BLACK_AND_WHITE -> ColorMode.COLOR
                    },
                )
            }
            is EnhanceEvent.SaveClicked -> save()
        }
    }

    private fun push(transform: (Adjustments) -> Adjustments) {
        adjustments.value = transform(adjustments.value)
        setState { it.copy(adjustments = adjustments.value) }
    }

    private suspend fun render(current: Adjustments) {
        setState { it.copy(isRendering = true) }
        when (val result = processor.process(sourceUri, CornerQuad.FULL, current)) {
            is DomainResult.Success ->
                setState { it.copy(previewUri = result.value.uri, isRendering = false) }
            is DomainResult.Failure -> {
                setState { it.copy(isRendering = false) }
                sendEffect(EnhanceEffect.ShowMessage("Preview couldn't update"))
            }
        }
    }

    private fun save() {
        if (currentState.isSaving) return
        setState { it.copy(isSaving = true) }
        viewModelScope.launch {
            val rendered = processor.process(sourceUri, CornerQuad.FULL, adjustments.value)
            if (rendered !is DomainResult.Success) {
                failSave()
                return@launch
            }
            val page = ScanPage(
                id = UUID.randomUUID().toString(),
                originalUri = sourceUri,
                processedUri = rendered.value.uri,
                corners = CornerQuad.FULL,
                adjustments = adjustments.value,
                sizeBytes = rendered.value.sizeBytes,
            )
            val documentId: String? = if (appendToDocumentId != null) {
                when (repository.addPage(appendToDocumentId, page)) {
                    is DomainResult.Success -> appendToDocumentId
                    is DomainResult.Failure -> null
                }
            } else {
                when (val created = repository.createDocument(defaultName(), page)) {
                    is DomainResult.Success -> created.value.id
                    is DomainResult.Failure -> null
                }
            }
            if (documentId != null) {
                sendEffect(EnhanceEffect.NavigateToDocument(documentId))
            } else {
                failSave()
            }
        }
    }

    private fun failSave() {
        setState { it.copy(isSaving = false) }
        sendEffect(EnhanceEffect.ShowMessage("Couldn't save the scan. Try again."))
    }

    private fun defaultName(): String =
        "Scan \u2013 " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date())

    private companion object {
        const val RENDER_DEBOUNCE_MS = 220L
    }
}
