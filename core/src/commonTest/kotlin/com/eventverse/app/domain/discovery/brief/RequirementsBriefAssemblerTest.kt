package com.eventverse.app.domain.discovery.brief

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.prototype.SpecOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** C4: perakit brief sisi server. Memakai draf garment (bentuk sama dengan draf kerja tenant). */
class RequirementsBriefAssemblerTest {
    private val pack = GarmentDomainPack.pack
    private val draft = DiscoveryDraft(
        pack = pack,
        blueprint = GarmentBlueprints.DEFAULT,
        screens = pack.screenSuggestions.map { PrototypeScreen("default-" + it.moduleId.value, it.moduleId, it.title, it.widget.code) }
    )
    private val sampling = GarmentModules.SAMPLING_ORDER.value
    private val inventory = GarmentModules.INVENTORY.value
    private val change = CaptureEntry("2026-10-04T10:00:00Z", SpecOp.AddEnumOption("item", "Kolom", "Revisi", "Dikerjakan"), ok = true, message = null)

    private fun coverage(vararg ids: String, covered: Boolean = true) =
        ids.map { id -> BriefCoverage(id, id, covered, if (covered) 300_000L else null, if (covered) null else 100_000L, if (covered) null else 250_000L) }

    @Test
    fun onlyIncludedModules_appear_withScreensAndEntitiesFromTheSamePrototypeSpec() {
        val brief = RequirementsBriefAssembler.assemble(draft, setOf(sampling), emptyList(), coverage(sampling, inventory))
        assertEquals(listOf(sampling), brief.modules.map { it.moduleId })
        val module = brief.modules.single()
        assertTrue(module.screens.any { it.widget == "KANBAN" && it.entityId != null })
        val entity = module.entities.single()
        assertEquals("Kolom", entity.statusField)
        assertTrue("Baru" in entity.transitions && "Dikerjakan" in entity.transitions.getValue("Baru"))
        assertEquals(listOf(sampling), brief.coverage.map { it.moduleId }, "cakupan modul yang dilepas tidak ikut")
    }

    @Test
    fun clientChanges_arePassedThroughInOrder_andShownInTheMarkdown() {
        val brief = RequirementsBriefAssembler.assemble(draft, setOf(sampling), listOf(change), coverage(sampling))
        assertEquals(listOf(change), brief.changes)
        assertTrue("Revisi" in BriefRenderer.markdown(brief), "perubahan klien harus terbaca di brief")
    }

    @Test
    fun uncoveredModule_becomesACustomNeed_coveredOneDoesNot() {
        val brief = RequirementsBriefAssembler.assemble(
            draft, setOf(sampling, inventory), emptyList(),
            coverage(sampling, covered = true) + coverage(inventory, covered = false)
        )
        assertEquals(1, brief.customNeeds.size)
        assertTrue(inventory in brief.customNeeds.single() && "CUSTOM_EXTENSION" in brief.customNeeds.single())
    }

    @Test
    fun isDeterministic_andWidgetsWithoutPlayableSpec_stillListTheirScreen() {
        val all = pack.modules.map { it.id.value }.toSet()
        val a = RequirementsBriefAssembler.assemble(draft, all, emptyList(), emptyList())
        assertEquals(a, RequirementsBriefAssembler.assemble(draft, all, emptyList(), emptyList()))
        // layar cetak (PRINT) tidak punya spec yang bisa dimainkan: tetap tercatat tanpa entitas
        assertTrue(a.modules.flatMap { it.screens }.any { it.widget == "PRINT" && it.entityId == null })
    }
}
