package com.eventverse.app.presentation.discovery.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.eventverse.app.domain.discovery.DemandLedger
import com.eventverse.app.infrastructure.api.DiscoveryApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/** Satu kandidat Rule of Three (E3) yang sudah dibongkar dari JSON respons. */
internal data class DemandCandidateUi(val term: String, val demandCount: Int, val samples: List<String>)

/** Sinyal gerbang dari `GET /api/discovery/demands`: ambang + kandidat yang menembusnya. */
internal data class DemandSignalsUi(val minimum: Int, val candidates: List<DemandCandidateUi>)

/**
 * Bongkar sinyal gerbang dari objek JSON respons `GET /api/discovery/demands`. Dipakai dua layar
 * Studio — Buku Demand (daftar penuh) dan kartu gerbang di Studio Pola — jadi parsing dan kartu
 * kandidatnya diangkat ke sini, bukan disalin (Aturan Tiga Kali, design-system Kontrak 4).
 */
internal fun parseDemandSignals(obj: JsonValue.Obj): DemandSignalsUi = DemandSignalsUi(
    minimum = obj.int("minimum") ?: DemandLedger.RULE_OF_THREE,
    candidates = obj.array("candidates").filterIsInstance<JsonValue.Obj>().map { c ->
        DemandCandidateUi(
            term = c.string("term").orEmpty(),
            demandCount = c.int("demandCount") ?: 0,
            samples = c.array("samples").filterIsInstance<JsonValue.Str>().map { it.value }
        )
    }
)

/**
 * Satu kartu kandidat: outline Primary — bukan ketebalan berbeda (design-system Kontrak 8) —
 * karena inilah sinyal yang menentukan; badge jumlah demand; kutipan narasi asli supaya pembaca
 * menilai konteksnya, bukan kata kaosnya.
 */
@Composable
internal fun DemandCandidateCard(candidate: DemandCandidateUi, modifier: Modifier = Modifier) {
    ClayCard(
        modifier = modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
        outlineColor = WeMadeColors.Primary
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Text(candidate.term, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            ClayBadge(text = "${candidate.demandCount} demand", tint = WeMadeColors.Primary)
        }
        candidate.samples.forEach { sample ->
            Text(
                "\u201C$sample\u201D",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Xs)
            )
        }
    }
}

/**
 * Gerbang Rule of Three di titik keputusan widget Studio (plan D5): kosakata widget tertutup, dan
 * menambah kind berarti mengubah renderer — jadi keputusan itu hanya layak bila kandidat di sini
 * yang menuntutnya. Dipasang di Studio Pola, tepat setelah kartu berisi pilihan widget.
 *
 * Gerbang baca buku demand tetap dipegang server (superadmin saja); [canWrite] — kunci yang sama
 * dengan gerbang tulis pola — hanya memutuskan apakah layar menembak endpoint. Non-superadmin
 * tidak buang request (server pasti 403); kartunya cukup menjelaskan gerbangnya.
 */
@Composable
internal fun WidgetDemandGateCard(canWrite: Boolean, modifier: Modifier = Modifier) {
    val client = remember { DiscoveryApiClient() }
    var signals by remember { mutableStateOf<DemandSignalsUi?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(canWrite) {
        if (!canWrite) return@LaunchedEffect
        client.listDemands()
            .mapCatching { raw ->
                parseDemandSignals(raw as? JsonValue.Obj ?: error("Respons buku demand tidak dikenali"))
            }
            .onSuccess { signals = it }
            .onFailure { failure = it.message ?: "Gagal memuat sinyal demand" }
    }

    ClayCard(modifier = modifier.fillMaxWidth()) {
        Text(
            "Gerbang Rule of Three (plan D5)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Kosakata widget tertutup — menambah kind berarti mengubah renderer. Widget baru hanya " +
                "layak dibangun bila istilah yang sama diminta cukup demand berbeda; di bawah ambang " +
                "adalah derau, bukan peta jalan.",
            style = MaterialTheme.typography.labelSmall,
            color = WeMadeColors.OnSurfaceMuted,
            modifier = Modifier.padding(top = ClaySpacing.Xs)
        )
        val data = signals
        when {
            !canWrite -> Text(
                "Sinyal demand hanya untuk superadmin platform — buku demand dijaga gerbang server.",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Sm)
            )
            failure != null -> Text(
                failure.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.Error,
                modifier = Modifier.padding(top = ClaySpacing.Sm)
            )
            data == null -> Text(
                "Memuat sinyal demand…",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Sm)
            )
            data.candidates.isEmpty() -> Text(
                "Belum ada istilah yang menembus ambang ${data.minimum} demand — kosong = tidak ada " +
                    "widget baru yang layak dibangun saat ini.",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Sm)
            )
            else -> {
                Text(
                    "Kandidat widget/modul berikutnya (≥ ${data.minimum} demand berbeda):",
                    style = MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(top = ClaySpacing.Sm)
                )
                data.candidates.take(CANDIDATES_SHOWN).forEach { candidate -> DemandCandidateCard(candidate) }
                val hidden = data.candidates.size - CANDIDATES_SHOWN
                if (hidden > 0) Text(
                    "…dan $hidden kandidat lagi — antrean penuhnya di Buku Demand.",
                    style = MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(top = ClaySpacing.Xs)
                )
            }
        }
    }
}
/** Kartu gerbang menampilkan puncak antrean saja; sisanya merujuk ke Buku Demand. */
private const val CANDIDATES_SHOWN = 3
