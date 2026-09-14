package com.vaani.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme

/** Sharp square checkbox. Done = coffee fill + drawn checkmark. */
@Composable
fun VaaniCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    boxSize: Dp = 24.dp,
) {
    val colors = VaaniTheme.colors
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(boxSize)
            .background(if (checked) colors.coffee else colors.surface, RectangleShape)
            .border(BorderStroke(VaaniSpacing.hairline, if (checked) colors.coffee else colors.muted), RectangleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(Modifier.size(boxSize)) {
                val w = size.minDimension
                val sw = w * 0.09f
                drawLine(colors.onCoffee, Offset(w * 0.25f, w * 0.52f), Offset(w * 0.43f, w * 0.70f), sw, StrokeCap.Butt)
                drawLine(colors.onCoffee, Offset(w * 0.43f, w * 0.70f), Offset(w * 0.76f, w * 0.32f), sw, StrokeCap.Butt)
            }
        }
    }
}

/** Square track + square knob toggle (coffee on / sunken off). No rounding. */
@Composable
fun VaaniToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(width = 48.dp, height = 26.dp)
            .background(if (checked) colors.coffee else colors.sunken, RectangleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(20.dp)
                .background(if (checked) colors.onCoffee else colors.surface, RectangleShape),
        )
    }
}
