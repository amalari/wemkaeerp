package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.crm.CrmViewMode
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Toggle switcher antara mode tampilan Kanban Board ⊞ dan Tabel / List ☰.
 */
@Composable
fun CrmViewToggle(
    currentMode: CrmViewMode,
    onModeChange: (CrmViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Hairline
            )
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayButton(
            text = "⊞ Kanban",
            style = if (currentMode == CrmViewMode.KANBAN) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
            fontSize = 12.sp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            onClick = { onModeChange(CrmViewMode.KANBAN) }
        )
        ClayButton(
            text = "☰ List",
            style = if (currentMode == CrmViewMode.LIST) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
            fontSize = 12.sp,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            onClick = { onModeChange(CrmViewMode.LIST) }
        )
    }
}
