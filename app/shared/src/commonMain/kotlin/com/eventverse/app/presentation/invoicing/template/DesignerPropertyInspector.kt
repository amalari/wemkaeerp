package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Panel properti di sisi kanan kanvas.
 *
 * ## Satu panel, bukan dua tab
 *
 * Sebelumnya panel ini punya tab kedua — "Isi Data Faktur Live" — tempat pengguna mengetikkan nama
 * klien, item, dan pajak untuk melihat hasilnya di kanvas. Tab itu dihapus karena keliru secara konsep:
 * **desainer ini menyusun template, bukan menerbitkan faktur**. Nilai yang tampil di kanvas datang dari
 * modul yang memproduksinya — identitas klien dari CRM, nominal dari mesin faktur, rekening dari profil
 * penerbit — dan formulir penerbitan faktur sudah ada di layar Invoicing.
 *
 * Bahaya yang dihindari lebih besar daripada fitur yang hilang: dua tempat berbeda yang sama-sama bisa
 * mengubah angka tagihan adalah cara paling mudah membuat faktur tidak cocok dengan dokumen sumbernya.
 */
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
            Text(
                text = if (selected == null) "Pengaturan Template" else elementTypeLabel(selected),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = if (selected == null) {
                    "Pilih sebuah elemen di kanvas untuk mengatur isi, huruf, dan warnanya."
                } else {
                    "Perubahan berlaku langsung di kanvas dan pada hasil cetak."
                },
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            HorizontalDivider(color = WeMadeColors.Border)

            if (selected == null) {
                TemplateSettings(state = state, onEvent = onEvent)
            } else {
                ElementSettings(element = selected, state = state, onEvent = onEvent)
            }
        }
    }
}

