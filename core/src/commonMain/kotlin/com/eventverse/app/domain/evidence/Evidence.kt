package com.eventverse.app.domain.evidence

/**
 * Batas kontrak evidence (TRD-BLDR-003 FR-1). Satu sumber angka untuk validator, codec, dan grader.
 */
object EvidenceLimits {
    /** Kutipan maksimum: cukup untuk satu kalimat dasar; mencegah narasi disalin utuh jadi "bukti". */
    const val QUOTE_MAX_CHARS = 300
}

/**
 * Kosakata tertutup asal bukti sebuah pilihan (TRD-BLDR-003 FR-1.2). Mekanik platform — bukan kosakata
 * industri (Uji Variabilitas: tidak berbeda per tenant/industri/admin). Nama tak dikenal **ditolak**
 * berpath (FR-1.4), tidak pernah jatuh ke default.
 */
enum class EvidenceSource {
    /** Kutipan persis dari narasi pengguna — [EvidenceRef.quote] wajib. */
    NARRATIVE,
    /** Jawaban pertanyaan wawancara — [EvidenceRef.referenceId] = questionId yang ada di sesi. */
    INTERVIEW_ANSWER,
    /** Jawaban pertanyaan follow-up Builder yang sudah berjawaban — [EvidenceRef.referenceId] = id pertanyaan. */
    BUILDER_ANSWER,
    /** Pesan USER di percakapan Builder tenant yang sama — [EvidenceRef.referenceId] = id pesan. */
    USER_MESSAGE,
    /** Bawaan pack aktif — [EvidenceRef.artifactKey] menunjuk artifact pack; ditampilkan sebagai bawaan, bukan ucapan pengguna. */
    PACK_DEFAULT,
    /** Usulan sistem yang dikonfirmasi pengguna — [EvidenceRef.referenceId] menunjuk aksi konfirmasi. */
    ACCEPTED_SUGGESTION,
}

/**
 * Alasan positif mengapa sebuah butir dipilih (TRD-BLDR-003 FR-1.1) — berbeda dari fingerprint idempotensi
 * (`BriefKeys.inputFingerprint`) yang hanya hash masukan. Field yang relevan bergantung [source]:
 * NARRATIVE → [quote]; INTERVIEW_ANSWER/BUILDER_ANSWER/USER_MESSAGE/ACCEPTED_SUGGESTION → [referenceId];
 * PACK_DEFAULT → [artifactKey]. Kelengkapan dan kebenarannya ditegakkan [EvidenceRules] (galat berpath),
 * bukan konstruktor, supaya keluaran agent bisa dikoreksi dengan umpan balik.
 */
data class EvidenceRef(
    val source: EvidenceSource,
    val quote: String? = null,
    val referenceId: String? = null,
    val artifactKey: String? = null,
)

/**
 * Jejak yang bisa dijamah [EvidenceRules] saat memvalidasi sebuah [EvidenceRef]: teks narasi, himpunan id
 * jawaban/pesan yang sah, dan kunci artifact pack aktif. Dibangun pemanggil (use case/service) dari data
 * tenant yang sama — evidence lintas tenant/konversasi mustahil lulus karena id-nya tidak ada di konteks.
 *
 * [narratives] boleh memuat lebih dari satu teks sumber (mis. cerita wawancara DAN narasi chat Builder):
 * kutipan sah bila substring persis dari **salah satu** narasi di konteks.
 */
data class EvidenceContext(
    val narratives: Set<String> = emptySet(),
    val interviewAnswerIds: Set<String> = emptySet(),
    val builderAnswerIds: Set<String> = emptySet(),
    val userMessageIds: Set<String> = emptySet(),
    val packArtifactKeys: Set<String> = emptySet(),
    val acceptedSuggestionIds: Set<String> = emptySet(),
)

/** Satu temuan validator evidence; [path] menunjuk bagian yang salah agar umpan balik ke model berpath. */
data class EvidenceIssue(val path: String, val message: String)

/** Validator netral evidence (TRD-BLDR-003 FR-1.3) — murni, tanpa I/O, tanpa dependensi layer server/klien. */
fun interface EvidenceValidator {
    fun validate(ref: EvidenceRef, context: EvidenceContext, path: String): List<EvidenceIssue>
}

/**
 * Aturan sahnya [EvidenceRef] menurut [EvidenceSource] (TRD-BLDR-003 FR-1.3). Daftar kosong = sah.
 *
 * - NARRATIVE: [EvidenceRef.quote] wajib, tidak kosong, dalam [EvidenceLimits.QUOTE_MAX_CHARS], dan
 *   **substring persis** salah satu narasi di konteks (kutipan yang bukan bagian cerita ditolak).
 * - INTERVIEW_ANSWER / BUILDER_ANSWER / USER_MESSAGE / ACCEPTED_SUGGESTION: [EvidenceRef.referenceId]
 *   wajib dan tercantum pada himpunan id konteks yang relevan.
 * - PACK_DEFAULT: [EvidenceRef.artifactKey] wajib dan tercantum pada kunci artifact pack aktif.
 *
 * Validator hanya membuktikan referensi **ada dan sah bentuknya**; kaitannya dengan kasus eval dinilai
 * grader `relevansi_evidence` (lapisan terpisah).
 */
object EvidenceRules : EvidenceValidator {

    override fun validate(ref: EvidenceRef, context: EvidenceContext, path: String): List<EvidenceIssue> = buildList {
        when (ref.source) {
            EvidenceSource.NARRATIVE -> {
                val q = ref.quote
                when {
                    q.isNullOrBlank() -> add(EvidenceIssue("$path.quote", "kutipan wajib untuk NARRATIVE"))
                    q.length > EvidenceLimits.QUOTE_MAX_CHARS ->
                        add(EvidenceIssue("$path.quote", "maksimal ${EvidenceLimits.QUOTE_MAX_CHARS} karakter"))
                    context.narratives.none { it.contains(q) } ->
                        add(EvidenceIssue("$path.quote", "kutipan tidak ada di narasi mana pun: '${q.take(40)}'"))
                }
            }
            EvidenceSource.INTERVIEW_ANSWER -> requireRef(ref.referenceId, context.interviewAnswerIds, path, "referenceId", "jawaban wawancara")
            EvidenceSource.BUILDER_ANSWER -> requireRef(ref.referenceId, context.builderAnswerIds, path, "referenceId", "jawaban Builder")
            EvidenceSource.USER_MESSAGE -> requireRef(ref.referenceId, context.userMessageIds, path, "referenceId", "pesan pengguna")
            EvidenceSource.ACCEPTED_SUGGESTION -> requireRef(ref.referenceId, context.acceptedSuggestionIds, path, "referenceId", "saran yang diterima")
            EvidenceSource.PACK_DEFAULT -> requireRef(ref.artifactKey, context.packArtifactKeys, path, "artifactKey", "artifact pack")
        }
    }

    /** Butir wajib ada dan tercantum di himpunan konteks yang sah — referensi mayin ditolak berpath. */
    private fun MutableList<EvidenceIssue>.requireRef(
        value: String?, allowed: Set<String>, path: String, field: String, label: String
    ) {
        when {
            value.isNullOrBlank() -> add(EvidenceIssue("$path.$field", "$field wajib untuk sumber ini"))
            value !in allowed -> add(EvidenceIssue("$path.$field", "$field '$value' bukan $label yang sah di konteks"))
        }
    }
}
