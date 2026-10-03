package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayPaneWidth
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * `PrototypeRenderer` (plan §4, Fase C/D): menggambar setiap deskriptor layar draf. Sample data
 * **berupa data** — dihitung `WidgetRegistry` di server dan dikirim dalam ringkasan draf, jadi
 * renderer hanya memetakan kind → layout. Semua kind memakai bahasa Clay; tidak ada warna literal.
 * Parameter [screens] memungkinkan pane memfilter pratinjau per modul tanpa logika render baru.
 *
 * Setiap layar digambar sebagai **bingkai perangkat** berlebar [ClayPaneWidth.PrototypeDevice]
 * (lebar ponsel), disusun berjajar lewat `ClayFlowRow` — bukan `fillMaxWidth` yang membuat
 * pratinjau terlihat seperti dokumen membentang, bukan aplikasi.
 */
@Composable
fun PrototypeRenderer(
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier,
    screens: List<DiscoveryScreenUi> = draft.screens
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
        if (screens.isEmpty()) {
            // Draf tanpa layar bukan kegagalan render: agent deterministik memang tidak mengusulkan
            // deskriptor layar. Tanpa pesan ini, prospek hanya melihat judul "Pratinjau Layar" yang
            // menggantung tanpa penjelasan.
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Draf ini belum punya layar pratinjau. Deskriptor layar diusulkan oleh agent LLM — " +
                        "jalankan server dengan DISCOVERY_AGENT=koog dan DEEPSEEK_API_KEY terisi untuk melihatnya, " +
                        "atau pilih pola Studio setelah modul dibangun.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
        ClayFlowRow(modifier = Modifier.fillMaxWidth(), spacing = ClaySpacing.Lg) {
            screens.forEach { screen ->
                val module = draft.modules.firstOrNull { it.id == screen.moduleId }
                PrototypeScreenCard(
                    modifier = Modifier.width(ClayPaneWidth.PrototypeDevice),
                    title = screen.title,
                    widget = screen.widget,
                    moduleName = module?.displayName ?: screen.moduleId,
                    rows = screen.sampleRows,
                    interactive = screen.interactive
                )
            }
        }
    }
}

@Composable
private fun PrototypeScreenCard(
    title: String,
    widget: String,
    moduleName: String,
    rows: List<Map<String, String>>,
    modifier: Modifier = Modifier,
    interactive: InteractiveScreen? = null
) {
    ClayCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    moduleName,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            val kind = WidgetKind.fromCode(widget)
            ClayBadge(
                text = kind?.displayName ?: widget,
                tint = when (kind) {
                    WidgetKind.FORM, WidgetKind.TABLE -> WeMadeColors.Primary
                    WidgetKind.KANBAN -> WeMadeColors.Accent
                    WidgetKind.DASHBOARD -> WeMadeColors.Success
                    WidgetKind.CUSTOM_SCREEN -> WeMadeColors.Purple
                    else -> WeMadeColors.OnSurfaceMuted
                },
                dot = true
            )
        }

        if (rows.isEmpty()) {
            // Satu-satunya jalan ke sini: modul layar tidak ada di pack, atau kode widget di luar
            // kosakata v1 (validator menolaknya, tapi renderer tidak boleh menebak). Kalimat lama
            // ("menyusul setelah pola Studio dipilih") menyesatkan begitu CUSTOM_SCREEN punya
            // kerangka sendiri — ia menyalahkan prospek atas keadaan yang bukan salahnya.
            Text(
                "Layar ini belum bisa dipratinjau: \"$widget\" tidak punya contoh tata letak, " +
                    "atau modulnya tidak ada di pak ini.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Sm)
            )
            return@ClayCard
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = ClaySpacing.Sm), color = WeMadeColors.Outline.copy(alpha = 0.3f))
        // Kanban yang bisa dimainkan menggantikan gambar statis; widget lain / baris tak sah tetap statis.
        if (interactive != null) InteractiveKanban(interactive) else WidgetBody(widget = widget, rows = rows)
    }
}

