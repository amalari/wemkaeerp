package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.rbac.ModuleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B6 langkah 2 — **usulan bertes** (PROPOSAL-iv-B6). Aturan baru ditulis sebagai fungsi murni di berkas ini
 * ([ProposedSharedModuleRules]) dan dikunci tes; **tidak** ada kode produksi yang berubah, validator belum disentuh.
 * Bila usulan disetujui, model ini dipindah ke `domain/pack` + `DomainPack.moduleReferences` dan tesnya ikut.
 */

/** Pack merujuk modul operasional platform: [portMapping] = port pack → port milik slot modul itu (satu-satu). */
data class ModuleReference(val platformModuleId: ModuleId, val portMapping: Map<PortType, PortType>)

object ProposedSharedModuleRules {

    /** Modul platform yang boleh dirujuk = modul **operasional** (punya slot dan port) dari pack bawaan. */
    private fun platformModule(id: ModuleId): ModuleDefinition? =
        DomainPackRegistry.shipped.firstNotNullOfOrNull { it.module(id) }?.takeIf { it.slot != null }

    fun validate(pack: DomainPack, refs: List<ModuleReference>): List<DiscoveryValidationIssue> {
        val out = mutableListOf<DiscoveryValidationIssue>()
        if (refs.isNotEmpty() && DomainPackRegistry.isShipped(pack.code))
            out += DiscoveryValidationIssue("$.pack.moduleReferences", "Pack bawaan platform tidak boleh merujuk modul lain; dokumennya wajib identik")
        val seen = mutableSetOf<ModuleId>()
        refs.forEachIndexed { i, ref ->
            val at = "$.pack.moduleReferences[$i]"
            val module = platformModule(ref.platformModuleId)
            val slot = module?.slot?.let { s -> DomainPackRegistry.shipped.firstNotNullOfOrNull { it.slot(s) } }
            when {
                module == null || slot == null -> out += DiscoveryValidationIssue("$at.platformModuleId",
                    "Modul '${ref.platformModuleId.value}' bukan modul operasional platform yang terdaftar; hanya modul bersama berslot yang bisa dirujuk")
                pack.module(ref.platformModuleId) != null -> out += DiscoveryValidationIssue("$at.platformModuleId",
                    "Modul '${ref.platformModuleId.value}' sudah didefinisikan di pack; pilih salah satu: definisi sendiri atau rujukan")
                else -> {
                    val needed = setOf(slot.defaultInput, slot.defaultOutput)
                    ref.portMapping.forEach { (own, theirs) ->
                        if (own !in pack.portTypes) out += DiscoveryValidationIssue("$at.portMapping.${own.value}", "Port '${own.value}' tidak ada di kosakata pack ${pack.code.value}")
                        if (theirs !in needed) out += DiscoveryValidationIssue("$at.portMapping.${own.value}", "Port platform '${theirs.value}' bukan port modul ini; port modul: ${needed.joinToString { it.value }}")
                    }
                    (needed - ref.portMapping.values.toSet()).forEach {
                        out += DiscoveryValidationIssue("$at.portMapping", "Port platform '${it.value}' belum dipetakan; petakan semua port modul (${needed.joinToString { n -> n.value }})")
                    }
                    if (ref.portMapping.values.toSet().size != ref.portMapping.size)
                        out += DiscoveryValidationIssue("$at.portMapping", "Dua port pack tidak boleh dipetakan ke port platform yang sama")
                }
            }
            if (!seen.add(ref.platformModuleId)) out += DiscoveryValidationIssue("$at.platformModuleId", "Modul '${ref.platformModuleId.value}' dirujuk dua kali")
        }
        return out
    }

    /** Modul yang berlaku untuk pack: miliknya sendiri + yang dirujuk (definisi dari registri, bukan salinan di pack). */
    fun effectiveModuleIds(pack: DomainPack, refs: List<ModuleReference>): Set<ModuleId> =
        pack.modules.map { it.id }.toSet() + refs.map { it.platformModuleId }

    /** Asal yang sah untuk modul rujukan: modul platform operasional → `REUSE_PLATFORM` (kini hanya untuk non-operasional). */
    fun referencedOriginIsReusePlatform(ref: ModuleReference): Boolean = platformModule(ref.platformModuleId) != null
}

