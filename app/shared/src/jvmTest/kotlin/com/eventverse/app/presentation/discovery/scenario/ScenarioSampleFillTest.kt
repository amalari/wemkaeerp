package com.eventverse.app.presentation.discovery.scenario

import com.eventverse.app.domain.discovery.scenario.CarryOutcome
import com.eventverse.app.domain.discovery.scenario.FieldCarry
import com.eventverse.app.domain.discovery.scenario.ScenarioCarryRule
import com.eventverse.app.domain.discovery.scenario.ScenarioId
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Temuan 9 (cek visual klinik, model sungguhan 2026-10-16): "Lanjut Alur" dari Pendaftaran ke Antrean berhenti dengan
 * `'Nomor antrean' wajib diisi`, karena `nomor` dan `status` adalah data baru yang lahir di Antrean dan tak mungkin dibawa.
 * Di sini layar menegakkan field wajib seperti reducer sungguhan; klinik = pack non-garment.
 */
class ScenarioSampleFillTest {
    private val pasien = EntitySpec("pasien", "Pasien", listOf(FieldSpec("nama", "Nama pasien", FieldType.TEXT, required = true)))

    private fun antrean(extra: List<FieldSpec> = emptyList()) = EntitySpec(
        "antrean", "Antrean",
        listOf(
            FieldSpec("nomor", "Nomor antrean", FieldType.TEXT, required = true),
            FieldSpec("pasien", "Pasien", FieldType.TEXT, required = true),
            FieldSpec("status", "Status", FieldType.ENUM, options = listOf(SelectOption(SelectOptionId("m"), "Menunggu"), SelectOption(SelectOptionId("d"), "Dipanggil")), required = true)
        ) + extra
    )

    /** Data yang MENEGAKKAN field wajib seperti reducer: baris dengan field wajib kosong ditolak. */
    private class StrictData(val entities: Map<String, EntitySpec>, seed: Map<String, List<PrototypeRow>>) : ScenarioScreenData {
        val rows = seed.mapValues { it.value.toMutableList() }.toMutableMap()
        override fun entityOf(screenId: String) = entities[screenId]
        override fun rowsOf(screenId: String) = rows[screenId].orEmpty()
        override suspend fun createRow(screenId: String, values: Map<String, String>): Result<PrototypeRow> {
            val missing = entities.getValue(screenId).fields.firstOrNull { it.required && values[it.key].isNullOrBlank() }
            if (missing != null) return Result.failure(IllegalStateException("'${missing.label}' wajib diisi"))
            val row = PrototypeRow("r${rows.values.sumOf { it.size } + 1}", values)
            rows.getOrPut(screenId) { mutableListOf() }.add(row)
            return Result.success(row)
        }
    }

    private val copyRule = ScenarioCarryRule { src, carries, _, _ -> CarryOutcome(carries.associate { it.toField to src[it.fromField].orEmpty() }) }

    private val scenario = ScenarioSketch(
        ScenarioId("scn"), "Pasien baru",
        listOf(
            ScenarioStep(ModuleId("klinik_pendaftaran"), "s-daftar", "Daftar", listOf(FieldCarry("nama", "pasien"))),
            ScenarioStep(ModuleId("klinik_antrean"), "s-antre", "Antrean")
        ),
        ScenarioSource.AI, confirmed = true
    )

    private fun data(target: EntitySpec) =
        StrictData(mapOf("s-daftar" to pasien, "s-antre" to target), mapOf("s-daftar" to listOf(PrototypeRow("p1", mapOf("nama" to "Sari Wulandari")))))

