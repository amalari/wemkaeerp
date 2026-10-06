package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.WidgetKind
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

    /**
     * Label tampilan manusiawi tiap tipe port (kosakata pack, sejajar [GarmentVocabulary.actions]).
     * Kuncinya kode port persis seperti di kontrak katalog; mencakup juga port batas/slot default
     * yang tidak ikut `wired` (`CommercialInquiry`, `CuttingOrderWithFabric`, dst.).
     */
    val labels: Map<String, String> = mapOf(
        "CommercialInquiry" to "Inquiry / Permintaan Penawaran",
        "ProductionOrderDraft" to "Draf Pesanan Produksi (PO)",
        "ApprovedSampleSpecification" to "Spesifikasi Sampel Disetujui",
        "TechPackAndYieldData" to "Tech Pack & Kebutuhan Bahan",
        "MaterialRequisition" to "Permintaan Pembelian Bahan",
        "VerifiedMaterialStock" to "Stok Bahan Terverifikasi",
        "CostingCalculationResult" to "Hasil Hitung HPP per Unit",
        "CuttingOrderWithFabric" to "SPK Potong + Kain Siap Potong",
        "CutPiecesBundle" to "Bundle Potongan Kain",
        "AssembledGarmentBundle" to "Bundle Hasil Jahit",
        "FinishedGarmentUnit" to "Unit Selesai Finishing",
        "InspectedAndGradedUnit" to "Unit Lolos QC (Tergrade)",
        "DispatchedShipmentManifest" to "Surat Jalan / Manifest Kirim",
        "IssuedInvoiceDocument" to "Invoice Terbit",
        "AnyOperationalPayload" to "Data Operasional Modul Kustom"
    )
}

/**
 * Kosakata & aksi pack konveksi (A4). Label aksi disalin persis dari placeholder B6e — layar generik
 * seluruh tenant garment tidak berubah sedikit pun — dan dikunci tabel emas `GarmentModulesParityTest`,
 * supaya perubahan kata di sini harus disengaja.
 */
object GarmentVocabulary {

    val actions: List<ModuleAction> = listOf(
        ModuleAction(ModuleActionCode.ADD, "Tambah Pesanan"),
        ModuleAction(ModuleActionCode.EDIT, "Input Progres"),
        ModuleAction(ModuleActionCode.APPROVE, "Setujui SPK"),
        ModuleAction(ModuleActionCode.DELETE, "Hapus Data")
    )

    val terms: Map<VocabularyKey, String> = mapOf(
        VocabularyKey.WORKPLACE to "pabrik",
        VocabularyKey.DOCUMENT to "Dokumen"
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
            SlotDefinition(m.slot, m.displayName, phase.code, PortType(m.input), PortType(m.output), m.widget, m.statuses)
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
            modules = GarmentModules.modules,
            actions = GarmentVocabulary.actions,
            vocabulary = GarmentVocabulary.terms,
            portLabels = GarmentPortTypes.labels,
            screenSuggestions = GarmentScreenSuggestions.all
        )
    }
}
