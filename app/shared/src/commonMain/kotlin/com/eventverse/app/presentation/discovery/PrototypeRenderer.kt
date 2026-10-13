package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayPaneWidth
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.IconArrowBack
import com.eventverse.app.presentation.designsystem.IconArrowForward
import com.eventverse.app.presentation.designsystem.claySurface

/**
 * `PrototypeRenderer` (plan §4, Fase C/D): menggambar setiap deskriptor layar draf. Sample data
 * **berupa data** — dihitung `WidgetRegistry` di server dan dikirim dalam ringkasan draf, jadi
 * renderer hanya memetakan kind → layout. Semua kind memakai bahasa Clay; tidak ada warna literal.
 * Parameter [screens] memungkinkan pane memfilter pratinjau per modul tanpa logika render baru.
 *
 * Mendukung mode [carousel] interaktif kiri-kanan dan [fullWidth] agar tampilan tabel/form leluasa.
 */
@Composable
fun PrototypeRenderer(
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier,
    screens: List<DiscoveryScreenUi> = draft.screens,
    session: PrototypeSession = remember(draft.screens) { PrototypeSession(draft.screens) },
    carousel: Boolean = false,
    fullWidth: Boolean = false
) {
    if (screens.isEmpty()) {
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Draf ini belum punya layar pratinjau. Deskriptor layar diusulkan oleh agent LLM - " +
                    "jalankan server dengan DISCOVERY_AGENT=koog dan DEEPSEEK_API_KEY terisi untuk melihatnya, " +
                    "atau pilih pola Studio setelah modul dibangun.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
        return
    }

    if (carousel && screens.size > 1) {
        var currentIndex by remember(screens) { mutableStateOf(0) }
        val safeIndex = currentIndex.coerceIn(0, screens.lastIndex)
        val currentScreen = screens[safeIndex]
        val currentModule = draft.modules.firstOrNull { it.id == currentScreen.moduleId }

        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            // Carousel Navigation Bar Kiri-Kanan
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Pill,
                containerColor = WeMadeColors.Surface,
                offset = ClayOffset.Small
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayIconButton(
                        onClick = { if (safeIndex > 0) currentIndex = safeIndex - 1 },
                        enabled = safeIndex > 0,
                        size = 32.dp
                    ) {
                        IconArrowBack(
                            Modifier.size(16.dp),
                            color = if (safeIndex > 0) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            Text(
                                text = "Layar ${safeIndex + 1} dari ${screens.size}:",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                            Text(
                                text = currentScreen.title,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            ClayBadge(
                                text = currentScreen.widget.uppercase(),
                                tint = WeMadeColors.Accent
                            )
                        }

                        // Dot indicators
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            screens.forEachIndexed { idx, _ ->
                                Box(
                                    modifier = Modifier
                                        .height(6.dp)
                                        .width(if (idx == safeIndex) 20.dp else 6.dp)
                                        .background(
                                            if (idx == safeIndex) WeMadeColors.Primary else WeMadeColors.OutlineSoft,
                                            CircleShape
                                        )
                                        .clickable { currentIndex = idx }
                                )
                            }
                        }
                    }

                    ClayIconButton(
                        onClick = { if (safeIndex < screens.lastIndex) currentIndex = safeIndex + 1 },
                        enabled = safeIndex < screens.lastIndex,
                        size = 32.dp
                    ) {
                        IconArrowForward(
                            Modifier.size(16.dp),
                            color = if (safeIndex < screens.lastIndex) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }

            // Quick Jump Screen Pills
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                screens.forEachIndexed { idx, s ->
                    val isCurrent = idx == safeIndex
                    Row(
                        modifier = Modifier
                            .claySurface(
                                shape = ClayShapes.Pill,
                                background = if (isCurrent) WeMadeColors.Primary else WeMadeColors.Surface,
                                outline = if (isCurrent) WeMadeColors.Outline else WeMadeColors.OutlineSoft,
                                offset = if (isCurrent) ClayOffset.Pressed else ClayOffset.Flat,
                                borderWidth = ClayBorder.Hairline
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { currentIndex = idx }
                            .padding(horizontal = ClaySpacing.Sm, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${idx + 1}. ${s.title}",
                            fontSize = 11.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrent) Color.White else WeMadeColors.OnSurface
                        )
                    }
                }
            }

            // Active Screen Card — FULL WIDTH & FULL HEIGHT!
            PrototypeScreenCard(
                modifier = Modifier.fillMaxWidth().weight(1f).fillMaxHeight(),
                title = currentScreen.title,
                widget = currentScreen.widget,
                moduleName = currentModule?.displayName ?: currentScreen.moduleId,
                rows = currentScreen.sampleRows,
                block = session.block(currentScreen.screenId),
                rationale = currentScreen.rationale,
                source = currentScreen.source
            )
        }
        return
    }

    if (carousel || fullWidth) {
        // Single screen (atau non-carousel full width) — FULL WIDTH & FULL HEIGHT!
        Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
            screens.forEach { screen ->
                val module = draft.modules.firstOrNull { it.id == screen.moduleId }
                PrototypeScreenCard(
                    modifier = Modifier.fillMaxWidth().weight(1f).fillMaxHeight(),
                    title = screen.title,
                    widget = screen.widget,
                    moduleName = module?.displayName ?: screen.moduleId,
                    rows = screen.sampleRows,
                    block = session.block(screen.screenId),
                    rationale = screen.rationale,
                    source = screen.source
                )
            }
        }
        return
    }

    // Default flow row (legacy phone frame width)
    ClayFlowRow(modifier = modifier.fillMaxWidth(), spacing = ClaySpacing.Lg) {
        screens.forEach { screen ->
            val module = draft.modules.firstOrNull { it.id == screen.moduleId }
            PrototypeScreenCard(
                modifier = Modifier.width(ClayPaneWidth.PrototypeDevice),
                title = screen.title,
                widget = screen.widget,
                moduleName = module?.displayName ?: screen.moduleId,
                rows = screen.sampleRows,
                block = session.block(screen.screenId),
                rationale = screen.rationale,
                source = screen.source
            )
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
    block: PlayableState? = null,
    rationale: String? = null,
    source: com.eventverse.app.domain.discovery.proposal.ProposalSource? = null
) {
    ClayCard(modifier = modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.padding(top = ClaySpacing.Xs),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    // Kontrak 13: nama modul boleh mengalah; lencana sumber tidak boleh patah per huruf.
                    Text(
                        moduleName,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (source != null) {
                        ProposalSourceBadge(source = source)
                    }
                }
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

        if (!rationale.isNullOrBlank()) {
            ProposalRationaleRow(
                rationale = rationale,
                modifier = Modifier.padding(top = ClaySpacing.Xs)
            )
        }

        // Layar berbinding Api tak punya baris contoh (datanya dari server) tetapi punya blok interaktif.
        if (rows.isEmpty() && block == null) {
            ProposalIncompleteWarning(
                widget = widget,
                rationale = rationale,
                modifier = Modifier.padding(top = ClaySpacing.Sm)
            )
            return@ClayCard
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = ClaySpacing.Sm), color = WeMadeColors.Outline.copy(alpha = 0.3f))
        // Blok yang bisa dimainkan menggantikan gambar statis; widget lain / baris tak sah tetap statis.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            if (block != null) InteractiveBlock(block) else WidgetBody(widget = widget, rows = rows)
        }
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
        WidgetKind.CUSTOM_SCREEN -> SkeletonSketch(rows)
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
                            modifier = Modifier.weight(1f)
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
                            modifier = Modifier.weight(1f)
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
                    // Nilai panjang (alamat, isi kiriman) boleh mengalah dan rata kanan, tidak menjepit labelnya (Kontrak 13).
                    Text(
                        isi,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f, fill = false).padding(start = ClaySpacing.Md)
                    )
                }
                HorizontalDivider(color = WeMadeColors.Outline.copy(alpha = 0.2f))
            }
            if (widget == "PRINT") {
                Text(
                    "Dokumen ini contoh tata letak cetak - isi nyata menyusul setelah modul dibangun.",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}
