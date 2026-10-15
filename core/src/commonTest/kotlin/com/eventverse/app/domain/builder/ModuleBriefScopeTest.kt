package com.eventverse.app.domain.builder

import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Batas lingkup brief (cek visual 2026-10-16): modul tahu tetangga serah-terimanya dan modul lain, di pack non-garment. */
class ModuleBriefScopeTest {
    private val tenant = TenantId("bordir-uji")
    // Fixture blueprint hanya mengaktifkan modul terakhir; di sini ketiga modul klinik aktif.
    private val draft = draftOf(InterviewFixtures.klinikPack, null).let { d ->
        d.copy(
            interview = InterviewFixtures.klinikSession,
            blueprint = d.blueprint.copy(modules = d.pack.modules.map { BlueprintModule(it.id.value, true) })
        )
    }

    private fun request(moduleId: String) = ModuleBriefInputs.request(
        tenant, draft, draft.pack.modules.first { it.id == ModuleId(moduleId) }, "cerita", emptyList(), null
    )

    private fun name(id: String) = draft.pack.modules.first { it.id == ModuleId(id) }.displayName

    @Test
    fun `modul penerima should menyebut pengirim sebagai upstream dan tanpa downstream`() {
        val r = request("klinik_poli")
        assertEquals(listOf(name("klinik_pendaftaran")), r.upstreamModules)
        assertTrue(r.downstreamModules.isEmpty())
    }

    @Test
    fun `modul pengirim should menyebut penerima sebagai downstream`() {
        val r = request("klinik_pendaftaran")
        assertEquals(listOf(name("klinik_poli")), r.downstreamModules)
        assertTrue(r.upstreamModules.isEmpty())
    }

    @Test
    fun `otherModules should memuat modul operasional aktif lain dan bukan dirinya sendiri`() {
        val r = request("klinik_poli")
        assertTrue(name("klinik_poli") !in r.otherModules, "bukan dirinya sendiri")
        assertTrue(name("klinik_pendaftaran") in r.otherModules)
        assertTrue(name("klinik_kasir") in r.otherModules)
    }

    @Test
    fun `tanpa wawancara should tidak ada tetangga tetapi modul lain tetap diketahui`() {
        val noInterview = draft.copy(interview = null)
        val r = ModuleBriefInputs.request(tenant, noInterview, noInterview.pack.modules.first { it.id == ModuleId("klinik_poli") }, "c", emptyList(), null)
        assertTrue(r.upstreamModules.isEmpty() && r.downstreamModules.isEmpty())
        assertTrue(r.otherModules.isNotEmpty())
    }
}
