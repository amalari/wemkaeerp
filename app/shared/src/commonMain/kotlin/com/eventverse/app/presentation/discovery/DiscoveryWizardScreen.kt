package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.infrastructure.api.DiscoveryApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.launch

/**
 * `DiscoveryWizardScreen` (plan §5, R16 — Fase D): funnel empat langkah dari narasi prospek
 * sampai CTA "Bangun Sistem Ini".
 *
 * 1. **Narasi** — deskripsi kebutuhan → `POST /api/discovery/drafts`.
 * 2. **Draf** — pak terpilih + [ModuleMapPane] + [PrototypeRenderer].
 * 3. **Estimasi** — `GET /price`; rentang bisa jujur ditahan (`isPublishable=false`).
 * 4. **Kunci & Bangun** — `lock` lalu `submit` → lead prospek tercatat.
 *
 * Gerbang layarnya `isAuthenticated` + `accessDecisions.isNotEmpty()` (diputus di `App.kt`);
 * di dalam layar tidak ada logika wewenang lagi.
 */
@Composable
fun DiscoveryWizardScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val client = remember { DiscoveryApiClient() }

    var step by remember { mutableStateOf(1) }
    var narrative by remember { mutableStateOf("") }
    var industryHint by remember { mutableStateOf("") }
    var draftId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<DiscoveryDraftUi?>(null) }
    var price by remember { mutableStateOf<JsonValue.Obj?>(null) }
    var companyName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ClaySpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        Text("Studio Discovery", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            listOf("1 Narasi", "2 Draf", "3 Estimasi", "4 Bangun").forEachIndexed { i, label ->
                ClayBadge(
                    text = label,
                    tint = when {
                        step == i + 1 -> WeMadeColors.Primary
                        step > i + 1 -> WeMadeColors.Success
                        else -> WeMadeColors.OnSurfaceMuted
                    },
                    dot = step == i + 1
                )
            }
        }

        error?.let {
            ClayCard(modifier = Modifier.fillMaxWidth(), outlineColor = WeMadeColors.Error) {
                Text(it, color = WeMadeColors.Error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }

        when (step) {
            2 -> draft?.let { d ->
                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Text(d.packDisplayName, fontWeight = FontWeight.Bold)
                    Text(
                        "${d.activeModules.size} modul aktif • ${d.screens.size} layar pratinjau",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                Text("Peta Modul (read-only)", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                ModuleMapPane(draft = d)
                Text("Alur Data", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                DataFlowPane(draft = d)
                Text("Pratinjau Layar", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                PrototypeRenderer(draft = d)
                StepActions(onBack = { step = 1 }, onBackLabel = "Ubah Narasi", onNext = { step = 3 }, onNextLabel = "Lihat Estimasi")
            }
            3 -> draftId?.let { id ->
                if (price == null && !busy) {
                    busy = true
                    scope.launch {
                        client.price(id).onSuccess { price = it as? JsonValue.Obj }.onFailure { error = it.message }
                        busy = false
                    }
                }
                price?.let { p ->
                    ClayCard(modifier = Modifier.fillMaxWidth()) { EstimasiPrice(p) }
                }
                StepActions(
                    onBack = { step = 2 }, onBackLabel = "Kembali ke Draf",
                    onNext = { step = 4 }, onNextLabel = "Lanjut ke Pemesanan"
                )
            }
            4 -> StepBuild(
                companyName = companyName, busy = busy, draft = draft,
                onNameChange = { companyName = it },
                onSubmit = {
                    val id = draftId
                    if (id == null) {
                        error = "Draf belum tersimpan."
                    } else {
                        busy = true; error = null
                        scope.launch {
                            client.lock(id)
                                .mapCatching { client.submit(id, companyName.ifBlank { "Prospek Baru" }).getOrThrow() }
                                .onSuccess { step = 5 }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    }
                }
            )
            else -> ClayCard(modifier = Modifier.fillMaxWidth(), outlineColor = WeMadeColors.Success) {
                Text(
                    "Terima kasih! Tim kami akan menghubungi Anda untuk membangun sistem ini.",
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Success
                )
            }
        }
    }
}
@Composable
private fun StepNarrative(
    narrative: String,
    industryHint: String,
    busy: Boolean,
    onNarrativeChange: (String) -> Unit,
    onHintChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Text("Ceritakan kebutuhan pabrik Anda", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = narrative,
            onValueChange = onNarrativeChange,
            modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
            minLines = 4,
            placeholder = { Text("Contoh: pabrik konveksi makloon, perlu pencatatan SPK jahit dan laporan harian lini…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeMadeColors.Primary,
                unfocusedBorderColor = WeMadeColors.Outline
            )
        )
        OutlinedTextField(
            value = industryHint,
            onValueChange = onHintChange,
            modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
            singleLine = true,
            placeholder = { Text("Industri (opsional): konveksi, bordir, sablon…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeMadeColors.Primary,
                unfocusedBorderColor = WeMadeColors.Outline
            )
        )
        ClayButton(
            text = if (busy) "Menyusun…" else "Susun Draf Sistem",
            onClick = onSubmit,
            enabled = !busy,
            modifier = Modifier.padding(top = ClaySpacing.Md)
        )
    }
}

@Composable
private fun StepBuild(
    companyName: String,
    busy: Boolean,
    draft: DiscoveryDraftUi?,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Text("Bangun Sistem Ini", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Kunci draf lalu kirim permintaan pembangunan. Draf yang sudah dikunci tidak bisa diubah lagi.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted
        )
        OutlinedTextField(
            value = companyName,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
            singleLine = true,
            placeholder = { Text("Nama perusahaan Anda") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeMadeColors.Primary,
                unfocusedBorderColor = WeMadeColors.Outline
            )
        )
        ClayButton(
            text = if (busy) "Mengirim…" else "Kunci & Bangun Sistem Ini",
            onClick = onSubmit,
            enabled = !busy,
            style = ClayButtonStyle.Accent,
            modifier = Modifier.padding(top = ClaySpacing.Md)
        )
    }
}

@Composable
private fun EstimasiPrice(p: JsonValue.Obj) {
    Text("Estimasi Langganan", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    val monthly = (p.entries["subscriptionMonthlyIdr"] as? JsonValue.Num)?.raw?.toDoubleOrNull()
    val withheld = p.entries["withheld"] == JsonValue.Bool(true)
    if (withheld) {
        Text(
            "Untuk industri ini kami belum punya riwayat pembangunan yang cukup, jadi rentang biaya ditahan " +
                "sampai survei singkat bersama tim kami. Estimasi langganan: ${rupiah(monthly)}.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
    } else {
        val low = (p.entries["gapLowMonthlyIdr"] as? JsonValue.Num)?.raw?.toDoubleOrNull()
        val high = (p.entries["gapHighMonthlyIdr"] as? JsonValue.Num)?.raw?.toDoubleOrNull()
        Text(
            "Biaya pembangunan sekali: ${low?.let { rupiah(it) } ?: "—"} – ${high?.let { rupiah(it) } ?: "—"}" +
                " • Langganan: ${rupiah(monthly)}/bulan",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
    }
}

/** Format Rupiah tanpa `String.format` (tidak tersedia di commonMain KMP). */
private fun rupiah(value: Double?): String {
    if (value == null) return "—"
    val n = value.toLong()
    val grouped = n.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $grouped"
}

@Composable
private fun StepActions(
    onBack: () -> Unit,
    onBackLabel: String,
    onNext: () -> Unit,
    onNextLabel: String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        ClayButton(text = onBackLabel, onClick = onBack, style = ClayButtonStyle.Secondary)
        ClayButton(text = onNextLabel, onClick = onNext)
    }
}
