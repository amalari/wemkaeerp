package com.eventverse.app.infrastructure.help

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import com.eventverse.app.domain.help.usecases.AskHelpCommand
import com.eventverse.app.domain.help.usecases.AskHelpUseCase
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
import com.eventverse.app.infrastructure.EnvLoader
import com.eventverse.app.infrastructure.discovery.DiscoveryAgents
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **Evals AI helper hidup** (TRD-HELP-001 Fase 3). Opt-in: `HELP_LIVE_EVALS=1` + `DEEPSEEK_API_KEY`
 * (env atau `.env`). Tanpa itu dilewati, jadi `:server:test` biasa tetap tanpa jaringan & biaya.
 *
 * ```
 * HELP_LIVE_EVALS=1 ./gradlew :server:test --tests '*KoogHelpLiveEvalsTest'
 * ```
 *
 * Kasus diukur dengan `agentRef` (LLM benar-benar menjawab, bukan fallback) + tutorial yang diharapkan.
 */
class KoogHelpLiveEvalsTest {

    private data class Case(val question: String, val expected: String)

    private val cases = listOf(
        Case("ada buyer baru chat WA, dicatat di mana?", "crm_new_lead"),
        Case("customer sudah setuju harga, lead-nya dipindah gimana?", "crm_move_stage"),
        Case("staf sales saya nggak bisa lihat menu CRM", "platform_rbac_role_access"),
    )

    @Test
    fun liveAgent_picksExpectedTutorial() = runBlocking {
        assumeTrue("HELP_LIVE_EVALS bukan 1 — evals dilewati", EnvLoader.get("HELP_LIVE_EVALS") == "1")
        val apiKey = EnvLoader.get("DEEPSEEK_API_KEY").takeIf { it.isNotBlank() }
        assumeTrue("DEEPSEEK_API_KEY kosong — evals dilewati", apiKey != null)

        val agent = KoogHelpAgent(MultiLLMPromptExecutor(DeepSeekLLMClient(requireNotNull(apiKey))), DiscoveryAgents.defaultModel)
        val ask = AskHelpUseCase(TutorialCatalog(ShippedTutorialSource), LexicalTutorialMatcher(), agent)
        val manage = ModuleAccessConfig(level = AccessLevel.MANAGE).let { AccessDecision(it, AccessSource.ROLE, it, ModuleAccessConfig()) }
        val decisions: Map<ModuleId, AccessDecision> = GarmentDomainPack.pack.modules.associate { it.id to manage }

        val results = cases.map { c ->
            val r = ask(AskHelpCommand(c.question, GarmentModules.CRM_SALES, GarmentDomainPack.pack, decisions)).getOrThrow()
            val pass = r.agentRef == agent.agentRef && r.suggestion?.tutorialId?.value == c.expected
            println("evals | ${r.agentRef} | ${c.expected} | ${if (pass) "PASS" else "FAIL"} | ${r.suggestion?.tutorialId?.value} | ${r.answer}")
            pass
        }
        assertTrue(results.all { it }, "lihat baris 'evals |' di output test")
    }
}
