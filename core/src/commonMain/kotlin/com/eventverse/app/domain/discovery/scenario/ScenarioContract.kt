package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.tenant.TenantId

/** Satu temuan validator skenario; [path] menunjuk bagian yang salah (mis. `steps[1].carries[0].toField`). */
data class ScenarioIssue(val path: String, val message: String)

/**
 * Validator skenario (TRD-BLDR-002 FR-2) terhadap [draft] tempat ia akan hidup: langkah ≤ [ScenarioLimits.STEPS_MAX]; modul
 * aktif di pack; layar milik modul itu; field `carries` ada di entitas kedua layar dan tipenya dapat dikoersi; `reworkTo` hanya
 * ke langkah **sebelumnya**; judul dan label dalam batas. Murni, tanpa I/O. Daftar kosong = sah. Implementasi: Track A.
 */
fun interface ScenarioValidator {
    fun validate(draft: DiscoveryDraft, scenario: ScenarioSketch): List<ScenarioIssue>
}

/** Ringkasan satu modul sebagai masukan pengusul (dari BRIEF TRD-BLDR-001 atau data draf bila brief belum ada). */
data class ScenarioModuleInput(val moduleId: String, val name: String, val summary: String?, val stageNames: List<String>)

/** Satu sambungan antar modul dari wawancara (`ModuleHandoff`): [from] menyerahkan dokumen [portLabel] ke [to]. */
data class ScenarioHandoffInput(val from: String, val to: String, val portLabel: String)

/** Layar draf yang boleh dipakai langkah: [fieldKeys] kunci field entitasnya, supaya pengusul hanya memetakan field nyata. */
data class ScenarioScreenInput(val moduleId: String, val screenId: String, val title: String, val fieldKeys: List<String>)

/**
 * Masukan pengusul skenario. Semua teks adalah **data** (narasi pengguna, label modul dari pack tenant) — bukan instruksi.
 * [feedback] terisi pada percobaan ulang (galat validator sebelumnya, berpath).
 */
data class ScenarioProposalRequest(
    val tenantId: TenantId,
    val narrative: String,
    val modules: List<ScenarioModuleInput>,
    val handoffs: List<ScenarioHandoffInput> = emptyList(),
    val screens: List<ScenarioScreenInput> = emptyList(),
    val feedback: String? = null
)

/**
 * Pengusul skenario (model di server; fallback deterministik di balik interface yang sama — pola `ModuleBriefer`). Hanya
 * **mengusulkan**: keluaran tidak tepercaya, divalidasi [ScenarioValidator], dan hasil yang disimpan selalu
 * `confirmed = false`. Maksimal [ScenarioLimits.SCENARIOS_MAX]; daftar kosong adalah jawaban sah (tidak mengarang).
 */
fun interface ScenarioProposer {
    suspend fun propose(request: ScenarioProposalRequest): Result<List<ScenarioSketch>>
}

/** Hasil membawa data satu langkah ke langkah berikutnya: [values] siap jadi baris baru; [rejected] pasangan yang tak dapat dibawa. */
data class CarryOutcome(val values: Map<String, String>, val rejected: List<ScenarioIssue> = emptyList())

/**
 * Aturan pembawa data simulasi (FR-6): menerjemahkan nilai baris asal menjadi nilai awal baris tujuan menurut
 * [FieldCarry], dengan koersi tipe yang sama dengan jalur isian lain. Pasangan yang tak dapat dibawa (field hilang, tipe tak
 * kompatibel) **dilaporkan di [CarryOutcome.rejected]**, tidak dibawa diam-diam dan tidak membuat baris setengah jadi.
 * Murni, tanpa UI dan tanpa jaringan — dapat diuji sendiri. Implementasi: Track A.
 */
fun interface ScenarioCarryRule {
    fun apply(sourceValues: Map<String, String>, carries: List<FieldCarry>, source: EntitySpec, target: EntitySpec): CarryOutcome
}

/**
 * Layanan skenario di draf tenant — pintu rute `propose`/`confirm` (Track B memakai fake sampai Track A selesai). Mengusulkan
 * menambah skenario `confirmed = false`; [confirm] satu-satunya jalan menjadikannya terkonfirmasi (aksi manusia, FR-8).
 * Perubahan draf tetap lewat jalur Terapkan yang ada: layanan ini tidak menulis di luar kontrak itu.
 */
interface ScenarioService {
    suspend fun propose(tenantId: TenantId): Result<List<ScenarioSketch>>

    /** Gagal dengan `IllegalArgumentException` bila [id] tidak ada, dan dengan galat validasi berpath bila skenario tak lagi sah. */
    suspend fun confirm(tenantId: TenantId, id: ScenarioId): Result<ScenarioSketch>
}
