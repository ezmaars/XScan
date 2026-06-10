package com.xscan.app.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xscan.app.domain.model.ScanDocument
import com.xscan.app.ui.components.Eyebrow
import com.xscan.app.ui.components.GhostButton
import com.xscan.app.ui.components.IconActionButton
import com.xscan.app.ui.components.PremiumCard
import com.xscan.app.ui.components.PrimaryButton
import com.xscan.app.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ───────────────────────────── Search ──────────────────────────────── */

@Composable
fun LibrarySearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppTheme.shapes.control)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.colors.surface)
            .border(AppTheme.shapes.hairline, AppTheme.colors.divider, shape)
            .padding(horizontal = AppTheme.spacing.md, vertical = AppTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = AppTheme.colors.inkTertiary,
            modifier = Modifier.width(20.dp),
        )
        Spacer(Modifier.width(AppTheme.spacing.sm))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search documents",
                    style = AppTheme.typography.body,
                    color = AppTheme.colors.inkTertiary,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                textStyle = AppTheme.typography.body.copy(color = AppTheme.colors.inkPrimary),
                cursorBrush = SolidColor(AppTheme.colors.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconActionButton(
                icon = Icons.Outlined.Close,
                contentDescription = "Clear search",
                onClick = { onQueryChanged("") },
            )
        }
    }
}

/* ─────────────────────────── Document card ─────────────────────────── */

private val cardDate = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

private fun Long.asReadableSize(): String = when {
    this >= 1 shl 20 -> "%.1f MB".format(this / 1048576f)
    this >= 1 shl 10 -> "%d KB".format(this / 1024)
    else -> "$this B"
}

@Composable
fun DocumentCard(
    document: ScanDocument,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    PremiumCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(AppTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover thumbnail — portrait, like a filed page.
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(AppTheme.shapes.control - 6.dp))
                    .background(AppTheme.colors.surface)
                    .border(
                        AppTheme.shapes.hairline,
                        AppTheme.colors.divider,
                        RoundedCornerShape(AppTheme.shapes.control - 6.dp),
                    ),
            ) {
                AsyncImage(
                    model = document.coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                )
            }

            Spacer(Modifier.width(AppTheme.spacing.md))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow(
                    text = "${document.pageCount} ${if (document.pageCount == 1) "page" else "pages"}",
                )
                Text(
                    text = document.name,
                    style = AppTheme.typography.titleCard,
                    color = AppTheme.colors.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${cardDate.format(Date(document.updatedAtMillis))} · " +
                        document.sizeBytes.asReadableSize(),
                    style = AppTheme.typography.meta,
                    color = AppTheme.colors.inkTertiary,
                )
            }

            Box {
                IconActionButton(
                    icon = Icons.Outlined.MoreVert,
                    contentDescription = "Document actions",
                    onClick = { menuOpen = true },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename", style = AppTheme.typography.label) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Delete",
                                style = AppTheme.typography.label,
                                color = AppTheme.colors.danger,
                            )
                        },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/* ─────────────────────────── Rename dialog ─────────────────────────── */

@Composable
fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        PremiumCard {
            Column(Modifier.padding(AppTheme.spacing.lg)) {
                Eyebrow("Rename document")
                Spacer(Modifier.height(AppTheme.spacing.md))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppTheme.shapes.control))
                        .background(AppTheme.colors.surface)
                        .padding(AppTheme.spacing.md),
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        textStyle = AppTheme.typography.body
                            .copy(color = AppTheme.colors.inkPrimary),
                        cursorBrush = SolidColor(AppTheme.colors.accent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(AppTheme.spacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.sm)) {
                    GhostButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = "Save",
                        onClick = { onConfirm(name.trim()) },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
