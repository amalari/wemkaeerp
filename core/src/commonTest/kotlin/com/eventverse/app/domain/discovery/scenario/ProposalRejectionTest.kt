package com.eventverse.app.domain.discovery.scenario

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.eventverse.app.domain.discovery.scenario.ScenarioTestFixtures as F

/**
 * Usulan yang tak dipakai tidak boleh diam (cek visual SC3, 2026-10-18: tiga kali propose kosong tanpa jejak): tiap percobaan
 * yang gagal dilaporkan dengan jenis + alasan, dan laporan tidak memuat isi narasi pengguna.
 */
class ProposalRejectionTest {
    private val seen = mutableListOf<Pair<com.eventverse.app.domain.tenant.TenantId, ProposalRejection>>()
    private fun service(repo: FakeDraftRepository, proposer: ScenarioProposer, narrative: String = "") =
        DiscoveryScenarioService(repo, proposer, narrative = { narrative }, onProposalRejected = { t, r -> seen += t to r })
    private fun bad(id: String = "bad") = F.validScenario(id).copy(steps = listOf(F.step(F.DAFTAR, "s_hantu"), F.step(F.POLI, null)))

    @Test
    fun `proposer error should be reported per attempt with its message`() = runTest {
        service(FakeDraftRepository.of(F.draftOf(handoffs = emptyList())), FakeProposer(Result.failure(IllegalStateException("kunci terlarang: source")))).propose(F.tenant)
        assertEquals(listOf(ProposalRejectionKind.PROPOSER_ERROR, ProposalRejectionKind.PROPOSER_ERROR), seen.map { it.second.kind })
        assertEquals(listOf(1, 2), seen.map { it.second.attempt })
        assertTrue(seen.all { it.first == F.tenant && it.second.detail.contains("kunci terlarang: source") })
    }

    @Test
    fun `empty answer should be reported once and not retried`() = runTest {
        val proposer = FakeProposer(Result.success(emptyList()))
        service(FakeDraftRepository.of(F.draftOf(handoffs = emptyList())), proposer).propose(F.tenant)
        assertEquals(1, proposer.requests.size)
        assertEquals(listOf(ProposalRejectionKind.EMPTY), seen.map { it.second.kind })
    }

    @Test
    fun `invalid answer should be reported with the validator paths`() = runTest {
        service(FakeDraftRepository.of(F.draftOf(handoffs = emptyList())), FakeProposer(Result.success(listOf(bad())))).propose(F.tenant)
        assertEquals(listOf(ProposalRejectionKind.INVALID, ProposalRejectionKind.INVALID), seen.map { it.second.kind })
        assertTrue(seen.first().second.detail.contains("scenarios[0].steps[0].screenId"))
    }

    @Test
    fun `valid proposals dropped at save should be reported as not added`() = runTest {
        val existing = F.validScenario("keep", ScenarioSource.USER, confirmed = true)
        service(FakeDraftRepository.of(F.draftOf(scenarios = listOf(existing))), FakeProposer(Result.success(listOf(F.validScenario("sama"))))).propose(F.tenant)
        val r = seen.single().second
        assertEquals(ProposalRejectionKind.NOT_ADDED, r.kind)
        assertEquals(0, r.attempt)
        assertTrue(r.detail.contains("1 usulan dibuang"), r.detail)
    }

    @Test
    fun `successful proposal and retry that succeeds should report only the failed attempt`() = runTest {
        service(FakeDraftRepository.of(F.draftOf()), FakeProposer(Result.success(listOf(bad())), Result.success(listOf(F.validScenario("ok"))))).propose(F.tenant)
        assertEquals(listOf(ProposalRejectionKind.INVALID), seen.map { it.second.kind })
        seen.clear()
        service(FakeDraftRepository.of(F.draftOf()), FakeProposer(Result.success(listOf(F.validScenario("ok"))))).propose(F.tenant)
        assertTrue(seen.isEmpty(), "tanpa penolakan tidak ada laporan")
    }

    @Test
    fun `report should never carry the user narrative and detail should be capped`() = runTest {
        val secret = "RAHASIA-PELANGGAN-XYZ"
        service(
            FakeDraftRepository.of(F.draftOf(handoffs = emptyList())),
            FakeProposer(Result.success(listOf(bad()))), narrative = secret
        ).propose(F.tenant)
        assertFalse(seen.any { it.second.detail.contains(secret) })
        assertTrue(ProposalRejection.of(ProposalRejectionKind.INVALID, 1, "x".repeat(5000)).detail.length <= ProposalRejection.DETAIL_MAX_CHARS)
    }
}
