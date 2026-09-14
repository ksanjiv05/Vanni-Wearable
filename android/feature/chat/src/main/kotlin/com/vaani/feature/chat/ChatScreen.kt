package com.vaani.feature.chat

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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.MonoStyle
import com.vaani.core.designsystem.theme.OverlineStyle
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatContent(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onSend = viewModel::onSend,
        modifier = modifier,
    )
}

@Composable
internal fun ChatContent(
    state: ChatUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    Column(modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = VaaniSpacing.screenH, vertical = VaaniSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
                VaaniIconView(VaaniIcon.ChevronLeft, tint = colors.ink)
            }
            Text("Ask your notes", color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(VaaniSpacing.screenH),
            verticalArrangement = Arrangement.spacedBy(VaaniSpacing.md),
        ) {
            items(state.messages.size, key = { state.messages[it].id }) { i ->
                when (val m = state.messages[i]) {
                    is ChatMessage.User -> UserBubble(m.text)
                    is ChatMessage.Answer -> AnswerCard(m)
                }
            }
            if (state.followUps.isNotEmpty()) {
                item {
                    Text("FOLLOW-UP", style = OverlineStyle, color = colors.muted, modifier = Modifier.padding(top = 8.dp))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.followUps.forEach { FollowUpChip(it) }
                    }
                }
            }
        }

        InputBar(state.input, onInputChange, onSend)
    }
}

@Composable
private fun UserBubble(text: String) {
    val colors = VaaniTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .wrapContentWidth(Alignment.End)
                .background(colors.coffee)
                .padding(horizontal = VaaniSpacing.md, vertical = VaaniSpacing.md),
        ) {
            Text(text, color = colors.onCoffee, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AnswerCard(answer: ChatMessage.Answer) {
    val colors = VaaniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(colors.surface),
    ) {
        Box(Modifier.width(VaaniSpacing.accentBar).fillMaxHeight().background(colors.coffee))
        Column(Modifier.padding(VaaniSpacing.lg)) {
            Text("FROM YOUR NOTES", style = OverlineStyle, color = colors.muted)
            Text(answer.lead, color = colors.ink, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
            answer.points.forEachIndexed { i, p ->
                Row(Modifier.padding(top = 8.dp)) {
                    Text("${i + 1}. ", color = colors.secondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        buildTextWithCite(p.text, p.citation),
                        color = colors.secondary,
                        fontSize = 15.sp,
                    )
                }
            }
            Text("SOURCES", style = OverlineStyle, color = colors.muted, modifier = Modifier.padding(top = 14.dp))
            answer.citations.forEach { c ->
                CitationChip(c)
            }
        }
    }
}

private fun buildTextWithCite(text: String, cite: String): String = "$text  [$cite]"

@Composable
private fun CitationChip(c: Citation) {
    val colors = VaaniTheme.colors
    val barColor = when (c.variant) {
        com.vaani.core.designsystem.component.ChipVariant.Coffee -> colors.coffee
        com.vaani.core.designsystem.component.ChipVariant.Slate -> colors.slate
        com.vaani.core.designsystem.component.ChipVariant.Sage -> colors.success
        com.vaani.core.designsystem.component.ChipVariant.Sunken -> colors.coffeeLo
    }
    Row(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(colors.sunken)
            .clickable {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(VaaniSpacing.accentBar).fillMaxHeight().background(barColor))
        Box(
            Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp).background(barColor).padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(c.badge, color = colors.onCoffee, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(c.note, color = colors.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text(c.seekLabel, color = colors.secondary, style = MonoStyle, modifier = Modifier.padding(end = VaaniSpacing.md))
    }
}

@Composable
private fun FollowUpChip(label: String) {
    val colors = VaaniTheme.colors
    Box(
        Modifier
            .background(colors.surface)
            .border(VaaniSpacing.hairline, colors.hairline)
            .clickable {}
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InputBar(input: String, onInputChange: (String) -> Unit, onSend: () -> Unit) {
    val colors = VaaniTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.background).padding(VaaniSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VaaniSpacing.sm),
    ) {
        Row(
            Modifier
                .weight(1f)
                .background(colors.surface)
                .border(VaaniSpacing.hairline, colors.hairline)
                .padding(horizontal = VaaniSpacing.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = input,
                onValueChange = onInputChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = colors.ink, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.coffee),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (input.isEmpty()) Text("Ask anything about your notes…", color = colors.muted, fontSize = 15.sp)
                    inner()
                },
            )
        }
        Box(
            Modifier.size(48.dp).background(colors.coffee, RectangleShape).clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            VaaniIconView(VaaniIcon.ArrowUp, tint = colors.onCoffee, size = 22.dp)
        }
    }
}
