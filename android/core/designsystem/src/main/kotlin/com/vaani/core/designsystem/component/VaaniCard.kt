package com.vaani.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme

/**
 * Surface card with an optional coffee left-accent bar and a hairline bottom
 * divider. Flat & matte — no elevation/shadow (DESIGN_SYSTEM.md). The accent
 * bar stretches to the card's intrinsic height.
 */
@Composable
fun VaaniCard(
    modifier: Modifier = Modifier,
    accent: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = VaaniTheme.colors
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.surface, RectangleShape)
            .then(clickModifier),
    ) {
        if (accent) {
            Box(
                Modifier
                    .width(VaaniSpacing.accentBar)
                    .fillMaxHeight()
                    .background(colors.coffee),
            )
        }
        Box(Modifier.padding(VaaniSpacing.lg)) { content() }
    }
}

/** 1dp hairline divider in the theme hairline color. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(VaaniSpacing.hairline).background(VaaniTheme.colors.hairline))
}
