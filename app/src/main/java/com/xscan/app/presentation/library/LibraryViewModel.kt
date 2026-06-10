package com.xscan.app.presentation.library

import androidx.lifecycle.viewModelScope
import com.xscan.app.domain.repository.DocumentRepository
import com.xscan.app.domain.repository.DomainResult
import com.xscan.app.presentation.common.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: DocumentRepository,
) : MviViewModel<LibraryUiState, LibraryEvent, LibraryEffect>(LibraryUiState.Loading) {

    /** Raw query echoes to the field instantly; the search itself is debounced. */
    private val query = MutableStateFlow("")
    private val renamingId = MutableStateFlow<String?>(null)
    private val failed = MutableStateFlow(false)

    init {
        val documents = query
            .debounce(QUERY_DEBOUNCE_MS)
            .distinctUntilChanged()
            .flatMapLatest { repository.observeDocuments(it) }

        combine(documents, query, renamingId, failed) { docs, q, renaming, hasFailed ->
            when {
                hasFailed -> LibraryUiState.Error()
                docs.isEmpty() && q.isBlank() -> LibraryUiState.Empty
                else -> LibraryUiState.Content(
                    documents = docs,
                    query = q,
                    renamingDocumentId = renaming,
                )
            }
        }
            .catch { emit(LibraryUiState.Error()) }
            .onEach { next -> setState { next } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.QueryChanged -> query.value = event.query
            is LibraryEvent.DocumentClicked ->
                sendEffect(LibraryEffect.NavigateToDocument(event.id))
            is LibraryEvent.NewScanClicked -> sendEffect(LibraryEffect.NavigateToCapture)
            is LibraryEvent.RenameRequested -> renamingId.value = event.id
            is LibraryEvent.RenameDismissed -> renamingId.value = null
            is LibraryEvent.RenameConfirmed -> rename(event.id, event.name)
            is LibraryEvent.DeleteConfirmed -> delete(event.id)
            is LibraryEvent.RetryClicked -> failed.value = false
        }
    }

    private fun rename(id: String, name: String) {
        renamingId.value = null
        viewModelScope.launch {
            if (repository.renameDocument(id, name) is DomainResult.Failure) {
                sendEffect(LibraryEffect.ShowMessage("Couldn't rename the document"))
            }
        }
    }

    private fun delete(id: String) {
        viewModelScope.launch {
            when (repository.deleteDocument(id)) {
                is DomainResult.Success ->
                    sendEffect(LibraryEffect.ShowMessage("Document deleted"))
                is DomainResult.Failure ->
                    sendEffect(LibraryEffect.ShowMessage("Couldn't delete the document"))
            }
        }
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 250L
    }
}
