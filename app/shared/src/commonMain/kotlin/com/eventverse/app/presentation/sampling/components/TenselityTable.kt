package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.FactorySizePresets
import com.eventverse.app.domain.sampling.TenselityEntry
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun TenselityTable(
    entries: List<TenselityEntry>,
    isEditable: Boolean = true,
    onEntriesChanged: (List<TenselityEntry>) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentEntries = remember(entries) {
        entries.ifEmpty { FactorySizePresets.DEFAULT_TENSELITY }
    }

    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconRuler(modifier = Modifier.size(16.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "SETELAN TENSELITY RAJUT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                }
                ClayBadge(
                    text = "${currentEntries.size} Parameter",
                    tint = WeMadeColors.Primary
                )
            }

            Text(
                text = "Kerapatan rajut mesin per panel (Badan, Tangan, Kerah) untuk standarisasi susut.",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                    .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BAGIAN / PARAMETER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.weight(1.8f)
                )
                Text(
                    text = "BADAN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.weight(1.0f)
                )
                Text(
                    text = "TANGAN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.weight(1.0f)
                )
                Text(
                    text = "KERAH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.weight(1.0f)
                )
            }

            // Table Rows
            currentEntries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Text(
                        text = entry.parameter,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = WeMadeColors.OnSurface,
                        modifier = Modifier.weight(1.8f)
                    )

                    TenselityCell(
                        value = entry.body,
                        isEditable = isEditable,
                        modifier = Modifier.weight(1.0f),
                        onValueChange = { newVal ->
                            val updated = currentEntries.toMutableList()
                            updated[index] = entry.copy(body = newVal)
                            onEntriesChanged(updated)
                        }
                    )

                    TenselityCell(
                        value = entry.sleeve,
                        isEditable = isEditable,
                        modifier = Modifier.weight(1.0f),
                        onValueChange = { newVal ->
                            val updated = currentEntries.toMutableList()
                            updated[index] = entry.copy(sleeve = newVal)
                            onEntriesChanged(updated)
                        }
                    )

                    TenselityCell(
                        value = entry.collar,
                        isEditable = isEditable,
                        modifier = Modifier.weight(1.0f),
                        onValueChange = { newVal ->
                            val updated = currentEntries.toMutableList()
                            updated[index] = entry.copy(collar = newVal)
                            onEntriesChanged(updated)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TenselityCell(
    value: String,
    isEditable: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isEditable) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.height(42.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeMadeColors.Primary,
                unfocusedBorderColor = WeMadeColors.Border,
                focusedContainerColor = WeMadeColors.Surface,
                unfocusedContainerColor = WeMadeColors.SurfaceMuted
            ),
            shape = ClayShapes.Tile
        )
    } else {
        Box(
            modifier = modifier
                .height(36.dp)
                .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
                .padding(horizontal = ClaySpacing.Xs),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = value.ifBlank { "-" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
        }
    }
}
