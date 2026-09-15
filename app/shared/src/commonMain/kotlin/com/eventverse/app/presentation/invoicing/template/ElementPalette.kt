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

            PaletteSectionTitle("Elemen Dasar")

            PaletteRow(
                label = "Teks",
                hint = "Tulisan statis. Klik dua kali di kanvas untuk mengubah isinya.",
                onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.StaticText)) },
                leading = { IconNote(Modifier.size(13.dp), color = WeMadeColors.Primary) }
            )

            PaletteRow(
                label = "Divider",
                hint = "Garis pemisah. Hanya lebarnya yang bisa diatur.",
                onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.Divider)) },
                leading = { IconDividerLine(Modifier.size(13.dp)) }
            )

            PaletteSectionTitle("Modul")

            InvoiceBindingRegistry.standaloneModules().forEach { module ->
                ModuleGroup(
                    module = module,
                    expanded = module in state.expandedModules,
                    onToggle = { onEvent(TemplateDesignerUiEvent.ToggleModuleExpanded(module)) },
                    onInsert = { descriptor ->
                        onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptor)))
                    }
                )
            }

            PaletteSectionTitle("Tabel")

            val tableExists = state.itemTableExists
            PaletteRow(
                label = "Tabel Baris Item",
                hint = if (tableExists) {
                    "Template sudah punya satu tabel — hapus dulu tabel yang ada."
                } else {
                    "Kolom nomor, deskripsi, qty, harga, dan subtotal. Barisnya ikut jumlah item faktur."
                },
                enabled = !tableExists,
                onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ItemTable)) },
                leading = { IconLayers(Modifier.size(13.dp), color = WeMadeColors.Primary) }
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

/**
 * Satu baris elemen yang bisa diklik.
 *
 * Dipakai untuk elemen dasar, isian modul, dan tabel — satu bentuk untuk satu arti yang sama
 * ("klik untuk menambah"), sehingga pengguna tidak perlu belajar tiga pola penambahan.
 */
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
 * Satu grup modul: kepala yang bisa dibuka-tutup, berisi isian yang dihasilkannya.
 *
 * Terlipat secara bawaan karena satu modul bisa punya belasan isian; membuka semuanya sekaligus akan
 * membuat panel ini lebih panjang dari kertas yang sedang disusun.
 */
@Composable
private fun ModuleGroup(
    module: BindingModuleSource,
    expanded: Boolean,
    onToggle: () -> Unit,
    onInsert: (BindingDescriptor) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = if (expanded) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                    outline = if (expanded) WeMadeColors.Primary else WeMadeColors.Border,
                    borderWidth = ClayBorder.Hairline
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModuleIcon(module, if (expanded) WeMadeColors.PrimaryDark else WeMadeColors.Primary)

            Text(
                text = module.displayName,
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (expanded) WeMadeColors.PrimaryDark else WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(ClaySpacing.Xs))

            if (expanded) {
                IconChevronUp(Modifier.size(10.dp), color = WeMadeColors.PrimaryDark)
            } else {
                IconChevronDown(Modifier.size(10.dp), color = WeMadeColors.OnSurfaceMuted)
            }
        }

        if (expanded) {
            Text(
                text = module.description,
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(start = ClaySpacing.Sm, bottom = ClaySpacing.Xs)
            )

            InvoiceBindingRegistry.descriptorsOf(module).forEach { descriptor ->
                Box(modifier = Modifier.padding(start = ClaySpacing.Md)) {
                    PaletteRow(
                        label = descriptor.displayName,
                        hint = descriptor.labelPreview(),
                        onClick = { onInsert(descriptor) },
                        leading = { IconPlus(Modifier.size(10.dp), color = WeMadeColors.OnSurfaceMuted) }
                    )
                }
            }
        }
    }
}

/**
 * Pratinjau label yang ikut terpasang saat isian disisipkan.
 *
 * Pengguna perlu tahu ini sebelum menempel: dua isian dari modul yang sama bisa tampil sangat berbeda
 * ("Telp: …" versus hanya nomornya), dan perbedaan itu datang dari `defaultPrefix` di registry.
 */
private fun BindingDescriptor.labelPreview(): String =
    if (defaultPrefix.isNotBlank()) "Berlabel \"${defaultPrefix.trim()}\"" else "Tanpa label di depan"

@Composable
private fun ModuleIcon(module: BindingModuleSource, color: Color) {
    val modifier = Modifier.size(13.dp)
    when (module.iconKey) {
        "user" -> IconUser(modifier, color = color)
        "receipt" -> IconReceipt(modifier, color = color)
        "database" -> IconDatabase(modifier, color = color)
        else -> IconLayers(modifier, color = color)
    }
}

/**
 * Ikon garis pemisah.
 *
 * Digambar langsung sebagai vektor karena `ClayIcons` belum punya ikon "garis": memakai ikon menu
 * (tiga garis) akan terbaca sebagai tombol menu, dan emoji dilarang karena tidak punya fallback font
 * di Compose Wasm.
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
