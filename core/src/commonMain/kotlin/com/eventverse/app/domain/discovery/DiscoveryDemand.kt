package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.auth.UserId
import kotlinx.datetime.Instant

/**
 * Satu baris buku demand (plan §6, E2): **narasi prospek verbatim** beserta apa yang bisa
 * diekspresikan generator dari narasi itu ([matchedModuleIds]) dan apa yang **belum** —
 * [unmatchedTerms].
 *
 * Alasan ia ada: dokumen [DiscoveryDraft] hanya menyimpan pack + blueprint + screens — narasi
 * asli sebelumnya **tidak tersimpan di mana pun**. Padahal narasi itulah sinyal produk paling
 * jujur: kalau tiga calon klien berbeda memakai kata yang tidak punya modul, itu kandidat
 * modul/widget berikutnya (gerbang Rule of Three, plan D5/E3) — bukan kebetulan kalimat.
 */
data class DiscoveryDemand(
    val id: String,
    val draftId: DiscoveryDraftId,
    val ownerUserId: UserId,
    val narrative: String,
    val industryHint: String?,
    val agentRef: String,
    val matchedModuleIds: List<String>,
    val unmatchedTerms: List<String>,
    val createdAt: Instant? = null
) {
    init {
        require(id.isNotBlank()) { "DiscoveryDemand.id kosong" }
        require(narrative.isNotBlank()) { "DiscoveryDemand.narrative kosong" }
    }
}

/** Penyimpanan buku demand. Tabel `ops.*`: platform-global, tanpa RLS tenant (pola discovery_drafts). */
interface DiscoveryDemandRepository {
    suspend fun save(demand: DiscoveryDemand): DiscoveryDemand

    /** Terbaru dulu — antrean review platform membaca dari atas. */
    suspend fun findAll(): List<DiscoveryDemand>

    /**
     * Draf menghasilkan paling banyak satu demand (E2); null untuk draf lahir sebelum V80.
     * Dipakai route untuk memulihkan teks narasi ke ringkasan draf (E1 — resume sesi).
     */
    suspend fun findByDraftId(draftId: DiscoveryDraftId): DiscoveryDemand?
}

/**
 * Kandidat modul/widget hasil gerbang Rule of Three: satu istilah muncul di **≥ 3 demand
 * berbeda** tanpa pernah menjadi modul. [samples] memuat kutipan narasi asli supaya reviewer
 * platform membaca konteksnya, bukan hanya kata kaosnya.
 */
data class DemandCandidate(val term: String, val demandCount: Int, val samples: List<String>)

/**
 * Buku demand — dua fungsi murni, tanpa I/O (plan §6 E2/E3):
 *
 * 1. [unmatchedTerms] — istilah narasi yang **belum terwakili** kosakata pack mana pun. Kata yang
 *    sudah jadi modul tidak pernah kembali sebagai sinyal; kata yang belum ya.
 * 2. [candidates] — gerbang Rule of Three (plan D5): widget/modul baru hanya layak dibangun bila
 *    **≥ 3 demand berbeda** menuntut hal yang sama. Di bawah itu derau; di atasnya peta jalan.
 *
 * Pencocokan istilah sengaja **substring sederhana** terhadap kosakata pack, bukan stemming atau
 * embedding: sasarannya menyaring kata yang sudah terjawab, bukan memahami bahasa. False positive
 * (kata berbeda per territori) tidak fatal — reviewer membaca [DemandCandidate.samples].
 */
object DemandLedger {

    /** Ambang Rule of Three (plan D5). Angka yang sama dengan gerbang widget Studio. */
    const val RULE_OF_THREE = 3

