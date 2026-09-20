package com.vaani.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaani.core.designsystem.component.ChipVariant
import com.vaani.core.designsystem.component.StatusChip
import com.vaani.core.designsystem.component.SyncedPill
import com.vaani.core.designsystem.component.VaaniCard
import com.vaani.core.designsystem.component.VaaniTopBar
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.MonoStyle
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniPalette
import com.vaani.core.designsystem.theme.VaaniTheme
import com.vaani.core.ui.SectionHeader

@Composable
fun LibraryScreen(
    onNoteClick: (String) -> Unit,
    onDeviceClick: () -> Unit,
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryContent(
        state = state,
        onNoteClick = onNoteClick,
        onDeviceClick = onDeviceClick,
        onChatClick = onChatClick,
        onRetry = viewModel::retry,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    onNoteClick: (String) -> Unit,
    onDeviceClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onRetry: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    // Recording id pending a delete confirmation (null = no dialog).
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().background(colors.background)) {
        VaaniTopBar(
            title = "Library",
            largeTitle = true,
            leadingIcon = VaaniIcon.Bluetooth,
            onLeadingClick = onDeviceClick,
            trailingIcon = VaaniIcon.Zap,
            onTrailingClick = onChatClick,
            trailingSlot = if (state.syncedLabel.isNotEmpty()) {
                { SyncedPill(state.syncedLabel) }
            } else null,
        )
        Text(
            text = "${state.notesCount} notes  ·  ${state.recordedLabel}",
            color = colors.muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = VaaniSpacing.screenH),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = VaaniSpacing.screenH,
                end = VaaniSpacing.screenH,
                top = VaaniSpacing.md,
                bottom = VaaniSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(VaaniSpacing.md),
        ) {
            state.sync?.let { item { SyncBanner(it) } }

            if (state.processing.isNotEmpty()) {
                item { DayGroupHeader("PROCESSING") }
                items(state.processing, key = { it.recordingId }) { row ->
                    ProcessingCard(
                        row = row,
                        onRetry = { onRetry(row.recordingId) },
                        onDelete = { confirmDelete = row.recordingId },
                    )
                }
            }

            state.groups.forEach { group ->
                item { DayGroupHeader(group.header) }
                items(group.notes, key = { it.id }) { row ->
                    NoteCard(
                        row = row,
                        onClick = { onNoteClick(row.id) },
                        onDelete = { confirmDelete = row.recordingId },
                    )
                }
            }

            if (!state.isLoading && state.processing.isEmpty() && state.groups.isEmpty()) {
                item { EmptyLibrary() }
            }
        }
    }

    confirmDelete?.let { recId ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete recording?") },
            text = { Text("This permanently removes the note, transcript and the audio file. This cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDelete(recId)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
            shape = androidx.compose.ui.graphics.RectangleShape,
        )
    }
}

@Composable
private fun EmptyLibrary() {
    val colors = VaaniTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = VaaniSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VaaniIconView(VaaniIcon.ArrowDown, tint = colors.muted, size = 28.dp)
        Text(
            "No notes yet",
            color = colors.ink,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = VaaniSpacing.md),
        )
        Text(
            "Tap the record button to import audio and transcribe it on-device.",
            color = colors.muted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp, start = VaaniSpacing.xl, end = VaaniSpacing.xl),
        )
    }
}

@Composable
private fun ProcessingCard(row: ProcessingRow, onRetry: () -> Unit, onDelete: () -> Unit) {
    val colors = VaaniTheme.colors
    VaaniCard(onClick = if (row.isFailed) onRetry else ({})) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = row.title,
                    color = colors.ink,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(32.dp).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    VaaniIconView(VaaniIcon.Trash, tint = colors.muted, size = 18.dp)
                }
            }
            Text(
                text = row.meta,
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(row.statusLabel, row.statusVariant)
            }
        }
    }
}

@Composable
private fun DayGroupHeader(header: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(header)
        Box(
            Modifier
                .padding(start = 8.dp)
                .height(1.dp)
                .fillMaxWidth()
                .background(VaaniTheme.colors.hairline),
        )
    }
}

@Composable
private fun SyncBanner(sync: SyncBannerState) {
    val colors = VaaniTheme.colors
    Column(Modifier.fillMaxWidth().background(VaaniPalette.Ink)) {
        Row(
            Modifier.fillMaxWidth().padding(VaaniSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaaniSpacing.md),
        ) {
            Box(
                Modifier.size(36.dp).background(colors.coffee),
                contentAlignment = Alignment.Center,
            ) {
                VaaniIconView(VaaniIcon.ArrowDown, tint = colors.onCoffee, size = 20.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(sync.title, color = VaaniPalette.Cream, style = MaterialTheme.typography.titleSmall)
                Text(sync.detail, color = VaaniPalette.Muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
            }
            Text(sync.percentLabel, color = colors.coffeeHi, style = MonoStyle.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp))
        }
        // progress bar (sharp)
        Box(Modifier.fillMaxWidth().height(3.dp).background(VaaniPalette.DarkRaised)) {
            Box(
                Modifier
                    .fillMaxWidth(sync.progress)
                    .height(3.dp)
                    .background(colors.coffee),
            )
        }
    }
}

@Composable
private fun NoteCard(row: NoteRow, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = VaaniTheme.colors
    VaaniCard(onClick = onClick) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = row.title,
                    color = colors.ink,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(row.durationLabel, color = colors.muted, style = MonoStyle)
                Box(
                    Modifier.padding(start = 8.dp).size(28.dp).clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    VaaniIconView(VaaniIcon.Trash, tint = colors.muted, size = 16.dp)
                }
            }
            Text(
                text = row.snippet,
                color = colors.secondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = row.meta,
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(row.statusLabel, row.statusVariant)
                if (row.todoCount > 0) {
                    StatusChip("${row.todoCount} to-dos", ChipVariant.Sunken)
                }
            }
        }
    }
}

private fun previewState() = LibraryUiState(
    isLoading = false,
    notesCount = 3,
    recordedLabel = "0.8 h recorded",
    syncedLabel = "Synced 2m",
    sync = SyncBannerState(
        title = "Syncing from device...",
        detail = "3 recordings · 47 MB · over Wi-Fi",
        progress = 0.34f,
        percentLabel = "34%",
    ),
    groups = listOf(
        DayGroup(
            "TODAY",
            listOf(
                NoteRow("n1", "rec-n1", "Standup with Ravi & Priya", "Pushed Atlas to next sprint…",
                    "14:20  ·  3 speakers  ·  #atlas", "14:20", "Ready", ChipVariant.Sage, 2),
                NoteRow("n2", "rec-n2", "Call with vendor", "Transcribing 8 min of audio…",
                    "08:03", "08:03", "Transcribing 62%", ChipVariant.Coffee, 1),
            ),
        ),
    ),
)

@androidx.compose.ui.tooling.preview.Preview(name = "Library · Light", showBackground = true, widthDp = 360, heightDp = 720)
@androidx.compose.ui.tooling.preview.Preview(name = "Library · Dark", showBackground = true, widthDp = 360, heightDp = 720, uiMode = 0x20)
@androidx.compose.runtime.Composable
private fun LibraryContentPreview() {
    VaaniTheme {
        LibraryContent(state = previewState(), onNoteClick = {})
    }
}
