package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Perpustakaan elemen di sisi kiri kanvas.
 *
 * ## Kenapa di panel, bukan di toolbar
 *
 * Sebelumnya elemen ditambahkan lewat tombol di toolbar (`+ Teks`, `+ Garis`, `+ Kotak`, `AI Auto-Map`)
 * yang berbaris di satu baris bersama alat kanvas, zoom, dan simpan. Cara itu tidak skalabel: setiap
 * jenis elemen baru menambah satu tombol di baris yang sama, sampai akhirnya toolbar kehabisan ruang
 * dan nama tombolnya harus disingkat hingga tak lagi jelas — `+ Kotak` tidak memberi tahu apa pun
 * tentang kotak macam apa yang akan muncul.
 *
 * Panel terpisah membuat dua hal sekaligus mungkin: elemen dikelompokkan menurut asalnya, dan kanvas
 * tetap menjadi pusat perhatian karena satu-satunya yang berubah saat palet dipakai adalah isi kertas.
 *
 * ## Dua kelompok, dua arti
 *
 * - **Elemen dasar** — isinya diketik pengguna, tidak terhubung ke data mana pun.
 * - **Modul** — nilai yang datang dari modul lain (CRM, Invoicing, profil penerbit). Ini bukan
 *   "teks dinamis" biasa: pengguna memilih *dari modul mana* nilainya diambil, sesuai kontrak output
 *   port modul di `AGENTS.md`.
 */
@Composable
fun ElementPalette(
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
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
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Text(
                text = "Perpustakaan Elemen",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "Klik sebuah elemen untuk menambahkannya ke kanvas. Posisinya otomatis di bawah elemen terakhir.",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            PaletteSectionTitle("Elemen Transaksi (Data Deal)")

            val descriptorName = InvoiceBindingRegistry.descriptorFor("billTo.name")
            if (descriptorName != null) {
                PaletteRow(
                    label = "Nama Klien / Perusahaan",
                    hint = "Nama brand/klien pemesan dari transaksi Deal.",
                    onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptorName))) },
                    leading = { IconUser(Modifier.size(13.dp), color = WeMadeColors.Primary) }
                )
            }

            val descriptorContact = InvoiceBindingRegistry.descriptorFor("billTo.contactPerson")
            if (descriptorContact != null) {
                PaletteRow(
                    label = "Kontak & Telepon",
                    hint = "Nama PIC & nomor kontak pemesan.",
                    onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptorContact))) },
                    leading = { IconPhone(Modifier.size(13.dp), color = WeMadeColors.Primary) }
                )
            }

            val descriptorAddress = InvoiceBindingRegistry.descriptorFor("billTo.address")
            if (descriptorAddress != null) {
                PaletteRow(
                    label = "Alamat Klien",
                    hint = "Alamat workshop / pengiriman pemesan.",
                    onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptorAddress))) },
                    leading = { IconDatabase(Modifier.size(13.dp), color = WeMadeColors.Primary) }
                )
            }

            val descriptorInvNumber = InvoiceBindingRegistry.descriptorFor("invoice.number")
            if (descriptorInvNumber != null) {
                val labelText = if (state.template.targetKind == com.eventverse.app.domain.invoicing.InvoiceKind.SAMPLE) {
                    "No. Invoice Sample"
                } else {
                    "Nomor Faktur / Invoice"
                }
                PaletteRow(
                    label = labelText,
                    hint = "Nomor faktur unik terbitan sistem.",
                    onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptorInvNumber))) },
                    leading = { IconReceipt(Modifier.size(13.dp), color = WeMadeColors.Primary) }
                )
            }

            val descriptorDate = InvoiceBindingRegistry.descriptorFor("invoice.issueDate")
            if (descriptorDate != null) {
                PaletteRow(
                    label = "Tanggal Terbit",
                    hint = "Tanggal pencetakan / penerbitan invoice.",
                    onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptorDate))) },
                    leading = { IconReceipt(Modifier.size(13.dp), color = WeMadeColors.Primary) }
                )
            }

            val tableExists = state.itemTableExists
            PaletteRow(
                label = "Tabel Produk / Jasa",
                hint = if (tableExists) {
                    "Template sudah memiliki tabel item pekerjaan."
                } else {
                    "Tabel dinamis: No, deskripsi pesanan, qty, harga satuan, dan subtotal."
                },
                enabled = !tableExists,
                onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ItemTable)) },
                leading = { IconLayers(Modifier.size(13.dp), color = WeMadeColors.Primary) }
            )

            val descriptorTotal = InvoiceBindingRegistry.descriptorFor("invoice.total")
            if (descriptorTotal != null) {
                PaletteRow(
                    label = "Total Tagihan",
                    hint = "Grand total nilai faktur transaksi.",
                    onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptorTotal))) },
                    leading = { IconReceipt(Modifier.size(13.dp), color = WeMadeColors.Primary) }
                )
            }

            val descriptorWords = InvoiceBindingRegistry.descriptorFor("invoice.totalInWords")
            if (descriptorWords != null) {
                PaletteRow(
                    label = "Terbilang Rupiah",
                    hint = "Konversi nominal ke kalimat terbilang rupiah.",
                    onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptorWords))) },
                    leading = { IconNote(Modifier.size(13.dp), color = WeMadeColors.Primary) }
                )
            }

            val descriptorBank = InvoiceBindingRegistry.descriptorFor("issuer.bankAccountNumber")
            if (descriptorBank != null) {
                PaletteRow(
                    label = "Rekening Bank Penerbit",
                    hint = "Instruksi transfer & no. rekening perusahaan.",
                    onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptorBank))) },
                    leading = { IconDatabase(Modifier.size(13.dp), color = WeMadeColors.Primary) }
                )
            }

            PaletteSectionTitle("Elemen Tata Letak")

            PaletteRow(
                label = "Teks Bebas / Label",
                hint = "Tulisan statis untuk judul kustom, catatan, atau instruksi.",
                onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.StaticText)) },
                leading = { IconNote(Modifier.size(13.dp), color = WeMadeColors.Primary) }
            )

            PaletteRow(
                label = "Divider",
                hint = "Garis pemisah horizontal antar bagian dokumen.",
                onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.Divider)) },
                leading = { IconDividerLine(Modifier.size(13.dp)) }
            )
        }
    }
}

@Composable
private fun PaletteSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        color = WeMadeColors.OnSurfaceMuted,
        modifier = Modifier.padding(top = ClaySpacing.Sm)
    )
}

@Composable
private fun PaletteRow(
    label: String,
    hint: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    enabled: Boolean = true
) {
    val labelColor = if (enabled) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted
    val hintColor =
        if (enabled) WeMadeColors.OnSurfaceMuted else WeMadeColors.OnSurfaceMuted.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (enabled) WeMadeColors.SurfaceMuted else WeMadeColors.Surface,
                outline = WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hint,
                fontSize = 10.sp,
                color = hintColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (enabled) {
            IconPlus(Modifier.size(11.dp), color = WeMadeColors.Primary)
        }
    }
}

/**
 * Ikon garis pemisah.
 */
@Composable
private fun IconDividerLine(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawLine(
            color = WeMadeColors.Primary,
            start = Offset(x = 0f, y = size.height / 2f),
            end = Offset(x = size.width, y = size.height / 2f),
            strokeWidth = (size.height * 0.09f).coerceAtLeast(1f)
        )
    }
}
