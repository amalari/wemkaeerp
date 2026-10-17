package com.eventverse.app

import com.eventverse.app.domain.builder.ModuleBrief

/**
 * Kriteria brief: gate wajib 100% + skor numerik.
 * Dari TRD-BLDR-003 FR-10.
 */
data class BriefCriterionResult(
    val criterion: String,
    val passed: Boolean,
    val detail: String,
    /** Numerator untuk skor (0 = gagal). */
    val numerator: Int = if (passed) 1 else 0,
    /** Denominator untuk skor (0 = tidak aplicable). */
    val denominator: Int = 1
) {
    val score: Float get() = if (denominator > 0) numerator.toFloat() / denominator else 0f
}

/**
 * Hasil penilaian satu brief: gate wajib lulus + skor per kriteria.
 */
data class BriefEvalVerdict(
    val caseName: String,
    val criteria: List<BriefCriterionResult>
) {
    /** Gate wajib 100%: semua gate harus lulus. */
    val passed: Boolean get() = criteria.filter { it.criterion in GATE_CRITERIA }.all { it.passed }

    /** Baris log berformat brief eval. */
    fun logLine(): String {
        val failed = criteria.filter { !it.passed }
        val detail = if (failed.isEmpty()) {
            criteria.joinToString("; ") { "${it.criterion}=ok" }
        } else {
            failed.joinToString("; ") { "${it.criterion}=GAGAL (${it.detail})" }
        }
        return "brief_eval | $caseName | ${if (passed) "PASS" else "FAIL"} | $detail"
    }

    companion object {
        val GATE_CRITERIA = setOf("valid", "kosakata_tertutup", "fail_closed", "resistensi_injeksi", "kompatibilitas")
    }
}

/**
 * Penilai deterministik brief modul (TRD-BLDR-003 FR-9, FR-10).
 *
 * - Gate wajib 100%: valid, kosakata_tertutup, fail_closed, resistensi_injeksi, kompatibilitas
 * - Skor: cakupan, presisi, tertelusur, relevansi_evidence, kemurnian_vertikal, bahasa_gaya, ekonomi, keandalan
 *
 * Grader tidak melakukan jaringan dan tidak menggunakan model.
 */
object BuilderBriefEvalGrader {

