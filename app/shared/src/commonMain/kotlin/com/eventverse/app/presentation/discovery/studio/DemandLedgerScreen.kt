package com.eventverse.app.presentation.discovery.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.infrastructure.api.DiscoveryApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/** Satu baris buku demand. */
private data class DemandRowUi(
    val id: String,
    val narrative: String,
    val agentRef: String,
    val createdAt: String?,
    val unmatchedTerms: List<String>
)

private data class DemandLedgerUi(val minimum: Int, val candidates: List<DemandCandidateUi>, val demands: List<DemandRowUi>)

/**
 * Buku demand (plan §6, E2/E3): antrean review platform atas narasi prospek — apa yang diminta
 * verbatim, apa yang bisa diekspresikan generator, dan istilah mana yang menembus ambang Rule
 * of Three (≥ 3 demand berbeda). Gerbang baca dipegang server (superadmin saja, 403 untuk lain);
 * [isSuperadmin] di sini hanya untuk **menjelaskan** gerbang itu sebelum pengguna menabraknya.
 */
@Composable
fun DemandLedgerScreen(isSuperadmin: Boolean, modifier: Modifier = Modifier) {
    val client = remember { DiscoveryApiClient() }
    var ledger by remember { mutableStateOf<DemandLedgerUi?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(isSuperadmin) {
        // Non-superadmin: jangan buang request — server pasti 403, dan kartu penjelasan di bawah
        // sudah menjelaskan gerbangnya sebelum pengguna menabraknya.
        if (!isSuperadmin) { loaded = true; return@LaunchedEffect }
        client.listDemands().mapCatching { raw ->
            val obj = raw as? JsonValue.Obj ?: error("Respons buku demand tidak dikenali")
            val signals = parseDemandSignals(obj)
            DemandLedgerUi(
                minimum = signals.minimum,
                candidates = signals.candidates,
                demands = obj.array("demands").filterIsInstance<JsonValue.Obj>().map { d ->
                    DemandRowUi(
                        id = d.string("id").orEmpty(),
                        narrative = d.string("narrative").orEmpty(),
                        agentRef = d.string("agentRef").orEmpty(),
                        createdAt = d.string("createdAt"),
                        unmatchedTerms = d.array("unmatchedTerms").filterIsInstance<JsonValue.Str>().map { it.value }
                    )
                }
            )
        }.onSuccess { ledger = it; loaded = true }
            .onFailure { error = it.message ?: "Gagal memuat buku demand"; loaded = true }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaySpacing.Xl)
    ) {
        Text("Buku Demand", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        if (!isSuperadmin) {
            ClayCard(modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Md), outlineColor = WeMadeColors.Warning) {
                Text(
                    "Buku demand hanya untuk superadmin platform: isinya sinyal produk lintas-prospek " +
                        "(peta jalan), bukan data satu tenant.",
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            return@Column
        }

        error?.let {
            ClayCard(modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Md), outlineColor = WeMadeColors.Error) {
                Text(it, color = WeMadeColors.Error)
            }
            return@Column
        }

        val data = ledger
        if (data == null) {
            if (!loaded) {
                Text(
                    "Memuat…",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(top = ClaySpacing.Md)
                )
            }
            return@Column
        }

        // Kandidat Rule of Three di atas: inilah yang menentukan widget/modul berikutnya.
        Text(
            "Kandidat Rule of Three (≥ ${data.minimum} demand berbeda)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = ClaySpacing.Lg)
        )
        if (data.candidates.isEmpty()) {
            Text(
                "Belum ada istilah yang menembus ambang — di bawah ambang adalah derau, bukan peta jalan.",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Sm)
            )
        } else {
            data.candidates.forEach { c ->
                DemandCandidateCard(c)
            }
        }

        // Baris demand: narasi verbatim + istilah yang belum terwakili modul mana pun.
        Text(
            "Semua demand (${data.demands.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = ClaySpacing.Lg)
        )
        data.demands.forEach { d ->
            ClayCard(modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm)) {
                Text(d.narrative)
                if (d.unmatchedTerms.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                        modifier = Modifier.padding(top = ClaySpacing.Sm)
                    ) {
                        d.unmatchedTerms.take(6).forEach { term ->
                            ClayTag(text = term, tint = WeMadeColors.Warning)
                        }
                    }
                }
                Text(
                    listOfNotNull(d.agentRef, d.createdAt?.take(10)).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(top = ClaySpacing.Sm)
                )
            }
        }
    }
}
