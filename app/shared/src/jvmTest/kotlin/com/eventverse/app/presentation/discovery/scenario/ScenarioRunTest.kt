package com.eventverse.app.presentation.discovery.scenario

import com.eventverse.app.domain.discovery.scenario.CarryOutcome
import com.eventverse.app.domain.discovery.scenario.FieldCarry
import com.eventverse.app.domain.discovery.scenario.ScenarioCarryRule
import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioIssue
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.domain.discovery.scenario.ScenarioStep
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.PrototypeRow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pemutar skenario murni (TRD-BLDR-002 SC1): fake data + fake aturan, tanpa UI dan tanpa jaringan. */
class ScenarioRunTest {

    private fun entity(id: String, vararg keys: String) =
        EntitySpec(id, id, keys.map { FieldSpec(it, it, FieldType.TEXT) })

    /** Data fake di memori; `reject` memaksa createRow gagal (aturan layar). Tidak ada dependensi klien HTTP. */
    private class FakeData(val entities: Map<String, EntitySpec>, seed: Map<String, List<PrototypeRow>> = emptyMap()) : ScenarioScreenData {
        val rows = seed.mapValues { it.value.toMutableList() }.toMutableMap()
        var reject: String? = null
        var creates = 0
        override fun entityOf(screenId: String) = entities[screenId]
        override fun rowsOf(screenId: String) = rows[screenId].orEmpty()
        override suspend fun createRow(screenId: String, values: Map<String, String>): Result<PrototypeRow> {
            reject?.let { return Result.failure(IllegalStateException(it)) }
            val row = PrototypeRow("r${++creates}", values)
            rows.getOrPut(screenId) { mutableListOf() }.add(row)
            return Result.success(row)
        }
    }

    /** Aturan fake: menyalin fromField -> toField; pasangan yang toField-nya diawali "x_" ditolak. */
    private val fakeRule = ScenarioCarryRule { src, carries, _, _ ->
        val ok = carries.filter { !it.toField.startsWith("x_") }
        CarryOutcome(
            ok.associate { it.toField to src[it.fromField].orEmpty() },
            carries.filter { it.toField.startsWith("x_") }.map { ScenarioIssue("carries", "ditolak ${it.toField}") }
        )
    }

    private fun scenario(vararg steps: ScenarioStep) =
        ScenarioSketch(ScenarioId("scn"), "Skenario", steps.toList(), ScenarioSource.USER, confirmed = true)

    private fun step(module: String, screen: String?, label: String, vararg carries: Pair<String, String>, reworkTo: Int? = null) =
        ScenarioStep(ModuleId(module), screen, label, carries.map { FieldCarry(it.first, it.second) }, reworkTo)

    // Garment: lead -> invoice -> sampel. Non-garment: klinik pasien -> kunjungan.
    private val garment = scenario(
        step("crm", "s-lead", "Lead", "nama" to "pelanggan", "butuh" to "catatan"),
        step("invoicing", "s-inv", "Invoice", "pelanggan" to "customer", reworkTo = null),
        step("sampling", "s-smp", "Sampel", reworkTo = 0)
    )
    private val garmentData get() = FakeData(
        mapOf("s-lead" to entity("lead", "nama", "butuh"), "s-inv" to entity("inv", "pelanggan", "catatan", "customer"), "s-smp" to entity("smp", "customer")),
        mapOf("s-lead" to listOf(PrototypeRow("l1", mapOf("nama" to "Budi", "butuh" to "Kaos 100")), PrototypeRow("l2", mapOf("nama" to "Sari", "butuh" to "Polo"))))
    )

