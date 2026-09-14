package com.vaani.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.VaaniTheme

/** The five bottom-nav destinations (center is the REC action, not a tab). */
enum class VaaniNavSlot { Library, Search, Rec, Tasks, Settings }

/**
 * Bottom nav: Library · Search · [REC square coffee FAB] · Tasks · Settings.
 * Flat, hairline top divider, sharp corners. The REC slot is a coffee square
 * with a mic icon and "REC" label.
 */
@Composable
fun VaaniBottomNav(
    selected: VaaniNavSlot,
    onSelect: (VaaniNavSlot) -> Unit,
    onRec: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    Column(modifier.fillMaxWidth().background(colors.background)) {
        HairlineDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(VaaniIcon.Library, "Library", selected == VaaniNavSlot.Library) { onSelect(VaaniNavSlot.Library) }
            NavItem(VaaniIcon.Search, "Search", selected == VaaniNavSlot.Search) { onSelect(VaaniNavSlot.Search) }
            RecItem(onRec)
            NavItem(VaaniIcon.CheckSquare, "Tasks", selected == VaaniNavSlot.Tasks) { onSelect(VaaniNavSlot.Tasks) }
            NavItem(VaaniIcon.Sliders, "Settings", selected == VaaniNavSlot.Settings) { onSelect(VaaniNavSlot.Settings) }
        }
    }
}

@Composable
private fun NavItem(icon: VaaniIcon, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = VaaniTheme.colors
    val tint = if (active) colors.coffee else colors.muted
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VaaniIconView(icon, tint = tint, size = 24.dp)
        Text(label, color = tint, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun RecItem(onRec: () -> Unit) {
    val colors = VaaniTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .background(colors.coffee, RectangleShape)
                .clickable(onClick = onRec),
            contentAlignment = Alignment.Center,
        ) {
            VaaniIconView(VaaniIcon.Mic, tint = colors.onCoffee, size = 26.dp)
        }
        Text("REC", color = colors.coffee, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
