package com.xscan.app.presentation.export

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.xscan.app.domain.model.AttachmentSize
import com.xscan.app.domain.model.ExportFormat
import com.xscan.app.domain.model.ExportTarget
import com.xscan.app.domain.model.ScanPage
import com.xscan.app.presentation.library.RenameDialog
import com.xscan.app.ui.components.ErrorState
import com.xscan.app.ui.components.Eyebrow
import com.xscan.app.ui.components.GhostButton
import com.xscan.app.ui.components.GoldThreadLoading
import com.xscan.app.ui.components.Hairline
import com.xscan.app.ui.components.IconActionButton
import com.xscan.app.ui.components.LoadingState
import com.xscan.app.ui.components.PremiumCard
import com.xscan.app.ui.components.PrimaryButton
import com.xscan.app.ui.components.ScreenHeader
import com.xscan.app.ui.components.SegmentedControl
import com.xscan.app.ui.theme.AppTheme

@Composable
fun DocumentRoute(
    viewModel: DocumentViewModel,
    onNavigateToCapture: (documentId: String) -> Unit,
    onLaunchShare: (uri: String, mime: String) -> Unit,
    onLaunchEmail: (uri: String, mime: String, subject: String) -> Unit,
    onLaunchPrint: (uri: String, jobName: String) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DocumentEffect.NavigateToCapture -> onNavigateToCapture(effect.documentId)
                is DocumentEffect.LaunchShare -> onLaunchShare(effect.uriString, effect.mimeType)
                is DocumentEffect.LaunchEmail ->
                    onLaunchEmail(effect.uriString, effect.mimeType, effect.subject)
                is DocumentEffect.LaunchPrint -> onLaunchPrint(effect.uriString, effect.jobName)
                is DocumentEffect.ShowMessage -> onShowMessage(effect.text)
            }
        }
    }

    DocumentScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun DocumentScreen(
    state: DocumentUiState,
    onEvent: (DocumentEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(AppTheme.colors.canvas)
            .statusBarsPadding(),
    ) {
        when (state) {
            is DocumentUiState.Loading -> LoadingState(label = "Opening the document")

            is DocumentUiState.NotFound -> ErrorState(
                icon = Icons.Outlined.ErrorOutline,
                title = "Document not found",
                body = "This document may have been deleted. Go back to the library.",
            )

            is DocumentUiState.Content -> DocumentContent(state, onEvent)
        }
    }
}

@Composable
private fun DocumentContent(
    state: DocumentUiState.Content,
    onEvent: (DocumentEvent) -> Unit,
) {
    val document = state.document

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
                    title = document.name,
                    eyebrow = "${document.pageCount} " +
                        if (document.pageCount == 1) "page" else "pages",
                    trailing = {
                        Row {
                            IconActionButton(
                                icon = Icons.Outlined.Edit,
                                contentDescription = "Rename document",
                                onClick = { onEvent(DocumentEvent.RenameRequested) },
                            )
                            Spacer(Modifier.width(AppTheme.spacing.xs))
                            IconActionButton(
                                icon = Icons.Outlined.SwapVert,
                                contentDescription = "Reorder pages",
                                onClick = { onEvent(DocumentEvent.ReorderModeToggled) },
                                active = state.isReorderMode,
                            )
                        }
                    },
                )
                Spacer(Modifier.height(AppTheme.spacing.lg))
            }

            items(document.pages, key = { it.id }) { page ->
                val index = document.pages.indexOfFirst { it.id == page.id }
                PageCard(
                    page = page,
                    pageNumber = index + 1,
                    reorderMode = state.isReorderMode,
                    canMoveUp = index > 0,
                    canMoveDown = index < document.pages.lastIndex,
                    onClick = { onEvent(DocumentEvent.PageClicked(page.id)) },
                    onDelete = { onEvent(DocumentEvent.DeletePageConfirmed(page.id)) },
                    onMove = { delta ->
                        val ids = document.pages.map { it.id }.toMutableList()
                        val target = index + delta
                        if (target in ids.indices) {
                            ids[index] = ids[target].also { ids[target] = ids[index] }
                            onEvent(DocumentEvent.PagesReordered(ids))
                        }
                    },
                )
            }

            item(key = "addPage") {
                Spacer(Modifier.height(AppTheme.spacing.xs))
                GhostButton(
                    text = "Add a page",
                    onClick = { onEvent(DocumentEvent.AddPageClicked) },
                )
            }
        }

        PrimaryButton(
            text = "Export",
            onClick = { onEvent(DocumentEvent.ExportClicked) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.lg)
                .width(220.dp),
        )

        if (state.isRenaming) {
            RenameDialog(
                currentName = document.name,
                onConfirm = { onEvent(DocumentEvent.Renamed(it)) },
                onDismiss = { onEvent(DocumentEvent.RenameDismissed) },
            )
        }

        state.viewingPageId?.let { id ->
            document.pages.firstOrNull { it.id == id }?.let { page ->
                PageViewer(page = page, onDismiss = { onEvent(DocumentEvent.ViewerDismissed) })
            }
        }

        state.exportSheet?.let { sheet ->
            ExportSheet(sheet = sheet, onEvent = onEvent)
        }
    }
}