    @Test
    fun `advance membawa nilai lewat aturan dan mencatat jejak`() = runTest {
        val data = garmentData
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(garment).advance(data, fakeRule))
        assertEquals(1, res.run.stepIndex)
        // baris sumber = baris terakhir di layar bila belum ada jejak
        assertEquals(mapOf("pelanggan" to "Sari", "catatan" to "Polo"), data.rows.getValue("s-inv").single().values)
        assertEquals(RowRef(1, "s-inv", "r1"), res.created)
        assertEquals(listOf(RowRef(1, "s-inv", "r1")), res.run.trail)
        // langkah berikutnya memakai baris yang dibawa (jejak), bukan baris terakhir lain
        val res2 = assertIs<ScenarioAdvance.Moved>(res.run.advance(data, fakeRule))
        assertEquals(mapOf("customer" to "Sari"), data.rows.getValue("s-smp").single().values)
        assertEquals(2, res2.run.stepIndex)
    }

    @Test
    fun `advance memakai baris jejak walau ada baris lain yang lebih baru`() = runTest {
        val data = garmentData
        val run1 = (ScenarioRun(garment).advance(data, fakeRule) as ScenarioAdvance.Moved).run
        data.rows.getValue("s-inv").add(PrototypeRow("zzz", mapOf("pelanggan" to "Lain", "catatan" to "")))
        assertIs<ScenarioAdvance.Moved>(run1.advance(data, fakeRule))
        assertEquals("Sari", data.rows.getValue("s-smp").single()["customer"])
    }

    @Test
    fun `langkah berkerangka pindah tanpa baris dan tidak dipalsukan`() = runTest {
        val sc = scenario(step("crm", "s-lead", "Lead", "nama" to "pelanggan"), step("sampling", null, "Sampel"))
        val data = garmentData
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(sc).advance(data, fakeRule))
        assertNull(res.created)
        assertEquals(1, res.run.stepIndex)
        assertTrue(res.note.orEmpty().contains("belum punya layar"))
        assertEquals(0, data.creates)
    }

    @Test
    fun `rework kembali ke langkah sebelumnya tanpa menghapus baris`() = runTest {
        val data = garmentData
        val atStep2 = ScenarioRun(garment).let { r ->
            val a = (r.advance(data, fakeRule) as ScenarioAdvance.Moved).run
            (a.advance(data, fakeRule) as ScenarioAdvance.Moved).run
        }
        assertEquals("Lead", atStep2.reworkLabel)
        val back = assertNotNull(atStep2.rework())
        assertEquals(0, back.stepIndex)
        assertEquals(2, back.trail.size)
        assertEquals(1, data.rows.getValue("s-inv").size)
        assertNull(ScenarioRun(garment, stepIndex = 1).rework()) // langkah 1 tidak punya reworkTo
        assertNull(ScenarioRun(scenario(step("a", "s", "A", reworkTo = 5), step("b", null, "B")), 0).rework()) // bukan mundur
    }

    @Test
    fun `pasangan sebagian ditolak membuat baris dengan peringatan`() = runTest {
        val sc = scenario(step("crm", "s-lead", "Lead", "nama" to "pelanggan", "butuh" to "x_catatan"), step("invoicing", "s-inv", "Invoice"))
        val data = garmentData
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(sc).advance(data, fakeRule))
        assertEquals(mapOf("pelanggan" to "Sari"), data.rows.getValue("s-inv").single().values)
        assertEquals(1, res.warnings.size)
        assertTrue(scenarioAdvanceMessage(res).orEmpty().startsWith("Sebagian data tidak dibawa"))
    }

    @Test
    fun `gagal total tidak pindah dan tidak membuat baris`() = runTest {
        val sc = scenario(step("crm", "s-lead", "Lead", "butuh" to "x_catatan"), step("invoicing", "s-inv", "Invoice"))
        val data = garmentData
        val res = assertIs<ScenarioAdvance.Blocked>(ScenarioRun(sc).advance(data, fakeRule))
        assertEquals(1, res.issues.size)
        assertEquals(0, data.creates)
        assertTrue(data.rows["s-inv"].isNullOrEmpty())
    }

    @Test
    fun `layar menolak baris maka blocked tanpa jejak`() = runTest {
        val data = garmentData.also { it.reject = "Pelanggan wajib diisi" }
        val res = assertIs<ScenarioAdvance.Blocked>(ScenarioRun(garment).advance(data, fakeRule))
        assertEquals("Pelanggan wajib diisi", res.reason)
        assertTrue(data.rows["s-inv"].isNullOrEmpty())
    }

    @Test
    fun `sumber tanpa baris diblokir dengan pesan jelas`() = runTest {
        val data = FakeData(garmentData.entities)
        val res = assertIs<ScenarioAdvance.Blocked>(ScenarioRun(garment).advance(data, fakeRule))
        assertTrue(res.reason.contains("Belum ada baris di 'Lead'"))
    }

    @Test
    fun `langkah terakhir tidak bisa lanjut`() = runTest {
        assertIs<ScenarioAdvance.Blocked>(ScenarioRun(garment, stepIndex = 2).advance(garmentData, fakeRule))
    }

    @Test
    fun `tenant non garment klinik membawa data pasien ke kunjungan`() = runTest {
        val klinik = scenario(
            step("pasien", "s-pasien", "Pendaftaran", "nama" to "pasien", "keluhan" to "keluhan"),
            step("kunjungan", "s-kunj", "Pemeriksaan", "pasien" to "pasien")
        )
        val data = FakeData(
            mapOf("s-pasien" to entity("pasien", "nama", "keluhan"), "s-kunj" to entity("kunjungan", "pasien", "keluhan")),
            mapOf("s-pasien" to listOf(PrototypeRow("p1", mapOf("nama" to "Ani", "keluhan" to "Demam"))))
        )
        assertIs<ScenarioAdvance.Moved>(ScenarioRun(klinik).advance(data, fakeRule))
        assertEquals(mapOf("pasien" to "Ani", "keluhan" to "Demam"), data.rows.getValue("s-kunj").single().values)
    }

    @Test
    fun `aturan bawaan klien adalah aturan core - jenis sama dibawa, field hilang dan beda jenis ditolak`() {
        val src = EntitySpec("a", "A", listOf(FieldSpec("t", "T", FieldType.TEXT), FieldSpec("n", "N", FieldType.NUMBER)))
        val tgt = EntitySpec("b", "B", listOf(FieldSpec("t2", "T2", FieldType.TEXT), FieldSpec("d", "D", FieldType.DATE)))
        assertEquals(com.eventverse.app.domain.discovery.scenario.DefaultScenarioCarryRule, ScenarioActions().carryRule)
        val out = ScenarioActions().carryRule.apply(
            mapOf("t" to "x", "n" to "5"), listOf(FieldCarry("t", "t2"), FieldCarry("n", "d"), FieldCarry("zz", "t2")), src, tgt
        )
        assertEquals(mapOf("t2" to "x"), out.values)
        assertEquals(2, out.rejected.size)
    }

    @Test
    fun `kelas pemutar tidak punya dependensi klien jaringan`() {
        val types = listOf(ScenarioRun::class, ScenarioScreenData::class, SessionScenarioData::class)
            .flatMap { k -> k.java.declaredFields.map { it.type.name } + k.java.declaredMethods.flatMap { m -> m.parameterTypes.map { it.name } } }
        assertTrue(types.none { it.contains("BuilderApiClient") || it.contains("HttpClient") }, "pemutar tidak boleh memakai klien HTTP: $types")
    }
}