@Composable
private fun WidgetBody(widget: String, rows: List<Map<String, String>>) {
    when (WidgetKind.fromCode(widget)) {
        WidgetKind.DASHBOARD -> ClayFlowRow(modifier = Modifier.fillMaxWidth()) {
            rows.forEach { row ->
                Column(
                    modifier = Modifier
                        .background(WeMadeColors.PrimaryContainer, ClayShapes.Tile)
                        .padding(ClaySpacing.Md)
                ) {
                    row.forEach { (k, v) ->
                        Text(k, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
                        Text(v, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        WidgetKind.KANBAN -> {
            // Papan nyata: kunci "Kolom" menamaikan kolom, kartu-kartunya menumpuk di bawahnya.
            // Entri kartu pertama = judul (tebal), sisanya detail redup. Sampel v1 (satu kartu per
            // kolom) otomatis ikut bentuk ini — tidak ada cabang khusus data lama.
            val kolom = rows.groupBy { it["Kolom"].orEmpty() }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                kolom.forEach { (namaKolom, kartuDiKolom) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(WeMadeColors.SurfaceMuted.copy(alpha = 0.14f), ClayShapes.Tile)
                            .padding(ClaySpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        Text(
                            namaKolom,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        kartuDiKolom.forEach { kartu ->
                            val isi = kartu.entries.filter { it.key != "Kolom" }.map { it.value }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WeMadeColors.Surface, ClayShapes.Chip)
                                    .padding(ClaySpacing.Sm),
                                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                            ) {
                                Text(
                                    isi.firstOrNull().orEmpty(),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                isi.drop(1).forEach { detail ->
                                    Text(
                                        detail,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                        color = WeMadeColors.OnSurfaceMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        WidgetKind.CHECKLIST -> Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        row.entries.firstOrNull()?.value ?: "",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 2
                    )
                    ClayBadge(
                        text = if (row["Selesai"] == "ya") "selesai" else "terbuka",
                        tint = if (row["Selesai"] == "ya") WeMadeColors.Success else WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
        WidgetKind.FORM -> Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            rows.firstOrNull()?.forEach { (k, v) ->
                if (k == "Simpan") {
                    ClayButton(text = v, onClick = {}, style = ClayButtonStyle.Secondary)
                } else {
                    Column {
                        Text(k, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
                        Text(
                            v,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(WeMadeColors.SurfaceMuted.copy(alpha = 0.14f), ClayShapes.Tile)
                                .padding(ClaySpacing.Sm)
                        )
                    }
                }
            }
        }
        WidgetKind.CUSTOM_SCREEN -> Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            // Kerangka, bukan isi: blok "penuh" berdiri sendiri, blok "separuh" dipasangkan dengan
            // tetangga berikutnya. Aturan pemasangan tinggal di renderer, bukan di sample — sample
            // cukup menyatakan blok apa yang ada.
            var index = 0
            while (index < rows.size) {
                val baris = rows[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    if (baris["Lebar"] == "penuh") {
                        CustomScreenBlock(baris["Blok"].orEmpty(), Modifier.fillMaxWidth())
                    } else {
                        CustomScreenBlock(baris["Blok"].orEmpty(), Modifier.weight(1f))
                        val pasangan = rows.getOrNull(index + 1)?.takeIf { it["Lebar"] != "penuh" }
                        if (pasangan != null) {
                            CustomScreenBlock(pasangan["Blok"].orEmpty(), Modifier.weight(1f))
                            index++
                        }
                    }
                }
                index++
            }
        }
        WidgetKind.TABLE -> Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            // Tabel nyata: header dari kunci baris pertama, lalu SEMUA baris data — bukan cuma
            // baris pertama seperti v1. Kolom sama berat; sampel v1 (kunci konsisten) ikut bentuk ini.
            val header = rows.firstOrNull()?.keys?.toList().orEmpty()
            // Lebar frame 360dp dibagi 5 kolom ≈ 60dp/kolom. Sel yang boleh wrap 2 baris membuat
            // "PT Sinar Jaya" pecah per kata (Kontrak 13). Baris tabel mockup dibuat satu baris
            // seperti tabel aplikasi nyata: teks menyusut via ellipsis, tidak pernah patah kata.
            if (header.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    header.forEach { k ->
                        Text(
                            k,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
                HorizontalDivider(color = WeMadeColors.Outline.copy(alpha = 0.3f))
            }
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    header.forEach { k ->
                        Text(
                            row[k].orEmpty(),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            // PRINT & kind tak dikenal: pasangan label–isi dokumen.
            rows.firstOrNull()?.forEach { (kolom, isi) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(kolom, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
                    Text(isi, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2)
                }
                HorizontalDivider(color = WeMadeColors.Outline.copy(alpha = 0.2f))
            }
            if (widget == "PRINT") {
                Text(
                    "Dokumen ini contoh tata letak cetak — isi nyata menyusul setelah modul dibangun.",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}

/**
 * Satu blok kerangka layar rancangan bebas — dipisah supaya [WidgetBody] tetap terbaca sebagai
 * susunan, bukan sebagai detail gaya. Rata (tanpa bayangan) karena ia menggambarkan *isi* kartu,
 * bukan kartu di atas kartu.
 */
@Composable
private fun CustomScreenBlock(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(WeMadeColors.SurfaceMuted.copy(alpha = 0.14f), ClayShapes.Tile)
            .padding(ClaySpacing.Md)
    )
}

