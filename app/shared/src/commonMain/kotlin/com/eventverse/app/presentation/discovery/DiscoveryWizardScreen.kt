package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.infrastructure.api.DiscoveryApiClient
import com.eventverse.app.infrastructure.storage.PlatformLocalStorage
import com.eventverse.app.presentation.deal.openInBrowser
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
    // Autosave narasi (plan §6 Fase E): cerita yang belum dikirim dipulihkan dari perangkat ini,
    // jadi tab yang tertutup tidak lagi memakan cerita prospek. Simpanannya dihapus saat draf
    // berhasil dibuat (lihat onSubmit) — narasi kini hidup di server, buku demand.
    var narrative by remember { mutableStateOf(PlatformLocalStorage.getItem(NARRATIVE_DRAFT_KEY) ?: "") }
    var industryHint by remember { mutableStateOf(PlatformLocalStorage.getItem(HINT_DRAFT_KEY) ?: "") }
    var draftId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<DiscoveryDraftUi?>(null) }
    var price by remember { mutableStateOf<JsonValue.Obj?>(null) }
    var companyName by remember { mutableStateOf("") }
    // Sesi interview persisten (plan §6 E1): draf DRAFT milik pengguna ini bisa dilanjutkan —
    // ringkasan penuh, jadi resume langsung ke langkah 2 tanpa fetch kedua. Kegagalan daftar
    // tidak memblokir funnel baru: data sesi lama tetap aman di server, yang hilang hanya
    // pintu lanjutannya sampai kunjungan berikutnya.
    var resumeCandidates by remember { mutableStateOf<List<DiscoveryDraftUi>>(emptyList()) }
    LaunchedEffect(Unit) {
        client.listDrafts().mapCatching { raw ->
            (raw as? JsonValue.Arr)?.items.orEmpty()
                .filterIsInstance<JsonValue.Obj>()
                .filter { it.string("status") == "DRAFT" }
                .mapNotNull { obj -> runCatching { DiscoveryDraftUi.fromJson(obj) }.getOrNull() }
        }.onSuccess { resumeCandidates = it }
    }

    var busy by remember { mutableStateOf(false) }
    var pdfBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    /**
     * Unduh PDF blueprint. Tautannya dibuka di tab baru (Wasm/JS) karena tab tidak bisa membawa
     * token sesi — server menerbitkan tiket pendek khusus draf ini (lihat `blueprintPdfUrl`).
     */
    fun openBlueprintPdf(id: String) {
        pdfBusy = true; error = null
        scope.launch {
            client.blueprintPdfUrl(id)
                .onSuccess { openInBrowser(it) }
                .onFailure { error = it.message ?: "Gagal menyiapkan PDF blueprint" }
            pdfBusy = false
        }
    }

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

        if (step == 1 && resumeCandidates.isNotEmpty()) {
            ResumeDraftsCard(
                candidates = resumeCandidates,
                busy = busy,
                onResume = { d ->
                    busy = true; error = null
                    // E1/E2: pulihkan cerita aslinya — "Ubah Narasi" tidak boleh kosong.
                    // Autosave mengikuti isi textarea: sesi yang dilanjutkan kini pemilik ceritanya.
                    d.narrative?.let {
                        narrative = it
                        PlatformLocalStorage.setItem(NARRATIVE_DRAFT_KEY, it)
                    }
                    draft = d; draftId = d.id; step = 2
                    busy = false
                }
            )
        }

        // Cabang per langkah **eksplisit** — dulu `else` dipakai untuk pesan "terima kasih", sehingga
        // langkah 1 (narasi) tidak pernah tampil dan prospek langsung diberi tahu ia sudah mengirim
        // permintaan. Ketahuan lewat pengecekan mata di peramban, bukan lewat test mana pun.
        when (step) {
            1 -> StepNarrative(
                narrative = narrative,
                industryHint = industryHint,
                busy = busy,
                onNarrativeChange = {
                    narrative = it
                    // Autosave di tiap ketikan: localStorage murah, cerita prospek tidak.
                    PlatformLocalStorage.setItem(NARRATIVE_DRAFT_KEY, it)
                },
                onHintChange = {
                    industryHint = it
                    PlatformLocalStorage.setItem(HINT_DRAFT_KEY, it)
                },
                onSubmit = {
                    if (narrative.isBlank()) {
                        error = "Ceritakan dulu kebutuhan bisnis Anda."
                    } else {
                        busy = true; error = null
                        scope.launch {
                            client.createDraft(narrative.trim(), industryHint.trim().ifBlank { null })
                                .mapCatching { raw ->
                                    DiscoveryDraftUi.fromJson(
                                        raw as? JsonValue.Obj
                                            ?: throw IllegalStateException("Respons draf tidak dikenali")
                                    )
                                }
                                .onSuccess { d ->
                                    // Narasi kini hidup di server (buku demand) — simpanan lokal
                                    // pensiun; dua salinan hidup = resep data bertentangan.
                                    PlatformLocalStorage.removeItem(NARRATIVE_DRAFT_KEY)
                                    PlatformLocalStorage.removeItem(HINT_DRAFT_KEY)
                                    draft = d; draftId = d.id; step = 2
                                }
                                .onFailure { error = it.message ?: "Gagal menyusun draf" }
                            busy = false
                        }
                    }
                }
            )
            2 -> draft?.let { d ->
                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Text(d.packDisplayName, fontWeight = FontWeight.Bold)
                    Text(
                        "${d.activeModules.size} modul aktif • ${d.screens.size} layar pratinjau",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    ClayButton(
                        text = if (pdfBusy) "Menyiapkan PDF…" else "Unduh Blueprint (PDF)",
                        onClick = { openBlueprintPdf(d.id) },
                        enabled = !pdfBusy,
                        style = ClayButtonStyle.Secondary,
                        modifier = Modifier.padding(top = ClaySpacing.Sm)
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
                companyName = companyName, busy = busy,
                onNameChange = { companyName = it },
                onBack = { step = 3 },
                onSubmit = {
                    val id = draftId
                    if (id == null) {
                        error = "Draf belum tersimpan."
                    } else {
                        busy = true; error = null
                        scope.launch {
                            client.lock(id)
                                // Tanpa `ifBlank { … }`: nama kosong pernah menjadi data "Prospek Baru"
                                // di ledger prospek — fallback diam yang membuat laporan tim menyebut
                                // perusahaan yang tidak pernah ada. Endpoint memang menolak nama kosong,
                                // jadi gerbangnya di sini (lihat StepBuild: tombol mati saat nama kosong).
                                .mapCatching { client.submit(id, companyName.trim()).getOrThrow() }
                                .onSuccess { step = 5 }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    }
                }
            )
            5 -> ClayCard(modifier = Modifier.fillMaxWidth(), outlineColor = WeMadeColors.Success) {
                Text(
                    "Terima kasih! Tim kami akan menghubungi Anda untuk membangun sistem ini.",
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Success
                )
            }
        }
    }
}
/**
 * Kunci autosave narasi wizard (plan §6 Fase E — sisa item "jendela kehilangan"): cerita yang
 * sedang diketik disimpan per perangkat lewat [PlatformLocalStorage] pada tiap ketikan dan
 * dipulihkan saat layar dibuka. Simpanannya dihapus saat draf berhasil dibuat — narasi kini
 * hidup di server (buku demand), dan dua salinan hidup adalah resep data bertentangan.
 * Desktop/JVM memakai penyimpanan in-memory: di sana autosave hanya melindungi ganti layar,
 * bukan tutup aplikasi — jujur pada kemampuan platformnya.
 */
private const val NARRATIVE_DRAFT_KEY = "discovery_narrative_draft"
private const val HINT_DRAFT_KEY = "discovery_hint_draft"

@Composable
private fun ResumeDraftsCard(
    candidates: List<DiscoveryDraftUi>,
    busy: Boolean,
    onResume: (DiscoveryDraftUi) -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth(), outlineColor = WeMadeColors.Primary) {
        Text(
            "Lanjutkan sesi sebelumnya",
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        candidates.take(3).forEach { d ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                // Yang boleh menyusut dinyatakan eksplisit (design-system Kontrak 13).
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(d.packDisplayName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        "${d.activeModules.size} modul aktif • draf belum dikunci",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.width(ClaySpacing.Sm))
                ClayButton(text = "Lanjutkan", onClick = { onResume(d) }, enabled = !busy)
            }
        }
    }
}
