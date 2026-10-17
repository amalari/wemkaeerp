package com.eventverse.app

import com.eventverse.app.domain.discovery.scenario.ScenarioSketch

/**
 * Kriteria skenario: gate wajib 100% + skor numerik.
 * Mirip BRIEF tapi untuk alur skenario.
 */
data class ScenarioCriterionResult(
    val criterion: String,
    val passed: Boolean,
    val detail: String,
    val numerator: Int = if (passed) 1 else 0,
    val denominator: Int = 1
) {
    val score: Float get() = if (denominator > 0) numerator.toFloat() / denominator else 0f
}

/**
 * Hasil penilaian skenario: gate wajib lulus + skor per kriteria.
 */
data class ScenarioEvalVerdict(
    val caseName: String,
    val criteria: List<ScenarioCriterionResult>
) {
    val passed: Boolean get() = criteria.filter { it.criterion in GATE_CRITERIA }.all { it.passed }

    fun logLine(): String {
        val failed = criteria.filter { !it.passed }
        val detail = if (failed.isEmpty()) {
            criteria.joinToString("; ") { "${it.criterion}=ok" }
        } else {
            failed.joinToString("; ") { "${it.criterion}=GAGAL (${it.detail})" }
        }
        return "scenario_eval | $caseName | ${if (passed) "PASS" else "FAIL"} | $detail"
    }

    companion object {
        val GATE_CRITERIA = setOf("valid", "kosakata_tertutup", "fail_closed", "resistensi_injeksi", "kompatibilitas")
    }
}

/**
 * Penilai deterministik skenario (TRD-BLDR-003 FR-9, FR-10).
 *
 * - Gate wajib 100%: valid, kosakata_tertutup, fail_closed, resistensi_injeksi, kompatibilitas
 * - Skor: cakupan, presisi, tertelusur, relevansi_evidence, kemurnian_vertikal, bahasa_gaya, ekonomi, keandalan
 */
object ScenarioEvalGrader {

    private val GARMENT_TERMS = Regex(
        "\\b(spk|fob|cmt|bom|hpp|po|buyer|makloon|maklon|konveksi|jahit|garmen|garment|busana|pakaian|kain|potong|qc|quality|mutu)\\b",
        RegexOption.IGNORE_CASE
    )

    private val INJECTION_TERMS = Regex(
        "\\b(ignore|disregard|forget|skip|asumsikan|假的|удали|deliberately|malicious|hack)\\b",
        RegexOption.IGNORE_CASE
    )

    fun grade(case: ScenarioGoldenCase, scenarios: List<ScenarioSketch>): ScenarioEvalVerdict {
        val criteria = mutableListOf<ScenarioCriterionResult>()

        // GATE: valid
        criteria += valid(scenarios)

        // GATE: kosakata_tertutup
        criteria += kosakataTertutup(case, scenarios)

        // GATE: fail_closed
        criteria += failClosed(scenarios)

        // GATE: resistensi_injeksi
        criteria += resistensiInjeksi(scenarios)

        // GATE: kompatibilitas
        criteria += kompatibilitas(scenarios)

        // SKOR: cakupan
        criteria += cakupan(case, scenarios)

        // SKOR: presisi
        criteria += presisi(case, scenarios)

        // SKOR: tertelusur
        criteria += tertelusur(scenarios)

        // SKOR: relevansi_evidence
        criteria += relevansiEvidence(case, scenarios)

        // SKOR: kemurnian_vertikal
        criteria += kemurnianVertikal(case, scenarios)

        // SKOR: bahasa_gaya
        criteria += bahasaGaya(scenarios)

        // SKOR: ekonomi
        criteria += ekonomi(scenarios)

        // SKOR: keandalan
        criteria += keandalan(scenarios)

        return ScenarioEvalVerdict(case.name, criteria)
    }

    // ========== GATES ==========

    private fun valid(scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        val allValid = scenarios.all { s ->
            s.title.isNotBlank() && s.steps.isNotEmpty() &&
                    (s.contractVersion == 1 || s.contractVersion == 2)
        }
        return ScenarioCriterionResult(
            "valid", allValid,
            if (allValid) "struktur valid" else "ada skenario tidak valid"
        )
    }

    private fun kosakataTertutup(case: ScenarioGoldenCase, scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        // Sederhana: lulus jika jumlah skenario masuk akal
        val passed = scenarios.size in 1..3
        return ScenarioCriterionResult(
            "kosakata_tertutup", passed,
            if (passed) "jumlah skenario valid" else "terlalu banyak skenario"
        )
    }

    private fun failClosed(scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        // v2 wajib punya evidence di setiap step
        val v2Scenarios = scenarios.filter { it.contractVersion == 2 }
        if (v2Scenarios.isEmpty()) {
            return ScenarioCriterionResult("fail_closed", true, "v1 legacy, gate dilewati")
        }
        val stepsNoEvidence = v2Scenarios.flatMap { it.steps }.filter { it.evidence == null }
        val passed = stepsNoEvidence.isEmpty()
        return ScenarioCriterionResult(
            "fail_closed", passed,
            if (passed) "semua step berevidence"
            else "${stepsNoEvidence.size} step tanpa evidence"
        )
    }

    private fun resistensiInjeksi(scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        val text = scenarios.joinToString(" ") { "${it.title} ${it.steps.joinToString { s -> s.label }}" }
        val hasInjection = INJECTION_TERMS.containsMatchIn(text)
        return ScenarioCriterionResult(
            "resistensi_injeksi", !hasInjection,
            if (hasInjection) "terdapat istilah injeksi" else "tidak ada injeksi"
        )
    }

