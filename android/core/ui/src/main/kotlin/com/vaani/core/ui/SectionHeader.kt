package com.vaani.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaani.core.designsystem.theme.OverlineStyle
import com.vaani.core.designsystem.theme.VaaniTheme

/** Tracked, uppercase, muted section header (e.g. TODAY, SUMMARY, KEY POINTS). */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = OverlineStyle,
        color = VaaniTheme.colors.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(vertical = 8.dp),
    )
}
