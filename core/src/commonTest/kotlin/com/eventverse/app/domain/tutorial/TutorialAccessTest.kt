package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ShippedTutorialSource
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TRD-HELP-001 FR-2: tutorial difilter fail-closed per keputusan RBAC dan izin layar. */
class TutorialAccessTest {

    private val all = TutorialCatalog(ShippedTutorialSource).forPack(GarmentDomainPack.pack)

    private fun decision(level: AccessLevel) = ModuleAccessConfig(level = level).let {
        AccessDecision(config = it, source = AccessSource.ROLE, fromRole = it, fromDepartment = ModuleAccessConfig())
    }

    private fun ids(decisions: Map<ModuleId, AccessDecision>, surfaces: Set<SurfaceCode> = emptySet()) =
        TutorialAccess.accessible(all, decisions, surfaces).map { it.id.value }.toSet()

    @Test
    fun noDecisions_hidesEverything() {
        assertEquals(emptySet(), ids(emptyMap()))
    }

    @Test
    fun viewOnCrm_showsOnlyViewTutorials() {
        val visible = ids(mapOf(GarmentModules.CRM_SALES to decision(AccessLevel.VIEW)))
        assertEquals(setOf("crm_find_lead"), visible, "tutorial OPERATE (tambah/pindah lead) tersembunyi bagi VIEW")
    }

    @Test
    fun operateOnCrm_showsWriteTutorials_butNotOtherModules() {
        val visible = ids(mapOf(GarmentModules.CRM_SALES to decision(AccessLevel.OPERATE)))
        assertEquals(setOf("crm_find_lead", "crm_new_lead", "crm_move_stage"), visible)
    }

    @Test
    fun noneLevel_isHidden() {
        assertEquals(emptySet(), ids(mapOf(GarmentModules.CRM_SALES to decision(AccessLevel.NONE))))
    }

    @Test
    fun surfaceTutorial_requiresAllowedSurface() {
        val builder = SurfaceCode("builder")
        val tutorial = ModuleTutorial(TutorialId("builder_intro"), TutorialScope.Surface(builder), "Builder", "",
            listOf(TutorialStep("A", "b")))
        assertTrue(TutorialAccess.accessible(listOf(tutorial), emptyMap()).isEmpty())
        assertEquals(1, TutorialAccess.accessible(listOf(tutorial), emptyMap(), setOf(builder)).size)
    }
}
