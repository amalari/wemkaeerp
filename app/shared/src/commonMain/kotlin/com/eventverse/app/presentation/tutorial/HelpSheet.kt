package com.eventverse.app.presentation.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.theme.WeMadeColors

/** Tab jendela bantuan. */
enum class HelpTab(val label: String) { TUTORIALS("Panduan"), ASK_AI("Tanya AI") }

/** Jendela bantuan dari tombol ❓: shell dengan dua tab; isinya dirender pemanggil. */
@Composable
internal fun HelpSheet(
    tab: HelpTab,
    onTabChange: (HelpTab) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable (HelpTab) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        ClayCard(modifier = Modifier.width(480.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bantuan", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface, modifier = Modifier.weight(1f))
                ClayIconButton(onClick = onDismiss) { IconClose(Modifier.size(12.dp), color = WeMadeColors.OnSurface) }
            }
            Row(modifier = Modifier.padding(vertical = ClaySpacing.Lg), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                HelpTab.entries.forEach { t ->
                    ClayButton(
                        text = t.label,
                        onClick = { onTabChange(t) },
                        style = if (t == tab) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
                        fontSize = 12.sp
                    )
                }
            }
            content(tab)
        }
    }
}
