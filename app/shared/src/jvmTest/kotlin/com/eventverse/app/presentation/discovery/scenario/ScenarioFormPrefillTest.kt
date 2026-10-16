package com.eventverse.app.presentation.discovery.scenario

import com.eventverse.app.domain.builder.StageSketch
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
import com.eventverse.app.domain.prototype.SelectOption
import com.eventverse.app.domain.prototype.SelectOptionId
import com.eventverse.app.domain.prototype.StateMachine
import com.eventverse.app.presentation.builder.chat.StageTag
import com.eventverse.app.presentation.builder.chat.stageTags
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Temuan 14 (layar tujuan formulir diisi, bukan dibuatkan baris) dan 10 (nada pesan, tag Tahap). Pack klinik, non-garment. */
class ScenarioFormPrefillTest {
    private val pasien = EntitySpec("pasien", "Pasien", listOf(FieldSpec("nama", "Nama pasien", FieldType.TEXT, required = true)))
    private val status = FieldSpec("status", "Status", FieldType.ENUM, options = listOf("Selesai", "Menunggu").mapIndexed { i, l -> SelectOption(SelectOptionId("o$i"), l) })
    private fun periksa(extra: List<FieldSpec> = emptyList(), machine: StateMachine? = null) = EntitySpec(
        "periksa", "Pemeriksaan",
        listOf(
            FieldSpec("pasien", "Pasien", FieldType.TEXT, required = true),
            FieldSpec("diagnosa", "Diagnosa", FieldType.TEXT, required = true),
            status
        ) + extra,
        machine
    )

    private class FormAwareData(val entities: Map<String, EntitySpec>, val forms: Set<String>, seed: Map<String, List<PrototypeRow>>) : ScenarioScreenData {
        val rows = seed.mapValues { it.value.toMutableList() }.toMutableMap()
        var prefilled: Pair<String, Map<String, String>>? = null
        override fun entityOf(screenId: String) = entities[screenId]
        override fun rowsOf(screenId: String) = rows[screenId].orEmpty()
        override fun isFormScreen(screenId: String) = screenId in forms
        override fun prefillForm(screenId: String, values: Map<String, String>) { prefilled = screenId to values }
        override suspend fun createRow(screenId: String, values: Map<String, String>): Result<PrototypeRow> {
            val missing = entities.getValue(screenId).fields.firstOrNull { it.required && values[it.key].isNullOrBlank() }
            if (missing != null) return Result.failure(IllegalStateException("'${missing.label}' wajib diisi"))
            val row = PrototypeRow("r${rows.values.sumOf { it.size } + 1}", values)
            rows.getOrPut(screenId) { mutableListOf() }.add(row)
            return Result.success(row)
        }
    }

    private val copyRule = ScenarioCarryRule { src, carries, _, _ -> CarryOutcome(carries.associate { it.toField to src[it.fromField].orEmpty() }) }

    private fun scenario(vararg carries: FieldCarry) = ScenarioSketch(
        ScenarioId("scn"), "Pasien periksa",
        listOf(
            ScenarioStep(ModuleId("klinik_pendaftaran"), "s-daftar", "Daftar", carries.toList().ifEmpty { listOf(FieldCarry("nama", "pasien")) }),
            ScenarioStep(ModuleId("klinik_periksa"), "s-periksa", "Dokter periksa pasien", listOf(FieldCarry("diagnosa", "diagnosa"))),
            ScenarioStep(ModuleId("klinik_resep"), "s-resep", "Resep")
        ),
        ScenarioSource.AI, confirmed = true
    )

    private fun data(target: EntitySpec, forms: Set<String>) = FormAwareData(
        mapOf("s-daftar" to pasien, "s-periksa" to target, "s-resep" to pasien), forms,
        mapOf("s-daftar" to listOf(PrototypeRow("p1", mapOf("nama" to "Sari Wulandari"))))
    )

