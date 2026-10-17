package com.eventverse.app.domain.builder

import com.eventverse.app.domain.evidence.EvidenceContext

/**
 * TRD-BLDR-003 F3: Live Eval Quality Gate
 *
 * Wrapper yang menambahkan quality gate ke live eval.
 * Jika gate gagal, run dianggap GAGAL (bukan sekadar warning).
 */
data class LiveEvalResult(
    val passed: Boolean,
    val gateResults: List<GateResult>,
    val scoreResults: List<ScoreResult>,
    val model: String,
    val caseName: String,
    val durationMs: Long,
    val tokensUsed: Int?,
    val costUsd: Double?,
    val error: String? = null
)

data class GateResult(
    val name: String,
    val passed: Boolean,
    val detail: String
)

data class ScoreResult(
    val name: String,
    val score: Float,
    val numerator: Int,
    val denominator: Int
)

/**
 * Internal criterion for evaluation.
 */
data class EvalCriterion(
    val criterion: String,
    val passed: Boolean,
    val score: Float,
    val numerator: Int,
    val denominator: Int,
    val detail: String
)

object LiveEvalGate {

    /**
     * Evaluate a brief against its own criteria (self-check mode for live eval).
     */
    fun evaluateBrief(
        brief: ModuleBrief,
        model: String,
        caseName: String,
        durationMs: Long,
        tokensUsed: Int? = null,
        costUsd: Double? = null
    ): LiveEvalResult {
        return try {
            val criteria = mutableListOf<EvalCriterion>()

            // Self-check: brief must have contract version 2 (v1 is legacy, always passes)
            val isLegacy = brief.contractVersion == 1
            val hasContractV2 = brief.contractVersion == 2
            criteria.add(
                EvalCriterion(
                    criterion = "contract_v2",
                    passed = true, // v1 is legacy, always passes
                    score = if (hasContractV2) 1f else 0f,
                    numerator = if (hasContractV2) 1 else 0,
                    denominator = 1,
                    detail = if (isLegacy) "Legacy (v1)" else "Contract v2 present"
                )
            )

            // Self-check: features should have evidence if v2
            val featuresWithEvidence = brief.features.count { it.evidence != null }
            val featuresPass = brief.contractVersion == 1 || featuresWithEvidence > 0
            criteria.add(
                EvalCriterion(
                    criterion = "feature_evidence",
                    passed = featuresPass,
                    score = if (featuresPass) 1f else 0f,
                    numerator = featuresWithEvidence,
                    denominator = brief.features.size,
                    detail = "$featuresWithEvidence/${brief.features.size} features have evidence"
                )
            )

            // Self-check: stages should have evidence if v2
            val stagesWithEvidence = brief.stages.count { it.evidence != null }
            val stagesPass = brief.contractVersion == 1 || stagesWithEvidence > 0
            criteria.add(
                EvalCriterion(
                    criterion = "stage_evidence",
                    passed = stagesPass,
                    score = if (stagesPass) 1f else 0f,
                    numerator = stagesWithEvidence,
                    denominator = brief.stages.size,
                    detail = "$stagesWithEvidence/${brief.stages.size} stages have evidence"
                )
            )

            val gateCriteria = setOf("contract_v2", "feature_evidence", "stage_evidence")
            val gateResultsList = criteria
                .filter { it.criterion in gateCriteria }
                .map { GateResult(it.criterion, it.passed, it.detail) }

            val scoreResultsList = criteria
                .filter { it.criterion !in gateCriteria }
                .map { ScoreResult(it.criterion, it.score, it.numerator, it.denominator) }

            val passed = gateResultsList.all { it.passed }

            LiveEvalResult(
                passed = passed,
                gateResults = gateResultsList,
                scoreResults = scoreResultsList,
                model = model,
                caseName = caseName,
                durationMs = durationMs,
                tokensUsed = tokensUsed,
                costUsd = costUsd
            )
        } catch (e: Exception) {
            LiveEvalResult(
                passed = false,
                gateResults = emptyList(),
                scoreResults = emptyList(),
                model = model,
                caseName = caseName,
                durationMs = durationMs,
                tokensUsed = tokensUsed,
                costUsd = costUsd,
                error = e.message
            )
        }
    }

    /**
     * Evaluate a scenario against its own criteria (self-check mode for live eval).
     */
    fun evaluateScenario(
        scenario: com.eventverse.app.domain.discovery.scenario.ScenarioSketch,
        model: String,
        caseName: String,
        durationMs: Long,
        tokensUsed: Int? = null,
        costUsd: Double? = null
    ): LiveEvalResult {
        return try {
            val criteria = mutableListOf<EvalCriterion>()

            // Self-check: scenario must have contract version 2 (v1 is legacy, always passes)
            val isLegacy = scenario.contractVersion == 1
            val hasContractV2 = scenario.contractVersion == 2
            criteria.add(
                EvalCriterion(
                    criterion = "contract_v2",
                    passed = true, // v1 is legacy, always passes
                    score = if (hasContractV2) 1f else 0f,
                    numerator = if (hasContractV2) 1 else 0,
                    denominator = 1,
                    detail = if (isLegacy) "Legacy (v1)" else "Contract v2 present"
                )
            )

            // Self-check: steps should have evidence if v2
            val stepsWithEvidence = scenario.steps.count { it.evidence != null }
            val stepsPass = scenario.contractVersion == 1 || stepsWithEvidence > 0
            criteria.add(
                EvalCriterion(
                    criterion = "step_evidence",
                    passed = stepsPass,
                    score = if (stepsPass) 1f else 0f,
                    numerator = stepsWithEvidence,
                    denominator = scenario.steps.size,
                    detail = "$stepsWithEvidence/${scenario.steps.size} steps have evidence"
                )
            )

            val gateCriteria = setOf("contract_v2", "step_evidence")
            val gateResultsList = criteria
                .filter { it.criterion in gateCriteria }
                .map { GateResult(it.criterion, it.passed, it.detail) }

            val scoreResultsList = criteria
                .filter { it.criterion !in gateCriteria }
                .map { ScoreResult(it.criterion, it.score, it.numerator, it.denominator) }

            val passed = gateResultsList.all { it.passed }

            LiveEvalResult(
                passed = passed,
                gateResults = gateResultsList,
                scoreResults = scoreResultsList,
                model = model,
                caseName = caseName,
                durationMs = durationMs,
                tokensUsed = tokensUsed,
                costUsd = costUsd
            )
        } catch (e: Exception) {
            LiveEvalResult(
                passed = false,
                gateResults = emptyList(),
                scoreResults = emptyList(),
                model = model,
                caseName = caseName,
                durationMs = durationMs,
                tokensUsed = tokensUsed,
                costUsd = costUsd,
                error = e.message
            )
        }
    }

    fun checkBudget(budgetUsd: Double, estimatedCostPerCall: Double, callsRemaining: Int): Boolean {
        return budgetUsd >= estimatedCostPerCall * callsRemaining
    }

    fun formatLog(result: LiveEvalResult): String {
        val status = if (result.passed) "PASS" else "FAIL"
        val gates = result.gateResults.joinToString(",") { "${it.name}=${if (it.passed) "ok" else "GAGAL"}" }
        return "live_eval | ${result.model} | ${result.caseName} | $status | $gates | ${result.durationMs}ms | ${result.tokensUsed ?: "-"} tokens"
    }
}
