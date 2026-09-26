package com.eventverse.app.presentation.techpack.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.techpack.TechPack
import com.eventverse.app.domain.techpack.TechPackId
import com.eventverse.app.domain.techpack.TechPackStatus
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.techpack.TechPackUiEvent
import com.eventverse.app.presentation.techpack.TechPackUiState
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun TechPackListPanel(
    state: TechPackUiState,
    onEvent: (TechPackUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxHeight(),
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Input
            ClayTextField(
                value = state.searchQuery,
                onValueChange = { onEvent(TechPackUiEvent.UpdateSearchQuery(it)) },
                placeholder = "Cari style, client, SPK...",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

            // Status Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusFilterChip(
                    label = "Semua",
                    isSelected = state.selectedStatusFilter == null,
                    onClick = { onEvent(TechPackUiEvent.SetStatusFilter(null)) }
                )
                StatusFilterChip(
                    label = "Draft",
                    isSelected = state.selectedStatusFilter == TechPackStatus.DRAFT,
                    onClick = { onEvent(TechPackUiEvent.SetStatusFilter(TechPackStatus.DRAFT)) }
                )
                StatusFilterChip(
                    label = "Rilis",
                    isSelected = state.selectedStatusFilter == TechPackStatus.RELEASED,
                    onClick = { onEvent(TechPackUiEvent.SetStatusFilter(TechPackStatus.RELEASED)) }
                )
                StatusFilterChip(
                    label = "Superseded",
                    isSelected = state.selectedStatusFilter == TechPackStatus.SUPERSEDED,
                    onClick = { onEvent(TechPackUiEvent.SetStatusFilter(TechPackStatus.SUPERSEDED)) }
                )
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            // Tech Pack Cards List
            val items = state.filteredTechPacks
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        Text(
                            text = "Belum ada Tech Pack",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Text(
                            text = "Buat draft baru atau impor dari SPK Sampling",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    items(items, key = { it.id.value }) { techPack ->
                        TechPackItemCard(
                            techPack = techPack,
                            isSelected = techPack.id == state.selectedTechPackId,
                            onClick = { onEvent(TechPackUiEvent.SelectTechPack(techPack.id)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .claySurface(
                shape = ClayShapes.Pill,
                background = if (isSelected) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                outline = if (isSelected) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                offset = if (isSelected) ClayOffset.Small else ClayOffset.Flat,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Xs)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) androidx.compose.ui.graphics.Color.White else WeMadeColors.OnSurfaceMuted
        )
    }
}

@Composable
private fun TechPackItemCard(
    techPack: TechPack,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .claySurface(
                shape = ClayShapes.Tile,
                background = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
                offset = if (isSelected) ClayOffset.Rest else ClayOffset.Small,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = techPack.styleCode.value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayBadge(
                        text = "v${techPack.version}",
                        tint = WeMadeColors.Secondary
                    )
                    StatusBadge(status = techPack.status)
                }
            }

            Text(
                text = techPack.styleName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (techPack.clientName.isNotBlank()) {
                Text(
                    text = techPack.clientName,
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (techPack.sourceSpkNumber.isNotBlank()) {
                    Text(
                        text = "SPK: ${techPack.sourceSpkNumber}",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    Text(
                        text = "${techPack.bomLines.size} Bahan",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "${techPack.laborOperations.size} Op",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: TechPackStatus) {
    val tint = when (status) {
        TechPackStatus.DRAFT -> WeMadeColors.Warning
        TechPackStatus.RELEASED -> WeMadeColors.Success
        TechPackStatus.SUPERSEDED -> WeMadeColors.OnSurfaceMuted
        TechPackStatus.ARCHIVED -> WeMadeColors.Error
    }
    ClayBadge(text = status.displayName, tint = tint)
}
