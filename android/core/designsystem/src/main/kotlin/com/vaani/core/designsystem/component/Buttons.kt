package com.vaani.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme

/** Primary button: coffee fill, cream label, sharp corners. */
@Composable
fun VaaniButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = VaaniTheme.colors
    Box(
        modifier = modifier
            .background(if (enabled) colors.coffee else colors.muted, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = VaaniSpacing.lg, vertical = VaaniSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.onCoffee, fontWeight = FontWeight.SemiBold)
    }
}

/** Secondary/outline button: surface fill + hairline stroke, sharp corners. */
@Composable
fun VaaniOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    strokeColor: Color? = null,
) {
    val colors = VaaniTheme.colors
    Box(
        modifier = modifier
            .background(colors.surface, RectangleShape)
            .border(BorderStroke(VaaniSpacing.hairline, strokeColor ?: colors.hairline), RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = VaaniSpacing.lg, vertical = VaaniSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = strokeColor ?: colors.ink, fontWeight = FontWeight.Medium)
    }
}
