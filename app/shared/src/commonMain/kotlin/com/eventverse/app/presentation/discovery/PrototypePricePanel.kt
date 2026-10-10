package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayToggleTag
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * Panel "Paket & Harga" di samping prototype: pilih modul yang dipakai, harga langsung dihitung ulang
 * oleh server (`GET /api/builder/draft/price?modules=…`, rumus yang sama dengan wizard). Estimasi, bukan penawaran
 * mengikat — kata itu sengaja ditulis di panel supaya tidak dibaca sebagai janji.
 */
@Composable
fun PrototypePricePanel(
    draft: DiscoveryDraftUi,
    included: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val client = remember { BuilderApiClient() }
    var price by remember { mutableStateOf<DraftPriceUi?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val modulesWithScreens = draft.modules.filter { m -> draft.screens.any { it.moduleId == m.id } }

    LaunchedEffect(draft.id, included) {
        if (included.isEmpty()) { price = null; error = "Pilih minimal satu modul untuk melihat harga."; return@LaunchedEffect }
        loading = true
        client.draftPrice(modules = included.sorted())
            .onSuccess { price = (it as? JsonValue.Obj)?.let(DraftPriceUi::fromJson); error = null }
            .onFailure { error = it.message ?: "Gagal menghitung estimasi" }
        loading = false
    }

    ClayCard(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Paket & Harga", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            ClayBadge(text = "Estimasi", tint = WeMadeColors.Info, dot = true)
        }
        Text(
            "Pilih modul yang akan dipakai. Layar dan harga mengikuti pilihan Anda.",
            style = MaterialTheme.typography.labelSmall,
            color = WeMadeColors.OnSurfaceMuted
        )
        ClayFlowRow(modifier = Modifier.fillMaxWidth(), spacing = ClaySpacing.Sm) {
            modulesWithScreens.forEach { m ->
                ClayToggleTag(text = m.displayName, tint = WeMadeColors.Primary, active = m.id in included, onToggle = { onToggle(m.id) })
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = ClaySpacing.Sm), color = WeMadeColors.Outline.copy(alpha = 0.3f))
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = WeMadeColors.Defect) }
        price?.let { PriceBody(it, loading) }
    }
}

@Composable
private fun PriceBody(p: DraftPriceUi, loading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        p.lines.forEach { l ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(l.displayName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
                Text(
                    when {
                        l.covered -> "${rupiah(l.monthlyIdr?.toDouble())}/bln"
                        l.gapLowIdr != null && l.gapHighIdr != null -> "dibangun · ${rupiah(l.gapLowIdr.toDouble())}-${rupiah(l.gapHighIdr.toDouble())}/bln"
                        else -> "dibangun · perlu survei"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (l.covered) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted
                )
            }
        }
        HorizontalDivider(color = WeMadeColors.Outline.copy(alpha = 0.3f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Langganan", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text("${rupiah(p.subscriptionMonthlyIdr.toDouble())}/bulan", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            when {
                p.withheld -> "Biaya pembangunan modul baru ditahan sampai survei singkat bersama tim kami."
                p.hasBuildCost -> "Modul baru: ${rupiah(p.gapLowMonthlyIdr?.toDouble())}-${rupiah(p.gapHighMonthlyIdr?.toDouble())}/bulan tambahan."
                else -> "Semua modul terpilih sudah tersedia - tidak ada biaya pembangunan tambahan."
            },
            style = MaterialTheme.typography.labelSmall,
            color = WeMadeColors.OnSurfaceMuted
        )
        Text(
            if (loading) "Menghitung ulang..." else "Estimasi untuk perencanaan, bukan penawaran mengikat.",
            style = MaterialTheme.typography.labelSmall,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}
