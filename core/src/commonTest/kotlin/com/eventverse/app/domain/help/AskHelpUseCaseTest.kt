package com.eventverse.app.domain.help

import com.eventverse.app.domain.help.usecases.AskHelpCommand
import com.eventverse.app.domain.help.usecases.AskHelpUseCase
import com.eventverse.app.domain.pack.ElearningPack
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ShippedTutorialSource
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.tutorial.LexicalTutorialMatcher
import com.eventverse.app.domain.tutorial.TutorialCatalog
import com.eventverse.app.domain.tutorial.TutorialId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRD-HELP-001 FR-6: wewenang disaring sebelum agent, dan agent tidak bisa menyarankan di luar kandidat. */
class AskHelpUseCaseTest {

    private class ScriptedAgent(var reply: Result<HelpAnswer>? = null) : HelpAgent {
        override val agentRef = "scripted"
        var seen: HelpQuestion? = null
        override suspend fun answer(question: HelpQuestion): Result<HelpAnswer> {
            seen = question
            return reply ?: DeterministicHelpAgent().answer(question)
        }
    }

    private val agent = ScriptedAgent()
    private val ask = AskHelpUseCase(TutorialCatalog(ShippedTutorialSource), LexicalTutorialMatcher(), agent)

    private fun level(l: AccessLevel) = ModuleAccessConfig(level = l).let { AccessDecision(it, AccessSource.ROLE, it, ModuleAccessConfig()) }
    private val crmOperator = mapOf(GarmentModules.CRM_SALES to level(AccessLevel.OPERATE))
    private fun cmd(q: String, decisions: Map<ModuleId, AccessDecision> = crmOperator, pack: com.eventverse.app.domain.pack.DomainPack = GarmentDomainPack.pack) =
        AskHelpCommand(q, GarmentModules.CRM_SALES, pack, decisions)

    @Test
    fun answersWithTopCandidate_andAlternatives() = runTest {
        val result = ask(cmd("cara bikin lead baru")).getOrThrow()
        assertEquals("crm_new_lead", result.suggestion?.tutorialId?.value)
        assertTrue(result.alternatives.none { it.tutorialId == result.suggestion?.tutorialId })
    }

    @Test
    fun agentNeverSeesTutorialsOutsideCallerAccess() = runTest {
        ask(cmd("kenapa staf tidak bisa lihat menu, atur hak akses role")).getOrThrow()
        assertTrue(requireNotNull(agent.seen).candidates.none { it.tutorial.id.value == "platform_rbac_role_access" },
            "operator CRM tanpa akses RBAC tidak boleh ditawari (atau diberi tahu) tutorial RBAC")
    }

    @Test
    fun noAccess_yieldsNoSuggestion() = runTest {
        val result = ask(cmd("cara bikin lead baru", decisions = emptyMap())).getOrThrow()
        assertNull(result.suggestion)
        assertEquals(DeterministicHelpAgent.NO_MATCH, result.answer)
    }

    @Test
    fun hallucinatedTutorialId_fallsBackToDeterministic() = runTest {
        agent.reply = Result.success(HelpAnswer("Buka tutorial rahasia", TutorialId("tidak_ada"), 0, "scripted"))
        val result = ask(cmd("cara bikin lead baru")).getOrThrow()
        assertEquals("crm_new_lead", result.suggestion?.tutorialId?.value)
        assertEquals("deterministic/help-v1", result.agentRef)
    }

    @Test
    fun outOfRangeStep_fallsBack_andAgentFailure_fallsBack() = runTest {
        agent.reply = Result.success(HelpAnswer("x", TutorialId("crm_new_lead"), 99, "scripted"))
        assertEquals("deterministic/help-v1", ask(cmd("cara bikin lead baru")).getOrThrow().agentRef)
        agent.reply = Result.failure(IllegalStateException("LLM mati"))
        assertEquals("deterministic/help-v1", ask(cmd("cara bikin lead baru")).getOrThrow().agentRef)
    }

    @Test
    fun validAgentAnswer_isKept() = runTest {
        agent.reply = Result.success(HelpAnswer("Klik + Tambah Lead.", TutorialId("crm_new_lead"), 1, "scripted"))
        val result = ask(cmd("cara bikin lead baru")).getOrThrow()
        assertEquals("scripted", result.agentRef)
        assertEquals(1, result.suggestion?.stepIndex)
    }

    @Test
    fun resolvedAction_skipsMatcherAndAgent_entirely() = runTest {
        val action = HelpAction.PrefillLead(GarmentModules.CRM_SALES, "catat lead PT Maju 0812 3456 7890")
        val result = ask(cmd(action.text).copy(actionResolver = HelpActionResolver { action })).getOrThrow()
        assertEquals(action, result.action)
        assertNull(result.suggestion)
        assertEquals(AskHelpUseCase.ACTION_REF, result.agentRef)
        assertNull(agent.seen, "pesan berisi data pelanggan tidak dikirim ke agent")
    }

    @Test
    fun agentSeesMaskedQuestion_noPhoneOrEmail() = runTest {
        ask(cmd("cara bikin lead baru untuk budi@maju.co.id 0812 3456 7890")).getOrThrow()
        val sent = requireNotNull(agent.seen).text
        assertFalse("3456" in sent || "maju.co.id" in sent, sent)
    }

    @Test
    fun blankOrTooLongQuestion_isRejected() = runTest {
        assertTrue(ask(cmd("   ")).isFailure)
        assertTrue(ask(cmd("a".repeat(501))).isFailure)
    }

    @Test
    fun nonGarmentPack_neverSuggestsGarmentTutorials() = ElearningPack.registered {
        kotlinx.coroutines.test.runTest {
            val result = ask(cmd("cara bikin lead baru", decisions = mapOf(GarmentModules.CRM_SALES to level(AccessLevel.MANAGE)), pack = ElearningPack.pack)).getOrThrow()
            assertNull(result.suggestion, "pack e-learning tidak punya CRM — keputusan akses CRM yang nyasar tidak membuka tutorialnya")
        }
    }
}
