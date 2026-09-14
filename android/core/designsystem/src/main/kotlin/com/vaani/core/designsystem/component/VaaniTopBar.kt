package com.vaani.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme

/**
 * App top bar. Supports an optional leading (back) icon, a title, and an
 * optional trailing icon (kebab) plus an arbitrary trailing slot (e.g. the
 * "Synced 2m" pill on Library).
 */
@Composable
fun VaaniTopBar(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: VaaniIcon? = null,
    onLeadingClick: (() -> Unit)? = null,
    trailingIcon: VaaniIcon? = null,
    onTrailingClick: (() -> Unit)? = null,
    trailingSlot: (@Composable () -> Unit)? = null,
    largeTitle: Boolean = false,
) {
    val colors = VaaniTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = VaaniSpacing.screenH, vertical = VaaniSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clickable(enabled = onLeadingClick != null) { onLeadingClick?.invoke() },
                contentAlignment = Alignment.CenterStart,
            ) {
                VaaniIconView(leadingIcon, tint = colors.ink)
            }
        }
        Text(
            text = title,
            color = colors.ink,
            fontWeight = FontWeight.Bold,
            fontSize = if (largeTitle) 30.sp else 20.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailingSlot != null) trailingSlot()
        if (trailingIcon != null) {
            Box(
                Modifier
                    .size(40.dp)
                    .clickable(enabled = onTrailingClick != null) { onTrailingClick?.invoke() },
                contentAlignment = Alignment.CenterEnd,
            ) {
                VaaniIconView(trailingIcon, tint = colors.ink)
            }
        }
    }
}

/** The "Synced 2m" pill: surface + hairline stroke + sage square dot. */
@Composable
fun SyncedPill(label: String, modifier: Modifier = Modifier) {
    val colors = VaaniTheme.colors
    Row(
        modifier = modifier
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(10.dp).background(colors.success))
        Text(label, color = colors.secondary, fontSize = 13.sp)
    }
}
