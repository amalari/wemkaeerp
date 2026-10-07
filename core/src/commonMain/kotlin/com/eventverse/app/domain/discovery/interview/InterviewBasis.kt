package com.eventverse.app.domain.discovery.interview

/**
 * Dasar sebuah butir wawancara (PLAN induk §4.1, §6.1, "ERP untukmu"): ERP dibentuk dari bisnis pengguna, jadi
 * setiap divisi/peran/tautan/sambungan harus bisa ditelusuri ke ceritanya. Kosakata tertutup milik sistem.
 */
enum class Basis(val code: String) {
    /** Kutipan dari cerita pengguna — [BasisRef.quote] wajib substring narasi. */
    NARASI("narasi"),
    /** Jawaban pengguna atas pertanyaan wawancara — [BasisRef.answerId] wajib ada di jejak giliran. */
    JAWABAN("jawaban"),
    /** Usulan konsultan yang dikonfirmasi pengguna (`CONFIRMED`/`CHANGED`), merujuk giliran yang menerimanya. */
    SARAN_DITERIMA("saran_diterima"),
    /** Usulan konsultan yang belum dijawab/ditolak — **tidak boleh** masuk draf; validator menolaknya. */
    SARAN_BELUM_DIJAWAB("saran_belum_dijawab");

    companion object {
        fun fromCode(code: String): Basis? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Rujukan dasar. [quote] untuk [Basis.NARASI]; [answerId] (= `InterviewAnswer.questionId` giliran terkait) untuk
 * [Basis.JAWABAN] dan [Basis.SARAN_DITERIMA]. Kelengkapan dan kebenarannya ditegakkan `InterviewBasisValidator`
 * (galat berpath), bukan konstruktor, supaya agent bisa mengoreksi dirinya.
 */
data class BasisRef(val basis: Basis, val quote: String? = null, val answerId: String? = null)

/** Profil bisnis hasil F0 (usaha apa) dan F1 (tujuan + titik sakit). */
data class BusinessProfile(
    val summary: String,
    val goals: List<String> = emptyList(),
    val painPoints: List<String> = emptyList()
)

/** Spesifikasi kebutuhan satu area hasil F2: siapa mengisi, apa dicatat, siapa melihat, kapan dianggap selesai. */
data class RequirementSpec(
    val areaKey: RoleKey,
    val whoFills: String? = null,
    val whatRecorded: String? = null,
    val whoSees: String? = null,
    val doneWhen: String? = null,
    val basisRef: BasisRef? = null
)
