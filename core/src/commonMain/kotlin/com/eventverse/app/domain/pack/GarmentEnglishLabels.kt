// FILE-SIZE-EXEMPT: tabel terjemahan pack garment — data terurut, bukan logika. Lihat .claude/rules/file-size-rules.md §3
package com.eventverse.app.domain.pack

/**
 * Terjemahan Inggris **label dokumen & petunjuk** pack garment (TRD-I18N-002): label port, judul/alasan usulan
 * layar, dan nama peran. Kunci = kode port, `ModuleId`, dan kata kamus peran (`RoleHint.word`, yang **tidak**
 * dilokalkan). `sampleRows` dan `defaultStatuses` sengaja tidak ada di sini (kunci/nilainya adalah protokol
 * struktur renderer & kunci data); lihat TRD-I18N-002 §4. Penambah `en` = [withGarmentEnglish].
 */
internal object GarmentEnglishLabels {

    /** Kode port → label. Mencakup seluruh kunci `GarmentPortTypes.labels`. */
    val ports: Map<String, String> = mapOf(
        "CommercialInquiry" to "Inquiry / Request for Quotation",
        "ProductionOrderDraft" to "Production Order Draft (PO)",
        "ApprovedSampleSpecification" to "Approved Sample Specification",
        "TechPackAndYieldData" to "Tech Pack & Material Requirements",
        "MaterialRequisition" to "Material Purchase Request",
        "VerifiedMaterialStock" to "Verified Material Stock",
        "CostingCalculationResult" to "Unit COGS Calculation Result",
        "CuttingOrderWithFabric" to "Cutting Work Order + Fabric Ready to Cut",
        "CutPiecesBundle" to "Cut Fabric Pieces Bundle",
        "AssembledGarmentBundle" to "Sewn Garment Bundle",
        "FinishedGarmentUnit" to "Finished Garment Unit",
        "InspectedAndGradedUnit" to "QC-Passed Unit (Graded)",
        "DispatchedShipmentManifest" to "Delivery Note / Shipment Manifest",
        "IssuedInvoiceDocument" to "Issued Invoice",
        "AnyOperationalPayload" to "Custom Module Operational Data",
        "StandardHandoffPayload" to "Standard Handoff",
        "DefectReworkPayload" to "Defect Return"
    )

    /** Kode modul → (judul layar, alasan pilihan layar). */
    val screens: Map<String, Pair<String, String>> = mapOf(
        "crm_sales" to ("PO & Prospect List" to "Chosen because POs and prospects are compared side by side - buyer, product, and shipping target - and their status is changed right in the row."),
        "sampling_order" to ("Sampling Work Order Board" to "Chosen because sampling work orders move from new, in progress, to done, and the work queue needs to be clearly visible on a board."),
        "tech_pack_bom" to ("BOM & Tech Pack Specification" to "Chosen because a BOM is a per-article list of materials read in rows and marked draft or final."),
        "costing_hpp" to ("COGS & Cost Dashboard" to "Chosen because the owner needs a quick summary of COGS, margin, and active orders without opening a list."),
        "inventory" to ("Fabric & Raw Material Stock" to "Chosen because fabric stock is checked in rows per material, marked as factory-owned or buyer-consigned."),
        "production_mrp" to ("Cutting Schedule & Mass Work Orders" to "Chosen because mass work orders move from cutting queue, in progress, to done and need to be visible as a board."),
        "operator_exec" to ("Sewing Line Kanban" to "Chosen because work is divided per sewing line and the production head needs to see each line's load."),
        "quality_control" to ("QC Inspection Checklist" to "Chosen because quality inspection is a list of check items ticked off one by one."),
        "fulfillment" to ("Delivery Note & Packing List" to "Chosen because the delivery note and packing list are handed to the buyer in printed form.")
    )

    /** Kata kamus peran (`RoleHint.word`) → nama peran tampil. */
    val roles: Map<String, String> = mapOf(
        "pemilik" to "Owner",
        "admin penjualan" to "Sales Admin",
        "sales" to "Sales",
        "sampling" to "Sampling Staff",
        "admin gudang" to "Warehouse Admin",
        "gudang" to "Warehouse Staff",
        "pola" to "Pattern Maker",
        "costing" to "Costing Staff",
        "kepala potong" to "Cutting Head",
        "ppic" to "PPIC Staff",
        "operator jahit" to "Sewing Operator",
        "operator rajut" to "Knitting Operator",
        "penjahit" to "Tailor",
        "qc" to "QC Inspector",
        "admin pengiriman" to "Shipping Admin",
        "packing" to "Packing Staff",
        "keuangan" to "Finance Staff"
    )
}
