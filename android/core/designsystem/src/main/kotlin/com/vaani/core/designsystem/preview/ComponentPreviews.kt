package com.vaani.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaani.core.designsystem.component.ChipVariant
import com.vaani.core.designsystem.component.HairlineDivider
import com.vaani.core.designsystem.component.StatusChip
import com.vaani.core.designsystem.component.VaaniButton
import com.vaani.core.designsystem.component.VaaniCard
import com.vaani.core.designsystem.component.VaaniCheckbox
import com.vaani.core.designsystem.component.VaaniOutlineButton
import com.vaani.core.designsystem.component.VaaniToggle
import androidx.compose.material3.Text
import com.vaani.core.designsystem.theme.VaaniTheme

/** Light + dark preview matrix for the core design-system components. */
@Preview(name = "Components · Light", showBackground = true, widthDp = 360)
@Preview(name = "Components · Dark", showBackground = true, widthDp = 360, uiMode = 0x20)
@Composable
private fun ComponentGalleryPreview() {
    VaaniTheme {
        var checked by remember { mutableStateOf(true) }
        var toggled by remember { mutableStateOf(true) }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Status chips", color = VaaniTheme.colors.ink)
            StatusChip("Ready", ChipVariant.Sage)
            StatusChip("Transcribing 62%", ChipVariant.Coffee)
            StatusChip("Syncing", ChipVariant.Slate)
            StatusChip("Queued", ChipVariant.Sunken)
            StatusChip("Failed", ChipVariant.Danger)

            HairlineDivider()

            VaaniButton("Primary", onClick = {}, modifier = Modifier.fillMaxWidth())
            VaaniOutlineButton("Secondary", onClick = {}, modifier = Modifier.fillMaxWidth())

            Column {
                VaaniCard {
                    Text("Card with coffee accent", color = VaaniTheme.colors.ink)
                }
            }

            VaaniCheckbox(checked = checked, onCheckedChange = { checked = it })
            VaaniToggle(checked = toggled, onCheckedChange = { toggled = it })
        }
    }
}
