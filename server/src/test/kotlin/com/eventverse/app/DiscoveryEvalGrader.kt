package com.eventverse.app

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.prototype.FieldType

/** Satu kriteria §6 pada satu kasus: lulus/gagal + bukti singkat untuk log `evals`. */
data class CriterionResult(val criterion: String, val passed: Boolean, val detail: String)

/** Hasil penilaian satu kasus emas: lulus hanya bila **semua** kriteria lulus. */
data class EvalVerdict(val caseName: String, val criteria: List<CriterionResult>) {

    val passed: Boolean get() = criteria.all { it.passed }

    /** Baris log berformat `evals | <agent> | <kasus> | PASS|FAIL | <detail>` (format lama dipertahankan). */
    fun logLine(agentRef: String): String {
        val failed = criteria.filter { !it.passed }
        val detail = if (failed.isEmpty()) {
            criteria.joinToString("; ") { "${it.criterion}=ok" }
        } else {
            failed.joinToString("; ") { "${it.criterion}=GAGAL (${it.detail})" }
        }
        return "evals | $agentRef | $caseName | ${if (passed) "PASS" else "FAIL"} | $detail"
    }
}

/**
 * Penilai otomatis evals discovery (plan SP-C4, kriteria §6 plan induk) — **kode terpisah yang dites
 * sendiri** (`DiscoveryEvalGraderTest`, kasus lulus/gagal buatan tangan) supaya skornya bisa dipercaya.
 *
 * Prinsip baseline (plan §6): **deterministik harus lulus 100% kriteria otomatis — kalau tidak,
 * penilainya yang rusak.** Sejak B3 merge, draf deterministik membawa layar ber-proposal dan kriteria isi
 * layar (jenis tampilan, status, field) dinilai **nyata**. Kriteria itu tetap dinilai **bila layar ada**:
 * draf tanpa layar (mis. pack tanpa `defaultWidget`) lulus kosong dengan keterangan "tidak ada layar" —
 * jujur menyebut sebabnya, bukan pura-pura menilai.
 */
object DiscoveryEvalGrader {

    /**
     * Daftar hitam istilah konveksi untuk kriteria kemurnian vertikal (§6): pola pendek yang berisiko
     * cocok di dalam kata lain dinilai dengan batas kata (`\b`), sisanya substring. Dipakai untuk pack
     * non-garment saja — pack garment justru diharapkan memuat istilah ini.
     */
    private val WORD_TERMS = Regex("\\b(spk|fob|cmt|bom|hpp|po|buyer|makloon|maklon)\\b", RegexOption.IGNORE_CASE)
    private val SUBSTRING_TERMS = listOf(
        "konveksi", "jahit", "garmen", "garment", "tekstil", "busana", "pakaian", "bordir", "sablon", "kain"
    )

    fun grade(case: DiscoveryGoldenCase, result: Result<DiscoveryDraft>): EvalVerdict {
        val draft = result.getOrNull()
            ?: return EvalVerdict(
                case.name,
                listOf(CriterionResult("valid", false, "gagal: ${result.exceptionOrNull()?.message}"))
            )

        val criteria = mutableListOf(valid(draft), coverage(case, draft))
        criteria += screenCriteria(case, draft)
        criteria += verticalPurity(case, draft)
        return EvalVerdict(case.name, criteria)
    }

    /** Kriteria 1 — valid: lolos `DiscoveryDraftValidator` (validator + seluruh `ScreenProposalValidator`). */
    private fun valid(draft: DiscoveryDraft): CriterionResult {
        val issues = DiscoveryDraftValidator.validate(draft)
        return CriterionResult(
            "valid",
            issues.isEmpty(),
            if (issues.isEmpty()) "validator bersih"
            else issues.take(3).joinToString { "${it.path}: ${it.message}" }
        )
    }

    /** Kriteria 2 — cakupan: kode pack (+ blueprint bila ditetapkan) dan sufiks modul per kemampuan. */
    private fun coverage(case: DiscoveryGoldenCase, draft: DiscoveryDraft): CriterionResult {
        val moduleIds = draft.pack.modules.map { it.id.value }
        // Tanpa petunjuk industri dan bukan garment, kode pack adalah pilihan agent (deterministik memakai
        // 'kustom' sebagai cadangan, bukan kebenaran). Dengan petunjuk, kodenya harus persis slug petunjuk.
        val packOk = draft.pack.code.value == case.expectedPackCode ||
            (case.industryHint == null && !case.garmentPack && draft.pack.code.value.matches(Regex("[a-z][a-z0-9_]*")))
        val blueprintOk = case.expectedBlueprintCode == null || draft.blueprint.code.value == case.expectedBlueprintCode
        val missing = case.expectedCapabilities
            .filter { synonyms -> moduleIds.none { id -> synonyms.any { id.endsWith("_$it") } } }
        val ok = packOk && blueprintOk && missing.isEmpty()
        val detail = buildString {
            append("pack=${draft.pack.code.value}")
            case.expectedBlueprintCode?.let { append(" blueprint=${draft.blueprint.code.value}") }
            append(" modules=$moduleIds")
            if (!packOk) append("; pack diharapkan ${case.expectedPackCode}")
            if (!blueprintOk) append("; blueprint diharapkan ${case.expectedBlueprintCode}")
            if (missing.isNotEmpty()) append("; kemampuan kurang=$missing")
        }
        return CriterionResult("cakupan_modul", ok, detail)
    }