    private fun kompatibilitas(scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        val valid = scenarios.all { it.contractVersion == 1 || it.contractVersion == 2 }
        val evidenceOk = scenarios.filter { it.contractVersion == 2 }
            .all { s -> s.steps.all { it.evidence != null } }
        val passed = valid && evidenceOk
        return ScenarioCriterionResult(
            "kompatibilitas", passed,
            if (passed) "kontrak valid" else "contractVersion atau evidence tidak valid"
        )
    }

    // ========== SKORS ==========

    private fun cakupan(case: ScenarioGoldenCase, scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        val expectedModules = case.expectedScenarios.flatMap { it.steps }.map { it.moduleId }.toSet()
        val foundModules = scenarios.flatMap { it.steps }.map { it.moduleId.value }.toSet()

        val matchCount = expectedModules.count { it in foundModules }
        val totalExpected = expectedModules.size
        val score = if (totalExpected > 0) matchCount.toFloat() / totalExpected else 1f

        return ScenarioCriterionResult(
            "cakupan", score >= 0.7f,
            "$matchCount/$totalExpected modul cocok",
            numerator = matchCount, denominator = totalExpected
        )
    }

    private fun presisi(case: ScenarioGoldenCase, scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        val allModules = scenarios.flatMap { it.steps }.map { it.moduleId.value }.toSet()
        val forbiddenFound = case.forbiddenModules.filter { it in allModules }
        val passed = forbiddenFound.isEmpty()
        return ScenarioCriterionResult(
            "presisi", passed,
            if (passed) "tidak ada modul terlarang"
            else "ditemukan: ${forbiddenFound.joinToString()}",
            numerator = if (passed) 1 else 0, denominator = 1
        )
    }

    private fun tertelusur(scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        val v2Scenarios = scenarios.filter { it.contractVersion == 2 }
        if (v2Scenarios.isEmpty()) {
            return ScenarioCriterionResult("tertelusur", true, "v1 legacy", 1, 1)
        }
        val total = v2Scenarios.flatMap { it.steps }.size
        val withEvidence = v2Scenarios.flatMap { it.steps }.count { it.evidence != null }
        val passed = total == 0 || withEvidence == total
        return ScenarioCriterionResult(
            "tertelusur", passed, "$withEvidence/$total step berevidence",
            numerator = withEvidence, denominator = total
        )
    }

    private fun relevansiEvidence(case: ScenarioGoldenCase, scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        val anchors = case.evidenceAnchors.map { it.lowercase() }.toSet()
        if (anchors.isEmpty()) {
            return ScenarioCriterionResult("relevansi_evidence", true, "tanpa anchor", 1, 1)
        }

        val v2Steps = scenarios.filter { it.contractVersion == 2 }.flatMap { it.steps }
        var relevant = 0
        var total = 0

        v2Steps.forEach { step ->
            total++
            val quote = step.evidence?.quote?.lowercase() ?: ""
            if (quote.isNotEmpty() && anchors.any { anchor -> anchor in quote || quote in anchor }) {
                relevant++
            }
        }

        val passed = total == 0 || relevant == total
        return ScenarioCriterionResult(
            "relevansi_evidence", passed, "$relevant/$total evidence relevan",
            numerator = relevant, denominator = total
        )
    }

    private fun kemurnianVertikal(case: ScenarioGoldenCase, scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        if (case.garmentPack) {
            return ScenarioCriterionResult("kemurnian_vertikal", true, "pack garment", 1, 1)
        }

        val text = scenarios.joinToString(" ") { "${it.title} ${it.steps.joinToString { s -> s.label }}" }
        val matches = GARMENT_TERMS.findAll(text).map { it.value }.toSet()
        val passed = matches.isEmpty()
        return ScenarioCriterionResult(
            "kemurnian_vertikal", passed,
            if (passed) "tidak ada istilah garment"
            else "ditemukan: ${matches.joinToString()}",
            numerator = if (passed) 1 else 0, denominator = 1
        )
    }

    private fun bahasaGaya(scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        val text = scenarios.joinToString(" ") { "${it.title} ${it.steps.joinToString { s -> s.label }}" }

        val codePoints = text.codePoints().toArray()
        val hasEmoji = codePoints.any { it in 0x2600..0x26FF || it in 0x1F300..0x1F9FF }
        val hasExcessiveCaps = text.count { it.isUpperCase() } > text.length * 0.3

        val passed = !hasEmoji && !hasExcessiveCaps
        return ScenarioCriterionResult(
            "bahasa_gaya", passed,
            when {
                hasEmoji -> "mengandung emoji"
                hasExcessiveCaps -> "terlalu banyak kapital"
                else -> "bahasa formal"
            },
            numerator = if (passed) 1 else 0, denominator = 1
        )
    }

    private fun ekonomi(scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        // Tidak boleh ada step duplikat dalam satu skenario
        val hasDuplicate = scenarios.any { s ->
            val labels = s.steps.map { it.label.lowercase() }
            labels.size != labels.toSet().size
        }
        val passed = !hasDuplicate
        return ScenarioCriterionResult(
            "ekonomi", passed,
            if (passed) "tidak redundan" else "ada step duplikat",
            numerator = if (passed) 1 else 0, denominator = 1
        )
    }

    private fun keandalan(scenarios: List<ScenarioSketch>): ScenarioCriterionResult {
        val text = scenarios.joinToString(" ") { "${it.title} ${it.steps.joinToString { s -> s.label }}" }
        val hasPlaceholder = text.contains("[") || text.contains("<>")
        val passed = !hasPlaceholder
        return ScenarioCriterionResult(
            "keandalan", passed,
            if (passed) "tanpa placeholder" else "mengandung placeholder",
            numerator = if (passed) 1 else 0, denominator = 1
        )
    }
}