@Composable
private fun PageCard(
    page: ScanPage,
    pageNumber: Int,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(AppTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(AppTheme.shapes.control - 6.dp))
                    .background(AppTheme.colors.surface),
            ) {
                AsyncImage(
                    model = page.processedUri,
                    contentDescription = "Page $pageNumber",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(AppTheme.spacing.md))
            Column(Modifier.weight(1f)) {
                Eyebrow("Page $pageNumber")
            }
            if (reorderMode) {
                IconActionButton(
                    icon = Icons.Outlined.ArrowUpward,
                    contentDescription = "Move page up",
                    onClick = { if (canMoveUp) onMove(-1) },
                )
                IconActionButton(
                    icon = Icons.Outlined.ArrowDownward,
                    contentDescription = "Move page down",
                    onClick = { if (canMoveDown) onMove(1) },
                )
                IconActionButton(
                    icon = Icons.Outlined.Delete,
                    contentDescription = "Delete page",
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun PageViewer(page: ScanPage, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.shapes.card))
                .background(AppTheme.colors.surfaceRaised)
                .padding(AppTheme.spacing.xs),
        ) {
            AsyncImage(
                model = page.processedUri,
                contentDescription = "Full page",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/* ─────────────────────────── Export sheet ──────────────────────────── */

private fun Long.asReadableSize(): String = when {
    this >= 1 shl 20 -> "%.1f MB".format(this / 1048576f)
    this >= 1 shl 10 -> "%d KB".format(this / 1024)
    else -> "$this B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(
    sheet: ExportSheetState,
    onEvent: (DocumentEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(DocumentEvent.ExportSheetDismissed) },
        containerColor = AppTheme.colors.surfaceRaised,
        shape = RoundedCornerShape(
            topStart = AppTheme.shapes.sheet,
            topEnd = AppTheme.shapes.sheet,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = AppTheme.spacing.lg,
                    end = AppTheme.spacing.lg,
                    bottom = AppTheme.spacing.xl,
                ),
        ) {
            Text(
                text = "Export",
                style = AppTheme.typography.titleScreen,
                color = AppTheme.colors.inkPrimary,
            )
            Spacer(Modifier.height(AppTheme.spacing.lg))

            when (val phase = sheet.phase) {
                is ExportPhase.Exporting -> {
                    GoldThreadLoading(label = "Preparing your document")
                    Spacer(Modifier.height(AppTheme.spacing.xl))
                }
                else -> {
                    if (phase is ExportPhase.Failed) {
                        Text(
                            text = "Export didn't finish. Pick a destination to try again.",
                            style = AppTheme.typography.bodySecondary,
                            color = AppTheme.colors.danger,
                        )
                        Spacer(Modifier.height(AppTheme.spacing.md))
                    }

                    Eyebrow("Format")
                    Spacer(Modifier.height(AppTheme.spacing.xs))
                    SegmentedControl(
                        options = listOf(ExportFormat.PDF, ExportFormat.JPEG),
                        selected = sheet.format,
                        onSelect = { onEvent(DocumentEvent.FormatSelected(it)) },
                        label = { it.name },
                    )

                    Spacer(Modifier.height(AppTheme.spacing.md))

                    Eyebrow("Attachment size")
                    Spacer(Modifier.height(AppTheme.spacing.xs))
                    SegmentedControl(
                        options = AttachmentSize.entries,
                        selected = sheet.size,
                        onSelect = { onEvent(DocumentEvent.SizeSelected(it)) },
                        label = { it.label },
                    )
                    Spacer(Modifier.height(AppTheme.spacing.xs))
                    Text(
                        text = sheet.estimatedBytes?.let { "About ${it.asReadableSize()}" }
                            ?: "Estimating size\u2026",
                        style = AppTheme.typography.meta,
                        color = AppTheme.colors.inkTertiary,
                    )

                    Spacer(Modifier.height(AppTheme.spacing.lg))
                    Hairline()
                    Spacer(Modifier.height(AppTheme.spacing.xs))

                    TargetRow(
                        icon = Icons.Outlined.Email,
                        title = "Email to myself",
                        subtitle = "Opens your mail app with the file attached",
                        onClick = {
                            onEvent(DocumentEvent.TargetSelected(ExportTarget.EmailToMyself))
                        },
                    )
                    TargetRow(
                        icon = Icons.Outlined.IosShare,
                        title = "Send as ${sheet.format.name}",
                        subtitle = "Share to any app on your phone",
                        onClick = { onEvent(DocumentEvent.TargetSelected(ExportTarget.Share)) },
                    )
                    TargetRow(
                        icon = Icons.Outlined.Print,
                        title = "Print",
                        subtitle = "Send a PDF to a nearby or cloud printer",
                        onClick = {
                            onEvent(DocumentEvent.TargetSelected(ExportTarget.CloudPrint))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    PremiumCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppTheme.spacing.xxs),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(AppTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.colors.accent,
                modifier = Modifier.width(24.dp),
            )
            Spacer(Modifier.width(AppTheme.spacing.md))
            Column {
                Text(
                    text = title,
                    style = AppTheme.typography.titleCard,
                    color = AppTheme.colors.inkPrimary,
                )
                Text(
                    text = subtitle,
                    style = AppTheme.typography.bodySecondary,
                    color = AppTheme.colors.inkTertiary,
                )
            }
        }
    }
}
