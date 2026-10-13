package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.MenuPlacement
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModuleFeatureMenuTest {

    @Test
    fun hidden_withoutReason_isRejected() {
        assertFailsWith<IllegalArgumentException> { MenuPlacement.Hidden(" ") }
    }

    @Test
    fun feature_shownWithoutMenuLabel_isRejected() {
        assertFailsWith<IllegalArgumentException> {
            ModuleFeature("x", "X", GarmentModules.FULFILLMENT, ModuleFeatureKind.TOOL, "d", menu = MenuPlacement.Shown)
        }
    }

    @Test
    fun registry_everyShownFeatureHasMenuLabelWithIdAndEn() {
        ModuleFeatureRegistry.all.filter { it.menu.isShown }.forEach { f ->
            val label: LocalizedText = requireNotNull(f.menuLabelText) { "Fitur ${f.code} Shown tanpa label" }
            assertTrue(label.has(com.eventverse.app.domain.i18n.LanguageCode.EN), "Fitur ${f.code}: label en wajib")
        }
    }
}