    /** Kriteria 3–5 — isi layar, dinilai bila layar ada (lihat KDoc objek). */
    private fun screenCriteria(case: DiscoveryGoldenCase, draft: DiscoveryDraft): List<CriterionResult> {
        val screens = draft.screens
        if (screens.isEmpty()) {
            return listOf(
                CriterionResult("jenis_tampilan", true, "tidak ada layar (baseline pra-B3)"),
                CriterionResult("status_bermakna", true, "tidak ada layar"),
                CriterionResult("field_memadai", true, "tidak ada layar")
            )
        }

        val displayKindProblems = mutableListOf<String>()
        val statusProblems = mutableListOf<String>()
        val fieldProblems = mutableListOf<String>()

        screens.forEach { s ->
            val kind = WidgetKind.fromCode(s.widget)
            if (kind == null) displayKindProblems += "${s.screenId}: '${s.widget}' bukan kosakata tertutup"
            val synonyms = case.expectedCapabilities
                .firstOrNull { syns -> syns.any { s.moduleId.value.endsWith("_$it") } }
            if (kind != null && synonyms != null) {
                val allowed = DiscoveryGoldenCases.allowedFor(case, synonyms)
                if (kind.code !in allowed) {
                    displayKindProblems += "${s.screenId} (${s.moduleId.value}): ${kind.code} di luar himpunan $allowed"
                }
            }

            val entity = s.proposal?.entity ?: return@forEach
            if (entity.fields.none { it.type == FieldType.TEXT }) {
                fieldProblems += "${s.screenId}: entity '${entity.id}' tidak punya satu pun field teks (judul-ish)"
            }
            val status = entity.statusField?.let { key -> entity.fields.firstOrNull { it.key == key } }
                ?: return@forEach
            if (status.options.size !in 2..8) {
                statusProblems += "${s.screenId}: status '${status.key}' punya ${status.options.size} pilihan (harus 2–8)"
            }
            // Rekalibrasi 2026-10-07: alur sah tidak harus acyclic — level stok atau status bayar boleh bolak-balik.
            // Yang dinilai: setiap status terhubung ke alur (tidak yatim) bila transisi dideklarasikan, dan
            // transisi hanya antar opsi yang ada (sudah dijaga validator). Alur kerja berurutan tetap lolos.
            if (entity.transitions.isNotEmpty()) {
                val connected = entity.transitions.keys + entity.transitions.values.flatten()
                val orphans = status.options.filter { it !in connected }
                if (orphans.isNotEmpty()) statusProblems += "${s.screenId}: status $orphans yatim (tidak terhubung ke alur)"
            }
        }

        return listOf(
            CriterionResult(
                "jenis_tampilan",
                displayKindProblems.isEmpty(),
                if (displayKindProblems.isEmpty()) "${screens.size} layar memakai jenis yang masuk akal"
                else displayKindProblems.joinToString()
            ),
            CriterionResult(
                "status_bermakna",
                statusProblems.isEmpty(),
                if (statusProblems.isEmpty()) "status layar dalam rentang 2–8 dan semuanya terhubung ke alur"
                else statusProblems.joinToString()
            ),
            CriterionResult(
                "field_memadai",
                fieldProblems.isEmpty(),
                if (fieldProblems.isEmpty()) "setiap entity punya field teks judul-ish"
                else fieldProblems.joinToString()
            )
        )
    }

    /** Kriteria 6 — kemurnian vertikal: nol istilah konveksi pada teks tampil pack non-garment. */
    private fun verticalPurity(case: DiscoveryGoldenCase, draft: DiscoveryDraft): CriterionResult {
        if (case.garmentPack) return CriterionResult("kemurnian_vertikal", true, "pack garment — tidak dinilai")

        val display = buildString {
            append(draft.pack.displayName).append(' ')
            draft.pack.modules.forEach { append(it.displayName).append(' ').append(it.description).append(' ') }
            draft.pack.vocabulary.values.forEach { append(it).append(' ') }
            draft.pack.actions.forEach { append(it.label).append(' ') }
            append(draft.blueprint.displayName).append(' ').append(draft.blueprint.description).append(' ')
            append(draft.blueprint.targetClientProfile).append(' ')
            draft.screens.forEach { s ->
                append(s.title).append(' ')
                s.proposal?.let { p ->
                    append(p.rationale).append(' ')
                    p.entity?.let { e ->
                        append(e.label).append(' ')
                        e.fields.forEach { f ->
                            append(f.label).append(' ')
                            f.options.forEach { append(it).append(' ') }
                        }
                    }
                    when (val v = p.view) {
                        is ViewProposal.Form -> append(v.submitLabel).append(' ')
                        is ViewProposal.Dashboard -> v.tiles.forEach { append(it.label).append(' ') }
                        else -> Unit
                    }
                }
            }
        }
        val hits = SUBSTRING_TERMS.filter { display.lowercase().contains(it) } +
            WORD_TERMS.findAll(display).map { it.value }.toList()
        return CriterionResult(
            "kemurnian_vertikal",
            hits.isEmpty(),
            if (hits.isEmpty()) "bebas istilah konveksi" else "istilah konveksi terdeteksi: $hits"
        )
    }
}