/** Pengaturan tingkat dokumen — tampil saat tidak ada elemen terpilih. */
@Composable
private fun TemplateSettings(
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        ClayTextField(
            value = state.template.name,
            onValueChange = { onEvent(TemplateDesignerUiEvent.UpdateTemplateName(it)) },
            label = "Nama Template Faktur"
        )

        InfoRow(label = "Ukuran kertas", value = state.template.paperSize.displayName)
        InfoRow(label = "Jumlah elemen", value = "${state.template.elements.size} elemen")

        HorizontalDivider(color = WeMadeColors.Border)

        SectionLabel("Jenis Tagihan Template Ini")
        Text(
            text = "Setiap template didedikasikan untuk 1 jenis tagihan khusus:",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            InvoiceKind.entries.forEach { kind ->
                val isSelected = kind == state.template.targetKind
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .claySurface(
                            shape = ClayShapes.Chip,
                            background = if (isSelected) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                            outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                            borderWidth = if (isSelected) ClayBorder.Medium else ClayBorder.Hairline,
                            offset = if (isSelected) ClayOffset.Pressed else ClayOffset.Flat
                        )
                        .clickable { onEvent(TemplateDesignerUiEvent.SetApplicableKind(kind)) }
                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = kind.displayName,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurface
                    )
                    if (isSelected) {
                        IconCheck(Modifier.size(13.dp), color = WeMadeColors.Primary)
                    }
                }
            }
        }

        HorizontalDivider(color = WeMadeColors.Border)

        SectionLabel("Alat Bantu Kanvas")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Tampilkan Grid", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "Mesh 10 mm sebagai acuan penempatan.",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            ClayButton(
                text = if (state.showGrid) "AKTIF" else "NONAKTIF",
                onClick = { onEvent(TemplateDesignerUiEvent.ToggleGrid(!state.showGrid)) },
                style = if (state.showGrid) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                fontSize = 10.sp
            )
        }

        Text(text = "Snap to Grid (Magnet Geser):", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            listOf(1, 5, 10).forEach { mm ->
                SegmentedChip(
                    label = "$mm mm",
                    selected = state.snapGridMm == mm,
                    onClick = { onEvent(TemplateDesignerUiEvent.SetSnapGrid(mm)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(color = WeMadeColors.Border)

        SectionLabel("Pemetaan Otomatis")

        // Dipindahkan dari toolbar: pemetaan otomatis mengubah isi template, jadi tempatnya bersama
        // pengaturan template lain — bukan di baris alat kanvas tempat ia dulu bersebelahan dengan
        // tombol zoom dan simpan.
        ClayButton(
            text = "Petakan Teks Statis ke Token",
            onClick = { onEvent(TemplateDesignerUiEvent.AutoMapWithAi) },
            style = ClayButtonStyle.Secondary,
            fontSize = 11.sp,
            leading = { IconZap(Modifier.size(13.dp)) }
        )
        Text(
            text = "Menebak token data untuk teks yang sudah ada, misalnya \"Telp: 0812...\" menjadi isian " +
                "telepon klien. Label teks murni dibiarkan apa adanya.",
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

/** Sakelar berbentuk chip yang menandai pilihannya lewat warna, bukan ketebalan garis. */
@Composable
private fun SegmentedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (selected) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                outline = if (selected) WeMadeColors.Primary else WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
            color = if (selected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurface
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            modifier = Modifier.weight(1f, fill = false),
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(ClaySpacing.Sm))
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.OnSurface)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = WeMadeColors.OnSurfaceMuted
    )
}

/** Properti satu elemen: isi, tipografi, geometri, dan perilaku di bawah tabel. */
@Composable
private fun ElementSettings(
    element: TemplateElement,
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        when (element) {
            is TemplateElement.StaticText -> StaticTextSettings(element, onEvent)
            is TemplateElement.BoundField -> BoundFieldSettings(element, state, onEvent)
            is TemplateElement.ItemTable -> ItemTableSettings(element)
            is TemplateElement.LineShape -> LineSettings(element)
            is TemplateElement.RectShape, is TemplateElement.ImageBox -> Unit
        }

        element.textStyleOrNull()?.let { style ->
            TextStyleControls(element = element, style = style, onEvent = onEvent)
        }

        GeometryControls(element = element, state = state, onEvent = onEvent)

        AnchorToggle(element = element, onEvent = onEvent)

        ClayButton(
            text = "Hapus Elemen",
            onClick = { onEvent(TemplateDesignerUiEvent.DeleteElement(element.elementId)) },
            style = ClayButtonStyle.Danger,
            fontSize = 11.sp
        )
    }
}

/**
 * Isi teks statis.
 *
 * Isian ini sengaja multi-baris: satu elemen teks boleh berisi paragraf (syarat pembayaran, catatan
 * kaki), dan memaksa pengguna mengetik paragraf di kotak satu baris membuatnya tidak pernah bisa
 * membaca ulang apa yang sudah ditulis sebelum menyimpannya.
 */
@Composable
private fun StaticTextSettings(
    element: TemplateElement.StaticText,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        SectionLabel("Isi Teks")

        ClayTextField(
            value = element.text,
            onValueChange = { onEvent(TemplateDesignerUiEvent.UpdateElementText(element.elementId, it)) },
            label = null,
            singleLine = false,
            minLines = 3,
            placeholder = "Tulis isi teks..."
        )

        Text(
            text = "Tip: klik dua kali elemen di kanvas untuk mengedit langsung di tempat.",
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

/**
 * Isian data dari modul lain.
 *
 * Dua hal yang bisa diubah pengguna di sini: **dari modul mana** nilainya diambil (pemilih token) dan
 * **label** yang menyertainya di depan/belakang. Nilainya sendiri tidak bisa diketik — itu justru
 * inti kontraknya.
 */
@Composable
private fun BoundFieldSettings(
    element: TemplateElement.BoundField,
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        SectionLabel("Sumber Data")

        TokenSelector(
            selectedToken = element.binding,
            onSelect = { token ->
                onEvent(TemplateDesignerUiEvent.UpdateElement(element.copy(binding = token)))
            }
        )

        BoundFieldSample(element = element, state = state)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Box(modifier = Modifier.weight(1f)) {
                ClayTextField(
                    value = element.prefix,
                    onValueChange = { onEvent(TemplateDesignerUiEvent.UpdateElement(element.copy(prefix = it))) },
                    label = "Label Depan"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ClayTextField(
                    value = element.suffix,
                    onValueChange = { onEvent(TemplateDesignerUiEvent.UpdateElement(element.copy(suffix = it))) },
                    label = "Label Belakang"
                )
            }
        }
    }
}

/**
 * Nilai contoh yang sedang diresolusi isian ini.
 *
 * Ditampilkan karena desainer tidak lagi punya tempat mengetik data: tanpa contoh ini, pengguna hanya
 * melihat nama token dan tidak tahu isi apa yang akan muncul saat faktur sungguhan dicetak.
 */
@Composable
private fun BoundFieldSample(
    element: TemplateElement.BoundField,
    state: TemplateDesignerUiState
) {
    val resolved = InvoiceBindingResolver.resolve(element.binding, state.previewInvoice)

    val sample = when (resolved) {
        is ResolvedBindingValue.Text -> resolved.value.ifBlank { "belum ada data" }
        is ResolvedBindingValue.Image -> resolved.assetUrl ?: "belum ada gambar"
        is ResolvedBindingValue.Empty -> "belum ada data"
    }
    val isSample = state.isSampleData

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = WeMadeColors.SurfaceMuted,
        borderWidth = ClayBorder.Hairline,
        contentPadding = PaddingValues(ClaySpacing.Sm)
    ) {
        Text(
            text = if (isSample) "Contoh data: $sample" else "Data terpasang: $sample",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (isSample) {
            Text(
                text = "Nilai ini berasal dari contoh bawaan desainer.",
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun ItemTableSettings(element: TemplateElement.ItemTable) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        SectionLabel("Kolom Tabel")

        element.columns.forEach { column ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = column.header,
                    modifier = Modifier.weight(1f, fill = false),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                Text(
                    text = column.binding.value,
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }

        Text(
            text = "Baris tabel mengikuti jumlah item faktur; tingginya menyesuaikan sendiri, " +
                "termasuk elemen di bawah tabel yang ditandai ikut bergeser.",
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        InfoRow(label = "Tinggi baris", value = "${element.rowHeight.value / 10} mm")
    }
}

@Composable
private fun LineSettings(element: TemplateElement.LineShape) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        SectionLabel("Garis")
        InfoRow(label = "Ketebalan", value = "${element.strokeMm10 / 10f} mm")
        Text(
            text = "Tinggi elemen garis adalah ketebalannya, jadi hanya lebarnya yang bisa diubah.",
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

/** Gaya teks elemen, bila elemen memang punya teks. */
private fun TemplateElement.textStyleOrNull(): TextStyleSpec? = when (this) {
    is TemplateElement.StaticText -> style
    is TemplateElement.BoundField -> style
    else -> null
}

/**
 * Kendali tipografi: ukuran, tebal, perataan, dan warna.
 *
 * Tinggi elemen **tidak** ada di sini dengan sengaja: tinggi teks adalah turunan dari isi, ukuran font,
 * dan lebar kotak. Menawarkan kolom "tinggi" hanya akan membuat pengguna bingung saat angkanya berubah
 * sendiri sesaat setelah diketik.
 */
@Composable
private fun TextStyleControls(
    element: TemplateElement,
    style: TextStyleSpec,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    fun apply(newStyle: TextStyleSpec) {
        onEvent(TemplateDesignerUiEvent.UpdateElement(element.withStyle(newStyle)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        SectionLabel("Tipografi")

        FontSizeStepper(
            fontSizePt = style.fontSizePt,
            onChange = { apply(style.copy(fontSizePt = it)) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayButton(
                text = if (style.isBold) "TEBAL: AKTIF" else "TEBAL: NONAKTIF",
                onClick = { apply(style.copy(isBold = !style.isBold)) },
                style = if (style.isBold) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
        }

        Text(text = "Perataan:", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            TextAlign.entries.forEach { align ->
                SegmentedChip(
                    label = when (align) {
                        TextAlign.LEFT -> "Kiri"
                        TextAlign.CENTER -> "Tengah"
                        TextAlign.RIGHT -> "Kanan"
                    },
                    selected = style.align == align,
                    onClick = { apply(style.copy(align = align)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text(text = "Warna teks:", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        ColorSwatchRow(
            selectedHex = style.colorHex,
            onSelect = { apply(style.copy(colorHex = it)) }
        )
    }
}

/** Pengatur ukuran font dengan tombol langkah dan nilai yang bisa dibaca. */
@Composable
private fun FontSizeStepper(
    fontSizePt: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Ukuran font",
            modifier = Modifier.weight(1f, fill = false),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        Spacer(modifier = Modifier.width(ClaySpacing.Sm))

        ClayButton(
            text = "-",
            onClick = { onChange((fontSizePt - 1).coerceAtLeast(MIN_FONT_SIZE_PT)) },
            style = ClayButtonStyle.Ghost,
            fontSize = 12.sp
        )
        Text(
            text = "$fontSizePt pt",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = WeMadeColors.OnSurface
        )
        ClayButton(
            text = "+",
            onClick = { onChange((fontSizePt + 1).coerceAtMost(MAX_FONT_SIZE_PT)) },
            style = ClayButtonStyle.Ghost,
            fontSize = 12.sp
        )
    }
}

/**
 * Daftar warna dari [InvoicePrintPalette].
 *
 * Warna diambil dari domain sebagai data (`Color(token.hex)`), bukan ditulis sebagai literal di UI:
 * palet ini adalah bagian dari template yang disimpan tenant, bukan keputusan desain layar.
 */
@Composable
private fun ColorSwatchRow(
    selectedHex: Long,
    onSelect: (Long) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        InvoicePrintPalette.ALL.forEach { token ->
            val isSelected = token.hex == selectedHex
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(token.hex), ClayShapes.Pill)
                    .border(
                        width = if (isSelected) ClayBorder.Thick else ClayBorder.Hairline,
                        color = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                        shape = ClayShapes.Pill
                    )
                    .clickable { onSelect(token.hex) }
            )
        }
    }
}

private const val MIN_FONT_SIZE_PT = 6
private const val MAX_FONT_SIZE_PT = 48

/**
 * Geometri elemen dalam milimeter.
 *
 * Lebar bisa diketik maupun ditarik di kanvas; keduanya melewati penjepitan domain yang sama
 * ([TemplateRect.resizedWidth]), sehingga angka di sini dan hasil tarikan tidak pernah berbeda.
 *
 * Tinggi ditampilkan sebagai **informasi**, bukan isian: nilainya turunan dari isi teks (atau jumlah
 * baris faktur untuk tabel). Kolom yang bisa diketik tetapi langsung berubah sendiri setelah dihitung
 * ulang adalah cara tercepat membuat pengguna kehilangan kepercayaan pada panel ini.
 */
@Composable
private fun GeometryControls(
    element: TemplateElement,
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    val heightLabel = when (element) {
        is TemplateElement.StaticText, is TemplateElement.BoundField -> "Tinggi (otomatis)"
        is TemplateElement.ItemTable -> "Tinggi (ikut baris)"
        is TemplateElement.LineShape -> "Tinggi (ketebalan)"
        is TemplateElement.RectShape, is TemplateElement.ImageBox -> "Tinggi"
    }

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        SectionLabel("Posisi & Ukuran (mm)")

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            CoordinateField(label = "X", valueMm10 = element.rect.x) { newX ->
                onEvent(
                    TemplateDesignerUiEvent.UpdateElementRect(
                        element.elementId,
                        element.rect.copy(x = clampX(newX, element, state))
                    )
                )
            }
            CoordinateField(label = "Y", valueMm10 = element.rect.y) { newY ->
                onEvent(TemplateDesignerUiEvent.UpdateElementRect(element.elementId, element.rect.copy(y = newY)))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            CoordinateField(label = "Lebar", valueMm10 = element.rect.width) { newWidth ->
                onEvent(TemplateDesignerUiEvent.ResizeElementWidth(element.elementId, newWidth.value))
            }
            Box(modifier = Modifier.weight(1f)) {
                Column {
                    Text(
                        text = heightLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.Border,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(horizontal = ClaySpacing.Md, vertical = 11.dp)
                    ) {
                        Text(
                            text = "${element.rect.height.value / 10} mm",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }
    }
}

/** Menjaga elemen tetap di dalam kertas saat X diketik, bukan hanya saat ditarik. */
private fun clampX(newX: Mm10, element: TemplateElement, state: TemplateDesignerUiState): Mm10 {
    val paperWidth = state.template.paperSize.width.value
    val maxX = (paperWidth - element.rect.width.value).coerceAtLeast(0)
    return Mm10(newX.value.coerceIn(0, maxX))
}

/**
 * Sakelar "ikut bergeser di bawah tabel".
 *
 * Elemen ber-anchor digeser turun sebesar pertambahan tinggi tabel item, sehingga blok tanda tangan dan
 * rekening tidak pernah tertimpa baris item yang bertambah.
 */
@Composable
private fun AnchorToggle(
    element: TemplateElement,
    onEvent: (TemplateDesignerUiEvent) -> Unit
) {
    val isAnchored = element.anchorBelowTable

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ikut Geser di Bawah Tabel",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "Turun otomatis mengikuti panjang tabel item agar tidak menumpuk.",
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        Spacer(modifier = Modifier.width(ClaySpacing.Sm))

        ClayButton(
            text = if (isAnchored) "AKTIF" else "NONAKTIF",
            onClick = { onEvent(TemplateDesignerUiEvent.UpdateElement(element.withAnchorBelowTable(!isAnchored))) },
            style = if (isAnchored) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
            fontSize = 10.sp
        )
    }
}

/**
 * Isian angka milimeter.
 *
 * Nilai ditahan sebagai teks lokal agar field boleh kosong saat diketik ulang. Memetakan field langsung
 * ke angka membuat menghapus isinya memaksa nilai 0 dan angka baru menempel di belakangnya ("0" lalu
 * "25" menjadi "025") — field terasa tidak bisa diisi. Teks lokal hanya memanggil [onChange] saat
 * isinya benar-benar angka.
 */
@Composable
private fun RowScope.CoordinateField(
    label: String,
    valueMm10: Mm10,
    onChange: (Mm10) -> Unit
) {
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

/**
 * Pemilih token data, dikelompokkan menurut modul yang memproduksinya.
 *
 * Isian yang bisa dipilih hanya yang bermakna di luar tabel: token baris (`line.*`) hanya bisa
 * diresolusi di dalam tabel item, jadi menawarkannya di sini akan menghasilkan elemen kosong.
 */
@Composable
private fun TokenSelector(
    selectedToken: BindingToken,
    onSelect: (BindingToken) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDescriptor = InvoiceBindingRegistry.descriptorFor(selectedToken)

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
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = selectedDescriptor?.displayName ?: selectedToken.value,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.PrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = selectedDescriptor?.moduleSource?.displayName ?: selectedToken.value,
                        fontSize = 10.sp,
                        color = WeMadeColors.PrimaryDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(ClaySpacing.Sm))

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
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(ClaySpacing.Xs)
            ) {
                InvoiceBindingRegistry.standaloneModules().forEach { module ->
                    Text(
                        text = module.displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.OnSurfaceMuted,
                        modifier = Modifier.padding(
                            start = ClaySpacing.Xs,
                            top = ClaySpacing.Sm,
                            bottom = ClaySpacing.Xxs
                        )
                    )

                    InvoiceBindingRegistry.descriptorsOf(module).forEach { descriptor ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(descriptor.token)
                                    expanded = false
                                }
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = descriptor.displayName,
                                modifier = Modifier.weight(1f, fill = false),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                            Text(
                                text = descriptor.token.value,
                                fontSize = 10.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun elementTypeLabel(el: TemplateElement): String = when (el) {
    is TemplateElement.StaticText -> "Teks"
    is TemplateElement.BoundField -> "Isian Dinamis"
    is TemplateElement.ItemTable -> "Tabel Baris Item"
    is TemplateElement.RectShape -> "Bentuk Kotak"
    is TemplateElement.LineShape -> "Divider"
    is TemplateElement.ImageBox -> "Kotak Logo / Gambar"
}







