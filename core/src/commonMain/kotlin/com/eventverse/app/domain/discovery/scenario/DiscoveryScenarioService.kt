package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.usecases.UpdateDiscoveryDraftUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock

/** Skenario tak sah terhadap draf terkini; [issues] berpath. Juga `IllegalArgumentException` (route memetakannya ke 422). */
class ScenarioInvalidException(val issues: List<ScenarioIssue>) :
    IllegalArgumentException(issues.joinToString("; ") { "${it.path}: ${it.message}" })

/**
 * Layanan skenario di draf kerja tenant (TRD-BLDR-002 FR-3, FR-5, FR-8). **SIGNATURE KONSTRUKTOR DIBEKUKAN (Track B memasangnya)**, urutan:
 *
 * `DiscoveryScenarioService(drafts, proposer, validator = ScenarioRules, deterministic = DeterministicScenarioBuilder::build,
 * clock = Clock.System, narrative = { "" })`
 *
 * - `drafts: DiscoveryDraftRepository` — baca `findByTenant`, tulis `save` (hanya mengubah `draft.scenarios` + `updatedAt`);
 * - `proposer: ScenarioProposer` — model (server) atau fake;
 * - `validator: ScenarioValidator` — per skenario terhadap draf TERKINI;
 * - `deterministic: (DiscoveryDraft) -> List<ScenarioSketch>` — fallback;
 * - `clock: Clock`;
 * - `narrative: suspend (TenantId) -> String` — narasi pengguna untuk pengusul. Layanan ini tidak punya akses chat; Track B
 *   menyuplai dari riwayat utas Semua. Default kosong = pengusul hanya berpegang pada data draf.
 *
 * Perilaku [propose]: pengusul dicoba, hasil divalidasi; tak sah → SATU ulang dengan `feedback` berpath; masih tak sah, gagal,
 * atau kosong → fallback deterministik. Skenario tak sah dibuang (tidak disimpan); daftar kosong adalah hasil sah. Semua hasil
 * `confirmed = false`; `source` jujur (AI dipaksa untuk keluaran pengusul, DETERMINISTIC dari fallback). Penyimpanan HANYA
 * MENAMBAH: skenario yang ada (terkonfirmasi/USER/lainnya) tidak diubah; id dibuat unik; urutan modul yang sudah ada dibuang;
 * total ≤ [ScenarioLimits.SCENARIOS_MAX]. Mengembalikan hanya skenario yang BARU ditambahkan.
 *
 * Draf `LOCKED` tidak boleh diubah (Kontrak 5 draf): semua aksi tulis gagal dengan `UpdateDiscoveryDraftUseCase.LockedException`.
 * Draf tenant tidak ada: [propose] = daftar kosong; [confirm]/[edit] = `IllegalArgumentException`.
 */
