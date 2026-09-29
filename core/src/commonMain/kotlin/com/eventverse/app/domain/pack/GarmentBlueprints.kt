package com.eventverse.app.domain.pack

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.OperationalModuleCatalog

/** Kunci parameter modul garment (TRD-PLAT-001 FR-3). */
object GarmentBlueprintParams {
    const val STOCK_OWNERSHIP = "stockOwnership"
    const val COSTING_BEHAVIOR = "costingBehavior"
    const val DEFECT_LIABILITY = "defectLiability"
}

/**
 * Tiga starter konveksi. **B4a**: dibangun dari perilaku preset lama (`supportedPresets`, `*For(preset)`) supaya
 * identik secara konstruksi; tabel emas di `GarmentBlueprintParityTest` membekukannya sebelum B4b memindah pembaca.
 */
object GarmentBlueprints {

    private fun fromLegacy(preset: GarmentBusinessPreset) = Blueprint(
        code = BlueprintCode(preset.code),
        pack = GarmentDomainPack.CODE,
        displayName = preset.displayName,
        shortBadge = preset.shortBadge,
        description = preset.description,
        targetClientProfile = preset.targetClientProfile,
        modules = OperationalModuleCatalog.all.map { spec ->
            BlueprintModule(
                moduleCode = spec.module.code,
                active = preset in spec.supportedPresets,
                parameters = buildMap {
                    put(GarmentBlueprintParams.STOCK_OWNERSHIP, spec.stockOwnershipFor(preset).name)
                    put(GarmentBlueprintParams.COSTING_BEHAVIOR, spec.costingBehaviorFor(preset).name)
                    spec.defectLiabilityFor(preset)?.let { put(GarmentBlueprintParams.DEFECT_LIABILITY, it.name) }
                }
            )
        }
    )

    val FOB_FULL_PACKAGE: Blueprint by lazy { fromLegacy(GarmentBusinessPreset.FOB_FULL_PACKAGE) }
    val CMT_MAKLOON: Blueprint by lazy { fromLegacy(GarmentBusinessPreset.CMT_MAKLOON) }
    val BRAND_D2C: Blueprint by lazy { fromLegacy(GarmentBusinessPreset.BRAND_D2C) }

    val all: List<Blueprint> get() = listOf(FOB_FULL_PACKAGE, CMT_MAKLOON, BRAND_D2C)

    /** Kode tak dikenal → null. Pemanggil yang menolak; fallback ke FOB tetap milik `GarmentBusinessPreset.fromCode` sampai B4d. */
    fun find(code: BlueprintCode): Blueprint? = all.firstOrNull { it.code == code }
}
