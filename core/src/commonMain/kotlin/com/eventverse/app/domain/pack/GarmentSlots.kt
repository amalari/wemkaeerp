package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.rbac.BusinessModule

/**
 * Slot kemampuan pack konveksi. Sejak B3 ini **sumber kebenaran** — `enum class ModuleArchetype`
 * sudah dihapus; nilainya disalin persis dan dikunci oleh `GarmentDomainPackParityTest`.
 *
 * Aturan domain konveksi (tahap, meja operator, stasiun) mencari **peran** lewat konstanta di sini
 * (`GarmentSlots.SEWING`), sehingga tetap aman bila tenant tidak punya peran itu.
 */
object GarmentSlots {
    val ORDER_INGESTION = SlotCode("order_ingestion")
    val RAW_MATERIAL = SlotCode("raw_material")
    val PRODUCT_ENGINEERING = SlotCode("product_engineering")
    val COSTING_HPP = SlotCode("costing_hpp")
    val CUTTING = SlotCode("cutting")
    val SEWING = SlotCode("sewing")
    val FINISHING = SlotCode("finishing")
    val QUALITY_CONTROL = SlotCode("quality_control")
    val FULFILLMENT = SlotCode("fulfillment")
    val CUSTOM_EXTENSION = SlotCode("custom_extension")

    internal data class Meta(val slot: SlotCode, val displayName: String, val input: String, val output: String, val representative: BusinessModule)

    /** Urutan = urutan enum lama (dipakai `all`, mis. untuk pilihan slot di UI). */
    internal val meta: List<Meta> = listOf(
        Meta(ORDER_INGESTION, "Penerimaan Pesanan / PO / Sales Ingestion", "CommercialInquiry", "ProductionOrderDraft", GarmentModules.CRM_SALES),
        Meta(RAW_MATERIAL, "Bahan Baku & Persediaan Gudang", "MaterialRequisition", "VerifiedMaterialStock", GarmentModules.INVENTORY),
        Meta(PRODUCT_ENGINEERING, "Rekayasa Produk: Tech Pack, BOM & Yield", "ApprovedSampleSpecification", "TechPackAndYieldData", GarmentModules.TECH_PACK_BOM),
        Meta(COSTING_HPP, "Perhitungan Biaya & HPP (Costing Engine)", "TechPackAndYieldData", "CostingCalculationResult", GarmentModules.COSTING_HPP),
        Meta(CUTTING, "Pemotongan Pola Kain (Spreading & Cutting)", "CuttingOrderWithFabric", "CutPiecesBundle", GarmentModules.PRODUCTION_MRP),
        Meta(SEWING, "Penjahitan & Perakitan (Sewing Line)", "CutPiecesBundle", "AssembledGarmentBundle", GarmentModules.OPERATOR_EXEC),
        Meta(FINISHING, "Finishing, Cuci, Setrika & Trimming", "AssembledGarmentBundle", "FinishedGarmentUnit", GarmentModules.OPERATOR_EXEC),
        Meta(QUALITY_CONTROL, "Pengawasan Mutu, Grading & Inspeksi", "FinishedGarmentUnit", "InspectedAndGradedUnit", GarmentModules.QUALITY_CONTROL),
        Meta(FULFILLMENT, "Pengemasan, Surat Jalan & Ekspedisi", "InspectedAndGradedUnit", "DispatchedShipmentManifest", GarmentModules.FULFILLMENT),
        Meta(CUSTOM_EXTENSION, "Modul Khusus Tambahan (Custom Plugin / Extension)", "AnyOperationalPayload", "AnyOperationalPayload", GarmentModules.PRODUCTION_MRP)
    )

    val all: List<SlotCode> = meta.map { it.slot }

    /** Kode tersimpan → slot kanonik; tidak peka huruf besar (perilaku `fromCode` enum lama). */
    fun fromCode(code: String?): SlotCode? = all.firstOrNull { it.value.equals(code, ignoreCase = true) }

    /**
     * Katalog proses opsional menyimpan slot sebagai **NAME enum lama** (`SEWING`), bukan code.
     * Format itu dipertahankan karena DB dipakai bersama `wemade-erp` (Jalur A).
     */
    fun fromLegacyName(name: String): SlotCode =
        requireNotNull(all.firstOrNull { it.value.uppercase() == name }) { "Slot lama tidak dikenal: $name" }

    fun legacyNameOf(slot: SlotCode): String = slot.value.uppercase()

    /** Modul bawaan yang mewakili slot — dipinjam node plugin kustom untuk ikon & cakupan akses. */
    fun representativeModule(slot: SlotCode): BusinessModule =
        requireNotNull(meta.firstOrNull { it.slot == slot }) { "Slot ${slot.value} bukan slot garment" }.representative

    /**
     * Slot yang diisi modul bawaan. Null untuk modul governance/foundation: mereka bukan stasiun,
     * dan memaksanya ke slot membuatnya layak tampil di kanvas — justru yang tidak boleh.
     */
    fun forModule(module: BusinessModule): SlotCode? = DomainPackRegistry.moduleDefinition(module)?.slot

    /** Slot untuk code modul tersimpan, bawaan atau kustom (kustom → [CUSTOM_EXTENSION]). */
    fun forModuleCode(moduleCode: String): SlotCode {
        val standard = ModuleIdCodec.standardOrNull(moduleCode)
        return standard?.let { forModule(it) } ?: CUSTOM_EXTENSION
    }
}
