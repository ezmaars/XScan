package com.xscan.app.presentation.export

import com.xscan.app.domain.model.AttachmentSize
import com.xscan.app.domain.model.ExportFormat
import com.xscan.app.domain.model.ExportTarget
import com.xscan.app.domain.model.ScanDocument
import com.xscan.app.presentation.common.UiEffect
import com.xscan.app.presentation.common.UiEvent
import com.xscan.app.presentation.common.UiState

/* ──────────────── Document detail (multipage editing) ──────────────── */

sealed interface DocumentUiState : UiState {
    data object Loading : DocumentUiState

    data class Content(
        val document: ScanDocument,
        val isReorderMode: Boolean = false,
        val isRenaming: Boolean = false,
        val viewingPageId: String? = null,          // Non-null = full-screen viewer
        val exportSheet: ExportSheetState? = null,  // Non-null = sheet shown
    ) : DocumentUiState

    data object NotFound : DocumentUiState
}

sealed interface DocumentEvent : UiEvent {
    data class PageClicked(val pageId: String) : DocumentEvent
    data object ViewerDismissed : DocumentEvent
    data object AddPageClicked : DocumentEvent
    data class DeletePageConfirmed(val pageId: String) : DocumentEvent
    data object ReorderModeToggled : DocumentEvent
    data class PagesReordered(val orderedPageIds: List<String>) : DocumentEvent
    data object RenameRequested : DocumentEvent
    data object RenameDismissed : DocumentEvent
    data class Renamed(val name: String) : DocumentEvent

    /* Export sheet lifecycle */
    data object ExportClicked : DocumentEvent
    data object ExportSheetDismissed : DocumentEvent
    data class FormatSelected(val format: ExportFormat) : DocumentEvent
    data class SizeSelected(val size: AttachmentSize) : DocumentEvent
    data class TargetSelected(val target: ExportTarget) : DocumentEvent
}

sealed interface DocumentEffect : UiEffect {
    data class NavigateToCapture(val documentId: String) : DocumentEffect
    data class LaunchShare(val uriString: String, val mimeType: String) : DocumentEffect
    data class LaunchEmail(
        val uriString: String,
        val mimeType: String,
        val subject: String,
    ) : DocumentEffect
    data class LaunchPrint(val uriString: String, val jobName: String) : DocumentEffect
    data class ShowMessage(val text: String) : DocumentEffect
}

/* ───────────────────────── Export sheet state ──────────────────────── */

data class ExportSheetState(
    val format: ExportFormat = ExportFormat.PDF,
    val size: AttachmentSize = AttachmentSize.MEDIUM,
    val estimatedBytes: Long? = null,     // Null while estimating
    val phase: ExportPhase = ExportPhase.Configuring,
)

sealed interface ExportPhase {
    data object Configuring : ExportPhase
    data class Exporting(val target: ExportTarget) : ExportPhase
    data object Failed : ExportPhase
}
