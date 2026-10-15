package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.usecases.UpdateDiscoveryDraftUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.eventverse.app.domain.discovery.scenario.ScenarioTestFixtures as F

class DiscoveryScenarioServiceTest {
    private fun service(repo: FakeDraftRepository, proposer: ScenarioProposer, narrative: String = "") =
        DiscoveryScenarioService(repo, proposer, narrative = { narrative })

    private fun bad(id: String = "bad") = F.validScenario(id).copy(steps = listOf(F.step(F.DAFTAR, "s_hantu"), F.step(F.POLI, null)))
    private val kasirFirst = F.validScenario("k").copy(steps = listOf(F.step(F.KASIR, null), F.step(F.POLI, null)))

    @Test
    fun `valid ai result should be saved unconfirmed with ai source`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf())
        val lied = F.validScenario("a", ScenarioSource.USER, confirmed = true)
        val added = service(repo, FakeProposer(Result.success(listOf(lied))), "cerita klinik").propose(F.tenant).getOrThrow()
        assertEquals(1, added.size)
        assertEquals(ScenarioSource.AI, added[0].source)
        assertFalse(added[0].confirmed)
        assertEquals(added, repo.stored?.draft?.scenarios)
        assertEquals(1, repo.saves)
    }

    @Test
    fun `request should carry narrative and only active operational modules of the tenant draft`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf(active = listOf(F.DAFTAR, F.POLI)))
        val proposer = FakeProposer(Result.success(emptyList()))
        service(repo, proposer, "cerita").propose(F.tenant).getOrThrow()
        val req = proposer.requests.first()
        assertEquals("cerita", req.narrative)
        assertEquals(listOf(F.DAFTAR, F.POLI), req.modules.map { it.moduleId })
        assertEquals(listOf(F.DAFTAR to F.POLI), req.handoffs.map { it.from to it.to })
        assertTrue(req.screens.all { it.moduleId != F.KASIR })
    }

    @Test
    fun `invalid first answer should retry once with path feedback then accept the valid retry`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf())
        val proposer = FakeProposer(Result.success(listOf(bad())), Result.success(listOf(F.validScenario("ok"))))
        val added = service(repo, proposer).propose(F.tenant).getOrThrow()
        assertEquals(2, proposer.requests.size)
        assertEquals(null, proposer.requests[0].feedback)
        assertTrue(proposer.requests[1].feedback.orEmpty().contains("scenarios[0].steps[0].screenId"))
        assertEquals(ScenarioSource.AI, added.single().source)
    }

    @Test
    fun `two invalid answers should fall back to deterministic and drop the invalid ones`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf())
        val proposer = FakeProposer(Result.success(listOf(bad())))
        val added = service(repo, proposer).propose(F.tenant).getOrThrow()
        assertEquals(2, proposer.requests.size)
        assertEquals(ScenarioSource.DETERMINISTIC, added.single().source)
        assertTrue(repo.stored?.draft?.scenarios.orEmpty().none { it.id.value == "bad" })
    }

    @Test
    fun `proposer failure should fall back to deterministic`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf())
        val added = service(repo, FakeProposer(Result.failure(IllegalStateException("model mati")))).propose(F.tenant).getOrThrow()
        assertEquals(ScenarioSource.DETERMINISTIC, added.single().source)
    }

    @Test
    fun `no valid result anywhere should give an honest empty list without saving`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf(handoffs = emptyList()))
        val added = service(repo, FakeProposer(Result.success(listOf(bad())))).propose(F.tenant).getOrThrow()
        assertEquals(emptyList(), added)
        assertEquals(0, repo.saves)
    }

    @Test
    fun `more than the limit from the model should be refused and not truncated`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf(handoffs = emptyList()))
        val four = listOf(F.validScenario("a"), kasirFirst, F.validScenario("c"), F.validScenario("d"))
        val proposer = FakeProposer(Result.success(four))
        assertEquals(emptyList(), service(repo, proposer).propose(F.tenant).getOrThrow())
        assertTrue(proposer.requests[1].feedback.orEmpty().contains("maksimal ${ScenarioLimits.SCENARIOS_MAX}"))
    }

    @Test
    fun `existing confirmed and user scenarios should never be overwritten`() = runTest {
        val confirmed = F.validScenario("keep", ScenarioSource.USER, confirmed = true)
        val repo = FakeDraftRepository.of(F.draftOf(scenarios = listOf(confirmed)))
        val other = F.validScenario("new").copy(steps = listOf(F.step(F.DAFTAR, null), F.step(F.POLI, null)))
        val added = service(repo, FakeProposer(Result.success(listOf(other)))).propose(F.tenant).getOrThrow()
        assertEquals(listOf(confirmed, added.single()), repo.stored?.draft?.scenarios)
    }

    @Test
    fun `duplicate module sequence of an existing scenario should not be added again`() = runTest {
        val existing = F.validScenario("keep", ScenarioSource.USER, confirmed = true)
        val repo = FakeDraftRepository.of(F.draftOf(scenarios = listOf(existing)))
        val added = service(repo, FakeProposer(Result.success(listOf(F.validScenario("sama"))))).propose(F.tenant).getOrThrow()
        assertEquals(emptyList(), added)
        assertEquals(0, repo.saves)
    }

    @Test
    fun `colliding id should be made unique and total should stay within the limit`() = runTest {
        val a = F.validScenario("same", ScenarioSource.USER, true).copy(steps = listOf(F.step(F.DAFTAR, null), F.step(F.POLI, null)))
        val b = F.validScenario("b").copy(steps = listOf(F.step(F.POLI, null), F.step(F.KASIR, null)))
        val repo = FakeDraftRepository.of(F.draftOf(scenarios = listOf(a, b)))
        val newOne = F.validScenario("same")
        val extra = kasirFirst.copy(id = ScenarioId("zzz"))
        val added = service(repo, FakeProposer(Result.success(listOf(newOne, extra)))).propose(F.tenant).getOrThrow()
        assertEquals(listOf("same-2"), added.map { it.id.value })
        assertEquals(ScenarioLimits.SCENARIOS_MAX, repo.stored?.draft?.scenarios?.size)
    }

    @Test
    fun `locked draft should refuse every write`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf(scenarios = listOf(F.validScenario())), DiscoveryDraftStatus.LOCKED)
        val svc = service(repo, FakeProposer(Result.success(listOf(F.validScenario("n")))))
        assertFailsWith<UpdateDiscoveryDraftUseCase.LockedException> { svc.propose(F.tenant).getOrThrow() }
        assertFailsWith<UpdateDiscoveryDraftUseCase.LockedException> { svc.confirm(F.tenant, ScenarioId("scn-1")).getOrThrow() }
        assertFailsWith<UpdateDiscoveryDraftUseCase.LockedException> { svc.edit(F.tenant, F.validScenario()).getOrThrow() }
        assertEquals(0, repo.saves)
    }

    @Test
    fun `missing draft should propose nothing and confirm should fail`() = runTest {
        val repo = FakeDraftRepository()
        val svc = service(repo, FakeProposer(Result.success(emptyList())))
        assertEquals(emptyList(), svc.propose(F.tenant).getOrThrow())
        assertFailsWith<IllegalArgumentException> { svc.confirm(F.tenant, ScenarioId("x")).getOrThrow() }
    }

    @Test
    fun `other tenant should not see the draft`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf())
        val svc = service(repo, FakeProposer(Result.success(listOf(F.validScenario()))))
        assertEquals(emptyList(), svc.propose(TenantId("tenant-lain")).getOrThrow())
        assertEquals(0, repo.saves)
    }

    @Test
    fun `confirm should set confirmed and be idempotent`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf(scenarios = listOf(F.validScenario())))
        val svc = service(repo, FakeProposer(Result.success(emptyList())))
        val first = svc.confirm(F.tenant, ScenarioId("scn-1")).getOrThrow()
        assertTrue(first.confirmed)
        assertEquals(1, repo.saves)
        assertEquals(first, svc.confirm(F.tenant, ScenarioId("scn-1")).getOrThrow())
        assertEquals(1, repo.saves, "konfirmasi ulang tidak menulis")
        assertEquals(ScenarioSource.AI, first.source, "konfirmasi tidak mengubah sumber")
    }

    @Test
    fun `confirm unknown id should fail with illegal argument`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf())
        assertFailsWith<IllegalArgumentException> { service(repo, FakeProposer()).confirm(F.tenant, ScenarioId("hantu")).getOrThrow() }
    }

    @Test
    fun `confirm should revalidate against the current draft and fail with path`() = runTest {
        // Skenario dulu sah, lalu layar s_poli hilang dari draf: tidak boleh terkonfirmasi.
        val drifted = F.draftOf(screens = listOf(F.daftarScreen, F.kasirScreen), scenarios = listOf(F.validScenario()))
        val repo = FakeDraftRepository.of(drifted)
        val e = assertFailsWith<ScenarioInvalidException> { service(repo, FakeProposer()).confirm(F.tenant, ScenarioId("scn-1")).getOrThrow() }
        assertTrue(e.issues.any { it.path == "steps[1].screenId" })
        assertFalse(repo.stored?.draft?.scenarios?.single()?.confirmed ?: true)
    }

    @Test
    fun `edit should mark user source and drop confirmation`() = runTest {
        val repo = FakeDraftRepository.of(F.draftOf(scenarios = listOf(F.validScenario("scn-1", ScenarioSource.AI, confirmed = true))))
        val svc = service(repo, FakeProposer())
        val renamed = F.validScenario("scn-1").copy(title = "Judul baru")
        val out = svc.edit(F.tenant, renamed).getOrThrow()
        assertEquals(ScenarioSource.USER, out.source)
        assertFalse(out.confirmed)
        assertEquals("Judul baru", repo.stored?.draft?.scenarios?.single()?.title)
    }

    @Test
    fun `edit should reject invalid or duplicate content and unknown id`() = runTest {
        val other = F.validScenario("o").copy(steps = listOf(F.step(F.DAFTAR, null), F.step(F.POLI, null)))
        val repo = FakeDraftRepository.of(F.draftOf(scenarios = listOf(F.validScenario("scn-1"), other)))
        val svc = service(repo, FakeProposer())
        assertFailsWith<ScenarioInvalidException> { svc.edit(F.tenant, bad("scn-1")).getOrThrow() }
        val dup = F.validScenario("scn-1").copy(steps = other.steps)
        assertFailsWith<ScenarioInvalidException> { svc.edit(F.tenant, dup).getOrThrow() }
        assertFailsWith<IllegalArgumentException> { svc.edit(F.tenant, F.validScenario("hantu")).getOrThrow() }
        assertEquals(0, repo.saves)
    }
}
