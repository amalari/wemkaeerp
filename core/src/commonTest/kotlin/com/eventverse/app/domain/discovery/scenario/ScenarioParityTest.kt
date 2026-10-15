package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.shared.discovery.ScenarioSketchCodec
import com.eventverse.app.shared.json.JsonParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.eventverse.app.domain.discovery.scenario.ScenarioTestFixtures as F

/** Tes paritas SA5: FR-8 (hanya terkonfirmasi) dan penanganan eksplisit tiap [ScenarioSource]. */
class ScenarioParityTest {

    @Test
    fun `confirmedScenarios should never expose unconfirmed scenarios of any source`() {
        val all = ScenarioSource.entries.flatMapIndexed { i, src ->
            listOf(F.validScenario("c$i", src, true), F.validScenario("u$i", src, false))
        }
        val reader = F.draftOf(scenarios = all).confirmedScenarios()
        assertTrue(reader.isNotEmpty())
        assertTrue(reader.all { it.confirmed })
        assertEquals(ScenarioSource.entries.size, reader.size)
        assertEquals(emptyList(), F.draftOf(scenarios = all.filter { !it.confirmed }).confirmedScenarios())
    }

    @Test
    fun `scenarios proposed by the service should stay out of confirmedScenarios until confirmed`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf())
        val svc = DiscoveryScenarioService(repo, FakeProposer(Result.success(listOf(F.validScenario("a")))))
        val added = svc.propose(F.tenant).getOrThrow().single()
        assertEquals(emptyList(), repo.stored?.draft?.confirmedScenarios())
        svc.confirm(F.tenant, added.id).getOrThrow()
        assertEquals(listOf(added.id), repo.stored?.draft?.confirmedScenarios()?.map { it.id })
    }

    @Test
    fun `every source should round trip the codec and get the same validator verdict`() {
        val draft = F.draftOf()
        ScenarioSource.entries.forEach { src ->
            val s = F.validScenario("s-${src.name}", src, confirmed = true)
            assertEquals(listOf(s), ScenarioSketchCodec.decode(JsonParser.parse(ScenarioSketchCodec.encode(listOf(s)).encode()), "$.scenarios"), src.name)
            assertEquals(emptyList(), ScenarioRules.validate(draft, s), src.name)
            assertTrue(ScenarioRules.validate(draft, s.copy(steps = emptyList())).isNotEmpty(), src.name)
        }
    }

    @Test
    fun `every source should be confirmable and editing should always end as user`() = runTest {
        ScenarioSource.entries.forEach { src ->
            val repo = FakeDraftRepository.of(F.draftOf(scenarios = listOf(F.validScenario("s", src))))
            val svc = DiscoveryScenarioService(repo, FakeProposer())
            assertEquals(src, svc.confirm(F.tenant, ScenarioId("s")).getOrThrow().source, src.name)
            assertEquals(ScenarioSource.USER, svc.edit(F.tenant, F.validScenario("s", src)).getOrThrow().source, src.name)
            assertFalse(repo.stored?.draft?.scenarios?.single()?.confirmed ?: true, src.name)
        }
    }

    @Test
    fun `service propose should only ever emit system sources`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf())
        val svc = DiscoveryScenarioService(repo, FakeProposer(Result.success(listOf(F.validScenario("a", ScenarioSource.USER, true)))))
        val out = svc.propose(F.tenant).getOrThrow()
        assertTrue(out.all { it.source == ScenarioSource.AI || it.source == ScenarioSource.DETERMINISTIC })
    }
}
