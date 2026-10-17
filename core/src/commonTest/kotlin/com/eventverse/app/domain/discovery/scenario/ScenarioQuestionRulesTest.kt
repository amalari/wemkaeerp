package com.eventverse.app.domain.discovery.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.eventverse.app.domain.discovery.scenario.ScenarioTestFixtures as F

/** SC3 / FR-9: validator memeriksa pertanyaan pengusul (jumlah, panjang, duplikat) dengan path `questions[i]`. */
class ScenarioQuestionRulesTest {
    private val draft = F.draftOf()
    private fun issues(vararg q: String) = F.pathsOf(ScenarioRules.validate(draft, F.validScenario().copy(questions = q.toList())))

    @Test
    fun `no questions and up to the limit should be valid`() {
        assertEquals(emptyList(), issues())
        assertEquals(emptyList(), issues("DP harus lunas dulu?", "Siapa menerbitkan invoice?"))
    }

    @Test
    fun `more than the limit should be rejected`() {
        assertEquals(listOf("questions"), issues("a?", "b?", "c?"))
    }

    @Test
    fun `too long question should be rejected with its index`() {
        assertEquals(listOf("questions[1]"), issues("ok?", "x".repeat(ScenarioLimits.QUESTION_MAX_CHARS + 1)))
    }

    @Test
    fun `duplicate question after normalization should be rejected`() {
        assertEquals(listOf("questions[1]"), issues("DP lunas dulu?", "  dp   LUNAS dulu? "))
    }

    @Test
    fun `list validation should keep reporting question issues under the scenario index`() {
        val bad = F.validScenario("scn-bad").copy(questions = listOf("a?", "b?", "c?"))
        assertTrue(ScenarioRules.validateAll(draft, listOf(bad)).any { it.path.startsWith("scenarios[0].questions") })
    }
}
