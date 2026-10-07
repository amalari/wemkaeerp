package com.eventverse.app

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.BasisRef
import com.eventverse.app.domain.discovery.interview.InterviewLimits
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleId

/** Satu kriteria eval wawancara (plan IV-C0) pada satu kasus: lulus/gagal + bukti singkat untuk log `evals`. */
data class InterviewCriterionResult(val criterion: String, val passed: Boolean, val detail: String)

/** Hasil penilaian satu kasus emas: lulus hanya bila **semua** kriteria lulus. */
data class InterviewEvalVerdict(val caseName: String, val criteria: List<InterviewCriterionResult>) {

    val passed: Boolean get() = criteria.all { it.passed }

    /** Kriteria yang gagal — dipisah di laporan eval live: gagal penilai vs gagal model. */
    val failedCriteria: List<InterviewCriterionResult> get() = criteria.filter { !it.passed }

    /** Baris log berformat `evals | <agent> | <kasus> | PASS|FAIL | <detail>` (format SP dipertahankan). */
    fun logLine(agentRef: String): String {
        val failed = failedCriteria
        val detail = if (failed.isEmpty()) {
            criteria.joinToString("; ") { "${it.criterion}=ok" }
        } else {
            failed.joinToString("; ") { "${it.criterion}=GAGAL (${it.detail})" }
        }
        return "evals | $agentRef | $caseName | ${if (passed) "PASS" else "FAIL"} | $detail"
    }
}

/**
 * Penilai berstruktur wawancara (plan IV-C0) — **kode terpisah yang dites sendiri**
 * (`InterviewEvalGraderTest`, kasus lulus/gagal buatan tangan) supaya skornya bisa dipercaya.
 *
 * Kriteria (plan IV-C0): `valid`, `divisi_masuk_akal`, `peran_ke_divisi`, `tautan_modul`,
 * `asal_modul`, `kemurnian_vertikal`, `jumlah_giliran`. Prinsipnya sama dengan `DiscoveryEvalGrader`:
 * **kunci jawaban (sesi emas) wajib lulus 100% — kalau tidak, penilainya yang rusak.**
 *
 * Kalibrasi terbuka (utang tercatat, plan IV-C5): kecocokan memakai *contains* huruf kecil pada
 * nama/label/id — longgar; salah positif mungkin, salah negatif pada ejaan berbeda tidak. Naikkan ke
 * pencocokan kata saat eval live menunjukkan salah positif.
 *
 * Sejak C6 ada sembilan kriteria: tujuh semula + `berdasar_cerita` (kontrak `basisRef` B7 — tiap
 * butir tertelusur ke kutipan cerita atau jawaban pengguna) + `tanpa_modul_tak_disebut` (kasus
 * negatif — pengetahuan modul lazim tidak boleh bocor jadi tebakan).
 */
object InterviewEvalGrader {

    /**
     * Daftar hitam istilah konveksi untuk kriteria kemurnian vertikal — identik dengan
     * `DiscoveryEvalGrader`. Sejak keputusan 2026-10-07 (commit `1b4e7b8`), kata *sablon, bordir, kain,
     * tekstil, potong* **bukan** kebocoran: kasus textile-adjacent dinilai dari kemampuan.
     */
    private val WORD_TERMS = Regex("\\b(spk|fob|cmt|bom|hpp|buyer|makloon|maklon)\\b", RegexOption.IGNORE_CASE)
    private val SUBSTRING_TERMS = listOf("konveksi", "jahit", "garmen", "garment", "busana", "pakaian")

    fun grade(case: InterviewEvalCase, draft: DiscoveryDraft): InterviewEvalVerdict {
        val session = draft.interview
            ?: return InterviewEvalVerdict(
                case.name,
                listOf(InterviewCriterionResult("valid", false, "draf tidak membawa wawancara (\$.interview kosong)"))
            )
        val criteria = mutableListOf(
            valid(session, draft.pack),
            divisions(case, session),
            roles(case, session, draft.pack),
            links(case, session, draft.pack),
            origins(case, session, draft.pack),
            verticalPurity(case, session),
            turns(case, session),
            basedOnStory(session),
            noForbiddenModule(case, session, draft.pack)
        )
        return InterviewEvalVerdict(case.name, criteria)
    }

