package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.discovery.scenario.ScenarioTestFixtures as F
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioRulesTest {
    private val draft = F.draftOf()
    private fun issues(s: ScenarioSketch) = ScenarioRules.validate(draft, s)
    private fun withStep(i: Int, step: ScenarioStep): ScenarioSketch {
        val base = F.validScenario()
        return base.copy(steps = base.steps.mapIndexed { n, s -> if (n == i) step else s })
    }

    @Test
    fun `valid klinik scenario should have no issues`() {
        assertEquals(emptyList(), issues(F.validScenario()))
    }

    @Test
    fun `steps over the limit should be rejected`() {
        val many = (0..ScenarioLimits.STEPS_MAX).map { F.step(F.POLI, null) }
        assertTrue("steps" in F.pathsOf(issues(F.validScenario().copy(steps = many))))
    }

    @Test
    fun `scenario without steps should be rejected`() {
        assertEquals(listOf("steps"), F.pathsOf(issues(F.validScenario().copy(steps = emptyList()))))
    }

    @Test
    fun `title and label over the limit should be rejected with path`() {
        val long = F.validScenario().copy(title = "x".repeat(ScenarioLimits.TITLE_MAX_CHARS + 1))
        assertEquals(listOf("title"), F.pathsOf(issues(long)))
        val label = withStep(1, F.step(F.POLI, "s_poli", "y".repeat(ScenarioLimits.LABEL_MAX_CHARS + 1), listOf(F.carry("tindakan", "tindakan"))))
        assertEquals(listOf("steps[1].label"), F.pathsOf(issues(label)))
    }

    @Test
    fun `module that is not active should be rejected`() {
        val inactive = F.draftOf(active = listOf(F.DAFTAR, F.KASIR))
        val paths = F.pathsOf(ScenarioRules.validate(inactive, F.validScenario()))
        assertTrue("steps[1].moduleId" in paths, paths.toString())
    }

    @Test
    fun `module outside the pack should be rejected`() {
        val s = withStep(2, F.step("bukan_modul", null))
        assertTrue("steps[2].moduleId" in F.pathsOf(issues(s)))
    }

    @Test
    fun `screen of another module should be rejected`() {
        val s = withStep(2, F.step(F.KASIR, "s_poli"))
        // Layar salah juga membuat carries langkah 1 tak terselesaikan: dilaporkan di jalurnya sendiri.
        assertEquals(listOf("steps[1].carries", "steps[2].screenId"), F.pathsOf(issues(s)))
    }

    @Test
    fun `unknown screen should be rejected`() {
        assertTrue("steps[2].screenId" in F.pathsOf(issues(withStep(2, F.step(F.KASIR, "s_hantu")))))
    }

    @Test
    fun `carries over the per step limit should be rejected`() {
        val many = (0..ScenarioLimits.CARRIES_MAX_PER_STEP).map { F.carry("nama", "nama") }
        assertTrue("steps[0].carries" in F.pathsOf(issues(withStep(0, F.step(F.DAFTAR, "s_daftar", carries = many)))))
    }

    @Test
    fun `last step with carries should be rejected`() {
        val s = withStep(2, F.step(F.KASIR, "s_kasir", carries = listOf(F.carry("pasien", "pasien"))))
        assertEquals(listOf("steps[2].carries"), F.pathsOf(issues(s)))
    }

    @Test
    fun `missing source field should be rejected with path`() {
        val s = withStep(0, F.step(F.DAFTAR, "s_daftar", carries = listOf(F.carry("hantu", "nama"))))
        assertEquals(listOf("steps[0].carries[0].fromField"), F.pathsOf(issues(s)))
    }

    @Test
    fun `missing target field should be rejected with path`() {
        val s = withStep(0, F.step(F.DAFTAR, "s_daftar", carries = listOf(F.carry("nama", "hantu"))))
        assertEquals(listOf("steps[0].carries[0].toField"), F.pathsOf(issues(s)))
    }

    @Test
    fun `incompatible types should be rejected`() {
        // NUMBER (umur) tidak dapat dibawa ke ENUM (jenis); TEXT (nama) tidak ke NUMBER (umur).
        val a = withStep(0, F.step(F.DAFTAR, "s_daftar", carries = listOf(F.carry("umur", "jenis"))))
        assertEquals(listOf("steps[0].carries[0].toField"), F.pathsOf(issues(a)))
        val b = withStep(0, F.step(F.DAFTAR, "s_daftar", carries = listOf(F.carry("nama", "umur"))))
        assertEquals(listOf("steps[0].carries[0].toField"), F.pathsOf(issues(b)))
    }

    @Test
    fun `compatible coercion number to text should be accepted`() {
        val s = withStep(0, F.step(F.DAFTAR, "s_daftar", carries = listOf(F.carry("umur", "nama"))))
        assertEquals(emptyList(), issues(s))
    }

    @Test
    fun `carry onto a skeleton step should be rejected`() {
        val s = withStep(1, F.step(F.POLI, "s_poli", carries = listOf(F.carry("tindakan", "tindakan")))).let {
            it.copy(steps = it.steps.mapIndexed { n, st -> if (n == 2) F.step(F.KASIR, null) else st })
        }
        assertEquals(listOf("steps[1].carries"), F.pathsOf(issues(s)))
    }

    @Test
    fun `carry from a skeleton step should be rejected`() {
        val s = withStep(0, F.step(F.DAFTAR, null, carries = listOf(F.carry("nama", "nama"))))
        assertEquals(listOf("steps[0].carries"), F.pathsOf(issues(s)))
    }

    @Test
    fun `two carries into the same target should be rejected`() {
        val s = withStep(0, F.step(F.DAFTAR, "s_daftar", carries = listOf(F.carry("nama", "nama"), F.carry("keluhan", "nama"))))
        assertEquals(listOf("steps[0].carries[1].toField"), F.pathsOf(issues(s)))
    }

    @Test
    fun `rework to a previous step should be accepted`() {
        assertEquals(emptyList(), issues(withStep(2, F.step(F.KASIR, "s_kasir", reworkTo = 1))))
    }

    @Test
    fun `rework to the same or a later step should be rejected`() {
        assertEquals(listOf("steps[1].reworkTo"), F.pathsOf(issues(withStep(1, F.step(F.POLI, "s_poli", carries = listOf(F.carry("tindakan", "tindakan")), reworkTo = 1)))))
        assertEquals(listOf("steps[0].reworkTo"), F.pathsOf(issues(withStep(0, F.step(F.DAFTAR, "s_daftar", reworkTo = 2)))))
    }

    @Test
    fun `more than one rework step should be rejected`() {
        val s = F.validScenario().let {
            it.copy(steps = listOf(it.steps[0], it.steps[1].copy(reworkTo = 0), it.steps[2].copy(reworkTo = 1)))
        }
        assertEquals(listOf("steps[2].reworkTo"), F.pathsOf(issues(s)))
    }

    @Test
    fun `more scenarios than the limit should be rejected`() {
        val list = listOf(
            F.validScenario("a"),
            F.validScenario("b").copy(steps = listOf(F.step(F.DAFTAR, null), F.step(F.POLI, null))),
            F.validScenario("c").copy(steps = listOf(F.step(F.POLI, null), F.step(F.KASIR, null))),
            F.validScenario("d").copy(steps = listOf(F.step(F.DAFTAR, null), F.step(F.KASIR, null)))
        )
        assertTrue("scenarios" in F.pathsOf(ScenarioRules.validateAll(draft, list)))
    }

    @Test
    fun `duplicate module sequence should be rejected in the list`() {
        val twin = F.validScenario("lain").copy(title = "Judul lain")
        val issues = ScenarioRules.validateAll(draft, listOf(F.validScenario("a"), twin))
        assertEquals(listOf("scenarios[1].steps"), F.pathsOf(issues))
    }

    @Test
    fun `duplicate id should be rejected in the list`() {
        val other = F.validScenario("a").copy(steps = listOf(F.step(F.DAFTAR, null), F.step(F.POLI, null)))
        assertEquals(listOf("scenarios[1].id"), F.pathsOf(ScenarioRules.validateAll(draft, listOf(F.validScenario("a"), other))))
    }

    @Test
    fun `list issues should carry the scenario index in the path`() {
        val bad = F.validScenario("b").copy(steps = listOf(F.step(F.DAFTAR, "s_hantu"), F.step(F.POLI, null)))
        assertEquals(listOf("scenarios[1].steps[0].screenId"), F.pathsOf(ScenarioRules.validateAll(draft, listOf(F.validScenario("a"), bad))))
    }

    @Test
    fun `garment pack scenario should be validated by the same rules`() {
        val ops = GarmentDomainPack.pack.modules.filter { it.kind == ModuleKind.OPERATIONAL }.take(2).map { it.id.value }
        val g = F.draftOf(
            pack = GarmentDomainPack.pack,
            screens = listOf(F.screen("g1", ops[0], F.field("nama", "Nama", com.eventverse.app.domain.prototype.FieldType.TEXT)),
                F.screen("g2", ops[1], F.field("pelanggan", "Pelanggan", com.eventverse.app.domain.prototype.FieldType.TEXT))),
            handoffs = emptyList(), active = ops
        )
        val ok = ScenarioSketch(ScenarioId("g"), "Garmen", listOf(F.step(ops[0], "g1", carries = listOf(F.carry("nama", "pelanggan"))), F.step(ops[1], "g2")), ScenarioSource.USER)
        assertEquals(emptyList(), ScenarioRules.validate(g, ok))
        val bad = ok.copy(steps = listOf(F.step(ops[0], "g1", carries = listOf(F.carry("nama", "hantu"))), F.step(ops[1], "g2")))
        assertEquals(listOf("steps[0].carries[0].toField"), F.pathsOf(ScenarioRules.validate(g, bad)))
    }

    @Test
    fun `module of garment id should not be active in klinik draft`() {
        val s = ScenarioSketch(ScenarioId("x"), "t", listOf(F.step(GarmentModules.CRM_SALES.value, null)), ScenarioSource.USER)
        assertEquals(listOf("steps[0].moduleId"), F.pathsOf(issues(s)))
        assertTrue(InterviewFixtures.klinikPack.module(GarmentModules.CRM_SALES) == null)
    }
}
