package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryValidationIssue

/**
 * Aturan "berdasar cerita" (PLAN induk §4.1/§6.1), dipanggil `InterviewValidator`. Galat berpath dan berpesan
 * supaya LLM tahu persis dasar apa yang harus dicantumkan. Tebakan tak berdasar **ditolak, bukan dilonggarkan**.
 *
 * Sesi `version` 1 (wawancara sebelum B7) tidak mewajibkan `basisRef`, tapi `basisRef` yang *ada* tetap diperiksa.
 */
internal object InterviewBasisValidator {

    fun validate(session: InterviewSession, at: String): List<DiscoveryValidationIssue> {
        val out = mutableListOf<DiscoveryValidationIssue>()
        fun add(path: String, message: String) { out += DiscoveryValidationIssue(path, message) }
        val required = session.version >= InterviewSession.BASED_ON_STORY
        val answerIds = session.answers.map { it.questionId }.toSet()

        fun check(path: String, ref: BasisRef?, confirmed: Confirmation? = null, what: String) {
            if (ref == null) {
                if (required) add("$path.basisRef", "$what wajib punya dasar (basisRef): kutipan cerita (NARASI) atau jawaban pengguna (JAWABAN). Tanpa dasar, hapus butir ini")
                return
            }
            when (ref.basis) {
                Basis.SARAN_BELUM_DIJAWAB -> add("$path.basisRef", "Saran yang belum dijawab pengguna tidak boleh masuk draf; tanyakan dulu, atau hapus butir ini")
                Basis.NARASI -> when {
                    ref.quote.isNullOrBlank() -> add("$path.basisRef.quote", "NARASI wajib menyertakan kutipan dari cerita")
                    ref.quote.length > InterviewLimits.TEXT -> add("$path.basisRef.quote", "Kutipan maksimum ${InterviewLimits.TEXT} karakter; ambil bagian yang relevan saja")
                    session.narrative == null -> add("$path.basisRef.quote", "Sesi tidak menyimpan cerita, jadi kutipan tidak bisa diperiksa")
                    !session.narrative.contains(ref.quote) -> add("$path.basisRef.quote", "Kutipan '${ref.quote.take(40)}' bukan bagian dari cerita pengguna; salin persis dari ceritanya")
                }
                Basis.JAWABAN, Basis.SARAN_DITERIMA -> {
                    if (ref.answerId.isNullOrBlank() || ref.answerId !in answerIds)
                        add("$path.basisRef.answerId", "answerId '${ref.answerId.orEmpty()}' tidak ada di $at.answers; rujuk giliran yang benar-benar dijawab pengguna")
                    if (ref.basis == Basis.SARAN_DITERIMA && confirmed != null && confirmed != Confirmation.CONFIRMED && confirmed != Confirmation.CHANGED)
                        add("$path.confirmed", "SARAN_DITERIMA mensyaratkan konfirmasi pengguna (CONFIRMED atau CHANGED), bukan $confirmed")
                }
            }
        }

        session.divisions.forEachIndexed { i, d -> check("$at.divisions[$i]", d.basisRef, null, "Divisi '${d.code.value}'") }
        session.roles.forEachIndexed { i, r -> check("$at.roles[$i]", r.basisRef, null, "Peran '${r.roleKey.value}'") }
        session.links.forEachIndexed { i, l -> check("$at.links[$i]", l.basisRef, l.confirmed, "Tautan '${l.roleKey.value}' → '${l.moduleId.value}'") }
        session.handoffs.forEachIndexed { i, h -> check("$at.handoffs[$i]", h.basisRef, h.confirmed, "Sambungan '${h.from.value}' → '${h.to.value}'") }

        session.narrative?.let { if (it.length > InterviewLimits.NARRATIVE) add("$at.narrative", "Cerita maksimum ${InterviewLimits.NARRATIVE} karakter") }
        session.profile?.let { p ->
            if (p.summary.isBlank() || p.summary.length > InterviewLimits.PROFILE_TEXT) add("$at.profile.summary", "Ringkasan usaha wajib terisi dan maksimum ${InterviewLimits.PROFILE_TEXT} karakter")
            listOf("goals" to p.goals, "painPoints" to p.painPoints).forEach { (key, items) ->
                if (items.size > InterviewLimits.GOALS) add("$at.profile.$key", "Terlalu banyak (${items.size}); maksimum ${InterviewLimits.GOALS}")
                items.forEachIndexed { i, t -> if (t.isBlank() || t.length > InterviewLimits.TEXT) add("$at.profile.$key[$i]", "Wajib terisi dan maksimum ${InterviewLimits.TEXT} karakter") }
            }
        }
        if (session.specs.size > InterviewLimits.SPECS) add("$at.specs", "Terlalu banyak area (${session.specs.size}); maksimum ${InterviewLimits.SPECS}")
        val areas = mutableMapOf<RoleKey, Int>()
        session.specs.forEachIndexed { i, s ->
            val p = "$at.specs[$i]"
            areas[s.areaKey]?.let { add("$p.areaKey", "Area '${s.areaKey.value}' sudah ada di $at.specs[$it]; satu spesifikasi per area") } ?: run { areas[s.areaKey] = i }
            listOf("whoFills" to s.whoFills, "whatRecorded" to s.whatRecorded, "whoSees" to s.whoSees, "doneWhen" to s.doneWhen).forEach { (k, v) ->
                if (v != null && (v.isBlank() || v.length > InterviewLimits.TEXT)) add("$p.$k", "Bila diisi, wajib tidak kosong dan maksimum ${InterviewLimits.TEXT} karakter")
            }
            check(p, s.basisRef, null, "Spesifikasi area '${s.areaKey.value}'")
        }
        return out
    }
}
