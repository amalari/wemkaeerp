package com.eventverse.app.presentation.rbac

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconUser
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.designsystem.scrollEdgeFade
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pemilih mode tampilan (Per Modul / Per Divisi / Per Jabatan). Label dikunci satu baris
 * (Kontrak 13) dan bilah dapat digeser horizontal bila lebar kontainer tak cukup, sehingga label
 * tidak pecah per suku kata di layar sempit.
 */
@Composable
internal fun RbacViewModeTabs(selected: RbacViewMode, onSelect: (RbacViewMode) -> Unit) {
    val tabShape = ClayShapes.Chip
    val scrollState = rememberScrollState()
    // Tab terpilih selalu terlihat: di layar sempit "Per Jabatan" terpotong di kanan, jadi bilah menggulir ke ujung.
    LaunchedEffect(selected) {
        scrollState.animateScrollTo(if (selected == RbacViewMode.entries.last()) scrollState.maxValue else 0)
    }
    Row(
        modifier = Modifier
            .clayFlat(shape = ClayShapes.Chip, background = WeMadeColors.SurfaceMuted, outline = WeMadeColors.Border)
            .scrollEdgeFade(scrollState, WeMadeColors.SurfaceMuted)
            .horizontalScroll(scrollState)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RbacViewMode.entries.forEach { mode ->
            val isSelected = selected == mode
            val tint = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
            Box(
                modifier = Modifier
                    .then(
                        if (isSelected) {
                            Modifier.clayFlat(
                                shape = tabShape,
                                background = WeMadeColors.Surface,
                                outline = WeMadeColors.Outline,
                                borderWidth = 1.5.dp
                            )
                        } else {
                            Modifier.clip(tabShape)
                        }
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (mode) {
                        RbacViewMode.PER_MODULE -> IconLayers(modifier = Modifier.size(14.dp), color = tint)
                        RbacViewMode.PER_DEPARTMENT -> IconPackage(modifier = Modifier.size(14.dp), color = tint)
                        RbacViewMode.PER_ROLE -> IconUser(modifier = Modifier.size(14.dp), color = tint)
                    }
                    Text(
                        text = mode.label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = tint,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
