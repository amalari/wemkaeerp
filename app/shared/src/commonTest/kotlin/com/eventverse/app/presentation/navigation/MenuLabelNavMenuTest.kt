package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRD-I18N-003 Track C: judul baris menu layar modul dibaca dari `menuLabel` pack (pack non-garment), bukan dari `AppNavScreen.title`. */
class MenuLabelNavMenuTest {

    private fun pack(menuLabel: LocalizedText?) = DomainPack(
        code = DomainPackCode("klinik"), displayName = "Klinik",
        phases = listOf(PhaseDefinition(PhaseCode("CARE"), 1, "Layanan", "", 0xFF16A34A)),
        slots = emptyList(), portTypes = emptySet(), wiredPortTypes = emptySet(),
        sections = listOf(ModuleSection(ModuleSectionCode("CARE"), "Layanan", 1, 0xFF2563EB, 0xFFEFF6FF)),
        modules = listOf(
            ModuleDefinition(
                GarmentModules.INVENTORY, "Persediaan Obat", "", ModuleSectionCode("CARE"), ModuleKind.FOUNDATION,
                "clipboard", ScopeCapability.GLOBAL_ONLY, setOf(DataScope.ALL_TENANT_DATA), null,
                menuLabelText = menuLabel
            )
        )
    )

    private val access = mapOf(GarmentModules.INVENTORY to ModuleAccessConfig(level = AccessLevel.VIEW))

    private fun entry(pack: DomainPack, language: LanguageCode) =
        buildNavMenu(access, auditView = false, pack = pack, language = language)
            .single().entries.single { it.screen == AppNavScreen.INVENTORY }

    @Test
    fun menu_entry_with_pack_menu_label_uses_that_label_per_language() {
        val p = pack(LocalizedText.of("Obat", "Medicine"))
        assertEquals("Obat", entry(p, LanguageCode.ID).title)
        val en = entry(p, LanguageCode.EN)
        assertEquals("Medicine", en.title)
        assertFalse(en.titleFallback)
    }

    @Test
    fun menu_entry_without_menu_label_uses_screen_title_and_flags_english_fallback() {
        val p = pack(null)
        val id = entry(p, LanguageCode.ID)
        assertEquals(AppNavScreen.INVENTORY.title, id.title)
        assertFalse(id.titleFallback, "Bahasa id: paritas, tanpa penanda")
        val en = entry(p, LanguageCode.EN)
        assertEquals(AppNavScreen.INVENTORY.title, en.title)
        assertTrue(en.titleFallback)
    }

    @Test
    fun title_for_returns_menu_title_of_screen_or_null_when_absent() {
        val menu = buildNavMenu(access, auditView = false, pack = pack(LocalizedText.of("Obat", "Medicine")), language = LanguageCode.EN)
        assertEquals("Medicine", menu.titleFor(AppNavScreen.INVENTORY))
        assertNull(menu.titleFor(AppNavScreen.LOGIN))
    }
}