    /** Kriteria 1 — valid: lolos `InterviewValidator` terhadap pack draf (galat berpath dilaporkan apa adanya). */
    private fun valid(session: InterviewSession, pack: DomainPack): InterviewCriterionResult {
        val issues = InterviewValidator.validate(session, pack)
        return InterviewCriterionResult(
            "valid",
            issues.isEmpty(),
            if (issues.isEmpty()) "validator bersih"
            else issues.take(3).joinToString { "${it.path}: ${it.message}" }
        )
    }

    /** Cocokkan kunci divisi ke sesi — dipakai kriteria dan pelari alur (mutu tebakan per langkah). */
    internal fun coveredDivisions(case: InterviewEvalCase, session: InterviewSession): Int {
        val names = session.divisions.map { it.name.lowercase() }
        return case.expectedDivisions.count { synonyms -> names.any { name -> synonyms.any { name.contains(it) } } }
    }

    internal fun coveredRoles(case: InterviewEvalCase, session: InterviewSession): Int {
        val divisionNameByCode = session.divisions.associate { it.code.value to it.name.lowercase() }
        return case.expectedRoles.count { expected ->
            session.roles.any { role ->
                val label = role.label.lowercase()
                expected.roleSynonyms.any { label.contains(it) } &&
                    divisionNameByCode[role.divisionCode.value]?.let { d -> expected.divisionSynonyms.any { d.contains(it) } } == true
            }
        }
    }

    internal fun coveredLinks(case: InterviewEvalCase, session: InterviewSession, pack: DomainPack): Int {
        val roleLabelByKey = session.roles.associate { it.roleKey.value to it.label.lowercase() }
        return case.expectedLinks.count { expected ->
            session.links.any { link ->
                val label = roleLabelByKey[link.roleKey.value] ?: return@any false
                expected.roleSynonyms.any { label.contains(it) } && moduleMatches(pack, link.moduleId, expected.moduleSynonyms)
            }
        }
    }

    /** Kriteria 2 — divisi masuk akal: setiap kunci divisi tercakup, dan draf tidak membengkak lewat [InterviewEvalCase.maxDivisions]. */
    private fun divisions(case: InterviewEvalCase, session: InterviewSession): InterviewCriterionResult {
        val names = session.divisions.map { it.name.lowercase() }
        val missing = case.expectedDivisions
            .filter { synonyms -> names.none { name -> synonyms.any { name.contains(it) } } }
        val over = case.maxDivisions?.let { session.divisions.size > it } == true
        val ok = missing.isEmpty() && !over
        val detail = buildString {
            append("divisi=${session.divisions.map { it.name }}")
            if (missing.isNotEmpty()) append("; kunci kurang=$missing")
            case.maxDivisions?.let { if (over) append("; melebihi batas kasus negatif maxDivisions=$it (cerita kecil harus draf kecil)") }
        }
        return InterviewCriterionResult("divisi_masuk_akal", ok, detail)
    }

    /** Kriteria 3 — peran→divisi benar: setiap kunci peran ada, dan divisi tempatnya sesuai kunci. */
    private fun roles(case: InterviewEvalCase, session: InterviewSession, pack: DomainPack): InterviewCriterionResult {
        val divisionNameByCode = session.divisions.associate { it.code.value to it.name.lowercase() }
        val problems = case.expectedRoles.mapNotNull { expected ->
            val matched = session.roles.any { role ->
                val label = role.label.lowercase()
                expected.roleSynonyms.any { label.contains(it) } &&
                    divisionNameByCode[role.divisionCode.value]?.let { divisionName ->
                        expected.divisionSynonyms.any { divisionName.contains(it) }
                    } == true
            }
            if (matched) null else "peran ${expected.roleSynonyms} di divisi ${expected.divisionSynonyms}"
        }
        val detail = "peran=${session.roles.map { "${it.label}->${it.divisionCode.value}" }}" +
            (if (problems.isNotEmpty()) "; tidak ketemu=$problems" else "")
        return InterviewCriterionResult("peran_ke_divisi", problems.isEmpty(), detail)
    }