class SharedModuleReferenceProposalTest {

    private val klinik = InterviewFixtures.klinikPack
    private val costing = GarmentModules.COSTING_HPP
    private val costingSlot = GarmentDomainPack.pack.slot(GarmentSlots.COSTING_HPP)!!
    private val inP = PortType("Permintaan")
    private val outP = PortType("Catatan")

    private fun ref(vararg map: Pair<PortType, PortType>, id: ModuleId = costing) = ModuleReference(id, map.toMap())
    private val complete = ref(inP to costingSlot.defaultInput, outP to costingSlot.defaultOutput)
    private fun paths(vararg refs: ModuleReference, pack: DomainPack = klinik) = ProposedSharedModuleRules.validate(pack, refs.toList()).map { it.path }

    @Test
    fun `pack non-garment merujuk modul operasional platform dengan portMapping lengkap lolos`() {
        assertEquals(emptyList(), ProposedSharedModuleRules.validate(klinik, listOf(complete)))
        // Kosakata pack tidak tercemar: klinik tetap hanya punya port-nya sendiri.
        assertEquals(setOf("Permintaan", "Catatan"), klinik.portTypes.map { it.value }.toSet())
    }

    @Test
    fun `rujukan ke id tak terdaftar atau modul tanpa slot ditolak berpath`() {
        assertEquals(listOf("$.pack.moduleReferences[0].platformModuleId"), paths(ref(id = ModuleId("modul_hantu"))))
        assertEquals(listOf("$.pack.moduleReferences[0].platformModuleId"), paths(ref(id = GarmentModules.ORG_CHART)))   // tata kelola: pakai salinan identik
    }

    @Test
    fun `portMapping tak lengkap, port asing, dan pemetaan ganda ditolak`() {
        assertTrue("$.pack.moduleReferences[0].portMapping" in paths(ref(inP to costingSlot.defaultInput)))
        assertTrue("$.pack.moduleReferences[0].portMapping.Permintaan" in paths(ref(inP to PortType("PortKarangan"), outP to costingSlot.defaultOutput)))
        assertTrue("$.pack.moduleReferences[0].portMapping.PortAsing" in paths(ref(PortType("PortAsing") to costingSlot.defaultInput, outP to costingSlot.defaultOutput)))
        assertTrue("$.pack.moduleReferences[0].portMapping" in paths(ref(inP to costingSlot.defaultInput, outP to costingSlot.defaultInput)))
    }

    @Test
    fun `modul tidak boleh sekaligus didefinisikan di pack dan dirujuk, dan rujukan ganda ditolak`() {
        val own = GarmentDomainPack.pack.module(costing)!!
        val withOwn = klinik.copy(
            slots = klinik.slots + costingSlot.copy(phase = klinik.phases.first().code, defaultInput = inP, defaultOutput = outP),
            modules = klinik.modules + own.copy(section = klinik.sections.last().code)
        )
        assertTrue("$.pack.moduleReferences[0].platformModuleId" in paths(complete, pack = withOwn))
        assertTrue("$.pack.moduleReferences[1].platformModuleId" in paths(complete, complete))
    }

    @Test
    fun `pack garment tidak berubah, dan pack bawaan tidak boleh merujuk`() {
        assertEquals(emptyList(), ProposedSharedModuleRules.validate(GarmentDomainPack.pack, emptyList()))
        assertEquals(listOf("$.pack.moduleReferences"), paths(complete, pack = GarmentDomainPack.pack).filter { it == "$.pack.moduleReferences" })
    }

    @Test
    fun `modul rujukan ikut dihitung sebagai modul pack untuk wawancara dan asalnya REUSE_PLATFORM`() {
        val effective = ProposedSharedModuleRules.effectiveModuleIds(klinik, listOf(complete))
        assertTrue(costing in effective && klinik.modules.all { it.id in effective })
        assertTrue(costing !in klinik.modules.map { it.id }, "tanpa rujukan, modul itu tidak ada di pack (fakta yang ditolak validator sekarang)")
        assertTrue(ProposedSharedModuleRules.referencedOriginIsReusePlatform(complete))
        assertEquals(ModuleKind.OPERATIONAL, GarmentDomainPack.pack.module(costing)!!.kind)
    }
}
