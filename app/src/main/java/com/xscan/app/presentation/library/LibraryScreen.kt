package com.xscan.app.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xscan.app.ui.components.EmptyState
import com.xscan.app.ui.components.ErrorState
import com.xscan.app.ui.components.LoadingState
import com.xscan.app.ui.components.PrimaryButton
import com.xscan.app.ui.components.ScreenHeader
import com.xscan.app.ui.theme.AppTheme

/**
 * Route layer: owns the ViewModel, collects one-shot effects, delegates
 * rendering to the stateless [LibraryScreen] below.
 */
@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    onNavigateToDocument: (String) -> Unit,
    onNavigateToCapture: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryEffect.NavigateToDocument -> onNavigateToDocument(effect.id)
                is LibraryEffect.NavigateToCapture -> onNavigateToCapture()
                is LibraryEffect.ShowMessage -> onShowMessage(effect.text)
            }
        }
    }

    LibraryScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(AppTheme.colors.canvas)
            .statusBarsPadding(),
    ) {
        when (state) {
            is LibraryUiState.Loading -> LoadingState(label = "Opening your library")

            is LibraryUiState.Empty -> EmptyState(
                icon = Icons.Outlined.DocumentScanner,
                title = "Your library is empty",
                body = "Scans you capture will be filed here, ready to search, edit, and share.",
                actionText = "Scan your first document",
                onAction = { onEvent(LibraryEvent.NewScanClicked) },
            )

            is LibraryUiState.Error -> ErrorState(
                icon = Icons.Outlined.ErrorOutline,
                title = "Couldn't open the library",
                body = "Your documents are safe. Check storage access and try again.",
                onRetry = if (state.retryable) {
                    { onEvent(LibraryEvent.RetryClicked) }
                } else null,
            )

            is LibraryUiState.Content -> LibraryContent(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState.Content,
    onEvent: (LibraryEvent) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = AppTheme.spacing.lg,
                end = AppTheme.spacing.lg,
                top = AppTheme.spacing.lg,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = "Library",
                    eyebrow = "${state.documents.size} documents",
                )
                Box(Modifier.height(AppTheme.spacing.lg))
            }

            item(key = "search") {
                LibrarySearchField(
                    query = state.query,
                    onQueryChanged = { onEvent(LibraryEvent.QueryChanged(it)) },
                )
                Box(Modifier.height(AppTheme.spacing.md))
            }

            if (state.isNoResults) {
                item(key = "noResults") {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "No matches",
                        body = "Nothing in your library matches \u201C${state.query}\u201D. " +
                            "Check the spelling or clear the search.",
                        actionText = "Clear search",
                        onAction = { onEvent(LibraryEvent.QueryChanged("")) },
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                    )
                }
            } else {
                items(state.documents, key = { it.id }) { document ->
                    DocumentCard(
                        document = document,
                        onClick = { onEvent(LibraryEvent.DocumentClicked(document.id)) },
                        onRename = { onEvent(LibraryEvent.RenameRequested(document.id)) },
                        onDelete = { onEvent(LibraryEvent.DeleteConfirmed(document.id)) },
                    )
                }
            }
        }

        // The one loud element on this screen.
        PrimaryButton(
            text = "New scan",
            onClick = { onEvent(LibraryEvent.NewScanClicked) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.lg)
                .width(220.dp),
        )

        state.renamingDocumentId?.let { id ->
            val current = state.documents.firstOrNull { it.id == id } ?: return@let
            RenameDialog(
                currentName = current.name,
                onConfirm = { onEvent(LibraryEvent.RenameConfirmed(id, it)) },
                onDismiss = { onEvent(LibraryEvent.RenameDismissed) },
            )
        }
    }
}
