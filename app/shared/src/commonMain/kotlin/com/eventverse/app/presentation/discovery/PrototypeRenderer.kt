package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * `PrototypeRenderer` (plan §4, Fase C/D): menggambar setiap deskriptor layar draf. Sample data
 * **berupa data** — dihitung `WidgetRegistry` di server dan dikirim dalam ringkasan draf, jadi
 * renderer hanya memetakan kind → layout. Semua kind memakai bahasa Clay; tidak ada warna literal.
 */
@Composable
fun PrototypeRenderer(
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
        if (draft.screens.isEmpty()) {
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
        draft.screens.forEach { screen ->
            val module = draft.modules.firstOrNull { it.id == screen.moduleId }
            PrototypeScreenCard(
                title = screen.title,
                widget = screen.widget,
                moduleName = module?.displayName ?: screen.moduleId,
                rows = screen.sampleRows
            )
        }
    }
}

@Composable
private fun PrototypeScreenCard(
    title: String,
    widget: String,
    moduleName: String,
    rows: List<Map<String, String>>
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
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
        WidgetBody(widget = widget, rows = rows)
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
        WidgetKind.KANBAN -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            rows.take(3).forEach { row ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(WeMadeColors.SurfaceMuted.copy(alpha = 0.14f), ClayShapes.Tile)
                        .padding(ClaySpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    row.forEach { (k, v) ->
                        Text(k, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
                        Text(v, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, maxLines = 2)
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
        else -> Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            // TABLE & PRINT: tabel sederhana — baris pertama jadi pasangan judul–isi.
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

