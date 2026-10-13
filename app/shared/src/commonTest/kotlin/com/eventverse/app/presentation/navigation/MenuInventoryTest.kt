package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.MenuPlacement
import com.eventverse.app.domain.pack.withoutTranslations
import com.eventverse.app.domain.pipeline.ModuleFeatureRegistry
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mengunci inventaris menu (TRD-I18N-003 Track 0, module-integration-rules §5.7): setiap layar & fitur sudah menyatakan
 * tampil/tidak (kompilator), dan keputusannya konsisten satu sama lain. Daftar `Shown` di bawah = perilaku
 * `isNavMenuItem = true` sebelum `MenuPlacement` ada (paritas), kecuali LOGIN yang kini jujur `Hidden` (bukan modul, tidak pernah masuk menu).
 */
class MenuInventoryTest {

    private val expectedShown = setOf(
        "ORG_CHART", "DYNAMIC_RBAC", "FACTORY_FLOW", "CRM_SALES", "SAMPLING_ORDER", "INVENTORY", "TECH_PACK_BOM", "COSTING_HPP",
        "PRODUCTION_MRP", "OPERATOR_EXEC", "TRACEABILITY", "QUALITY_CONTROL", "FULFILLMENT", "MASTER_DATA", "VENDOR_CONTACTS", "INVOICING"
    )

    @Test
    fun shownScreens_matchFrozenInventory() {
        val shown = AppNavScreen.entries.filter { it.menu.isShown }.map { it.name }.toSet()
        assertEquals(expectedShown, shown)
    }

    @Test
    fun hiddenScreensAndFeatures_alwaysHaveReason() {
        val reasons = AppNavScreen.entries.map { it.menu } + ModuleFeatureRegistry.all.map { it.menu }
        reasons.filterIsInstance<MenuPlacement.Hidden>().forEach { assertTrue(it.reason.isNotBlank()) }
    }

    @Test
    fun featureCode_pointsToRegisteredFeatureHostedByTheScreensModule() {
        AppNavScreen.entries.filter { it.featureCode != null }.forEach { screen ->
            val feature = ModuleFeatureRegistry.all.firstOrNull { it.code == screen.featureCode }
            assertTrue(feature != null, "${screen.name}: fitur '${screen.featureCode}' tidak terdaftar")
            assertEquals(screen.businessModule, feature.hostModule, "${screen.name}: induk fitur harus = businessModule layar")
        }
    }

    @Test
    fun shownFeature_hasAShownScreen_andViceVersa() {
        ModuleFeatureRegistry.all.filter { it.menu.isShown }.forEach { f ->
            assertTrue(AppNavScreen.entries.any { it.featureCode == f.code && it.menu.isShown }, "Fitur ${f.code} Shown tanpa layar Shown")
        }
        AppNavScreen.entries.filter { it.featureCode != null && it.menu.isShown }.forEach { s ->
            assertTrue(ModuleFeatureRegistry.all.first { it.code == s.featureCode }.menu.isShown, "${s.name} Shown tetapi fiturnya Hidden")
        }
    }

    @Test
    fun eachModuleWithShownScreens_hasExactlyOneMainScreen() {
        AppNavScreen.entries.filter { it.menu.isShown && it.businessModule != null }.groupBy { it.businessModule }.forEach { (module, screens) ->
            val main = screens.filter { it.featureCode == null }
            assertEquals(1, main.size, "Modul ${module?.value}: harus tepat satu layar utama Shown, dapat ${main.map { it.name }}")
        }
    }

    @Test
    fun menuTitle_idLanguage_equalsLegacyScreenTitleForEveryShownScreen() {
        val pack = GarmentDomainPack.pack
        val all = pack.modules.associate { it.id to ModuleAccessConfig(level = AccessLevel.MANAGE) }
        val entries = buildNavMenu(permissions = all, auditView = false, language = LanguageCode.ID).flatMap { it.entries }
        AppNavScreen.entries.filter { it.menu.isShown && it.businessModule != null }.forEach { screen ->
            val entry = entries.firstOrNull { it.screen == screen }
            assertTrue(entry != null, "${screen.name} hilang dari menu")
            assertEquals(screen.title, entry.title, "Judul id ${screen.name} berubah")
            assertTrue(!entry.titleFallback)
        }
    }

    @Test
    fun menuTitle_englishForTracingFeature_usesFeatureLabelWithoutFallback() {
        val r = menuTitle(AppNavScreen.TRACEABILITY, GarmentDomainPack.pack.module(AppNavScreen.TRACEABILITY.businessModule!!), LanguageCode.EN)
        assertEquals("Bundle & Sack Tracing", r.text)
        assertTrue(!r.isFallback)
    }

    @Test
    fun menuTitle_englishForPackWithoutLabel_isHonestFallback() {
        // Garment kini punya menuLabel (Track A); pack "tanpa label" = garment id-saja (menuLabel dibuang).
        val noLabel = GarmentDomainPack.pack.withoutTranslations().module(AppNavScreen.ORG_CHART.businessModule!!)
        val r = menuTitle(AppNavScreen.ORG_CHART, noLabel, LanguageCode.EN)
        assertEquals(AppNavScreen.ORG_CHART.title, r.text)
        assertTrue(r.isFallback, "Belum ada menuLabel: harus ditandai belum diterjemahkan, bukan tampak diterjemahkan")
    }

    @Test
    fun menuTitle_englishForGarmentOrgChart_usesPackLabelWithoutFallback() {
        val r = menuTitle(AppNavScreen.ORG_CHART, GarmentDomainPack.pack.module(AppNavScreen.ORG_CHART.businessModule!!), LanguageCode.EN)
        assertEquals("Organization Chart", r.text)
        assertTrue(!r.isFallback)
    }
}

