package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ModuleActionCode
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Butir B2 (plan induk §3.4): `WidgetRegistry.interactiveFor` meneruskan `ScreenSuggestion.dataBinding`
 * ke [InteractiveScreen.binding]. Fixture pack **non-garment** ("uji" — bengkel servis) memenuhi
 * Kontrak 6 (tenant/vertikal non-default), plus satu cek kompatibilitas ke pack garment lama yang
 * belum menyebut binding (= memori).
 */
class PackBindingPassThroughTest {
    private val moduleId = ModuleId("uji_modul")

    private val pack = DomainPack(
        code = DomainPackCode("uji"),
        displayName = "Pack Uji",
        phases = listOf(PhaseDefinition(PhaseCode("uji"), 1, "Fase Uji", "vertikal uji", 0xFF111111)),
        slots = listOf(SlotDefinition(SlotCode("uji_slot"), "Slot Uji", PhaseCode("uji"), PortType("uji_masuk"), PortType("uji_keluar"))),
        portTypes = setOf(PortType("uji_masuk"), PortType("uji_keluar")),
        wiredPortTypes = emptySet(),
        sections = listOf(ModuleSection(ModuleSectionCode("uji_seksi"), "Seksi Uji", 1, 0xFF111111, 0xFFEEEEEE)),
        modules = listOf(
            ModuleDefinition(
                moduleId, "Modul Uji", "deskripsi uji", ModuleSectionCode("uji_seksi"),
                ModuleKind.OPERATIONAL, "uji", ScopeCapability.GLOBAL_ONLY, setOf(DataScope.ALL_TENANT_DATA), slot = SlotCode("uji_slot")
            )
        ),
        actions = ModuleActionCode.neutral,
        vocabulary = emptyMap(),
        portLabels = emptyMap(),
        screenSuggestions = listOf(
            ScreenSuggestion(
                moduleId, "Antrian Perbaikan", WidgetKind.TABLE,
                sampleRows = listOf(
                    mapOf("No" to "1", "Nama" to "Rina", "Total" to "350000", "Status" to "Baru"),
                    mapOf("No" to "2", "Nama" to "Doni", "Total" to "120000", "Status" to "Selesai")
                ),
                tableHints = TableHints(
                    "Status", listOf("Baru", "Selesai"),
                    fields = listOf(FieldHint("No", FieldType.NUMBER), FieldHint("Total", FieldType.NUMBER)),
                    inlineCreate = true, editableFields = listOf("Nama", "Total")
                ),
                dataBinding = DataBinding.Api("/api/tenant/modules/uji_modul/perbaikan")
            )
        )
    )

    private val screen = PrototypeScreen("default-uji_modul", moduleId, "Antrian Perbaikan", "TABLE")

    @Test
    fun interactiveFor_forwardsApiBinding_andTypedFields() {
        val built = assertNotNull(WidgetRegistry.interactiveFor(screen, pack))
        assertEquals(DataBinding.Api("/api/tenant/modules/uji_modul/perbaikan"), built.binding, "binding pack diteruskan ke layar")
        val fields = assertNotNull(built.spec.entity("item")).fields
        assertEquals(listOf(FieldType.NUMBER, FieldType.TEXT, FieldType.NUMBER, FieldType.ENUM), fields.map { it.type })
        val t = assertNotNull(built.spec.screens.single().table)
        assertTrue(t.inlineCreate && t.editableFields == listOf("Nama", "Total"))
    }

    @Test
    fun interactiveFor_packWithoutBinding_staysMemory() {
        val garment = GarmentDomainPack.pack
        val built = assertNotNull(
            WidgetRegistry.interactiveFor(PrototypeScreen("default-${GarmentModules.INVENTORY.value}", GarmentModules.INVENTORY, "Stok Kain", "TABLE"), garment)
        )
        assertEquals(DataBinding.Memory, built.binding, "pack lama tanpa kunci binding = memori (kompatibel mundur)")
    }
}