    /** Kata fungsi & kata isian narasi — bukan sinyal kemampuan. Disenaraikan eksplisit agar keputusannya bisa diaudit. */
    private val STOPWORDS = setOf(
        "yang", "dengan", "untuk", "kami", "kita", "saya", "adalah", "dari", "pada", "agar",
        "bisa", "dapat", "juga", "sudah", "akan", "jika", "bila", "saat", "setiap", "semua",
        "banyak", "secara", "serta", "atau", "hanya", "ingin", "butuh", "perlu", "buat",
        "sistem", "aplikasi", "software", "program", "seperti", "biar", "kalau", "ada",
        "the", "and", "for", "with", "that", "this", "have", "want", "need", "from"
    )

    /**
     * Istilah narasi yang tidak muncul di kosakata pack (id & nama modul, slot, fase, seksi,
     * label aksi, kosakata chrome, kode pack). Uraian modul sengaja **tidak** ikut — ia prosa
     * yang dihasilkan agent, bukan kemampuan yang dijanjikan.
     */
    fun unmatchedTerms(narrative: String, draft: DiscoveryDraft, limit: Int = 12): List<String> {
        val known = buildString {
            draft.pack.modules.forEach {
                append(it.id.value.lowercase()).append(' ').append(it.displayName.lowercase()).append(' ')
            }
            draft.pack.slots.forEach { append(it.code.value.lowercase()).append(' ') }
            draft.pack.phases.forEach { append(it.displayName.lowercase()).append(' ') }
            draft.pack.sections.forEach { append(it.displayName.lowercase()).append(' ') }
            draft.pack.actions.forEach { append(it.label.lowercase()).append(' ') }
            draft.pack.vocabulary.values.forEach { append(it.lowercase()).append(' ') }
            append(draft.pack.code.value.lowercase()).append(' ').append(draft.pack.displayName.lowercase())
        }
        // Tanpa kelas Unicode di regex (\p{L} melempar di JS tanpa flag 'u'): pemetaan karakter
        // jadi pemisah, pola yang sama dengan `DeterministicDiscoveryAgent.packCode`.
        return narrative.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.length in 4..40 }
            .filter { it !in STOPWORDS && !it.all(Char::isDigit) }
            .distinct()
            .filterNot { token -> known.contains(token) }
            .take(limit)
    }

    /**
     * Gerbang Rule of Three: kelompokkan istilah lintas demand **berbeda** (satu demand yang
     * mengulang istilah yang sama dihitung sekali), ambang [minimum].
     */
    fun candidates(demands: List<DiscoveryDemand>, minimum: Int = RULE_OF_THREE): List<DemandCandidate> {
        val demandIdsByTerm = linkedMapOf<String, MutableSet<String>>()
        val samplesByTerm = mutableMapOf<String, MutableList<String>>()
        demands.forEach { demand ->
            demand.unmatchedTerms.forEach { term ->
                demandIdsByTerm.getOrPut(term) { mutableSetOf() }.add(demand.id)
                val samples = samplesByTerm.getOrPut(term) { mutableListOf() }
                if (samples.size < 3) samples.add(demand.narrative.take(120))
            }
        }
        return demandIdsByTerm.mapNotNull { (term, ids) ->
            if (ids.size >= minimum) {
                DemandCandidate(term = term, demandCount = ids.size, samples = samplesByTerm.getValue(term))
            } else {
                null
            }
        }.sortedWith(compareByDescending<DemandCandidate> { it.demandCount }.thenBy { it.term })
    }

    /** Rekam satu demand dari hasil pembuatan draf — dipanggil route saat `POST /drafts` berhasil. */
    fun record(
        stored: StoredDiscoveryDraft,
        request: DiscoveryRequest,
        agentRef: String,
        id: String,
        createdAt: Instant
    ): DiscoveryDemand = DiscoveryDemand(
        id = id,
        draftId = stored.id,
        ownerUserId = stored.ownerUserId,
        narrative = request.narrative,
        industryHint = request.industryHint,
        agentRef = agentRef,
        matchedModuleIds = stored.draft.pack.modules.map { it.id.value },
        unmatchedTerms = unmatchedTerms(request.narrative, stored.draft),
        createdAt = createdAt
    )
}
