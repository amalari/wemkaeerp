package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DeterministicInterviewGuesser
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleHandoff
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.discovery.interview.acceptAll
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackDecodeException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B6 (PROPOSAL-iv-B6, disetujui opsi (a)): rujukan ke modul bersama platform dengan `portMapping` lengkap. */
class ModuleReferenceTest {

    private val garment = GarmentDomainPack.pack
    private val costing = GarmentModules.COSTING_HPP
    private val slot = garment.slot(GarmentSlots.COSTING_HPP)!!
    private val inP = PortType("Permintaan")
    private val outP = PortType("Catatan")
    private val label = "Perhitungan Biaya"

    private fun ref(vararg map: Pair<PortType, PortType>, id: ModuleId = costing, label: String = this.label) = ModuleReference(id, label, map.toMap())
    private val complete = ref(inP to slot.defaultInput, outP to slot.defaultOutput)
    private val klinik = InterviewFixtures.klinikPack
    private fun withRefs(vararg r: ModuleReference, pack: DomainPack = klinik) = pack.copy(moduleReferences = r.toList())
    private fun paths(pack: DomainPack) = ModuleReferenceRules.validate(pack).map { it.path }

    @AfterTest fun cleanup() = DomainPackRegistry.unregister(DomainPackCode("klinik"))

    @Test
    fun `pack non-garment merujuk modul bersama dengan portMapping lengkap lolos, tanpa mencemari kosakata`() {
        val pack = withRefs(complete)
        assertEquals(emptyList(), ModuleReferenceRules.validate(pack))
        assertEquals(emptyList(), DomainPackRegistry.violations(pack))
        assertEquals(setOf("Permintaan", "Catatan"), pack.portTypes.map { it.value }.toSet())
        assertEquals(inP to outP, pack.slotPorts(costing), "port modul dalam kosakata pack")
        assertEquals(label, pack.moduleLabel(costing))
        assertTrue(pack.isReferenced(costing) && !pack.isReferenced(ModuleId("klinik_poli")))
    }

    @Test
    fun `hanya modul bersama yang ditawarkan platform bisa dirujuk`() {
        assertEquals(listOf("$.pack.moduleReferences[0].platformModuleId"), paths(withRefs(ref(id = ModuleId("modul_hantu")))))
        // crm_sales operasional bawaan, tetapi tidak ditawarkan sebagai modul bersama.
        assertEquals(listOf("$.pack.moduleReferences[0].platformModuleId"), paths(withRefs(ref(id = GarmentModules.CRM_SALES))))
        // tata kelola: jalur salinan identik, bukan rujukan.
        assertEquals(listOf("$.pack.moduleReferences[0].platformModuleId"), paths(withRefs(ref(id = GarmentModules.ORG_CHART))))
    }

    @Test
    fun `portMapping tak lengkap, port asing, dan pemetaan ganda ditolak berpath`() {
        assertTrue("$.pack.moduleReferences[0].portMapping" in paths(withRefs(ref(inP to slot.defaultInput))))
        assertTrue("$.pack.moduleReferences[0].portMapping.Permintaan" in paths(withRefs(ref(inP to PortType("PortKarangan"), outP to slot.defaultOutput))))
        assertTrue("$.pack.moduleReferences[0].portMapping.PortAsing" in paths(withRefs(ref(PortType("PortAsing") to slot.defaultInput, outP to slot.defaultOutput))))
        assertTrue("$.pack.moduleReferences[0].portMapping" in paths(withRefs(ref(inP to slot.defaultInput, outP to slot.defaultInput))))
        assertTrue("$.pack.moduleReferences[0].label" in paths(withRefs(complete.copy(label = " "))))
    }

    @Test
    fun `tidak boleh sekaligus didefinisikan dan dirujuk, tidak boleh dirujuk dua kali`() {
        val own = garment.module(costing)!!.copy(section = klinik.sections.last().code)
        val withOwn = klinik.copy(slots = klinik.slots + slot.copy(phase = klinik.phases.first().code, defaultInput = inP, defaultOutput = outP), modules = klinik.modules + own)
        assertTrue("$.pack.moduleReferences[0].platformModuleId" in paths(withRefs(complete, pack = withOwn)))
        assertTrue("$.pack.moduleReferences[1].platformModuleId" in paths(withRefs(complete, complete)))
    }

    @Test
    fun `pack garment tidak berubah dan pack bawaan tidak boleh merujuk`() {
        assertEquals(emptyList(), ModuleReferenceRules.validate(garment))
        assertTrue(garment.moduleReferences.isEmpty())
        val tampered = garment.copy(moduleReferences = listOf(complete))
        assertTrue("$.pack.moduleReferences" in paths(tampered))
        val draft = com.eventverse.app.domain.discovery.DiscoveryDraft(tampered, GarmentBlueprints.FOB_FULL_PACKAGE)
        assertTrue(DiscoveryDraftValidator.validate(draft).any { it.path == "$.pack" })
    }

    @Test
    fun `registri menolak pack ber-rujukan rusak dan menerima yang sah`() {
        assertFailsWith<IllegalArgumentException> { DomainPackRegistry.register(withRefs(ref(inP to slot.defaultInput))) }
        DomainPackRegistry.register(withRefs(complete))
        assertEquals(listOf(complete), DomainPackRegistry.find(klinik.code)?.moduleReferences)
    }

