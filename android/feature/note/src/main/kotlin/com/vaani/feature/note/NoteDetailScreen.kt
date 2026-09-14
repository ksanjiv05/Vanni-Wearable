package com.vaani.feature.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaani.core.designsystem.component.ChipVariant
import com.vaani.core.designsystem.component.HairlineDivider
import com.vaani.core.designsystem.component.StatusChip
import com.vaani.core.designsystem.component.VaaniCheckbox
import com.vaani.core.designsystem.component.VaaniTopBar
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.MonoStyle
import com.vaani.core.designsystem.theme.VaaniPalette
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme
import com.vaani.core.ui.SectionHeader

@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NoteDetailContent(
        state = state,
        onBack = onBack,
        onToggleTodo = viewModel::toggleTodo,
        onTogglePlay = viewModel::togglePlay,
        modifier = modifier,
    )
}

@Composable
internal fun NoteDetailContent(
    state: NoteDetailUiState,
    onBack: () -> Unit,
    onToggleTodo: (String) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    Column(modifier.fillMaxSize().background(colors.background)) {
        VaaniTopBar(
            title = "",
            leadingIcon = VaaniIcon.ChevronLeft,
            onLeadingClick = onBack,
            trailingIcon = VaaniIcon.MoreH,
            onTrailingClick = {},
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = VaaniSpacing.screenH,
                end = VaaniSpacing.screenH,
                bottom = VaaniSpacing.xxl,
            ),
        ) {
            item {
                Text(state.title, color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Text(state.meta, color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Row(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.tags.forEach { TagChip(it) }
                }
            }

            item { SummaryCard(state.summary) }

            if (state.keyPoints.isNotEmpty()) {
                item { SectionHeader("Key Points", Modifier.padding(top = 8.dp)) }
                items(state.keyPoints, key = { it.id }) { KeyPointRowView(it) }
            }

            if (state.todos.isNotEmpty()) {
                item { SectionHeader("To-dos", Modifier.padding(top = 8.dp)) }
                items(state.todos, key = { it.id }) { TodoRowView(it, onToggleTodo) }
            }

            if (state.transcript.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader("Transcript")
                        Box(Modifier.weight(1f))
                        Text(state.transcriptMeta, color = colors.muted, fontSize = 12.sp)
                    }
                }
                items(state.transcript, key = { it.id }) { TranscriptRowView(it) }
            }
        }

        AudioPlayerBar(state.player, onTogglePlay)
    }
}

private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.items(
    list: List<T>,
    noinline key: (T) -> Any,
    crossinline itemContent: @Composable (T) -> Unit,
) = items(count = list.size, key = { key(list[it]) }) { itemContent(list[it]) }

@Composable
private fun TagChip(label: String) {
    val colors = VaaniTheme.colors
    Box(
        Modifier
            .background(colors.surface)
            .border(VaaniSpacing.hairline, colors.hairline)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = colors.slate, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SummaryCard(summary: String) {
    val colors = VaaniTheme.colors
    Row(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(colors.surface),
    ) {
        Box(Modifier.width(VaaniSpacing.accentBar).fillMaxHeight().background(colors.coffee))
        Column(Modifier.padding(VaaniSpacing.lg)) {
            SectionHeader("Summary")
            Text(summary, color = colors.secondary, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun KeyPointRowView(row: KeyPointRow) {
    val colors = VaaniTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().background(colors.surface).padding(VaaniSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(colors.slate))
            Text(
                row.text,
                color = colors.ink,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Text(row.seekLabel, color = colors.muted, style = MonoStyle)
        }
        HairlineDivider()
    }
}

@Composable
private fun TodoRowView(row: TodoRow, onToggle: (String) -> Unit) {
    val colors = VaaniTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = VaaniSpacing.md, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 48dp touch target holding a sharp square checkbox
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                VaaniCheckbox(checked = row.done, onCheckedChange = { onToggle(row.id) }, boxSize = 26.dp)
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    row.text,
                    color = if (row.done) colors.muted else colors.ink,
                    fontSize = 15.sp,
                    fontWeight = if (row.done) FontWeight.Normal else FontWeight.SemiBold,
                )
                if (row.subtitle.isNotEmpty()) {
                    Text(row.subtitle, color = colors.muted, fontSize = 12.sp)
                }
            }
        }
        HairlineDivider()
    }
}

@Composable
private fun TranscriptRowView(row: TranscriptRow) {
    val colors = VaaniTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SpeakerTag(row.speaker, row.speakerVariant)
        Text(
            row.text,
            color = colors.ink,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Text(row.timeLabel, color = colors.muted, style = MonoStyle, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SpeakerTag(label: String, variant: ChipVariant) {
    val colors = VaaniTheme.colors
    val bg = when (variant) {
        ChipVariant.Coffee -> colors.coffee
        ChipVariant.Slate -> colors.slate
        ChipVariant.Sunken -> colors.coffeeLo
        ChipVariant.Sage -> colors.success
    }
    Box(Modifier.size(28.dp).background(bg), contentAlignment = Alignment.Center) {
        Text(label, color = colors.onCoffee, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AudioPlayerBar(player: PlayerState, onTogglePlay: () -> Unit) {
    val colors = VaaniTheme.colors
    Column(Modifier.fillMaxWidth().background(VaaniPalette.Ink).padding(VaaniSpacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(colors.coffee)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center,
            ) {
                VaaniIconView(
                    VaaniIcon.Play,
                    tint = colors.onCoffee,
                    size = 22.dp,
                )
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                // scrubber
                Box(Modifier.fillMaxWidth().height(4.dp).background(VaaniPalette.DarkRaised)) {
                    Box(Modifier.fillMaxWidth(player.progress).height(4.dp).background(colors.coffeeHi))
                    Box(
                        Modifier
                            .padding(start = 0.dp)
                            .fillMaxWidth(player.progress)
                            .height(4.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Box(Modifier.size(10.dp).background(VaaniPalette.Cream))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(player.positionLabel, color = VaaniPalette.Muted, style = MonoStyle)
                    Box(Modifier.weight(1f))
                    Text(player.durationLabel, color = VaaniPalette.Muted, style = MonoStyle)
                }
            }
        }
    }
}
