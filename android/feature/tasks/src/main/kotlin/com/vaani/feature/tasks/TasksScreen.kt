package com.vaani.feature.tasks

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaani.core.designsystem.component.HairlineDivider
import com.vaani.core.designsystem.component.VaaniCheckbox
import com.vaani.core.designsystem.theme.MonoStyle
import com.vaani.core.designsystem.theme.OverlineStyle
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme

@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TasksContent(
        state = state,
        onFilter = viewModel::setFilter,
        onToggle = viewModel::toggle,
        modifier = modifier,
    )
}

@Composable
internal fun TasksContent(
    state: TasksUiState,
    onFilter: (TaskFilter) -> Unit,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    Column(modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = VaaniSpacing.screenH, vertical = VaaniSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tasks", color = colors.ink, style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f))
            Text("${state.openCount} open", color = colors.muted, style = MaterialTheme.typography.bodySmall)
        }

        SegmentedControl(state.filter, onFilter)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = VaaniSpacing.screenH,
                end = VaaniSpacing.screenH,
                top = VaaniSpacing.md,
                bottom = VaaniSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(VaaniSpacing.sm),
        ) {
            state.groups.forEach { group ->
                item(key = "h-${group.header}") { GroupHeader(group.header, group.danger) }
                items(group.rows, key = { it.id }) { row -> TaskRowView(row, onToggle) }
            }
        }
    }
}

@Composable
private fun SegmentedControl(selected: TaskFilter, onFilter: (TaskFilter) -> Unit) {
    val colors = VaaniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = VaaniSpacing.screenH)
            .border(VaaniSpacing.hairline, colors.hairline),
    ) {
        Segment("Open", selected == TaskFilter.OPEN, Modifier.weight(1f)) { onFilter(TaskFilter.OPEN) }
        Segment("All", selected == TaskFilter.ALL, Modifier.weight(1f)) { onFilter(TaskFilter.ALL) }
        Segment("Done", selected == TaskFilter.DONE, Modifier.weight(1f)) { onFilter(TaskFilter.DONE) }
    }
}

@Composable
private fun Segment(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = VaaniTheme.colors
    Box(
        modifier
            .background(if (active) colors.coffee else colors.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) colors.onCoffee else colors.secondary,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun GroupHeader(header: String, danger: Boolean) {
    val colors = VaaniTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = VaaniSpacing.sm)) {
        Text(header, style = OverlineStyle, color = if (danger) colors.danger else colors.muted)
        Box(Modifier.padding(start = 8.dp).height(1.dp).fillMaxWidth().background(colors.hairline))
    }
}

@Composable
private fun TaskRowView(row: TaskRow, onToggle: (String) -> Unit) {
    val colors = VaaniTheme.colors
    val barColor = when (row.priority) {
        TaskPriority.HIGH -> colors.danger
        TaskPriority.MEDIUM -> colors.warning
        TaskPriority.LOW -> colors.slate
        TaskPriority.DONE -> colors.coffee
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(colors.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(VaaniSpacing.accentBar).fillMaxHeight().background(barColor))
        // 48dp touch target holding the sharp square checkbox
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            VaaniCheckbox(checked = row.done, onCheckedChange = { onToggle(row.id) }, boxSize = 24.dp)
        }
        Column(Modifier.weight(1f).padding(vertical = VaaniSpacing.md)) {
            Text(
                row.text,
                color = if (row.done) colors.muted else colors.ink,
                fontWeight = if (row.done) FontWeight.Normal else FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
            Row(modifier = Modifier.padding(top = 2.dp)) {
                Text("from ", color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
                Text(row.sourceNote, color = colors.secondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            }
            Text(row.subtitle, color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
        }
        Column(
            Modifier.padding(end = VaaniSpacing.md),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(row.seekLabel, color = colors.muted, style = MonoStyle)
            PriorityChip(row.priorityLabel, row.priority)
        }
    }
    HairlineDivider()
}

@Composable
private fun PriorityChip(label: String, priority: TaskPriority) {
    val colors = VaaniTheme.colors
    val (bg, fg) = when (priority) {
        TaskPriority.HIGH -> colors.danger to colors.onCoffee
        TaskPriority.MEDIUM -> colors.warning to colors.ink
        TaskPriority.LOW -> colors.slate to colors.onCoffee
        TaskPriority.DONE -> colors.slate to colors.onCoffee
    }
    Box(Modifier.background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, color = fg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
