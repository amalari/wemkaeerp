package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun DesignerPropertyInspector(
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = state.selectedElement

    ClayCard(
        modifier = modifier.fillMaxHeight(),
        containerColor = WeMadeColors.Surface,
        borderWidth = ClayBorder.Medium,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            if (selected == null) {
                // Template-level properties
                Text(
                    text = "Pengaturan Template",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = WeMadeColors.OnSurface
                )

                HorizontalDivider(color = WeMadeColors.Border)

                ClayTextField(
                    value = state.template.name,
                    onValueChange = { onEvent(TemplateDesignerUiEvent.UpdateTemplateName(it)) },
                    label = "Nama Template Faktur"
                )

                Text(
                    text = "Ukuran Kertas",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Text(
                    text = "A4 Portrait (210 × 297 mm)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                HorizontalDivider(color = WeMadeColors.Border)

                Text(
                    text = "Alat Bantu Kanvas",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted
                )

                // Grid Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Tampilkan Grid (10mm)", fontSize = 12.sp)
                    ClayButton(
                        text = if (state.showGrid) "AKTIF" else "NONAKTIF",
                        onClick = { onEvent(TemplateDesignerUiEvent.ToggleGrid(!state.showGrid)) },
                        style = if (state.showGrid) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                        fontSize = 10.sp
                    )
                }

                // Snap to grid
                Text(text = "Snap to Grid (Magnet Geser):", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    listOf(1, 5, 10).forEach { mm ->
                        val isSelected = state.snapGridMm == mm
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clayFlat(
                                    shape = ClayShapes.Chip,
                                    background = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                                    outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .clickable { onEvent(TemplateDesignerUiEvent.SetSnapGrid(mm)) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${mm} mm",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                color = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(ClaySpacing.Lg))
                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = WeMadeColors.SurfaceMuted,
                    borderWidth = ClayBorder.Hairline,
                    contentPadding = PaddingValues(ClaySpacing.Md)
                ) {
                    Text(
                        text = "💡 Petunjuk Desain:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Primary
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    Text(
                        text = "Klik salah satu elemen di kanvas untuk mengedit posisi, ukuran font, token data dinamis, atau mengaktifkan fitur geser dinamis di bawah tabel.",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            } else {
                // Element-specific properties
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Properti Elemen",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = elementTypeLabel(selected),
                            fontSize = 11.sp,
                            color = WeMadeColors.Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    ClayButton(
                        text = "Hapus",
                        onClick = { onEvent(TemplateDesignerUiEvent.DeleteElement(selected.elementId)) },
                        style = ClayButtonStyle.Danger,
                        fontSize = 11.sp
                    )
                }

                HorizontalDivider(color = WeMadeColors.Border)

                // Coordinate inputs (in mm)
                Text(text = "Koordinat & Ukuran (Milimeter)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    CoordinateField(label = "X (mm)", valueMm10 = selected.rect.x) { newX ->
                        onEvent(TemplateDesignerUiEvent.UpdateElementRect(selected.elementId, selected.rect.copy(x = newX)))
                    }
                    CoordinateField(label = "Y (mm)", valueMm10 = selected.rect.y) { newY ->
                        onEvent(TemplateDesignerUiEvent.UpdateElementRect(selected.elementId, selected.rect.copy(y = newY)))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    CoordinateField(label = "Lebar (mm)", valueMm10 = selected.rect.width) { newW ->
                        onEvent(TemplateDesignerUiEvent.UpdateElementRect(selected.elementId, selected.rect.copy(width = newW)))
                    }
                    CoordinateField(label = "Tinggi (mm)", valueMm10 = selected.rect.height) { newH ->
                        onEvent(TemplateDesignerUiEvent.UpdateElementRect(selected.elementId, selected.rect.copy(height = newH)))
                    }
                }

                // Bound Field Token Selector
                if (selected is TemplateElement.BoundField) {
                    HorizontalDivider(color = WeMadeColors.Border)
                    Text(text = "Pilih Token Data Dinamis", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
                    TokenSelector(
                        selectedToken = selected.binding,
                        onSelect = { token ->
                            onEvent(TemplateDesignerUiEvent.UpdateElement(selected.copy(binding = token)))
                        }
                    )
                }

                // Static Text Input
                if (selected is TemplateElement.StaticText) {
                    HorizontalDivider(color = WeMadeColors.Border)
                    ClayTextField(
                        value = selected.text,
                        onValueChange = { onEvent(TemplateDesignerUiEvent.UpdateElement(selected.copy(text = it))) },
                        label = "Isi Teks Statis"
                    )
                }

                // Text Style Controls (for Text & BoundField)
                val styleSpec = when (selected) {
                    is TemplateElement.StaticText -> selected.style
                    is TemplateElement.BoundField -> selected.style
                    else -> null
                }

                if (styleSpec != null) {
                    HorizontalDivider(color = WeMadeColors.Border)
                    Text(text = "Tipografi & Perataan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Font: ${styleSpec.fontSizePt} pt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        ClayButton(
                            text = "-1",
                            onClick = {
                                val updated = styleSpec.copy(fontSizePt = (styleSpec.fontSizePt - 1).coerceAtLeast(6))
                                updateElementStyle(selected, updated, onEvent)
                            },
                            style = ClayButtonStyle.Ghost,
                            fontSize = 11.sp
                        )
                        ClayButton(
                            text = "+1",
                            onClick = {
                                val updated = styleSpec.copy(fontSizePt = (styleSpec.fontSizePt + 1).coerceAtMost(36))
                                updateElementStyle(selected, updated, onEvent)
                            },
                            style = ClayButtonStyle.Ghost,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        ClayButton(
                            text = if (styleSpec.isBold) "TEBAL" else "REGULAR",
                            onClick = {
                                val updated = styleSpec.copy(isBold = !styleSpec.isBold)
                                updateElementStyle(selected, updated, onEvent)
                            },
                            style = if (styleSpec.isBold) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                            fontSize = 10.sp
                        )
                    }

                    // Alignment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        TextAlign.entries.forEach { align ->
                            val isSelected = styleSpec.align == align
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clayFlat(
                                        shape = ClayShapes.Chip,
                                        background = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                                        outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .clickable {
                                        val updated = styleSpec.copy(align = align)
                                        updateElementStyle(selected, updated, onEvent)
                                    }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (align) {
                                        TextAlign.LEFT -> "Kiri"
                                        TextAlign.CENTER -> "Tengah"
                                        TextAlign.RIGHT -> "Kanan"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurface
                                )
                            }
                        }
                    }
                }

                // Anchor Below Table Switch
                HorizontalDivider(color = WeMadeColors.Border)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Geser di Bawah Tabel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Turun dinamis mengikuti panjang tabel item agar tidak menumpuk.",
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    val isAnchored = selected.anchorBelowTable
                    ClayButton(
                        text = if (isAnchored) "AKTIF" else "NONAKTIF",
                        onClick = {
                            val updated = selected.withAnchorBelowTable(!isAnchored)
                            onEvent(TemplateDesignerUiEvent.UpdateElement(updated))
                        },
                        style = if (isAnchored) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.CoordinateField(
    label: String,
    valueMm10: Mm10,
    onChange: (Mm10) -> Unit
) {
    val mmValue = valueMm10.value / 10
    ClayTextField(
        value = mmValue.toString(),
        onValueChange = { str ->
            val num = str.toIntOrNull() ?: 0
            onChange(Mm10(num * 10))
        },
        label = label,
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun TokenSelector(
    selectedToken: BindingToken,
    onSelect: (BindingToken) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Card,
                    background = WeMadeColors.PrimaryContainer,
                    outline = WeMadeColors.Primary,
                    borderWidth = ClayBorder.Hairline
                )
                .clickable { expanded = !expanded }
                .padding(ClaySpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedToken.value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.PrimaryDark
                )
                Text(text = if (expanded) "▲" else "▼", fontSize = 10.sp, color = WeMadeColors.PrimaryDark)
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(ClaySpacing.Xs))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(ClaySpacing.Xs)
            ) {
                InvoiceBindingRegistry.DOCUMENT.forEach { desc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(desc.token)
                                expanded = false
                            }
                            .padding(vertical = 4.dp, horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = desc.displayName, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Text(text = desc.token.value, fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
                    }
                }
            }
        }
    }
}

private fun updateElementStyle(
    element: TemplateElement,
    newStyle: TextStyleSpec,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    val updated = when (element) {
        is TemplateElement.StaticText -> element.copy(style = newStyle)
        is TemplateElement.BoundField -> element.copy(style = newStyle)
        else -> element
    }
    onEvent(TemplateDesignerUiEvent.UpdateElement(updated))
}

private fun elementTypeLabel(el: TemplateElement): String = when (el) {
    is TemplateElement.StaticText -> "Teks Statis"
    is TemplateElement.BoundField -> "Kolom Dinamis (Token: ${el.binding.value})"
    is TemplateElement.ItemTable -> "Tabel Item Pekerjaan"
    is TemplateElement.RectShape -> "Bentuk Kotak"
    is TemplateElement.LineShape -> "Garis Pembatas"
    is TemplateElement.ImageBox -> "Kotak Logo / Gambar"
}
