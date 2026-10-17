package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.eventverse.app.domain.discovery.scenario.ScenarioTestFixtures as F

/**
 * Keputusan produk temuan 5 (opsi b): modul FOUNDATION berdeskripsi boleh menjadi langkah skenario walau tak ada di kanvas
 * ("invoice DP" -> penagihan), dan ikut ditawarkan ke pengusul. GOVERNANCE tidak pernah; FOUNDATION tanpa deskripsi tidak
 * (model tak punya bahan untuk memilihnya). Perilaku modul operasional tak berubah (paritas).
 */
class ScenarioFoundationModuleTest {
    private val template = InterviewFixtures.klinikPack.modules.first()
    private fun module(id: String, kind: ModuleKind, description: String) =
        template.copy(id = com.eventverse.app.domain.pack.ModuleId(id), kind = kind, description = description, descriptionText = null)

    private val penagihan = module("klinik_penagihan", ModuleKind.FOUNDATION, "Tagihan dan DP pasien")
    private val polos = module("klinik_polos", ModuleKind.FOUNDATION, "")
    private val tata = module("klinik_tata", ModuleKind.GOVERNANCE, "Aturan klinik")

    private fun draftWith(vararg extra: ModuleDefinition) =
        F.draftOf(pack = InterviewFixtures.klinikPack.let { it.copy(modules = it.modules + extra) })

    private fun ids(list: List<ModuleDefinition>) = list.map { it.id.value }

    @Test
    fun `foundation module with description should be eligible while blank foundation and governance should not`() {
        val eligible = ids(ScenarioRules.eligibleModules(draftWith(penagihan, polos, tata)))
        assertEquals(listOf(F.DAFTAR, F.POLI, F.KASIR, "klinik_penagihan"), eligible)
    }

    @Test
    fun `operational module outside the blueprint should stay ineligible`() {
        val eligible = ids(ScenarioRules.eligibleModules(F.draftOf(active = listOf(F.DAFTAR, F.KASIR))))
        assertEquals(listOf(F.DAFTAR, F.KASIR), eligible)
    }

    @Test
    fun `proposer request should offer the eligible foundation module`() {
        val request = ScenarioRequests.of(TenantId("klinik-uji"), draftWith(penagihan, polos, tata), "cerita", null)
        assertTrue("klinik_penagihan" in request.modules.map { it.moduleId })
        assertFalse("klinik_polos" in request.modules.map { it.moduleId })
        assertFalse("klinik_tata" in request.modules.map { it.moduleId })
    }

    @Test
    fun `step on a foundation module should be valid and on a blank or governance module rejected`() {
        val draft = draftWith(penagihan, polos, tata)
        fun scenario(module: String) = F.validScenario().let { s -> s.copy(steps = s.steps.take(1) + s.steps[1].copy(carries = emptyList()) + F.step(module, null)) }
        assertEquals(emptyList(), ScenarioRules.validate(draft, scenario("klinik_penagihan")))
        assertTrue("steps[2].moduleId" in F.pathsOf(ScenarioRules.validate(draft, scenario("klinik_polos"))))
        assertTrue("steps[2].moduleId" in F.pathsOf(ScenarioRules.validate(draft, scenario("klinik_tata"))))
    }

    @Test
    fun `garment pack should offer its described foundation modules and all active operational modules`() {
        val pack = GarmentDomainPack.pack
        val draft = F.draftOf(pack = pack, screens = emptyList(), handoffs = emptyList(), active = pack.modules.filter { it.kind == ModuleKind.OPERATIONAL }.map { it.id.value }.take(3))
        val offered = ids(ScenarioRules.eligibleModules(draft))
        val foundation = pack.modules.filter { it.kind == ModuleKind.FOUNDATION && it.description.isNotBlank() }.map { it.id.value }
        assertTrue(offered.containsAll(foundation))
        assertEquals(3 + foundation.size, offered.size)
    }
}
