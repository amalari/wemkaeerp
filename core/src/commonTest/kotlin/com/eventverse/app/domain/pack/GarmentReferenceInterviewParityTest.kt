package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Paritas B1 (Strangler Fig): wawancara acuan harus mengikuti pack garment — modul baru tanpa padanan → gagal. */
class GarmentReferenceInterviewParityTest {

    private val pack = GarmentDomainPack.pack
    private val session = GarmentReferenceInterview.session

    @Test
    fun `wawancara acuan lolos validator tanpa temuan`() {
        assertEquals(emptyList(), InterviewValidator.validate(session, pack).map { "${it.path}: ${it.message}" })
    }

    @Test
    fun `setiap modul pack garment punya tautan di wawancara acuan`() {
        val linked = session.links.map { it.moduleId }.toSet()
        val missing = pack.modules.map { it.id }.filter { it !in linked }
        assertTrue(missing.isEmpty(), "Modul garment tanpa padanan di GarmentReferenceInterview: ${missing.map { it.value }}")
    }

    @Test
    fun `tidak ada tautan ke modul di luar pack dan asalnya mengikuti jenis modul`() {
        val byId = pack.modules.associateBy { it.id }
        session.links.forEach { l ->
            val def = requireNotNull(byId[l.moduleId]) { "${l.moduleId.value} bukan modul garment" }
            val expected = if (def.kind == ModuleKind.OPERATIONAL) ModuleOrigin.REUSE_PACK else ModuleOrigin.REUSE_PLATFORM
            assertEquals(expected, l.origin, "Asal ${l.moduleId.value}")
        }
    }

    @Test
    fun `setiap peran punya tautan dan setiap divisi punya peran`() {
        val roleKeys = session.links.map { it.roleKey }.toSet()
        assertTrue(session.roles.all { it.roleKey in roleKeys }, "Ada peran tanpa modul")
        val divisions = session.roles.map { it.divisionCode }.toSet()
        assertTrue(session.divisions.all { it.code in divisions }, "Ada divisi tanpa peran")
    }

    @Test
    fun `draf garment berwawancara sah di validator draf dan round-trip codec`() {
        val draft = DiscoveryDraft(pack, GarmentBlueprints.FOB_FULL_PACKAGE, interview = session)
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draft))
        assertEquals(draft, DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(draft)))
    }
}
