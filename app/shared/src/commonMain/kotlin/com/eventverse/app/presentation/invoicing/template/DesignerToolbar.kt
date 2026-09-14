package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
                    text = "← Kembali",
                    onClick = onClose,
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp
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

            // Center: Add Element Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                ClayButton(
                    text = if (state.isSaving) "Menyimpan…" else "Simpan Perubahan",
                    enabled = !state.isSaving,
                    onClick = { onEvent(TemplateDesignerUiEvent.SaveTemplate) },
                    style = ClayButtonStyle.Primary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
