package com.eventverse.app

/**
 * TRD-BLDR-003 F2: Coverage Registry
 *
 * Tracks what's been evaluated (Covered) and what's exempt from evaluation.
 * Implements two-way ratchet: new items must be registered, removed items must have reason.
 */
data class EvalCoverageEntry(
    val key: String,
    val type: CoverageType,
    val status: CoverageStatus,
    val reason: String,
    val addedAt: Long = System.currentTimeMillis()
)

enum class CoverageType {
    BRIEF_CASE,
    SCENARIO_CASE,
    CRITERION
}

enum class CoverageStatus {
    COVERED,
    EXEMPT
}

/**
 * Registry untuk tracking coverage evaluation.
 * Mirip TutorialCoverageTest pattern.
 */
object BuilderEvalCoverage {

    private val _entries = mutableMapOf<String, EvalCoverageEntry>()

    val entries: Map<String, EvalCoverageEntry> get() = _entries.toMap()

    /**
     * Register a new item as COVERED.
     */
    fun cover(key: String, type: CoverageType) {
        require(key.isNotBlank()) { "Key tidak boleh kosong" }
        require(!_entries.containsKey(key)) { "Key sudah terdaftar: $key" }
        _entries[key] = EvalCoverageEntry(
            key = key,
            type = type,
            status = CoverageStatus.COVERED,
            reason = "Grader tersedia dan lulus"
        )
    }

    /**
     * Register an item as EXEMPT dengan alasan.
     */
    fun exempt(key: String, type: CoverageType, reason: String) {
        require(key.isNotBlank()) { "Key tidak boleh kosong" }
        require(reason.isNotBlank()) { "Alasan exempt wajib" }
        require(!_entries.containsKey(key)) { "Key sudah terdaftar: $key" }
        _entries[key] = EvalCoverageEntry(
            key = key,
            type = type,
            status = CoverageStatus.EXEMPT,
            reason = reason
        )
    }

    /**
     * Check if a key is registered (Covered or Exempt).
     */
    fun isRegistered(key: String): Boolean = key in _entries

    /**
     * Get coverage status for a key.
     */
    fun status(key: String): CoverageStatus? = _entries[key]?.status

    /**
     * All covered entries.
     */
    fun covered(): List<EvalCoverageEntry> = _entries.values.filter { it.status == CoverageStatus.COVERED }

    /**
     * All exempt entries.
     */
    fun exempt(): List<EvalCoverageEntry> = _entries.values.filter { it.status == CoverageStatus.EXEMPT }

    /**
     * Initialize with current golden cases.
     */
    fun initializeFromGoldenCases() {
        // Brief cases
        BuilderBriefGoldenCases.all.forEach { c ->
            cover("brief:${c.name}", CoverageType.BRIEF_CASE)
        }

        // Scenario cases
        ScenarioGoldenCases.all.forEach { c ->
            cover("scenario:${c.name}", CoverageType.SCENARIO_CASE)
        }

        // Criteria
        listOf(
            "valid", "kosakata_tertutup", "fail_closed", "resistensi_injeksi", "kompatibilitas",
            "cakupan", "presisi", "tertelusur", "relevansi_evidence", "kemurnian_vertikal",
            "bahasa_gaya", "ekonomi", "keandalan"
        ).forEach { criterion ->
            cover("criterion:$criterion", CoverageType.CRITERION)
        }
    }

    /**
     * Reset for testing.
     */
    fun reset() {
        _entries.clear()
    }

    /**
     * Summary report.
     */
    fun summary(): String {
        val covered = covered()
        val exempt = exempt()
        return buildString {
            appendLine("=== BuilderEvalCoverage Summary ===")
            appendLine("COVERED: ${covered.size}")
            covered.groupBy { it.type }.forEach { (type, entries) ->
                appendLine("  $type: ${entries.size}")
            }
            appendLine("EXEMPT: ${exempt.size}")
            exempt.groupBy { it.type }.forEach { (type, entries) ->
                appendLine("  $type: ${entries.size}")
                entries.forEach { e -> appendLine("    - ${e.key}: ${e.reason}") }
            }
            appendLine("================================")
        }
    }
}
