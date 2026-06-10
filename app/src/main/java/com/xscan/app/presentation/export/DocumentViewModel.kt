package com.xscan.app.presentation.export

import androidx.lifecycle.viewModelScope
import com.xscan.app.domain.model.AttachmentSize
import com.xscan.app.domain.model.ExportFormat
import com.xscan.app.domain.model.ExportRequest
import com.xscan.app.domain.model.ExportTarget
import com.xscan.app.domain.repository.DocumentExporter
import com.xscan.app.domain.repository.DocumentRepository
import com.xscan.app.domain.repository.DomainResult
import com.xscan.app.presentation.common.MviViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class DocumentViewModel(
    private val documentId: String,
    private val repository: DocumentRepository,
    private val exporter: DocumentExporter,
) : MviViewModel<DocumentUiState, DocumentEvent, DocumentEffect>(DocumentUiState.Loading) {

    private var estimateJob: Job? = null

    init {
        repository.observeDocument(documentId)
            .onEach { document ->
                setState { previous ->
                    when {
                        document == null -> DocumentUiState.NotFound
                        previous is DocumentUiState.Content ->
                            previous.copy(document = document)
                        else -> DocumentUiState.Content(document = document)
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: DocumentEvent) {
        when (event) {
            is DocumentEvent.PageClicked ->
                updateContent { it.copy(viewingPageId = event.pageId) }
            is DocumentEvent.ViewerDismissed ->
                updateContent { it.copy(viewingPageId = null) }
            is DocumentEvent.AddPageClicked ->
                sendEffect(DocumentEffect.NavigateToCapture(documentId))
            is DocumentEvent.DeletePageConfirmed -> deletePage(event.pageId)
            is DocumentEvent.ReorderModeToggled -> updateContent {
                it.copy(isReorderMode = !it.isReorderMode)
            }
            is DocumentEvent.PagesReordered -> reorder(event.orderedPageIds)
            is DocumentEvent.RenameRequested -> updateContent { it.copy(isRenaming = true) }
            is DocumentEvent.RenameDismissed -> updateContent { it.copy(isRenaming = false) }
            is DocumentEvent.Renamed -> rename(event.name)

            is DocumentEvent.ExportClicked -> openExportSheet()
            is DocumentEvent.ExportSheetDismissed ->
                updateContent { it.copy(exportSheet = null) }
            is DocumentEvent.FormatSelected -> updateSheet { it.copy(format = event.format) }
            is DocumentEvent.SizeSelected -> updateSheet { it.copy(size = event.size) }
            is DocumentEvent.TargetSelected -> export(event.target)
        }
    }

    /* ─────────────────────── Multipage editing ─────────────────────── */

    private fun deletePage(pageId: String) {
        viewModelScope.launch {
            val content = currentState as? DocumentUiState.Content ?: return@launch
            if (content.document.pageCount == 1) {
                sendEffect(
                    DocumentEffect.ShowMessage(
                        "A document needs at least one page. Delete the document instead.",
                    ),
                )
                return@launch
            }
            if (repository.deletePage(documentId, pageId) is DomainResult.Failure) {
                sendEffect(DocumentEffect.ShowMessage("Couldn't delete the page"))
            }
        }
    }

    private fun reorder(orderedPageIds: List<String>) {
        viewModelScope.launch {
            if (repository.reorderPages(documentId, orderedPageIds) is DomainResult.Failure) {
                sendEffect(DocumentEffect.ShowMessage("Couldn't reorder pages"))
            }
        }
    }

    private fun rename(name: String) {
        updateContent { it.copy(isRenaming = false) }
        if (name.isBlank()) return
        viewModelScope.launch {
            if (repository.renameDocument(documentId, name.trim()) is DomainResult.Failure) {
                sendEffect(DocumentEffect.ShowMessage("Couldn't rename the document"))
            }
        }
    }

    /* ───────────────────────────── Export ──────────────────────────── */

    private fun openExportSheet() {
        updateContent { it.copy(exportSheet = ExportSheetState()) }
        refreshEstimate()
    }

    private fun updateSheet(transform: (ExportSheetState) -> ExportSheetState) {
        updateContent { content ->
            content.exportSheet?.let {
                // Settings changed → previous estimate is stale until recomputed.
                content.copy(
                    exportSheet = transform(it).copy(
                        estimatedBytes = null,
                        phase = ExportPhase.Configuring,
                    ),
                )
            } ?: content
        }
        refreshEstimate()
    }

    private fun refreshEstimate() {
        estimateJob?.cancel()
        val sheet = sheetOrNull() ?: return
        estimateJob = viewModelScope.launch {
            val bytes = exporter.estimateSizeBytes(request(sheet, target = ExportTarget.Share))
            updateContent { content ->
                content.exportSheet?.let {
                    content.copy(exportSheet = it.copy(estimatedBytes = bytes))
                } ?: content
            }
        }
    }

    private fun export(target: ExportTarget) {
        val sheet = sheetOrNull() ?: return
        if (sheet.phase is ExportPhase.Exporting) return

        // Printing always renders a PDF, whatever the selected format.
        val format = if (target is ExportTarget.CloudPrint) ExportFormat.PDF else sheet.format
        updateContent {
            it.copy(exportSheet = sheet.copy(phase = ExportPhase.Exporting(target)))
        }

        viewModelScope.launch {
            val effectiveSheet = sheet.copy(format = format)
            when (val result = exporter.export(request(effectiveSheet, target))) {
                is DomainResult.Success -> {
                    val uri = result.value.toString()
                    val mime = when (format) {
                        ExportFormat.PDF -> "application/pdf"
                        ExportFormat.JPEG -> "image/jpeg"
                    }
                    val name = (currentState as? DocumentUiState.Content)
                        ?.document?.name ?: "Scan"
                    updateContent { it.copy(exportSheet = null) }
                    sendEffect(
                        when (target) {
                            is ExportTarget.EmailToMyself ->
                                DocumentEffect.LaunchEmail(uri, mime, subject = name)
                            is ExportTarget.Share -> DocumentEffect.LaunchShare(uri, mime)
                            is ExportTarget.CloudPrint ->
                                DocumentEffect.LaunchPrint(uri, jobName = name)
                        },
                    )
                }
                is DomainResult.Failure -> updateContent {
                    it.copy(exportSheet = sheet.copy(phase = ExportPhase.Failed))
                }
            }
        }
    }

    private fun request(sheet: ExportSheetState, target: ExportTarget) = ExportRequest(
        documentId = documentId,
        format = sheet.format,
        size = sheet.size,
        target = target,
    )

    private fun sheetOrNull(): ExportSheetState? =
        (currentState as? DocumentUiState.Content)?.exportSheet

    private inline fun updateContent(
        transform: (DocumentUiState.Content) -> DocumentUiState.Content,
    ) {
        setState { state ->
            if (state is DocumentUiState.Content) transform(state) else state
        }
    }
}
