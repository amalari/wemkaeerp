package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Clock

@Composable
fun DesignerToolbar(
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = WeMadeColors.Surface,
        borderWidth = ClayBorder.Medium,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Title & Template Name
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = "Kembali",
                    onClick = onClose,
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp,
                    leading = { IconArrowBack(Modifier.size(13.dp), color = WeMadeColors.OnSurfaceMuted) }
                )

                Column {
                    Text(
                        text = state.template.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Kertas: ${state.template.paperSize.name} (${state.template.paperSize.widthMm10 / 10} × ${state.template.paperSize.heightMm10 / 10} mm)",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }

            // Center: Canvas Tool + Add Element Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pemilih alat kanvas. Dipisah dari tombol tambah elemen karena ini mengubah
                // arti tarikan mouse, bukan menambah isi kertas — dan tanpa mode Geser tidak ada
                // satu pun titik di kanvas yang bisa dipakai untuk memindahkan tampilan A4.
                Row(
                    modifier = Modifier
                        .clayFlat(
                            shape = ClayShapes.Button,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Border,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(ClaySpacing.Xxs),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CanvasTool.entries.forEach { tool ->
                        val isActive = state.canvasTool == tool
                        val tint = if (isActive) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                        Row(
                            modifier = Modifier
                                .claySurface(
                                    shape = ClayShapes.Chip,
                                    background = if (isActive) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                                    outline = if (isActive) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                                    borderWidth = ClayBorder.Hairline,
                                    offset = if (isActive) ClayOffset.Pressed else ClayOffset.Flat
                                )
                                .clickable { onEvent(TemplateDesignerUiEvent.SetCanvasTool(tool)) }
                                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (tool) {
                                CanvasTool.SELECT -> IconCursor(Modifier.size(13.dp), color = tint)
                                CanvasTool.PAN -> IconHandMove(Modifier.size(13.dp), color = tint)
                            }
                            Text(
                                text = tool.label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                }

                ClayButton(
                    text = "+ Teks",
                    onClick = {
                        val newId = "txt-${Clock.System.now().toEpochMilliseconds()}"
                        val el = TemplateElement.StaticText(
                            elementId = newId,
                            rect = TemplateRect(Mm10(200), Mm10(1000), Mm10(600), Mm10(80)),
                            text = "Teks Baru",
                            style = TextStyleSpec(fontSizePt = 10, isBold = false)
                        )
                        onEvent(TemplateDesignerUiEvent.AddElement(el))
                    },
                    style = ClayButtonStyle.Secondary,
                    fontSize = 11.sp
                )

                ClayButton(
                    text = "+ Kolom Data",
                    onClick = {
                        val newId = "field-${Clock.System.now().toEpochMilliseconds()}"
                        val el = TemplateElement.BoundField(
                            elementId = newId,
                            rect = TemplateRect(Mm10(200), Mm10(1100), Mm10(600), Mm10(80)),
                            binding = BindingToken("invoice.number"),
                            style = TextStyleSpec(fontSizePt = 10, isBold = false)
                        )
                        onEvent(TemplateDesignerUiEvent.AddElement(el))
                    },
                    style = ClayButtonStyle.Secondary,
                    fontSize = 11.sp
                )

                ClayButton(
                    text = "+ Garis",
                    onClick = {
                        val newId = "line-${Clock.System.now().toEpochMilliseconds()}"
                        val el = TemplateElement.LineShape(
                            elementId = newId,
                            rect = TemplateRect(Mm10(150), Mm10(1200), Mm10(1800), Mm10(10)),
                            strokeHex = 0xFF1E293BL,
                            strokeMm10 = 3
                        )
                        onEvent(TemplateDesignerUiEvent.AddElement(el))
                    },
                    style = ClayButtonStyle.Secondary,
                    fontSize = 11.sp
                )

                ClayButton(
                    text = "+ Kotak",
                    onClick = {
                        val newId = "rect-${Clock.System.now().toEpochMilliseconds()}"
                        val el = TemplateElement.RectShape(
                            elementId = newId,
                            rect = TemplateRect(Mm10(150), Mm10(1250), Mm10(1800), Mm10(200)),
                            fillHex = 0xFFF1F5F9L,
                            strokeHex = 0xFF1E293BL,
                            strokeMm10 = 2
                        )
                        onEvent(TemplateDesignerUiEvent.AddElement(el))
                    },
                    style = ClayButtonStyle.Secondary,
                    fontSize = 11.sp
                )

                ClayButton(
                    text = "AI Auto-Map",
                    onClick = { onEvent(TemplateDesignerUiEvent.AutoMapWithAi) },
                    style = ClayButtonStyle.Secondary,
                    fontSize = 11.sp,
                    leading = { IconZap(Modifier.size(13.dp)) }
                )
            }

            // Right: Zoom & Save
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom controls
                ClayButton(
                    text = "-",
                    onClick = { onEvent(TemplateDesignerUiEvent.SetZoom(state.zoomPercent - 15)) },
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp
                )
                Text(
                    text = "${state.zoomPercent}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayButton(
                    text = "+",
                    onClick = { onEvent(TemplateDesignerUiEvent.SetZoom(state.zoomPercent + 15)) },
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.width(ClaySpacing.Sm))

                if (state.prefillData != null) {
                    ClayButton(
                        text = if (state.isSaving) "Menerbitkan…" else "Simpan & Pratinjau Faktur",
                        enabled = !state.isSaving,
                        onClick = { onEvent(TemplateDesignerUiEvent.SaveAndCreateInvoice()) },
                        style = ClayButtonStyle.Primary,
                        fontSize = 12.sp,
                        leading = { IconReceipt(Modifier.size(13.dp), color = WeMadeColors.Surface) }
                    )
                } else {
                    ClayButton(
                        text = if (state.isSaving) "Menyimpan…" else "Simpan Template",
                        enabled = !state.isSaving,
                        onClick = { onEvent(TemplateDesignerUiEvent.SaveTemplate) },
                        style = ClayButtonStyle.Primary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