    /** Kriteria 4 — tautan modul benar: setiap kunci tautan (peran × kemampuan) tercakup satu tautan. */
    private fun links(case: InterviewEvalCase, session: InterviewSession, pack: DomainPack): InterviewCriterionResult {
        val roleLabelByKey = session.roles.associate { it.roleKey.value to it.label.lowercase() }
        val missing = case.expectedLinks.mapNotNull { expected ->
            val matched = session.links.any { link ->
                val label = roleLabelByKey[link.roleKey.value] ?: return@any false
                expected.roleSynonyms.any { label.contains(it) } && moduleMatches(pack, link.moduleId, expected.moduleSynonyms)
            }
            if (matched) null else expected.moduleSynonyms
        }
        val detail = "tautan=${session.links.map { "${it.roleKey.value}->${it.moduleId.value}" }}" +
            (if (missing.isNotEmpty()) "; kunci kurang=$missing" else "")
        return InterviewCriterionResult("tautan_modul", missing.isEmpty(), detail)
    }

    /** Kriteria 5 — asal modul masuk akal: asal tautan yang cocok harus anggota himpunan kunci kasus. */
    private fun origins(case: InterviewEvalCase, session: InterviewSession, pack: DomainPack): InterviewCriterionResult {
        val roleLabelByKey = session.roles.associate { it.roleKey.value to it.label.lowercase() }
        val problems = case.expectedLinks.flatMap { expected ->
            session.links.filter { link ->
                val label = roleLabelByKey[link.roleKey.value] ?: return@filter false
                expected.roleSynonyms.any { label.contains(it) } && moduleMatches(pack, link.moduleId, expected.moduleSynonyms)
            }.filter { it.origin !in expected.origins }
                .map { "${it.roleKey.value}->${it.moduleId.value} diklaim ${it.origin.code}, masuk akal: ${expected.origins.joinToString { o -> o.code }}" }
        }
        val detail = if (problems.isEmpty()) "semua tautan bertemu kunci memakai asal yang masuk akal" else problems.joinToString()
        return InterviewCriterionResult("asal_modul", problems.isEmpty(), detail)
    }

    /** Kriteria 6 — kemurnian vertikal: nol istilah konveksi pada teks tampil wawancara pack non-garment. */
    private fun verticalPurity(case: InterviewEvalCase, session: InterviewSession): InterviewCriterionResult {
        if (case.garmentPack) return InterviewCriterionResult("kemurnian_vertikal", true, "pack garment — tidak dinilai")
        val display = buildString {
            session.divisions.forEach { append(it.name).append(' ') }
            session.roles.forEach { append(it.label).append(' ') }
            session.links.forEach { append(it.features.joinToString(" ")).append(' ') }
        }
        val hits = SUBSTRING_TERMS.filter { display.lowercase().contains(it) } +
            WORD_TERMS.findAll(display).map { it.value }.toList()
        return InterviewCriterionResult(
            "kemurnian_vertikal",
            hits.isEmpty(),
            if (hits.isEmpty()) "bebas istilah konveksi" else "istilah konveksi terdeteksi: $hits"
        )
    }

