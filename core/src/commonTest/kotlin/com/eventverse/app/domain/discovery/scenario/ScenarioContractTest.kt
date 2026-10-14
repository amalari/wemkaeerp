package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Kontrak TRD-BLDR-002 Track 0: bentuk tipe dan port dibekukan di sini; implementasinya Track A/B. */
class ScenarioContractTest {
    private val tenant = TenantId("bordir-uji")

    @Test
    fun `value objects should reject blank identifiers`() {
        assertFailsWith<IllegalArgumentException> { ScenarioId(" ") }
        assertFailsWith<IllegalArgumentException> { FieldCarry("", "b") }
        assertFailsWith<IllegalArgumentException> { FieldCarry("a", " ") }
        assertFailsWith<IllegalArgumentException> { ScenarioStep(ModuleId("m"), "s", " ") }
        assertFailsWith<IllegalArgumentException> { ScenarioStep(ModuleId("m"), "", "L") }
        assertFailsWith<IllegalArgumentException> { ScenarioStep(ModuleId("m"), null, "L", reworkTo = -1) }
        assertFailsWith<IllegalArgumentException> { ScenarioSketch(ScenarioId("s"), " ", emptyList(), ScenarioSource.AI) }
    }

    @Test
    fun `skeleton step with null screen should be valid and scenario should default to unconfirmed`() {
        val step = ScenarioStep(ModuleId("sampling_order"), null, "Sampel")
        assertEquals(null, step.screenId)
        assertFalse(ScenarioSketch(ScenarioId("s"), "Judul", listOf(step), ScenarioSource.DETERMINISTIC).confirmed)
    }

    @Test
    fun `limits should match TRD-BLDR-002 FR-2`() {
        assertEquals(3, ScenarioLimits.SCENARIOS_MAX)
        assertEquals(7, ScenarioLimits.STEPS_MAX)
        assertEquals(80, ScenarioLimits.TITLE_MAX_CHARS)
        assertEquals(2, ScenarioLimits.QUESTIONS_MAX)
    }

    @Test
    fun `validator port should be satisfiable by a lambda on a non-garment draft`() {
        val draft = draftOf(InterviewFixtures.klinikPack, null)
        val validator = ScenarioValidator { _, sc ->
            if (sc.steps.size > ScenarioLimits.STEPS_MAX) listOf(ScenarioIssue("steps", "terlalu panjang")) else emptyList()
        }
        val ok = ScenarioSketch(ScenarioId("s"), "Pasien", listOf(ScenarioStep(ModuleId("klinik_poli"), null, "Periksa")), ScenarioSource.USER)
        assertTrue(validator.validate(draft, ok).isEmpty())
    }

    @Test
    fun `proposer port should be satisfiable and may return an honest empty list`() = runTest {
        val proposer = ScenarioProposer { Result.success(emptyList()) }
        val req = ScenarioProposalRequest(tenant, "cerita", listOf(ScenarioModuleInput("klinik_poli", "Poli", null, emptyList())))
        assertEquals(emptyList(), proposer.propose(req).getOrThrow())
    }

    @Test
    fun `carry rule port should report rejected pairs instead of dropping them silently`() {
        fun entity(id: String, vararg keys: String) =
            EntitySpec(id, id, keys.map { FieldSpec(it, it, FieldType.TEXT) })
        val rule = ScenarioCarryRule { values, carries, source, target ->
            val ok = carries.filter { c -> source.fields.any { it.key == c.fromField } && target.fields.any { it.key == c.toField } }
            val bad = carries - ok.toSet()
            CarryOutcome(ok.associate { it.toField to values.getValue(it.fromField) }, bad.map { ScenarioIssue(it.toField, "field tak ada") })
        }
        val out = rule.apply(
            mapOf("nama" to "Budi"), listOf(FieldCarry("nama", "pasien"), FieldCarry("nama", "tidak_ada")),
            entity("a", "nama"), entity("b", "pasien")
        )
        assertEquals(mapOf("pasien" to "Budi"), out.values)
        assertEquals(1, out.rejected.size)
    }

    @Test
    fun `service port should be satisfiable by a fake`() = runTest {
        val sc = ScenarioSketch(ScenarioId("s"), "J", listOf(ScenarioStep(ModuleId("m"), null, "L")), ScenarioSource.AI)
        val service = object : ScenarioService {
            override suspend fun propose(tenantId: TenantId) = Result.success(listOf(sc))
            override suspend fun confirm(tenantId: TenantId, id: ScenarioId) = Result.success(sc.copy(confirmed = true))
        }
        assertTrue(service.confirm(tenant, ScenarioId("s")).getOrThrow().confirmed)
        assertEquals(1, service.propose(tenant).getOrThrow().size)
    }
}
