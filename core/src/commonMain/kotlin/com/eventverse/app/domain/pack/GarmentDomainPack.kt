package com.eventverse.app.domain.pack

import com.eventverse.app.domain.pack.GarmentSlots



/**
 * Fase kanvas pack konveksi. Sejak B1 ini **sumber kebenaran** — enum `PipelineStage` sudah dihapus;
 * nilainya disalin persis dari enum itu dan dikunci oleh `GarmentDomainPackParityTest`.
 */
object GarmentPhases {
    val COMMERCIAL = PhaseDefinition(PhaseCode("COMMERCIAL"), 1, "1. Komersial & Sampling", "Negosiasi Order & Prototipe Sample", 0xFF2563EB)
    val ENGINEERING = PhaseDefinition(PhaseCode("ENGINEERING"), 2, "2. Spesifikasi & HPP", "Tech Pack, BOM & Kalkulasi Biaya", 0xFF7C3AED)
    val SUPPLY_CHAIN = PhaseDefinition(PhaseCode("SUPPLY_CHAIN"), 3, "3. Rantai Pasok & Bahan Baku", "Penerimaan Kain Rol & Aksesoris", 0xFF0D9488)
    val MANUFACTURING = PhaseDefinition(PhaseCode("MANUFACTURING"), 4, "4. Lantai Produksi", "Jadwal Mesin, Potong & Jahit", 0xFFEA580C)
    val ASSURANCE_DELIVERY = PhaseDefinition(PhaseCode("ASSURANCE_DELIVERY"), 5, "5. Mutu & Pengiriman", "Inspeksi QC, Packing & Surat Jalan", 0xFF16A34A)

    val all: List<PhaseDefinition> = listOf(COMMERCIAL, ENGINEERING, SUPPLY_CHAIN, MANUFACTURING, ASSURANCE_DELIVERY)

    /** Fase tiap slot — dulu `ModuleArchetype.defaultStage`. Slot yang lupa dipetakan membuat pack gagal dibangun. */
    internal val phaseOfSlot: Map<String, PhaseDefinition> = mapOf(
        "order_ingestion" to COMMERCIAL,
        "product_engineering" to ENGINEERING,
        "costing_hpp" to ENGINEERING,
        "raw_material" to SUPPLY_CHAIN,
        "cutting" to MANUFACTURING,
        "sewing" to MANUFACTURING,
        "finishing" to MANUFACTURING,
        "custom_extension" to MANUFACTURING,
        "quality_control" to ASSURANCE_DELIVERY,
        "fulfillment" to ASSURANCE_DELIVERY
    )
}

/**
 * Tipe dokumen konveksi yang mengalir antarmodul. Sejak B2 ini **sumber kebenaran** — objek
 * `PortDataTypeRegistry` sudah dihapus; daftar dikunci oleh `GarmentDomainPackParityTest`.
 */
object GarmentPortTypes {
    val PRODUCTION_ORDER_DRAFT = PortType("ProductionOrderDraft")
    val APPROVED_SAMPLE_SPECIFICATION = PortType("ApprovedSampleSpecification")
    val TECH_PACK_AND_YIELD_DATA = PortType("TechPackAndYieldData")
    val MATERIAL_REQUISITION = PortType("MaterialRequisition")
    val VERIFIED_MATERIAL_STOCK = PortType("VerifiedMaterialStock")
    val COSTING_CALCULATION_RESULT = PortType("CostingCalculationResult")
    val CUT_PIECES_BUNDLE = PortType("CutPiecesBundle")
    val ASSEMBLED_GARMENT_BUNDLE = PortType("AssembledGarmentBundle")
    val INSPECTED_AND_GRADED_UNIT = PortType("InspectedAndGradedUnit")
    val DISPATCHED_SHIPMENT_MANIFEST = PortType("DispatchedShipmentManifest")
    val ISSUED_INVOICE_DOCUMENT = PortType("IssuedInvoiceDocument")

    /** Port yang dipakai kanvas menyambung modul. Tipe baru di spec katalog wajib masuk sini. */
    val wired: Set<PortType> = setOf(
        PRODUCTION_ORDER_DRAFT, APPROVED_SAMPLE_SPECIFICATION, TECH_PACK_AND_YIELD_DATA,
        MATERIAL_REQUISITION, VERIFIED_MATERIAL_STOCK, COSTING_CALCULATION_RESULT, CUT_PIECES_BUNDLE,
        ASSEMBLED_GARMENT_BUNDLE, INSPECTED_AND_GRADED_UNIT, DISPATCHED_SHIPMENT_MANIFEST, ISSUED_INVOICE_DOCUMENT
    )
}

/**
 * Pack konveksi. Fase ([GarmentPhases], B1) dan port ([GarmentPortTypes], B2) = data literal.
, slot ([GarmentSlots], B3) = data literal.
 */
object GarmentDomainPack {

    val CODE = DomainPackCode("garment")

    val pack: DomainPack by lazy {
        val slots = GarmentSlots.meta.map { m ->
            val phase = requireNotNull(GarmentPhases.phaseOfSlot[m.slot.value]) { "Slot ${m.slot.value} belum dipetakan ke fase garment" }
            SlotDefinition(m.slot, m.displayName, phase.code, PortType(m.input), PortType(m.output))
        }
        val wired = GarmentPortTypes.wired
        DomainPack(
            code = CODE,
            displayName = "Konveksi & Garmen",
            phases = GarmentPhases.all,
            slots = slots,
            portTypes = wired + slots.flatMap { listOf(it.defaultInput, it.defaultOutput) },
            wiredPortTypes = wired,
            sections = GarmentModules.sections,
            modules = GarmentModules.modules
        )
    }
}
