package com.xscan.app.presentation.library

import com.xscan.app.domain.model.ScanDocument
import com.xscan.app.presentation.common.UiEffect
import com.xscan.app.presentation.common.UiEvent
import com.xscan.app.presentation.common.UiState

sealed interface LibraryUiState : UiState {
    data object Loading : LibraryUiState

    /** No documents at all — the invitation state. */
    data object Empty : LibraryUiState

    data class Content(
        val documents: List<ScanDocument>,
        val query: String = "",
        val renamingDocumentId: String? = null,
    ) : LibraryUiState {
        /** Search ran and matched nothing — distinct from [Empty]. */
        val isNoResults: Boolean get() = documents.isEmpty() && query.isNotBlank()
    }

    data class Error(val retryable: Boolean = true) : LibraryUiState
}

sealed interface LibraryEvent : UiEvent {
    data class QueryChanged(val query: String) : LibraryEvent
    data class DocumentClicked(val id: String) : LibraryEvent
    data class RenameRequested(val id: String) : LibraryEvent
    data class RenameConfirmed(val id: String, val name: String) : LibraryEvent
    data object RenameDismissed : LibraryEvent
    data class DeleteConfirmed(val id: String) : LibraryEvent
    data object NewScanClicked : LibraryEvent
    data object RetryClicked : LibraryEvent
}

sealed interface LibraryEffect : UiEffect {
    data class NavigateToDocument(val id: String) : LibraryEffect
    data object NavigateToCapture : LibraryEffect
    data class ShowMessage(val text: String) : LibraryEffect
}
