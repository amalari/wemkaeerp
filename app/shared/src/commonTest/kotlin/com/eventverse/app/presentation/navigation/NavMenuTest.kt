package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.ModuleCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Menu adalah keputusan wewenang, jadi ia diuji tanpa merender apa pun.
 *
 * Berkas ini ditulis ulang ketika Bagan Organisasi, RBAC, dan Alur Pabrik menjadi modul. Versi
 * sebelumnya menguji `isImpersonating` dan `SYSTEM_SECTION_TITLE`: dua hal yang ada semata karena
 * ketiga layar itu berada **di luar** matriks wewenang. Setelah keduanya masuk matriks, aturan
 * khususnya tidak lagi punya alasan untuk ada — dan tesnya ikut hilang bersama aturannya.
 */
class NavMenuTest {

    private fun grant(vararg pairs: Pair<BusinessModule, AccessLevel>) =
        pairs.associate { (module, level) -> module to ModuleAccessConfig(level = level) }

    @Test
    fun build_menu_when_no_permissions_should_be_empty() {
        val sections = buildNavMenu(permissions = emptyMap(), auditView = false)

        assertTrue(
            sections.isEmpty(),
            "Tanpa satu pun wewenang, tidak ada menu yang boleh muncul — termasuk layar tata kelola"
        )
    }

    @Test
    fun build_menu_when_governance_granted_should_appear_in_first_section() {
        val sections = buildNavMenu(
            permissions = grant(
                BusinessModule.ORG_CHART to AccessLevel.VIEW,
                BusinessModule.DYNAMIC_RBAC to AccessLevel.MANAGE,
                BusinessModule.INVENTORY to AccessLevel.OPERATE
            ),
            auditView = false
        )

        // Seksi tata kelola harus berada di puncak drawer, seperti sebelum ketiganya jadi modul.
        assertEquals(ModuleCategory.GOVERNANCE.displayName, sections.first().title)
        assertEquals(
            listOf(AppNavScreen.ORG_CHART, AppNavScreen.DYNAMIC_RBAC),
            sections.first().entries.map { it.screen }
        )
    }

    @Test
    fun build_menu_when_governance_module_denied_should_hide_it() {
        val sections = buildNavMenu(
            permissions = grant(
                BusinessModule.ORG_CHART to AccessLevel.VIEW,
                BusinessModule.DYNAMIC_RBAC to AccessLevel.NONE,
                BusinessModule.FACTORY_FLOW to AccessLevel.NONE
            ),
            auditView = false
        )

        val screens = sections.flatMap { it.entries }.map { it.screen }
        assertTrue(AppNavScreen.ORG_CHART in screens)
        assertFalse(
            AppNavScreen.DYNAMIC_RBAC in screens,
            "Layar RBAC kini tunduk pada matriks seperti modul lain, bukan selalu terlihat"
        )
        assertFalse(AppNavScreen.FACTORY_FLOW in screens)
    }

    @Test
    fun build_menu_when_module_granted_should_group_under_its_category_header() {
        val sections = buildNavMenu(
            permissions = grant(BusinessModule.INVENTORY to AccessLevel.OPERATE),
            auditView = false
        )

        val logistics = sections.single { it.title == ModuleCategory.LOGISTICS.displayName }
        assertEquals(AppNavScreen.INVENTORY, logistics.entries.single().screen)
        assertEquals(AccessLevel.OPERATE, logistics.entries.single().accessLevel)
        assertFalse(logistics.entries.single().locked)
    }

    @Test
    fun build_menu_should_omit_categories_whose_modules_are_all_denied() {
        val sections = buildNavMenu(
            permissions = grant(
                BusinessModule.INVENTORY to AccessLevel.VIEW,
                BusinessModule.COSTING_HPP to AccessLevel.NONE
            ),
            auditView = false
        )

        assertTrue(
            sections.none { it.title == ModuleCategory.TECHNICAL.displayName },
            "Header tanpa isi menjanjikan sesuatu yang tidak ada"
        )
    }

    @Test
    fun build_menu_in_audit_view_should_show_denied_modules_locked() {
        val sections = buildNavMenu(
            permissions = grant(
                BusinessModule.DYNAMIC_RBAC to AccessLevel.NONE,
                BusinessModule.INVENTORY to AccessLevel.NONE
            ),
            auditView = true
        )

        val entries = sections.flatMap { it.entries }
        assertTrue(entries.isNotEmpty(), "Mode audit memperlihatkan batasnya, bukan menyembunyikannya")
        assertTrue(entries.all { it.locked })
    }

    @Test
    fun first_accessible_screen_should_skip_locked_entries() {
        // Mode audit menampilkan modul terkunci; layar pendaratan tidak boleh mendarat di sana.
        val sections = buildNavMenu(
            permissions = grant(
                BusinessModule.ORG_CHART to AccessLevel.NONE,
                BusinessModule.INVENTORY to AccessLevel.OPERATE
            ),
            auditView = true
        )

        assertEquals(AppNavScreen.INVENTORY, firstAccessibleScreen(sections))
    }

    @Test
    fun first_accessible_screen_when_everything_locked_should_be_null() {
        val sections = buildNavMenu(
            permissions = grant(BusinessModule.ORG_CHART to AccessLevel.NONE),
            auditView = true
        )

        assertNull(
            firstAccessibleScreen(sections),
            "Tidak ada tujuan yang sah harus dinyatakan, bukan disamarkan dengan tujuan asal-asalan"
        )
    }
}