class DiscoveryScenarioService(
    private val drafts: DiscoveryDraftRepository,
    private val proposer: ScenarioProposer,
    private val validator: ScenarioValidator = ScenarioRules,
    private val deterministic: (DiscoveryDraft) -> List<ScenarioSketch> = DeterministicScenarioBuilder::build,
    private val clock: Clock = Clock.System,
    private val narrative: suspend (TenantId) -> String = { "" },
    /** Dipanggil untuk tiap percobaan pengusul yang tak dipakai (alasan tanpa isi narasi); default tak melakukan apa-apa. */
    private val onProposalRejected: (TenantId, ProposalRejection) -> Unit = { _, _ -> }
) : ScenarioService {

    override suspend fun propose(tenantId: TenantId): Result<List<ScenarioSketch>> = runCatching {
        val stored = drafts.findByTenant(tenantId) ?: run {
            // Kontrak tetap: tanpa draf hasilnya daftar kosong. Yang berubah hanya jejaknya (dulu senyap).
            onProposalRejected(tenantId, ProposalRejection.of(ProposalRejectionKind.NO_DRAFT, 0, "tenant belum punya draf kerja"))
            return@runCatching emptyList()
        }
        ensureOpen(stored)
        val draft = stored.draft
        val text = narrative(tenantId)
        val proposed = fromProposer(draft, { r -> onProposalRejected(tenantId, r) }) { feedback -> ScenarioRequests.of(tenantId, draft, text, feedback) }
            ?: deterministic(draft).map { it.copy(source = ScenarioSource.DETERMINISTIC, confirmed = false) }
                .filter { validator.validate(draft, it).isEmpty() }
        val added = admit(draft.scenarios, proposed)
        if (proposed.isNotEmpty() && added.isEmpty()) {
            onProposalRejected(tenantId, ProposalRejection.of(
                ProposalRejectionKind.NOT_ADDED, 0,
                "${proposed.size} usulan dibuang: urutan modul sama dengan skenario yang ada atau kuota ${ScenarioLimits.SCENARIOS_MAX} penuh (ada ${draft.scenarios.size})"
            ))
        }
        if (added.isNotEmpty()) save(stored, draft.copy(scenarios = draft.scenarios + added))
        added
    }

    override suspend fun confirm(tenantId: TenantId, id: ScenarioId): Result<ScenarioSketch> = runCatching {
        val stored = open(tenantId)
        val current = stored.draft.scenarios.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Skenario ${id.value} tidak ada")
        if (current.confirmed) return@runCatching current
        validate(stored.draft, current)
        val confirmed = current.copy(confirmed = true)
        save(stored, stored.draft.copy(scenarios = stored.draft.scenarios.map { if (it.id == id) confirmed else it }))
        confirmed
    }

    /**
     * Suntingan pengguna atas skenario yang ada (id sama): isi diganti, `source = USER`, `confirmed = false` (suntingan harus
     * dikonfirmasi ulang). Divalidasi terhadap draf terkini dan tidak boleh menyamai urutan modul skenario lain.
     */
    suspend fun edit(tenantId: TenantId, edited: ScenarioSketch): Result<ScenarioSketch> = runCatching {
        val stored = open(tenantId)
        val all = stored.draft.scenarios
        if (all.none { it.id == edited.id }) throw IllegalArgumentException("Skenario ${edited.id.value} tidak ada")
        val next = edited.copy(source = ScenarioSource.USER, confirmed = false)
        val issues = validator.validate(stored.draft, next).toMutableList()
        if (all.any { it.id != next.id && ScenarioRules.sequenceKey(it) == ScenarioRules.sequenceKey(next) }) {
            issues += ScenarioIssue("steps", "skenario ganda: urutan modul sama dengan skenario lain")
        }
        if (issues.isNotEmpty()) throw ScenarioInvalidException(issues)
        save(stored, stored.draft.copy(scenarios = all.map { if (it.id == next.id) next else it }))
        next
    }

    private fun validate(draft: DiscoveryDraft, scenario: ScenarioSketch) {
        validator.validate(draft, scenario).takeIf { it.isNotEmpty() }?.let { throw ScenarioInvalidException(it) }
    }

    private suspend fun open(tenantId: TenantId): StoredDiscoveryDraft {
        val stored = drafts.findByTenant(tenantId) ?: throw IllegalArgumentException("Draf tenant ${tenantId.value} tidak ada")
        ensureOpen(stored)
        return stored
    }

    private fun ensureOpen(stored: StoredDiscoveryDraft) {
        if (stored.status == DiscoveryDraftStatus.LOCKED) {
            throw UpdateDiscoveryDraftUseCase.LockedException("Draf ${stored.id.value} sudah terkunci dan tidak bisa diubah")
        }
    }

    private suspend fun save(stored: StoredDiscoveryDraft, draft: DiscoveryDraft) {
        drafts.save(stored.copy(draft = draft, updatedAt = clock.now()))
    }

    /** Dua percobaan; `null` = gagal total, tak sah, atau kosong → fallback. Hasil dipaksa `AI` dan belum terkonfirmasi. */
    private suspend fun fromProposer(
        draft: DiscoveryDraft, reject: (ProposalRejection) -> Unit, request: (String?) -> ScenarioProposalRequest
    ): List<ScenarioSketch>? {
        var feedback: String? = null
        repeat(MAX_ATTEMPTS) { n ->
            val attempt = n + 1
            val result = try {
                proposer.propose(request(feedback))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Result.failure(e)
            }
            val list = result.getOrNull()?.map { it.copy(source = ScenarioSource.AI, confirmed = false) }
            if (list == null) {
                feedback = "model gagal: ${result.exceptionOrNull()?.message ?: "tanpa jawaban"}"
                reject(ProposalRejection.of(ProposalRejectionKind.PROPOSER_ERROR, attempt, feedback))
            } else if (list.isEmpty()) {
                reject(ProposalRejection.of(ProposalRejectionKind.EMPTY, attempt, "model menjawab daftar kosong"))
                return null
            } else {
                val tooMany = if (list.size > ScenarioLimits.SCENARIOS_MAX) {
                    listOf(ScenarioIssue("scenarios", "maksimal ${ScenarioLimits.SCENARIOS_MAX} skenario"))
                } else emptyList()
                val all = tooMany + list.flatMapIndexed { i, s ->
                    validator.validate(draft, s).map { ScenarioIssue("scenarios[$i].${it.path}", it.message) }
                }
                if (all.isEmpty()) return list
                feedback = all.joinToString("; ") { "${it.path}: ${it.message}" }
                reject(ProposalRejection.of(ProposalRejectionKind.INVALID, attempt, feedback))
            }
        }
        return null
    }

    /** Tambahan baru saja: bukan duplikat urutan modul, id unik, sisa kuota [ScenarioLimits.SCENARIOS_MAX]. */
    private fun admit(existing: List<ScenarioSketch>, proposed: List<ScenarioSketch>): List<ScenarioSketch> {
        val sequences = existing.map(ScenarioRules::sequenceKey).toMutableSet()
        val ids = existing.map { it.id.value }.toMutableSet()
        val out = mutableListOf<ScenarioSketch>()
        for (s in proposed) {
            if (existing.size + out.size >= ScenarioLimits.SCENARIOS_MAX) break
            if (!sequences.add(ScenarioRules.sequenceKey(s))) continue
            var id = s.id.value
            var n = 2
            while (!ids.add(id)) id = "${s.id.value}-${n++}"
            out += s.copy(id = ScenarioId(id))
        }
        return out
    }

    companion object {
        const val MAX_ATTEMPTS = 2
    }
}

/** Perakitan masukan pengusul dari draf (modul skenario: operasional aktif + dasar berdeskripsi, handoff wawancara, layar berproposal). Teks = data tenant. */
internal object ScenarioRequests {
    fun of(tenantId: TenantId, draft: DiscoveryDraft, narrative: String, feedback: String?): ScenarioProposalRequest {
        val active = ScenarioRules.eligibleModules(draft)
        val ids = active.map { it.id.value }.toSet()
        return ScenarioProposalRequest(
            tenantId = tenantId, narrative = narrative,
            modules = active.map { ScenarioModuleInput(it.id.value, it.displayName, it.description.takeIf(String::isNotBlank), emptyList()) },
            handoffs = draft.interview?.handoffs.orEmpty().filter { it.from.value in ids && it.to.value in ids }
                .map { ScenarioHandoffInput(it.from.value, it.to.value, it.portType.value) },
            screens = draft.screens.filter { it.moduleId.value in ids && it.proposal?.entity != null }.map { s ->
                ScenarioScreenInput(s.moduleId.value, s.screenId, s.title, s.proposal?.entity?.fields.orEmpty().map { it.key })
            },
            feedback = feedback
        )
    }
}