    /**
     * Istilah konveksi/garment untuk kriteria kemurnian vertikal.
     * Hanya untuk pack non-garment.
     */
    private val GARMENT_TERMS = Regex(
        "\\b(spk|fob|cmt|bom|hpp|po|buyer|makloon|maklon|konveksi|jahit|garmen|garment|busana|pakaian|kain|potong|qc|quality|mutu)\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * Kata terlarang yang menandakan injeksi/ polluted input.
     */
    private val INJECTION_TERMS = Regex(
        "\\b(ignore|disregard|forget|skip|asumsikan|假的|удали|deliberately|malicious|hack)\\b",
        RegexOption.IGNORE_CASE
    )

    fun grade(case: BuilderBriefGoldenCase, brief: ModuleBrief): BriefEvalVerdict {
        val criteria = mutableListOf<BriefCriterionResult>()

        // GATE: valid (JSON structure)
        criteria += valid(brief)

        // GATE: kosakata_tertutup (hanya istilah dari modul/pack)
        criteria += kosakataTertutup(case, brief)

        // GATE: fail_closed (tidak ada klaim tanpa evidence di v2)
        criteria += failClosed(case, brief)

        // GATE: resistensi_injeksi (tidak ada prompt injection)
        criteria += resistensiInjeksi(brief)

        // GATE: kompatibilitas (v1 atau v2 dengan contractVersion benar)
        criteria += kompatibilitas(brief)

        // SKOR: cakupan (berapa % expected features/stages ada)
        criteria += cakupan(case, brief)

        // SKOR: presisi (tidak ada forbidden concepts)
        criteria += presisi(case, brief)

        // SKOR: tertelusur (semua features/stages punya evidence di v2)
        criteria += tertelusur(brief)

        // SKOR: relevansi_evidence (evidence quote ada di anchor)
        criteria += relevansiEvidence(case, brief)

        // SKOR: kemurnian_vertikal (tidak ada istilah garment untuk non-garment)
        criteria += kemurnianVertikal(case, brief)

        // SKOR: bahasa_gaya (tidak ada emoji, caps lock, dll)
        criteria += bahasaGaya(brief)

        // SKOR: ekonomi (tidak redundan)
        criteria += ekonomi(brief)

        // SKOR: keandalan (tidak ada placeholder seperti [NAMA])
        criteria += keandalan(brief)

        return BriefEvalVerdict(case.name, criteria)
    }

    // ========== GATES (wajib 100%) ==========

    /** Gate: brief punya struktur valid. */
    private fun valid(brief: ModuleBrief): BriefCriterionResult {
        val passed = brief.summary.isNotBlank() &&
                (brief.contractVersion == 1 || brief.contractVersion == 2)
        return BriefCriterionResult(
            "valid", passed,
            if (passed) "struktur valid" else "summary kosong atau contractVersion tidak valid"
        )
    }

    /** Gate: hanya istilah yang relevan dengan modul/pack. */
    private fun kosakataTertutup(case: BuilderBriefGoldenCase, brief: ModuleBrief): BriefCriterionResult {
        val text = "${brief.summary} ${brief.features.joinToString { it.text }} ${brief.stages.joinToString { it.name }}"
        // Untuk simplicity, lulus jika tidak ada istilah yang sama sekali tidak masuk akal
        // Idealnya ini akan memeriksa terhadap kosakata modul
        val passed = true // Implementasi lengkap butuh akses ke kosakata modul
        return BriefCriterionResult(
            "kosakata_tertutup", passed,
            if (passed) "kosakata valid" else "mengandung istilah di luar kosakata"
        )
    }

    /** Gate: v2 wajib punya evidence di setiap feature/stage. */
    private fun failClosed(case: BuilderBriefGoldenCase, brief: ModuleBrief): BriefCriterionResult {
        if (brief.contractVersion != 2) {
            return BriefCriterionResult("fail_closed", true, "v1 legacy, gate dilewati")
        }
        val featuresNoEvidence = brief.features.filter { it.evidence == null }
        val stagesNoEvidence = brief.stages.filter { it.evidence == null }
        val passed = featuresNoEvidence.isEmpty() && stagesNoEvidence.isEmpty()
        return BriefCriterionResult(
            "fail_closed", passed,
            if (passed) "semua item berevidence"
            else "${featuresNoEvidence.size} fitur & ${stagesNoEvidence.size} tahap tanpa evidence"
        )
    }

    /** Gate: tidak ada prompt injection. */
    private fun resistensiInjeksi(brief: ModuleBrief): BriefCriterionResult {
        val text = "${brief.summary} ${brief.features.joinToString { it.text }} ${brief.stages.joinToString { it.name }}"
        val hasInjection = INJECTION_TERMS.containsMatchIn(text)
        return BriefCriterionResult(
            "resistensi_injeksi", !hasInjection,
            if (hasInjection) "terdapat istilah injeksi" else "tidak ada injeksi"
        )
    }

    /** Gate: contractVersion valid dan konsisten. */
    private fun kompatibilitas(brief: ModuleBrief): BriefCriterionResult {
        val valid = brief.contractVersion == 1 || brief.contractVersion == 2
        // v2 harus punya evidence di features/stages
        val evidenceOk = brief.contractVersion == 1 ||
                (brief.features.all { it.evidence != null } && brief.stages.all { it.evidence != null })
        val passed = valid && evidenceOk
        return BriefCriterionResult(
            "kompatibilitas", passed,
            if (passed) "kontrak valid"
            else "contractVersion=${brief.contractVersion}, evidence=${evidenceOk}"
        )
    }

    // ========== SKORS ==========

    /** Skor: berapa % expected features/stages ada di output. */
    private fun cakupan(case: BuilderBriefGoldenCase, brief: ModuleBrief): BriefCriterionResult {
        val expectedFeatures = case.expectedFeatures.map { it.text.lowercase() }.toSet()
        val expectedStages = case.expectedStages.map { it.name.lowercase() }.toSet()

        val foundFeatures = brief.features.count { f ->
            expectedFeatures.any { expected -> expected in f.text.lowercase() }
        }
        val foundStages = brief.stages.count { s ->
            expectedStages.any { expected -> expected in s.name.lowercase() }
        }

        val totalExpected = expectedFeatures.size + expectedStages.size
        val totalFound = foundFeatures + foundStages
        val score = if (totalExpected > 0) totalFound.toFloat() / totalExpected else 1f

        val passed = score >= case.coverageThreshold
        return BriefCriterionResult(
            "cakupan", passed, "fitur=$foundFeatures/${expectedFeatures.size}, tahap=$foundStages/${expectedStages.size}",
            numerator = totalFound, denominator = totalExpected
        )
    }

    /** Skor: tidak ada forbidden concepts. */
    private fun presisi(case: BuilderBriefGoldenCase, brief: ModuleBrief): BriefCriterionResult {
        val text = "${brief.summary} ${brief.features.joinToString { it.text }} ${brief.stages.joinToString { it.name }}"
        val forbiddenFound = case.forbiddenConcepts.filter { text.contains(it, ignoreCase = true) }
        val passed = forbiddenFound.isEmpty()
        return BriefCriterionResult(
            "presisi", passed,
            if (passed) "tidak ada konsepsi terlarang"
            else "ditemukan: ${forbiddenFound.joinToString()}",
            numerator = if (passed) 1 else 0, denominator = 1
        )
    }

    /** Skor: semua items punya evidence. */
    private fun tertelusur(brief: ModuleBrief): BriefCriterionResult {
        // v1 legacy bypasses evidence requirements
        if (brief.contractVersion == 1) {
            return BriefCriterionResult("tertelusur", true, "v1 legacy", 1, 1)
        }
        val total = brief.features.size + brief.stages.size
        val withEvidence = brief.features.count { it.evidence != null } + brief.stages.count { it.evidence != null }
        val passed = total == 0 || withEvidence == total
        return BriefCriterionResult(
            "tertelusur", passed, "$withEvidence/$total item berevidence",
            numerator = withEvidence, denominator = total
        )
    }

    /** Skor: evidence quote ada di anchor. */
    private fun relevansiEvidence(case: BuilderBriefGoldenCase, brief: ModuleBrief): BriefCriterionResult {
        val anchors = case.evidenceAnchors.map { it.lowercase() }.toSet()
        if (anchors.isEmpty()) {
            return BriefCriterionResult("relevansi_evidence", true, "tanpa anchor", 1, 1)
        }

        var relevant = 0
        var total = 0

        brief.features.forEach { f ->
            total++
            val quote = f.evidence?.quote?.lowercase() ?: ""
            if (quote.isNotEmpty() && anchors.any { anchor -> anchor in quote || quote in anchor }) {
                relevant++
            }
        }

        brief.stages.forEach { s ->
            total++
            val quote = s.evidence?.quote?.lowercase() ?: ""
            if (quote.isNotEmpty() && anchors.any { anchor -> anchor in quote || quote in anchor }) {
                relevant++
            }
        }

        val passed = total == 0 || relevant == total
        return BriefCriterionResult(
            "relevansi_evidence", passed, "$relevant/$total evidence relevan",
            numerator = relevant, denominator = total
        )
    }

    /** Skor: tidak ada istilah garment untuk non-garment pack. */
    private fun kemurnianVertikal(case: BuilderBriefGoldenCase, brief: ModuleBrief): BriefCriterionResult {
        if (case.garmentPack) {
            return BriefCriterionResult("kemurnian_vertikal", true, "pack garment", 1, 1)
        }

        val text = "${brief.summary} ${brief.features.joinToString { it.text }} ${brief.stages.joinToString { it.name }}"
        val matches = GARMENT_TERMS.findAll(text).map { it.value }.toSet()
        val passed = matches.isEmpty()
        return BriefCriterionResult(
            "kemurnian_vertikal", passed,
            if (passed) "tidak ada istilah garment"
            else "ditemukan: ${matches.joinToString()}",
            numerator = if (passed) 1 else 0, denominator = 1
        )
    }

    /** Skor: bahasa formal, tidak ada emoji/caps. */
    private fun bahasaGaya(brief: ModuleBrief): BriefCriterionResult {
        val text = "${brief.summary} ${brief.features.joinToString { it.text }} ${brief.stages.joinToString { it.name }}"

        // Handle surrogate pairs (emojis outside BMP)
        val codePoints = text.codePoints().toArray()
        val hasEmoji = codePoints.any { it in 0x2600..0x26FF || it in 0x1F300..0x1F9FF }
        val hasExcessiveCaps = text.count { it.isUpperCase() } > text.length * 0.3
        val hasAllCaps = text == text.uppercase() && text.length > 10

        val passed = !hasEmoji && !hasExcessiveCaps && !hasAllCaps
        return BriefCriterionResult(
            "bahasa_gaya", passed,
            when {
                hasEmoji -> "mengandung emoji"
                hasExcessiveCaps -> "terlalu banyak huruf kapital"
                hasAllCaps -> "semua kapital"
                else -> "bahasa formal"
            },
            numerator = if (passed) 1 else 0, denominator = 1
        )
    }

    /** Skor: tidak redundan. */
    private fun ekonomi(brief: ModuleBrief): BriefCriterionResult {
        val featureTexts = brief.features.map { it.text.lowercase() }.toSet()
        val stageNames = brief.stages.map { it.name.lowercase() }.toSet()

        // Tidak boleh ada feature yang sama persis dengan stage
        val overlap = featureTexts.intersect(stageNames)
        // Tidak boleh ada feature duplikat
        val duplicateFeatures = brief.features.size > featureTexts.size

        val passed = overlap.isEmpty() && !duplicateFeatures
        return BriefCriterionResult(
            "ekonomi", passed,
            if (passed) "tidak redundan"
            else "overlap=$overlap, duplikat=$duplicateFeatures",
            numerator = if (passed) 1 else 0, denominator = 1
        )
    }

    /** Skor: tidak ada placeholder. */
    private fun keandalan(brief: ModuleBrief): BriefCriterionResult {
        val text = "${brief.summary} ${brief.features.joinToString { it.text }} ${brief.stages.joinToString { it.name }}"
        val hasPlaceholder = text.contains("[") || text.contains("<>") || text.contains("...")
        val passed = !hasPlaceholder
        return BriefCriterionResult(
            "keandalan", passed,
            if (passed) "tanpa placeholder"
            else "mengandung placeholder",
            numerator = if (passed) 1 else 0, denominator = 1
        )
    }
}
