package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryDraft

/**
 * Perencana alur penuh (alur "alur & modul dulu, tanya per modul kemudian"): dipanggil **sekali** saat wawancara
 * dimulai, membaca seluruh narasi, lalu mengusulkan divisi, peran, tautan modul, dan sambungan (G1–G4) sekaligus
 * sebagai tebakan `GUESS`/`GUESSED` berdasar cerita. Pelaksananya agent AI model besar (server) atau tidak ada.
 *
 * Perencana boleh **bertanya dulu** bila hal pokok tak bisa disimpulkan dari cerita: ia mengembalikan sesi dengan
 * [InterviewSession.clarifications] terisi (tanpa rencana). Setelah dijawab ia dipanggil lagi dan **tidak boleh
 * bertanya lagi** (tanda: [InterviewSession.clarifications] sudah berisi) — satu putaran tanya, lalu rencana.
 *
 * Seperti [InterviewStepFiller]: port ini hanya **mengusulkan** — pemanggil memvalidasi hasilnya, dan galat apa
 * pun (timeout, usulan tak sah) berarti sesi tetap seperti sebelumnya. Wawancara tidak pernah gagal karena AI.
 */
fun interface InterviewPlanner {
    suspend fun plan(draft: DiscoveryDraft, session: InterviewSession, narrative: String): InterviewSession
}

/** Langkah-langkah yang sekaligus diisi satu rencana. */
val PLANNED_STEPS: List<InterviewStep> = listOf(
    InterviewStep.G1_DIVISI, InterviewStep.G2_PERAN, InterviewStep.G3_MODUL, InterviewStep.G4_SAMBUNGAN
)

/**
 * Sesi dianggap **sudah punya rencana** bila divisi, peran, dan tautan sama-sama ada. Dipakai untuk melewati
 * pengisian per langkah (model kecil) — rencana sudah mencakup semuanya; pengguna tinggal meninjau.
 */
val InterviewSession.isPlanned: Boolean
    get() = divisions.isNotEmpty() && roles.isNotEmpty() && links.isNotEmpty()

/** Menggabungkan usulan rencana ke sesi: tebakan lama tetap, butir pengguna tak tersentuh, yang menggantung dipangkas. */
fun InterviewSession.mergingPlan(
    divisions: List<DivisionDraft>, roles: List<RoleDraft>, links: List<RoleModuleLink>, handoffs: List<ModuleHandoff>
): InterviewSession = PLANNED_STEPS.fold(this) { s, step -> s.mergingGuessesOf(step, divisions, roles, links, handoffs) }
