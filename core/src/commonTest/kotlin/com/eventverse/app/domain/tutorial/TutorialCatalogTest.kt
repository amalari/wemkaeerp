package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.ElearningPack
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ShippedTutorialSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TRD-HELP-001 FR-1: invarian katalog, termasuk tenant kedua (pack e-learning tanpa tutorial pack). */
class TutorialCatalogTest {

    private val catalog = TutorialCatalog(ShippedTutorialSource)

    @Test
    fun garmentCatalog_hasNoViolations() {
        assertEquals(emptyList(), catalog.violations(GarmentDomainPack.pack))
    }

    @Test
    fun garmentCatalog_containsPlatformAndPackTutorials() {
        val ids = catalog.forPack(GarmentDomainPack.pack).map { it.id.value }
        assertTrue("platform_rbac_role_access" in ids)
        assertTrue("crm_new_lead" in ids)
    }

    @Test
    fun nonGarmentPack_getsOnlyPlatformTutorialsForItsModules() = ElearningPack.registered {
        val tutorials = catalog.forPack(ElearningPack.pack)
        assertEquals(listOf("platform_org_chart_basics"), tutorials.map { it.id.value },
            "e-learning hanya memakai org_chart; tutorial CRM & RBAC tidak boleh bocor")
        assertEquals(emptyList(), catalog.violations(ElearningPack.pack))
    }

    @Test
    fun violations_reportUnknownAnchorAndModule() {
        val broken = object : TutorialSource {
            override fun tutorialsFor(pack: com.eventverse.app.domain.pack.DomainPackCode) = listOf(
                ModuleTutorial(
                    id = TutorialId("broken"),
                    scope = TutorialScope.Module(com.eventverse.app.domain.pack.ModuleId("tidak_ada")),
                    title = "Rusak",
                    summary = "",
                    steps = listOf(TutorialStep("A", "b", anchor = TutorialAnchorId("tidak.terdaftar"), screen = GarmentModules.CRM_SALES)),
                )
            )
            override fun anchorsFor(pack: com.eventverse.app.domain.pack.DomainPackCode) = emptySet<TutorialAnchorId>()
        }
        val problems = TutorialCatalog(broken).violations(GarmentDomainPack.pack)
        assertTrue(problems.any { "modul tidak_ada" in it }, problems.toString())
        assertTrue(problems.any { "anchor tidak.terdaftar" in it }, problems.toString())
    }
}
