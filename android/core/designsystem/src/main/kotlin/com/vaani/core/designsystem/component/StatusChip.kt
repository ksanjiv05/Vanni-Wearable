package com.vaani.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaani.core.designsystem.theme.VaaniTheme

/** Fill variants for status chips (DESIGN_SYSTEM.md §Components). */
enum class ChipVariant { Sage, Coffee, Slate, Sunken }

/** Solid, sharp status chip. sage=Ready, coffee=Processing, slate=Syncing, sunken=Queued. */
@Composable
fun StatusChip(
    label: String,
    variant: ChipVariant,
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    val (bg, fg) = when (variant) {
        ChipVariant.Sage -> colors.success to colors.onCoffee
        ChipVariant.Coffee -> colors.coffee to colors.onCoffee
        ChipVariant.Slate -> colors.slate to colors.onCoffee
        ChipVariant.Sunken -> colors.sunken to colors.secondary
    }
    Box(
        modifier = modifier
            .background(bg, RectangleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
