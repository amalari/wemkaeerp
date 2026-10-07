package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryDraft

/**
 * Pengisi tebakan satu langkah wawancara (G1–G4) saat langkah itu **mulai ditanyakan**. Pelaksananya agent AI
 * (server) atau tidak ada sama sekali (tebakan deterministik dari `propose` tetap berlaku). Port ini hanya
 * **mengusulkan**: hasilnya selalu divalidasi `InterviewValidator` oleh pemanggil, dan kegagalan apa pun (galat,
 * timeout, hasil tak sah) berarti sesi tetap seperti sebelumnya — wawancara tidak pernah gagal karena AI.
 */
fun interface InterviewStepFiller {
    suspend fun fill(draft: DiscoveryDraft, session: InterviewSession, step: InterviewStep, narrative: String): InterviewSession
}

/**
 * Mengganti **tebakan sistem** di [step] dengan usulan baru ([newDivisions] dst.), mempertahankan butir milik
 * pengguna (`ItemSource.ANSWER` / tautan yang sudah dikonfirmasi pengguna), lalu memangkas butir langkah lanjut
 * yang menggantung karena butir yang diganti (peran tanpa divisi, tautan tanpa peran, sambungan tanpa modul).
 * Tanpa pemangkasan, tebakan deterministik langkah lanjut menunjuk kunci yang sudah tidak ada dan seluruh sesi
 * ditolak validator.
 */
fun InterviewSession.replacingGuessesOf(
    step: InterviewStep,
    newDivisions: List<DivisionDraft> = emptyList(),
    newRoles: List<RoleDraft> = emptyList(),
    newLinks: List<RoleModuleLink> = emptyList(),
    newHandoffs: List<ModuleHandoff> = emptyList()
): InterviewSession {
    val replaced = when (step) {
        InterviewStep.G1_DIVISI -> copy(divisions = divisions.filter { it.source == ItemSource.ANSWER } + newDivisions.filterNot { n -> divisions.any { it.source == ItemSource.ANSWER && it.code == n.code } })
        InterviewStep.G2_PERAN -> copy(roles = roles.filter { it.source == ItemSource.ANSWER } + newRoles.filterNot { n -> roles.any { it.source == ItemSource.ANSWER && it.roleKey == n.roleKey } })
        InterviewStep.G3_MODUL -> copy(links = links.filter { it.confirmed != Confirmation.GUESSED } + newLinks.filterNot { n -> links.any { it.confirmed != Confirmation.GUESSED && it.roleKey == n.roleKey && it.moduleId == n.moduleId } })
        InterviewStep.G4_SAMBUNGAN -> copy(handoffs = handoffs.filter { it.confirmed != Confirmation.GUESSED } + newHandoffs.filterNot { n -> handoffs.any { it.confirmed != Confirmation.GUESSED && it.from == n.from && it.to == n.to && it.portType == n.portType } })
        else -> this
    }
    return replaced.withoutDangling()
}

/**
 * Menggabungkan usulan baru ke tebakan yang **sudah ada** di [step] (hybrid): tebakan deterministik lama tetap,
 * usulan baru menambah yang belum tercakup; kunci sama → usulan baru menang. Beda dengan [replacingGuessesOf]
 * yang membuang tebakan lama — di sini kamus yang sempit tidak hilang, dan agent mengisi celahnya saja.
 * Butir milik pengguna tidak disentuh. Hasil tetap divalidasi pemanggil; bila gabungan tak sah, pemanggil
 * mempertahankan sesi lama.
 */
fun InterviewSession.mergingGuessesOf(
    step: InterviewStep,
    newDivisions: List<DivisionDraft> = emptyList(),
    newRoles: List<RoleDraft> = emptyList(),
    newLinks: List<RoleModuleLink> = emptyList(),
    newHandoffs: List<ModuleHandoff> = emptyList()
): InterviewSession {
    val merged = when (step) {
        InterviewStep.G1_DIVISI -> copy(divisions = divisions.filterNot { o -> newDivisions.any { it.code == o.code && o.source != ItemSource.ANSWER } } + newDivisions.filterNot { n -> divisions.any { it.code == n.code && it.source == ItemSource.ANSWER } })
        InterviewStep.G2_PERAN -> copy(roles = roles.filterNot { o -> newRoles.any { it.roleKey == o.roleKey && o.source != ItemSource.ANSWER } } + newRoles.filterNot { n -> roles.any { it.roleKey == n.roleKey && it.source == ItemSource.ANSWER } })
        InterviewStep.G3_MODUL -> copy(links = links.filterNot { o -> newLinks.any { it.roleKey == o.roleKey && it.moduleId == o.moduleId && o.confirmed == Confirmation.GUESSED } } + newLinks.filterNot { n -> links.any { it.roleKey == n.roleKey && it.moduleId == n.moduleId && it.confirmed != Confirmation.GUESSED } })
        InterviewStep.G4_SAMBUNGAN -> copy(handoffs = handoffs.filterNot { o -> newHandoffs.any { it.from == o.from && it.to == o.to && it.portType == o.portType && o.confirmed == Confirmation.GUESSED } } + newHandoffs.filterNot { n -> handoffs.any { it.from == n.from && it.to == n.to && it.portType == n.portType && it.confirmed != Confirmation.GUESSED } })
        else -> this
    }
    return merged.withoutDangling()
}

/** Membuang butir yang menunjuk butir lain yang sudah tidak ada, dan memastikan satu kepala per divisi. */
fun InterviewSession.withoutDangling(): InterviewSession {
    val divisionCodes = divisions.map { it.code }.toSet()
    val keptRoles = roles.filter { it.divisionCode in divisionCodes }
    val headSeen = mutableSetOf<DivisionCode>()
    val normalizedRoles = keptRoles.map { r -> if (r.isHead && !headSeen.add(r.divisionCode)) r.copy(isHead = false) else r }
    val roleKeys = normalizedRoles.map { it.roleKey }.toSet()
    val keptLinks = links.filter { it.roleKey in roleKeys }
    val linkedModules = keptLinks.map { it.moduleId }.toSet()
    val keptHandoffs = handoffs.filter { it.from in linkedModules && it.to in linkedModules }
    return copy(roles = normalizedRoles, links = keptLinks, handoffs = keptHandoffs)
}
