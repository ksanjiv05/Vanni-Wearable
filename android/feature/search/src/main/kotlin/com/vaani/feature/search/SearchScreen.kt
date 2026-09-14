package com.vaani.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaani.core.designsystem.component.HairlineDivider
import com.vaani.core.designsystem.component.StatusChip
import com.vaani.core.designsystem.component.ChipVariant
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.MonoStyle
import com.vaani.core.designsystem.theme.OverlineStyle
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme

@Composable
fun SearchScreen(
    onOpenNote: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onClear = viewModel::clearQuery,
        onToggleFilter = viewModel::toggleFilter,
        onOpenNote = onOpenNote,
        modifier = modifier,
    )
}

@Composable
internal fun SearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onToggleFilter: (String) -> Unit,
    onOpenNote: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    Column(modifier.fillMaxSize().background(colors.background)) {
        Text(
            "Search",
            color = colors.ink,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(horizontal = VaaniSpacing.screenH, vertical = VaaniSpacing.md),
        )
        SearchField(state.query, onQueryChange, onClear)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = VaaniSpacing.screenH, vertical = VaaniSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.offline) OfflineBadge()
            Box(Modifier.weight(1f))
            Text(state.resultSummary, color = colors.muted, style = MonoStyle)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = VaaniSpacing.xxl),
        ) {
            item {
                Column(Modifier.padding(horizontal = VaaniSpacing.screenH)) {
                    Text("FILTERS", style = OverlineStyle, color = colors.muted, modifier = Modifier.padding(vertical = 8.dp))
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = VaaniSpacing.screenH),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.filters.size, key = { state.filters[it].label }) { i ->
                        FilterChip(state.filters[i], onToggleFilter)
                    }
                }
                Text(
                    "TOP MATCHES",
                    style = OverlineStyle,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = VaaniSpacing.screenH, vertical = 12.dp),
                )
            }
            items(state.results.size, key = { state.results[it].id }) { i ->
                ResultRow(state.results[i], onOpenNote)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    val colors = VaaniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = VaaniSpacing.screenH)
            .background(colors.surface)
            .border(VaaniSpacing.hairline, colors.hairline)
            .padding(horizontal = VaaniSpacing.md, vertical = VaaniSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VaaniIconView(VaaniIcon.Search, tint = colors.muted, size = 20.dp)
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = colors.ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.coffee),
            modifier = Modifier.weight(1f).padding(start = 10.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Search notes, people, to-dos…", color = colors.muted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Box(Modifier.size(28.dp).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                VaaniIconView(VaaniIcon.Close, tint = colors.muted, size = 18.dp)
            }
        }
    }
}

@Composable
private fun OfflineBadge() {
    val colors = VaaniTheme.colors
    Row(
        Modifier.background(colors.surface).border(VaaniSpacing.hairline, colors.hairline).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VaaniIconView(VaaniIcon.Zap, tint = colors.success, size = 14.dp)
        Text("Works offline", color = colors.secondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FilterChip(chip: FilterChipState, onToggle: (String) -> Unit) {
    val colors = VaaniTheme.colors
    val bg = if (chip.active) colors.coffee else colors.surface
    val fg = if (chip.active) colors.onCoffee else colors.secondary
    Box(
        Modifier
            .background(bg)
            .then(if (chip.active) Modifier else Modifier.border(VaaniSpacing.hairline, colors.hairline))
            .clickable { onToggle(chip.label) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(chip.label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ResultRow(result: SearchResult, onOpenNote: (String) -> Unit) {
    val colors = VaaniTheme.colors
    val variant = when (result.kind) {
        ResultKind.SUMMARY -> ChipVariant.Slate
        ResultKind.TRANSCRIPT -> ChipVariant.Coffee
        ResultKind.KEY_POINT -> ChipVariant.Slate
        ResultKind.TODO -> ChipVariant.Sage
    }
    Column {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .clickable(enabled = result.noteId.isNotEmpty()) { onOpenNote(result.noteId) }
                .padding(VaaniSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(result.kindLabel, variant)
                Box(Modifier.weight(1f))
                Text(result.score, color = colors.secondary, style = MonoStyle, fontWeight = FontWeight.Bold)
            }
            Text(
                result.title,
                color = colors.ink,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                result.snippet,
                color = colors.secondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Box(Modifier.weight(1f))
                Text(result.timeLabel, color = colors.muted, style = MonoStyle)
            }
        }
        HairlineDivider()
    }
}
