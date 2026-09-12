package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.ModuleCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavMenuTest {

    private fun grant(vararg pairs: Pair<BusinessModule, AccessLevel>) =
        pairs.associate { (module, level) -> module to ModuleAccessConfig(level = level) }

    @Test
    fun build_menu_when_owner_with_no_permissions_should_show_only_system_section() {
        val sections = buildNavMenu(
            permissions = emptyMap(),
            auditView = false,
            isImpersonating = false
        )

        assertEquals(1, sections.size)
        assertEquals(SYSTEM_SECTION_TITLE, sections.single().title)
        assertEquals(
            listOf(AppNavScreen.ORG_CHART, AppNavScreen.DYNAMIC_RBAC, AppNavScreen.FACTORY_FLOW),
            sections.single().entries.map { it.screen }
        )
    }

    @Test
    fun build_menu_when_module_granted_should_group_under_its_category_header() {
        val sections = buildNavMenu(
            permissions = grant(BusinessModule.INVENTORY to AccessLevel.OPERATE),
            auditView = false,
            isImpersonating = false
        )

        val logistics = sections.single { it.title == ModuleCategory.LOGISTICS.displayName }
        assertEquals(listOf(AppNavScreen.INVENTORY), logistics.entries.map { it.screen })
        assertEquals("Input", logistics.entries.single().badge)
        assertFalse(logistics.entries.single().locked)
    }

    @Test
    fun build_menu_when_category_has_no_accessible_module_should_hide_the_section() {
        val sections = buildNavMenu(
            permissions = grant(BusinessModule.INVENTORY to AccessLevel.VIEW),
            auditView = false,
            isImpersonating = false
        )

        val titles = sections.map { it.title }
        assertTrue(ModuleCategory.LOGISTICS.displayName in titles)
        assertFalse(ModuleCategory.SALES.displayName in titles)
        assertFalse(ModuleCategory.TECHNICAL.displayName in titles)
        assertFalse(ModuleCategory.QUALITY.displayName in titles)
    }

    @Test
    fun build_menu_when_audit_view_enabled_should_reveal_every_category_as_locked() {
        val sections = buildNavMenu(
            permissions = grant(BusinessModule.INVENTORY to AccessLevel.MANAGE),
            auditView = true,
            isImpersonating = false
        )

        val titles = sections.map { it.title }
        ModuleCategory.entries.forEach { assertTrue(it.displayName in titles, "hilang: $it") }

        val sales = sections.single { it.title == ModuleCategory.SALES.displayName }
        assertTrue(sales.entries.all { it.locked })
        assertTrue(sales.entries.all { it.badge == "Terkunci" })

        val inventory = sections
            .single { it.title == ModuleCategory.LOGISTICS.displayName }
            .entries.single { it.screen == AppNavScreen.INVENTORY }
        assertFalse(inventory.locked)
        assertEquals("Penuh", inventory.badge)
    }

    @Test
    fun build_menu_when_impersonating_without_audit_should_hide_system_section() {
        val sections = buildNavMenu(
            permissions = grant(BusinessModule.OPERATOR_EXEC to AccessLevel.OPERATE),
            auditView = false,
            isImpersonating = true
        )

        assertFalse(sections.any { it.title == SYSTEM_SECTION_TITLE })
        assertEquals(listOf(ModuleCategory.PRODUCTION.displayName), sections.map { it.title })
    }

    @Test
    fun build_menu_when_impersonating_with_audit_should_show_system_section_locked() {
        val sections = buildNavMenu(
            permissions = emptyMap(),
            auditView = true,
            isImpersonating = true
        )

        val system = sections.first()
        assertEquals(SYSTEM_SECTION_TITLE, system.title)
        assertTrue(system.entries.all { it.locked })
        assertTrue(system.entries.all { it.badge == "Admin" })
    }

    @Test
    fun build_menu_should_cover_every_module_backed_screen_when_audit_view_enabled() {
        val listed = buildNavMenu(
            permissions = emptyMap(),
            auditView = true,
            isImpersonating = false
        ).filter { it.title != SYSTEM_SECTION_TITLE }
            .flatMap { it.entries }
            .map { it.screen }
            .toSet()

        val expected = AppNavScreen.entries.filter { it.businessModule != null }.toSet()
        assertEquals(expected, listed)
    }

    @Test
    fun build_menu_should_place_system_section_first() {
        val sections = buildNavMenu(
            permissions = grant(BusinessModule.CRM_SALES to AccessLevel.MANAGE),
            auditView = false,
            isImpersonating = false
        )

        assertEquals(SYSTEM_SECTION_TITLE, sections.first().title)
    }
}
