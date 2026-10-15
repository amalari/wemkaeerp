package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.discovery.scenario.ScenarioTestFixtures as F
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeterministicScenarioBuilderTest {

    @Test
    fun `klinik handoff chain should become one deterministic unconfirmed scenario`() {
        val s = DeterministicScenarioBuilder.build(F.draftOf()).single()
        assertEquals(listOf(F.DAFTAR, F.POLI, F.KASIR), s.steps.map { it.moduleId.value })
        assertEquals(listOf("Pendaftaran", "Poli", "Kasir"), s.steps.map { it.label })
        assertEquals(ScenarioSource.DETERMINISTIC, s.source)
        assertTrue(!s.confirmed)
        assertEquals(emptyList(), ScenarioRules.validate(F.draftOf(), s))
    }

    @Test
    fun `carries should pair identical keys and identical normalized labels only`() {
        val s = DeterministicScenarioBuilder.build(F.draftOf()).single()
        // daftar -> poli: nama/umur/jenis (key sama) dan keluhan->catatan (label "Keluhan" sama).
        assertEquals(
            setOf(F.carry("nama", "nama"), F.carry("keluhan", "catatan"), F.carry("umur", "umur"), F.carry("jenis", "jenis")),
            s.steps[0].carries.toSet()
        )
        // poli -> kasir: hanya tindakan; "Nama" vs "Pasien" tidak dikarang.
        assertEquals(listOf(F.carry("tindakan", "tindakan")), s.steps[1].carries)
        assertEquals(emptyList(), s.steps[2].carries)
    }

    @Test
    fun `pair with incompatible types should be skipped`() {
        val kasir = F.screen("s_kasir", F.KASIR, F.field("tindakan", "Tindakan", FieldType.NUMBER))
        val s = DeterministicScenarioBuilder.build(F.draftOf(screens = listOf(F.daftarScreen, F.poliScreen, kasir))).single()
        assertEquals(emptyList(), s.steps[1].carries)
    }

    @Test
    fun `module without proposed screen should be a skeleton step with no carries`() {
        val s = DeterministicScenarioBuilder.build(F.draftOf(screens = listOf(F.daftarScreen, F.kasirScreen))).single()
        assertNull(s.steps[1].screenId)
        assertEquals(emptyList(), s.steps[0].carries)
        assertEquals(emptyList(), s.steps[1].carries)
    }

    @Test
    fun `longest path should win over a shorter branch`() {
        val h = listOf(F.handoff(F.DAFTAR, F.KASIR), F.handoff(F.DAFTAR, F.POLI), F.handoff(F.POLI, F.KASIR))
        val s = DeterministicScenarioBuilder.build(F.draftOf(handoffs = h)).single()
        assertEquals(listOf(F.DAFTAR, F.POLI, F.KASIR), s.steps.map { it.moduleId.value })
    }

    @Test
    fun `cycle should not loop forever and entry less cycle should yield nothing`() {
        val ring = listOf(F.handoff(F.DAFTAR, F.POLI), F.handoff(F.POLI, F.KASIR), F.handoff(F.KASIR, F.DAFTAR))
        assertEquals(emptyList(), DeterministicScenarioBuilder.build(F.draftOf(handoffs = ring)))
        // Satu sambungan dari modul tak dikenal diabaikan: cincin tetap tanpa pintu masuk -> kosong, bukan karangan.
        val tail = ring + F.handoff("klinik_masuk", F.DAFTAR)
        assertEquals(emptyList(), DeterministicScenarioBuilder.build(F.draftOf(handoffs = tail)))
    }

    @Test
    fun `reuse platform and inactive modules should be ignored`() {
        val h = listOf(F.handoff(F.DAFTAR, "org_chart"), F.handoff(F.DAFTAR, F.POLI))
        val s = DeterministicScenarioBuilder.build(F.draftOf(handoffs = h)).single()
        assertEquals(listOf(F.DAFTAR, F.POLI), s.steps.map { it.moduleId.value })
        val onlyInactive = F.draftOf(handoffs = listOf(F.handoff(F.DAFTAR, F.POLI)), active = listOf(F.DAFTAR, F.KASIR))
        assertEquals(emptyList(), DeterministicScenarioBuilder.build(onlyInactive))
    }

    @Test
    fun `no interview or no handoff should give an honest empty list`() {
        assertEquals(emptyList(), DeterministicScenarioBuilder.build(F.draftOf(handoffs = emptyList())))
        assertEquals(emptyList(), DeterministicScenarioBuilder.build(F.draftOf().copy(interview = null)))
    }

    @Test
    fun `bengkel pack should also work`() {
        val bp = InterviewFixtures.bengkelPack
        val a = "bengkel_penerimaan"
        val b = "bengkel_servis"
        val d = F.draftOf(
            pack = bp, screens = listOf(F.screen("s_a", a, F.field("plat", "Plat", FieldType.TEXT)), F.screen("s_b", b, F.field("plat", "Plat", FieldType.TEXT))),
            handoffs = listOf(F.handoff(a, b)), active = listOf(a, b)
        )
        val s = DeterministicScenarioBuilder.build(d).single()
        assertEquals(listOf(F.carry("plat", "plat")), s.steps[0].carries)
    }
}
