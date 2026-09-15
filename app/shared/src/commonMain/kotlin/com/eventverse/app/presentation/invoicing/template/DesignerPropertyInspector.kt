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
import androidx.compose.ui.text.style.TextOverflow
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
            // Tab Switcher: Tata Letak vs Live Data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                DesignerInspectorTab.entries.forEach { tab ->
                    val isSelected = state.activeInspectorTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .claySurface(
                                shape = ClayShapes.Button,
                                background = if (isSelected) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                                outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                                borderWidth = ClayBorder.Hairline,
                                offset = if (isSelected) ClayOffset.Pressed else ClayOffset.Flat
                            )
                            .clickable { onEvent(TemplateDesignerUiEvent.SetInspectorTab(tab)) }
                            .padding(vertical = ClaySpacing.Sm, horizontal = ClaySpacing.Xs),
                        contentAlignment = Alignment.Center
                    ) {
                        val tint = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (tab) {
                                DesignerInspectorTab.LAYOUT -> IconRuler(Modifier.size(12.dp), color = tint)
                                DesignerInspectorTab.LIVE_DATA -> IconNote(Modifier.size(12.dp), color = tint)
                            }
                            Text(
                                text = tab.label,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = tint
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = WeMadeColors.Border)

            if (state.activeInspectorTab == DesignerInspectorTab.LIVE_DATA) {
                LiveDataInspector(state = state, onEvent = onEvent)
            } else if (selected == null) {
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
                        text = "Petunjuk Desain:",
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
                    Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                    Text(
                        text = "Tombol panah menggeser elemen terpilih " +
                            "${state.snapGridMm.coerceAtLeast(1)} mm per tekan (sebesar grid magnet " +
                            "di atas); Shift + panah melompat 10× lipat. Delete menghapus elemen, " +
                            "Escape melepas pilihan.",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                    Text(
                        text = "Untuk menggeser tampilan kertasnya, pilih alat \"Geser Kanvas\" di " +
                            "toolbar lalu tarik kanvas — atau tarik area kosong di luar kertas.",
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

                    val aiSuggestion = remember(selected.text, selected.rect) {
                        InvoiceAiMappingEngine.analyzeElement(selected)
                    }
                    if (aiSuggestion != null) {
                        val descriptor = InvoiceBindingRegistry.descriptorFor(aiSuggestion.token)
                        ClayCard(
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = WeMadeColors.PrimaryContainer,
                            borderWidth = ClayBorder.Hairline,
                            contentPadding = PaddingValues(ClaySpacing.Sm)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconZap(Modifier.size(12.dp), color = WeMadeColors.Primary)
                                        Text(
                                            text = "Rekomendasi AI",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = WeMadeColors.Primary
                                        )
                                    }
                                    ClayBadge(
                                        text = "${(aiSuggestion.confidence * 100).toInt()}% cocok",
                                        tint = WeMadeColors.Success,
                                        fontSize = 9.sp
                                    )
                                }
                                Text(
                                    text = "Petakan ke: ${descriptor?.displayName ?: aiSuggestion.token.value}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WeMadeColors.OnSurface
                                )
                                Text(
                                    text = aiSuggestion.explanation,
                                    fontSize = 10.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                                ClayButton(
                                    text = if (aiSuggestion.isStaticLabelOnly) "Jadikan Kolom Dinamis Berlabel" else "Hubungkan Token Ini",
                                    onClick = {
                                        onEvent(
                                            TemplateDesignerUiEvent.UpdateElement(
                                                InvoiceAiMappingEngine.toBoundField(selected, aiSuggestion)
                                            )
                                        )
                                    },
                                    style = if (aiSuggestion.isStaticLabelOnly) ClayButtonStyle.Secondary else ClayButtonStyle.Primary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
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
    // Nilai ditahan sebagai teks lokal agar field boleh kosong saat diketik ulang.
    // Sebelumnya field dipetakan langsung ke angka, sehingga menghapus isinya memaksa nilai 0 dan
    // angka baru selalu menempel di belakangnya ("0" lalu "25" menjadi "025") — field terasa
    // tidak bisa diisi. Teks lokal hanya memanggil [onChange] saat isinya benar-benar angka.
    var text by remember(valueMm10) { mutableStateOf((valueMm10.value / 10).toString()) }

    ClayTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(4)
            text = digits
            digits.toIntOrNull()?.let { onChange(Mm10(it * 10)) }
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
                if (expanded) {
                    IconChevronUp(Modifier.size(10.dp), color = WeMadeColors.PrimaryDark)
                } else {
                    IconChevronDown(Modifier.size(10.dp), color = WeMadeColors.PrimaryDark)
                }
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

@Composable
private fun LiveDataInspector(
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    val live = state.previewInvoice
    val billTo = live.billTo
    val line = live.lines.firstOrNull()

    var clientName by remember(billTo.name) { mutableStateOf(billTo.name) }
    var pic by remember(billTo.contactPerson) { mutableStateOf(billTo.contactPerson) }
    var phone by remember(billTo.phone) { mutableStateOf(billTo.phone) }
    var email by remember(billTo.email) { mutableStateOf(billTo.email) }
    var address by remember(billTo.address) { mutableStateOf(billTo.address) }

    var itemDesc by remember(line?.description) { mutableStateOf(line?.description ?: "") }
    var qtyStr by remember(line?.quantity) { mutableStateOf(((line?.quantity?.micros ?: 1_000_000L) / 1_000_000.0).toString()) }
    var priceStr by remember(line?.unitPrice) { mutableStateOf(((line?.unitPrice?.minorUnits ?: 0L) / (line?.unitPrice?.currency?.minorFactor ?: 100L)).toString()) }
    var taxStr by remember(live.taxRatio) { mutableStateOf(((live.taxRatio.toDouble() * 100.0).toInt()).toString()) }

    Text(
        text = "Data Klien & Penagihan",
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        color = WeMadeColors.OnSurface
    )

    ClayTextField(
        value = clientName,
        onValueChange = {
            clientName = it
            onEvent(TemplateDesignerUiEvent.UpdateLiveClient(it, pic, phone, email, address))
        },
        label = "Nama Klien / Perusahaan *"
    )

    ClayTextField(
        value = pic,
        onValueChange = {
            pic = it
            onEvent(TemplateDesignerUiEvent.UpdateLiveClient(clientName, it, phone, email, address))
        },
        label = "PIC / Kontak Person"
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        Box(modifier = Modifier.weight(1f)) {
            ClayTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    onEvent(TemplateDesignerUiEvent.UpdateLiveClient(clientName, pic, it, email, address))
                },
                label = "No. Telepon / WA"
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            ClayTextField(
                value = email,
                onValueChange = {
                    email = it
                    onEvent(TemplateDesignerUiEvent.UpdateLiveClient(clientName, pic, phone, it, address))
                },
                label = "Email Klien"
            )
        }
    }

    ClayTextField(
        value = address,
        onValueChange = {
            address = it
            onEvent(TemplateDesignerUiEvent.UpdateLiveClient(clientName, pic, phone, email, it))
        },
        label = "Alamat Pengiriman / Penagihan"
    )

    HorizontalDivider(color = WeMadeColors.Border)

    Text(
        text = "Rincian Item & Nilai",
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        color = WeMadeColors.OnSurface
    )

    ClayTextField(
        value = itemDesc,
        onValueChange = {
            itemDesc = it
            val q = qtyStr.toDoubleOrNull() ?: 1.0
            val p = priceStr.toLongOrNull() ?: 0L
            val t = taxStr.toDoubleOrNull() ?: 11.0
            onEvent(TemplateDesignerUiEvent.UpdateLiveItem(it, q, p, t))
        },
        label = "Deskripsi Pekerjaan / Produk *"
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        Box(modifier = Modifier.weight(0.7f)) {
            ClayTextField(
                value = qtyStr,
                onValueChange = {
                    qtyStr = it
                    val q = it.toDoubleOrNull() ?: 1.0
                    val p = priceStr.toLongOrNull() ?: 0L
                    val t = taxStr.toDoubleOrNull() ?: 11.0
                    onEvent(TemplateDesignerUiEvent.UpdateLiveItem(itemDesc, q, p, t))
                },
                label = "Jumlah (Qty)"
            )
        }
        Box(modifier = Modifier.weight(1.3f)) {
            ClayTextField(
                value = priceStr,
                onValueChange = {
                    priceStr = it
                    val q = qtyStr.toDoubleOrNull() ?: 1.0
                    val p = it.toLongOrNull() ?: 0L
                    val t = taxStr.toDoubleOrNull() ?: 11.0
                    onEvent(TemplateDesignerUiEvent.UpdateLiveItem(itemDesc, q, p, t))
                },
                label = "Harga Satuan (Rp)"
            )
        }
    }

    ClayTextField(
        value = taxStr,
        onValueChange = {
            taxStr = it
            val q = qtyStr.toDoubleOrNull() ?: 1.0
            val p = priceStr.toLongOrNull() ?: 0L
            val t = it.toDoubleOrNull() ?: 11.0
            onEvent(TemplateDesignerUiEvent.UpdateLiveItem(itemDesc, q, p, t))
        },
        label = "Tarif PPN (%)"
    )

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = WeMadeColors.SurfaceMuted,
        borderWidth = ClayBorder.Hairline,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Subtotal:", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                Text(text = live.subtotal.formatted(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "PPN (${(live.taxRatio.toDouble() * 100.0).toInt()}%):", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                Text(text = live.taxAmount.formatted(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = WeMadeColors.Border)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Estimasi Total:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = WeMadeColors.Primary)
                Text(text = live.total.formatted(), fontSize = 13.sp, fontWeight = FontWeight.Black, color = WeMadeColors.Primary)
            }
        }
    }

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = WeMadeColors.PrimaryContainer,
        borderWidth = ClayBorder.Hairline,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Text(
            text = "Sinkronisasi Live: Data yang Anda ketik di atas langsung terpasang pada titik-titik elemen kanvas A4 yang bertaut secara otomatis.",
            fontSize = 11.sp,
            color = WeMadeColors.Primary
        )
    }
}