    @Test
    fun `field wajib yang tak tercakup pembawa should diisi contoh dan alur berpindah`() = runTest {
        val d = data(antrean())
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(scenario).advance(d, copyRule))
        val row = d.rows.getValue("s-antre").single()
        assertEquals("Sari Wulandari", row.values["pasien"], "nilai yang dibawa tetap data nyata")
        assertEquals("(contoh)", row.values["nomor"])
        assertEquals("Menunggu", row.values["status"], "enum memakai opsi pertama")
        assertEquals(listOf("Nomor antrean", "Status"), res.sampled)
        assertEquals(1, res.run.stepIndex)
        assertTrue(scenarioAdvanceMessage(res)!!.contains("Nomor antrean, Status"), "pengguna diberi tahu mana yang contoh")
    }

    @Test
    fun `nilai yang dibawa should tidak ditimpa nilai contoh`() = runTest {
        val s = scenario.copy(steps = scenario.steps.mapIndexed { i, st -> if (i == 0) st.copy(carries = listOf(FieldCarry("nama", "pasien"), FieldCarry("nama", "nomor"))) else st })
        val d = data(antrean())
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(s).advance(d, copyRule))
        assertEquals("Sari Wulandari", d.rows.getValue("s-antre").single().values["nomor"])
        assertEquals(listOf("Status"), res.sampled)
    }

    @Test
    fun `tanpa field wajib tak tercakup should tidak ada nilai contoh`() = runTest {
        val onlyPasien = EntitySpec("antrean", "Antrean", listOf(FieldSpec("pasien", "Pasien", FieldType.TEXT, required = true)))
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(scenario).advance(data(onlyPasien), copyRule))
        assertTrue(res.sampled.isEmpty())
        assertEquals(null, scenarioAdvanceMessage(res))
    }

    @Test
    fun `field wajib bertipe tanpa nilai contoh should tetap memblokir dengan pesan layar`() = runTest {
        val withRelation = antrean(listOf(FieldSpec("dokter", "Dokter", FieldType.RELATION, required = true, target = "klinik_dokter:dokter")))
        val res = assertIs<ScenarioAdvance.Blocked>(ScenarioRun(scenario).advance(data(withRelation), copyRule))
        assertEquals("'Dokter' wajib diisi", res.reason, "id rujukan tak boleh dikarang")
    }

    /**
     * Temuan baru dari UI nyata: antrean klinik bermesin-status dengan `status` TIDAK wajib. Baris hasil "Lanjut Alur" ada tetapi
     * tanpa status tidak masuk kolom papan mana pun. Baris harus memulai di status awal.
     */
    @Test
    fun `entitas bermesin status should memulai baris di status awal agar tampil di papan`() = runTest {
        val papan = EntitySpec(
            "antrean", "Antrean",
            listOf(
                FieldSpec("nomor", "Nomor antrean", FieldType.TEXT, required = true),
                FieldSpec("pasien", "Pasien", FieldType.TEXT, required = true),
                FieldSpec("status", "Status antrean", FieldType.ENUM, options = listOf("Selesai", "Menunggu", "Dipanggil").mapIndexed { i, l -> SelectOption(SelectOptionId("o$i"), l) })
            ),
            com.eventverse.app.domain.prototype.StateMachine("status", mapOf("Menunggu" to setOf("Dipanggil"), "Dipanggil" to setOf("Selesai")))
        )
        val d = data(papan)
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(scenario).advance(d, copyRule))
        assertEquals("Menunggu", d.rows.getValue("s-antre").single().values["status"], "status awal mesin, bukan opsi pertama (Selesai)")
        assertEquals(listOf("Nomor antrean"), res.sampled, "status awal adalah aturan mesin, bukan nilai contoh")
    }

    @Test
    fun `status wajib bermesin should memakai status awal bukan opsi pertama`() = runTest {
        val papan = EntitySpec(
            "antrean", "Antrean",
            listOf(
                FieldSpec("pasien", "Pasien", FieldType.TEXT, required = true),
                FieldSpec("status", "Status", FieldType.ENUM, options = listOf("Selesai", "Menunggu").mapIndexed { i, l -> SelectOption(SelectOptionId("o$i"), l) }, required = true)
            ),
            com.eventverse.app.domain.prototype.StateMachine("status", mapOf("Menunggu" to setOf("Selesai")))
        )
        val d = data(papan)
        val res = assertIs<ScenarioAdvance.Moved>(ScenarioRun(scenario).advance(d, copyRule))
        assertEquals("Menunggu", d.rows.getValue("s-antre").single().values["status"])
        assertTrue(res.sampled.isEmpty(), "status sudah terisi status awal, tidak perlu contoh")
    }
}