    @Test
    fun `validator draf melaporkan rujukan rusak dengan path, dan draf sah lolos`() {
        val bad = draftOf(withRefs(ref(inP to slot.defaultInput)), null)
        assertTrue(DiscoveryDraftValidator.validate(bad).any { it.path == "$.pack.moduleReferences[0].portMapping" })
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draftOf(withRefs(complete), null)))
    }

    @Test
    fun `codec round-trip, kunci tak ditulis bila kosong, dan dokumen rusak ditolak berpath`() {
        val pack = withRefs(complete)
        assertEquals(pack, DomainPackCodec.decode(DomainPackCodec.encodeToString(pack)))
        assertEquals(draftOf(pack, null), DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(draftOf(pack, null))))
        assertEquals(false, DomainPackCodec.encodeToString(klinik).contains("moduleReferences"))
        val root = DomainPackCodec.encode(pack)
        fun bad(v: JsonValue) = JsonValue.Obj(root.entries + ("moduleReferences" to v))
        assertEquals("$.moduleReferences", assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(bad(JsonValue.Str("x"))) }.path)
        val noMap = JsonValue.Arr(listOf(JsonValue.Obj(mapOf("platformModuleId" to JsonValue.Str("costing_hpp"), "label" to JsonValue.Str("x")))))
        assertEquals("$.moduleReferences[0].portMapping", assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(bad(noMap)) }.path)
    }

    private fun linkTo(origin: ModuleOrigin, features: List<String> = emptyList()) = InterviewSession(
        step = InterviewStep.G3_MODUL,
        divisions = listOf(DivisionDraft(DivisionCode("biaya"), "Biaya", ItemSource.ANSWER)),
        roles = listOf(RoleDraft(RoleKey("akuntan"), "Akuntan", DivisionCode("biaya"), ItemSource.ANSWER)),
        links = listOf(RoleModuleLink(RoleKey("akuntan"), costing, origin, features, Confirmation.CONFIRMED))
    )

    @Test
    fun `wawancara menerima modul rujukan sebagai REUSE_PLATFORM atau EXTEND, menolak REUSE_PACK dan NEW`() {
        val pack = withRefs(complete)
        fun issues(s: InterviewSession) = InterviewValidator.validate(s, pack).map { it.path }
        assertEquals(emptyList(), issues(linkTo(ModuleOrigin.REUSE_PLATFORM)))
        assertEquals(emptyList(), issues(linkTo(ModuleOrigin.EXTEND, listOf("Laporan harian"))))
        assertEquals(listOf("$.interview.links[0].origin"), issues(linkTo(ModuleOrigin.REUSE_PACK)))
        assertEquals(listOf("$.interview.links[0].origin"), issues(linkTo(ModuleOrigin.NEW)))
        assertEquals(listOf("$.interview.links[0].origin"), issues(linkTo(ModuleOrigin.EXTEND)))
        // Tanpa rujukan, modul itu tetap tak ada di pack (perilaku lama tidak berubah).
        assertTrue("$.interview.links[0].moduleId" in InterviewValidator.validate(linkTo(ModuleOrigin.REUSE_PLATFORM), klinik).map { it.path })
    }

    @Test
    fun `sambungan ke modul rujukan memakai port pack hasil pemetaan`() {
        val pack = withRefs(complete)
        val base = linkTo(ModuleOrigin.REUSE_PLATFORM)
        fun h(port: String) = base.copy(handoffs = listOf(ModuleHandoff(ModuleId("klinik_poli"), costing, PortType(port), Confirmation.CONFIRMED)))
        assertEquals(emptyList(), InterviewValidator.validate(h("Catatan"), pack).filter { it.path.contains("handoffs") })
        // Port platform mentah bukan bagian kosakata pack: ditolak.
        val raw = InterviewValidator.validate(h("TechPackAndYieldData"), pack).map { it.path }
        assertTrue("$.interview.handoffs[0].portType" in raw, raw.toString())
    }

    @Test
    fun `tebakan deterministik memakai label pack, asal REUSE_PLATFORM, dan port hasil pemetaan`() {
        val pack = withRefs(complete).copy(roleHints = listOf(
            RoleHint("perawat", "Perawat", ModuleId("klinik_poli")), RoleHint("akuntan", "Akuntan", costing)
        ))
        val s = DeterministicInterviewGuesser.propose(pack, "Perawat memeriksa pasien lalu akuntan menghitung biaya.")
        assertEquals(listOf("Poli", label), s.divisions.map { it.name }, "nama modul rujukan = label pack, bukan nama platform")
        assertEquals(ModuleOrigin.REUSE_PLATFORM, s.links.single { it.moduleId == costing }.origin)
        assertEquals(listOf(PortType("Catatan")), s.handoffs.map { it.portType })
        assertEquals(emptyList(), InterviewValidator.validate(s, pack))
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draftOf(pack, s.acceptAll())))
        assertNull(DeterministicInterviewGuesser.propose(klinik.copy(roleHints = pack.roleHints.take(1)), "akuntan").links.firstOrNull())
    }
}