    /**
     * Kriteria 8 — berdasar cerita (C6): sesi versi 2, menyimpan cerita, dan **setiap** butir
     * (divisi/peran/tautan/sambungan/spesifikasi) membawa dasar yang sah — kutipan NARASI yang benar-
     * benar substring cerita, atau JAWABAN/SARAN_DITERIMA yang menunjuk jejak giliran. Dinilai
     * independen dari validator supaya penilaiannya bisa dipercaya sendirinya.
     */
    private fun basedOnStory(session: InterviewSession): InterviewCriterionResult {
        if (session.version < InterviewSession.BASED_ON_STORY)
            return InterviewCriterionResult("berdasar_cerita", false, "sesi version=${session.version}, wajib >= ${InterviewSession.BASED_ON_STORY}")
        val narrative = session.narrative
            ?: return InterviewCriterionResult("berdasar_cerita", false, "sesi tidak menyimpan cerita (narrative kosong)")
        val answerIds = session.answers.map { it.questionId }.toSet()
        val items = buildList {
            session.divisions.forEachIndexed { i, d -> add("divisions[$i]" to d.basisRef) }
            session.roles.forEachIndexed { i, r -> add("roles[$i]" to r.basisRef) }
            session.links.forEachIndexed { i, l -> add("links[$i]" to l.basisRef) }
            session.handoffs.forEachIndexed { i, h -> add("handoffs[$i]" to h.basisRef) }
            session.specs.forEachIndexed { i, sp -> add("specs[$i]" to sp.basisRef) }
        }
        val problems = items.mapNotNull { (label, ref) ->
            when {
                ref == null -> "$label tanpa basisRef"
                ref.basis == Basis.SARAN_BELUM_DIJAWAB -> "$label saran belum dijawab pengguna"
                ref.basis == Basis.NARASI && (ref.quote?.let { q -> q.isNotBlank() && narrative.contains(q) } != true) ->
                    "$label kutipan tidak ada di cerita: '${ref.quote?.take(40)}'"
                (ref.basis == Basis.JAWABAN || ref.basis == Basis.SARAN_DITERIMA) && (ref.answerId == null || ref.answerId !in answerIds) ->
                    "$label answerId '${ref.answerId}' tidak ada di jejak giliran"
                else -> null
            }
        }
        val detail = if (problems.isEmpty()) "${items.size}/${items.size} butir tertelusur ke cerita/jawaban"
        else "${items.size - problems.size}/${items.size} tertelusur; ${problems.take(3).joinToString()}"
        return InterviewCriterionResult("berdasar_cerita", problems.isEmpty(), detail)
    }

    /**
     * Kriteria 9 — tanpa modul yang tak disebut (C6, kasus negatif): pada kasus dengan daftar
     * larangan, tidak ada tautan ke modul yang cocok sinonim larangan. Kosong = kriteria lulus tanpa dinilai.
     */
    private fun noForbiddenModule(case: InterviewEvalCase, session: InterviewSession, pack: DomainPack): InterviewCriterionResult {
        if (case.forbiddenModuleSynonyms.isEmpty())
            return InterviewCriterionResult("tanpa_modul_tak_disebut", true, "tanpa daftar larangan")
        val leaked = session.links.filter { moduleMatches(pack, it.moduleId, case.forbiddenModuleSynonyms) }
            .map { "${it.roleKey.value}->${it.moduleId.value}" }
        return InterviewCriterionResult(
            "tanpa_modul_tak_disebut",
            leaked.isEmpty(),
            if (leaked.isEmpty()) "tidak ada modul larangan (${case.forbiddenModuleSynonyms.size} sinonim dicek)"
            else "modul lazim bocor tanpa disebut cerita: $leaked"
        )
    }

    /** Kriteria 7 — jumlah giliran: jejak giliran terjemahan (G1–G5) tidak melewati batas kasus maupun platform; fase konsultan F0–F2 tidak dihitung karena gilirannya milik platform. */
    private fun turns(case: InterviewEvalCase, session: InterviewSession): InterviewCriterionResult {
        val limit = minOf(case.maxTurns, InterviewLimits.TURNS)
        val used = session.answers.count { !it.step.isConsultant }
        val ok = used <= limit
        return InterviewCriterionResult(
            "jumlah_giliran",
            ok,
            if (ok) "$used giliran (batas $limit)" else "$used giliran melebihi batas $limit — wawancara terlalu panjang"
        )
    }

    /** Modul pertama di pack yang cocok sinonim kunci — dipakai kriteria dan simulasi pengguna kooperatif. */
    internal fun matchingModule(pack: DomainPack, synonyms: Set<String>): ModuleId? =
        pack.modules.firstOrNull { m -> moduleMatches(pack, m.id, synonyms) }?.id

    /** Cocokkan modul dari id **atau** nama tampilnya — sinonim kunci ditulis untuk keduanya. */
    private fun moduleMatches(pack: DomainPack, id: ModuleId, synonyms: Set<String>): Boolean = synonyms.any { syn ->
        id.value.lowercase().contains(syn) || pack.module(id)?.displayName?.lowercase()?.contains(syn) == true
    }
}
