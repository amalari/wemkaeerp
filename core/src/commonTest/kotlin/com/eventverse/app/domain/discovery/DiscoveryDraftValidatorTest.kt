package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Plan §2 A2: setiap galat punya path (`$.pack.modules[i].id`, `$.blueprint.modules[0].moduleCode`). */
class DiscoveryDraftValidatorTest {

    private fun packWithModules(vararg modules: ModuleDefinition): DomainPack = DomainPack(
        code = DomainPackCode("klinik"),
        displayName = "Klinik",
        phases = listOf(PhaseDefinition(PhaseCode("OPERASI"), 1, "1. Operasi", "Alur harian", 0xFF2563EB)),
        slots = modules.filter { it.slot != null }.map {
            SlotDefinition(it.slot!!, it.displayName, PhaseCode("OPERASI"), PortType("Permintaan"), PortType("Catatan"))
        },
        portTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
        wiredPortTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
        sections = listOf(ModuleSection(ModuleSectionCode("UTAMA"), "Operasional", 1, 0xFF2563EB, 0xFFEFF6FF)),
        modules = modules.toList()
    )

    private fun prefixedModule() = ModuleDefinition(
        id = ModuleId("klinik_antrean"), displayName = "Antrean", description = "Antrean pasien",
        section = ModuleSectionCode("UTAMA"), kind = ModuleKind.OPERATIONAL, iconKey = "clipboard",
        scopeCapability = ScopeCapability.HIERARCHICAL,
        supportedScopes = setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA),
        slot = SlotCode("klinik_antrean")
    )

    @Test
    fun `modul baru tanpa prefiks dilaporkan dengan path modulnya`() {
        val pencuri = ModuleDefinition(
            id = ModuleId("crm_sales"), displayName = "Milik platform", description = "",
            section = ModuleSectionCode("UTAMA"), kind = ModuleKind.GOVERNANCE, iconKey = "clipboard",
            scopeCapability = ScopeCapability.HIERARCHICAL,
            supportedScopes = setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA),
            slot = null
        )
        val pack = packWithModules(prefixedModule(), pencuri)
        val blueprint = Blueprint(
            code = BlueprintCode("klinik_starter"), pack = pack.code, displayName = "s", shortBadge = "s",
            description = "s", targetClientProfile = "s", modules = listOf(BlueprintModule("klinik_antrean", true))
        )
        val issues = DiscoveryDraftValidator.validate(DiscoveryDraft(pack, blueprint))

        val stolen = issues.first { it.message.contains("crm_sales") }
        assertEquals("$.pack.modules[1].id", stolen.path)
    }

    @Test
    fun `blueprint yang menyebut modul asing ditolak saat konstruksi`() {
        val pack = packWithModules(prefixedModule())
        val blueprint = Blueprint(
            code = BlueprintCode("klinik_starter"), pack = pack.code, displayName = "s", shortBadge = "s",
            description = "s", targetClientProfile = "s",
            modules = listOf(BlueprintModule("modul_hantu", true))
        )
        // Lapisan pertama: invarian DiscoveryDraft menolaknya sebelum validator dipanggil. Validator
        // memeriksa ulang dengan path sebagai lapisan kedua (mis. dokumen lama yang tidak lewat konstruktor).
        val ex = assertFailsWith<IllegalArgumentException> { DiscoveryDraft(pack, blueprint) }
        assertTrue(ex.message!!.contains("modul_hantu"))
    }

    @Test
    fun `pack bawaan yang ditulis ulang ditolak dengan path pack`() {
        // Dokumen dengan kode "garment" tapi isi berbeda dari pack yang dikirim platform = penulisan ulang
        // kosakata platform di balik nama bawaan. Validator wajib menolaknya di `$.pack`.
        val corrupted = GarmentDomainPack.pack.copy(
            modules = GarmentDomainPack.pack.modules + ModuleDefinition(
                id = ModuleId("hantu"), displayName = "Hantu", description = "",
                section = GarmentDomainPack.pack.modules.first().section, kind = ModuleKind.GOVERNANCE,
                iconKey = "clipboard", scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA), slot = null
            )
        )
        val blueprint = GarmentBlueprints.FOB_FULL_PACKAGE
        val issues = DiscoveryDraftValidator.validate(DiscoveryDraft(corrupted, blueprint))

        assertTrue(issues.any { it.path == "$.pack" && it.message.contains("identik") })
    }

    @Test
    fun `draf sah menghasilkan nol temuan`() {
        val pack = packWithModules(prefixedModule())
        val blueprint = Blueprint(
            code = BlueprintCode("klinik_starter"), pack = pack.code, displayName = "s", shortBadge = "s",
            description = "s", targetClientProfile = "s", modules = listOf(BlueprintModule("klinik_antrean", true))
        )
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(DiscoveryDraft(pack, blueprint)))
    }
}
