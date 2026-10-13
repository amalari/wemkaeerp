package com.eventverse.app

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.i18n.PackLanguages
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
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

/**
 * Pack non-garment untuk tes lokalisasi (TRD-I18N-002, Kontrak 6): kode [code] bebas dari konsep konveksi.
 * [complete] = fase & slot punya `en`; `false` = satu slot sengaja tanpa `en` (klaim `en` tetap dinyatakan → selisih terlapor).
 */
internal object I18nPackFixtures {

    fun pack(code: String, languages: Set<String> = setOf("id", "en"), complete: Boolean = true): DomainPack {
        val claimed = PackLanguages(languages.map { LanguageCode(it) }.toSet())
        val en = LanguageCode.EN in claimed.languages
        val phase = PhaseDefinition(
            code = PhaseCode("LAYANAN"), order = 1, displayName = "1. Layanan", subtitle = "Pasien datang", colorHex = 0xFF2563EB,
            displayNameText = LocalizedText.of("1. Layanan", if (en) "1. Service" else null),
            subtitleText = LocalizedText.of("Pasien datang", if (en) "Patient arrives" else null)
        )
        val slotA = SlotDefinition(
            SlotCode("${code}_layanan"), "Layanan", PhaseCode("LAYANAN"), PortType("Kunjungan"), PortType("Rekam"),
            displayNameText = LocalizedText.of("Layanan", if (en) "Service" else null)
        )
        val slotB = SlotDefinition(
            SlotCode("${code}_kasir"), "Kasir", PhaseCode("LAYANAN"), PortType("Rekam"), PortType("Kunjungan"),
            displayNameText = LocalizedText.of("Kasir", if (en && complete) "Cashier" else null)
        )
        return DomainPack(
            code = DomainPackCode(code),
            displayName = "Klinik Uji",
            phases = listOf(phase),
            slots = listOf(slotA, slotB),
            portTypes = setOf(PortType("Kunjungan"), PortType("Rekam")),
            wiredPortTypes = setOf(PortType("Kunjungan"), PortType("Rekam")),
            sections = listOf(
                GarmentDomainPack.pack.sections.first { it.code.value == "GOVERNANCE" },
                ModuleSection(ModuleSectionCode("LAYANAN"), "Layanan", 2, 0xFF16A34A, 0xFFF0FDF4)
            ),
            modules = listOf(
                requireNotNull(GarmentDomainPack.pack.module(GarmentModules.ORG_CHART)),
                ModuleDefinition(
                    ModuleId("${code}_antrean"), "Antrean Pasien", "Antrean pendaftaran & poli", ModuleSectionCode("LAYANAN"),
                    ModuleKind.OPERATIONAL, "clipboard", ScopeCapability.HIERARCHICAL,
                    setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA), SlotCode("${code}_layanan")
                )
            ),
            languages = claimed
        )
    }
}
