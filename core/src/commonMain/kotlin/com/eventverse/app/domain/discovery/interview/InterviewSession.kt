package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.PortType

/** Divisi hasil G1. [name] teks tampil milik pengguna; [code] kunci slug. */
data class DivisionDraft(val code: DivisionCode, val name: String, val source: ItemSource, val basisRef: BasisRef? = null) {
    init { require(name.isNotBlank()) { "DivisionDraft.name kosong" } }
}

/** Peran hasil G2. [isHead] = kepala divisi (paling banyak satu per divisi — ditegakkan validator, berpath). */
data class RoleDraft(
    val roleKey: RoleKey,
    val label: String,
    val divisionCode: DivisionCode,
    val source: ItemSource,
    val isHead: Boolean = false,
    val basisRef: BasisRef? = null
) {
    init { require(label.isNotBlank()) { "RoleDraft.label kosong" } }
}

/**
 * Peran ↔ modul hasil G3. [confidence] (0–100) hanya terisi untuk tebakan sistem; null = jawaban pengguna.
 * Perubahan pengguna tercatat di [confirmed] (`CHANGED`) supaya mutu tebakan bisa diukur dan istilah yang
 * selalu diubah bisa naik menjadi kandidat kamus baru (buku demand).
 */
data class RoleModuleLink(
    val roleKey: RoleKey,
    val moduleId: ModuleId,
    val origin: ModuleOrigin,
    val features: List<String> = emptyList(),
    val confirmed: Confirmation = Confirmation.GUESSED,
    val confidence: Int? = null,
    val basisRef: BasisRef? = null
) {
    init { require(confidence == null || confidence in 0..100) { "RoleModuleLink.confidence harus 0-100" } }
}

/** Serah-terima hasil G4: [from] menyerahkan dokumen bertipe [portType] ke [to]. */
data class ModuleHandoff(
    val from: ModuleId,
    val to: ModuleId,
    val portType: PortType,
    val confirmed: Confirmation = Confirmation.GUESSED,
    val basisRef: BasisRef? = null
)

/** Jejak satu giliran (untuk ukur mutu tebakan & buku demand). [text] = jawaban bebas pengguna bila ada. */
data class InterviewAnswer(
    val turn: Int,
    val step: InterviewStep,
    val questionId: String,
    val outcome: Confirmation,
    val text: String? = null
) {
    init {
        require(turn > 0) { "InterviewAnswer.turn harus positif" }
        require(questionId.isNotBlank()) { "InterviewAnswer.questionId kosong" }
    }
}

/**
 * Pertanyaan klarifikasi dari perencana alur penuh: hal pokok yang tak bisa disimpulkan dari cerita sehingga
 * **ditanyakan dulu** alih-alih ditebak. [answer] null = belum dijawab. Jawabannya ditambahkan ke cerita
 * ([InterviewSession.narrative]) sebelum rencana disusun ulang, sehingga tetap bisa dikutip sebagai `basisRef`.
 */
data class Clarification(val id: String, val question: String, val answer: String? = null) {
    init {
        require(id.isNotBlank()) { "Clarification.id kosong" }
        require(question.isNotBlank()) { "Clarification.question kosong" }
    }
}

/**
 * Keadaan wawancara satu draf (kontrak plan §6). Murni data: **bukan** aturan — aturan milik [InterviewValidator].
 *
 * **Kode vs data:** isi divisi, peran, dan modul berbeda per usaha ⇒ data. Hanya [InterviewStep], [ModuleOrigin],
 * [Confirmation], dan [ItemSource] yang enum, karena mekanik wawancaranya dimiliki platform.
 *
 * Hasilnya **usulan yang ditinjau**, tidak otomatis membuat `Department`/`DepartmentModuleAssignment` (asumsi §12).
 * Draf tanpa wawancara tidak punya sesi ini (`DiscoveryDraft.interview == null`) dan tetap terbaca byte-per-byte.
 */
data class InterviewSession(
    val step: InterviewStep,
    val divisions: List<DivisionDraft> = emptyList(),
    val roles: List<RoleDraft> = emptyList(),
    val links: List<RoleModuleLink> = emptyList(),
    val handoffs: List<ModuleHandoff> = emptyList(),
    val answers: List<InterviewAnswer> = emptyList(),
    /**
     * Versi aturan dasar. **1** (bawaan, dan semua wawancara yang tersimpan sebelum B7) = `basisRef` tidak wajib —
     * dokumen lama dibaca apa adanya, tidak ditulis ulang. **2** = "berdasar cerita": setiap divisi/peran/tautan/
     * sambungan wajib `basisRef`. Sesi baru memakai [BASED_ON_STORY].
     */
    val version: Int = 1,
    /** Cerita pengguna (terpotong [InterviewLimits.NARRATIVE]) — disalin ke sesi supaya `Basis.NARASI` bisa diperiksa tanpa buku demand. */
    val narrative: String? = null,
    val profile: BusinessProfile? = null,
    val specs: List<RequirementSpec> = emptyList(),
    /** Pertanyaan klarifikasi perencana (opsional; kosong = tidak ada). */
    val clarifications: List<Clarification> = emptyList()
) {
    /** Ada pertanyaan klarifikasi yang belum dijawab — rencana alur penuh menunggu jawabannya. */
    val awaitingClarification: Boolean get() = clarifications.any { it.answer.isNullOrBlank() }

    companion object { const val BASED_ON_STORY = 2 }
}