    @Test
    fun `layar tujuan formulir should mengisi isian dan tidak membuat baris`() = runTest {
        val d = data(periksa(), setOf("s-periksa"))
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(scenario()).advance(d, copyRule))
        assertTrue(res.prefilled)
        assertEquals(null, res.created)
        assertTrue(d.rows["s-periksa"].isNullOrEmpty(), "baris lahir saat pengguna menyimpan, bukan di pemutar")
        val (screen, values) = requireNotNull(d.prefilled) { "prefillForm tidak dipanggil" }
        assertEquals("s-periksa", screen)
        assertEquals("Sari Wulandari", values["pasien"], "nilai terbawa ikut mengisi formulir")
        assertEquals("(contoh)", values["diagnosa"], "field wajib tak tercakup diisi contoh")
        assertEquals(listOf("Diagnosa"), res.sampled)
        assertEquals(1, res.run.stepIndex)
        assertTrue(scenarioAdvanceMessage(res)!!.contains("Diagnosa"))
    }

    @Test
    fun `layar tujuan non formulir should tetap membuat baris dengan status awal mesin`() = runTest {
        val machine = StateMachine("status", mapOf("Menunggu" to setOf("Selesai")))
        val d = data(periksa(machine = machine), emptySet())
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(scenario()).advance(d, copyRule))
        assertEquals(false, res.prefilled)
        assertEquals(null, d.prefilled)
        val row = d.rows.getValue("s-periksa").single()
        assertEquals("Menunggu", row.values["status"])
        assertEquals(listOf("Diagnosa"), res.sampled)
    }

    @Test
    fun `nilai terbawa should menimpa contoh pada formulir`() = runTest {
        val s = scenario(FieldCarry("nama", "pasien"), FieldCarry("nama", "diagnosa"))
        val d = data(periksa(), setOf("s-periksa"))
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(s).advance(d, copyRule))
        assertEquals("Sari Wulandari", d.prefilled?.second?.get("diagnosa"))
        assertTrue(res.sampled.isEmpty())
    }

    @Test
    fun `formulir dengan status bermesin should terisi status awal`() = runTest {
        val machine = StateMachine("status", mapOf("Menunggu" to setOf("Selesai")))
        val d = data(periksa(machine = machine), setOf("s-periksa"))
        ScenarioRun(scenario()).advance(d, copyRule)
        assertEquals("Menunggu", d.prefilled?.second?.get("status"))
    }

    @Test
    fun `tipe tanpa nilai contoh pada formulir should dibiarkan kosong tanpa dikarang`() = runTest {
        val withRelation = periksa(listOf(FieldSpec("dokter", "Dokter", FieldType.RELATION, required = true, target = "klinik_dokter:dokter")))
        val d = data(withRelation, setOf("s-periksa"))
        ScenarioRun(scenario()).advance(d, copyRule)
        assertTrue(d.prefilled?.second?.get("dokter").isNullOrBlank(), "id rujukan tak boleh dikarang")
    }

    @Test
    fun `lanjut dari formulir yang belum disimpan should ditolak dengan pesan simpan dulu`() = runTest {
        val d = data(periksa(), setOf("s-periksa"))
        val atForm = assertIs<ScenarioAdvance.Moved>(ScenarioRun(scenario()).advance(d, copyRule)).run
        val res = assertIs<ScenarioAdvance.Blocked>(atForm.advance(d, copyRule))
        assertTrue(res.reason.contains("Simpan dulu"), res.reason)
        d.rows["s-periksa"] = mutableListOf(PrototypeRow("x1", mapOf("pasien" to "Sari", "diagnosa" to "Flu")))
        assertIs<ScenarioAdvance.Moved>(atForm.advance(d, copyRule), "setelah disimpan, lanjut bekerja")
    }

    @Test
    fun `nada pesan should membedakan penolakan peringatan dan info`() {
        val run = ScenarioRun(scenario())
        assertEquals(ScenarioNoticeTone.BLOCKED, scenarioAdvanceNotice(ScenarioAdvance.Blocked("Ditolak."))?.tone)
        assertEquals(ScenarioNoticeTone.WARNING, scenarioAdvanceNotice(ScenarioAdvance.Moved(run, null, listOf(ScenarioIssue("c", "x"))))?.tone)
        assertEquals(ScenarioNoticeTone.WARNING, scenarioAdvanceNotice(ScenarioAdvance.Moved(run, null, prefilled = true))?.tone)
        assertEquals(ScenarioNoticeTone.INFO, scenarioAdvanceNotice(ScenarioAdvance.Moved(run, null, note = "Netral."))?.tone)
        assertEquals(null, scenarioAdvanceNotice(ScenarioAdvance.Moved(run, null)))
    }

    @Test
    fun `tag tahap should dipecah per keterangan`() {
        assertEquals(emptyList(), stageTags(StageSketch("Triase")))
        assertEquals(listOf(StageTag("opsional", false), StageTag("rework ke Triase", true)), stageTags(StageSketch("Periksa", optional = true, reworkTo = "Triase")))
    }
}
