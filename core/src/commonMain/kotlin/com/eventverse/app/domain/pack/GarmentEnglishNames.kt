// FILE-SIZE-EXEMPT: tabel terjemahan pack garment — data terurut, bukan logika. Lihat .claude/rules/file-size-rules.md §3
package com.eventverse.app.domain.pack

/**
 * Terjemahan Inggris **nama struktur** pack garment (TRD-I18N-002): fase, slot, seksi, modul, aksi, kosakata.
 * Kunci = kode tersimpan (`PhaseCode`, `SlotCode`, `ModuleSectionCode`, `ModuleId`); kode **tidak pernah**
 * dilokalkan. Padanan istilah industri baku: Potong = Cutting, Surat Jalan = Delivery Note, HPP = COGS/costing,
 * SPK = Work Order, Kain Rol = Fabric Roll. Teks `id` tetap sumber kebenaran di `GarmentPhases`/`GarmentSlots`/
 * `GarmentModules`; berkas ini hanya menambahkan `en` lewat [withGarmentEnglish].
 */
internal object GarmentEnglishNames {

    /** Kode fase → (nama, subjudul). */
    val phases: Map<String, Pair<String, String>> = mapOf(
        "COMMERCIAL" to ("1. Commercial & Sampling" to "Order Negotiation & Sample Prototyping"),
        "ENGINEERING" to ("2. Specification & Costing" to "Tech Pack, BOM & Cost Calculation"),
        "SUPPLY_CHAIN" to ("3. Supply Chain & Raw Materials" to "Receiving Fabric Rolls & Trims"),
        "MANUFACTURING" to ("4. Production Floor" to "Machine Schedule, Cutting & Sewing"),
        "ASSURANCE_DELIVERY" to ("5. Quality & Delivery" to "QC Inspection, Packing & Delivery Note")
    )

    /** Kode slot → nama. */
    val slots: Map<String, String> = mapOf(
        "order_ingestion" to "Order Intake / PO / Sales Ingestion",
        "sampling" to "Pattern & Sampling Order",
        "raw_material" to "Raw Materials & Warehouse Inventory",
        "product_engineering" to "Product Engineering: Tech Pack, BOM & Yield",
        "costing_hpp" to "Costing & COGS (Costing Engine)",
        "cutting" to "Fabric Cutting (Spreading & Cutting)",
        "sewing" to "Sewing & Assembly (Sewing Line)",
        "finishing" to "Finishing, Washing, Pressing & Trimming",
        "quality_control" to "Quality Control, Grading & Inspection",
        "fulfillment" to "Packing, Delivery Note & Shipping",
        "custom_extension" to "Additional Custom Module (Plugin / Extension)"
    )

    /** Kode seksi → nama. */
    val sections: Map<String, String> = mapOf(
        "GOVERNANCE" to "Systems & Structure",
        "FOUNDATION" to "Master Data & Reference",
        "SALES" to "Sales & Customer Relations",
        "LOGISTICS" to "Warehouse, Raw Materials & Logistics",
        "TECHNICAL" to "Design, Patterns & Costing",
        "PRODUCTION" to "Production Floor & Operators",
        "QUALITY" to "Quality & Inspection",
        "FINANCE" to "Finance & Billing"
    )

    /** Kode modul → (nama, deskripsi). */
    val modules: Map<String, Pair<String, String>> = mapOf(
        "org_chart" to ("Organization Chart & Employees" to "Divisions, job levels, and employee records."),
        "dynamic_rbac" to ("Access Rights & Positions (RBAC)" to "Authority matrix per position, module assignment to divisions, and persona testing."),
        "factory_flow" to ("Factory Flow (Pipeline)" to "Tenant operations canvas: module order, renaming, and bypass."),
        "master_data" to ("Materials & Price Master Data" to "Catalog of yarn, fabric, trims, packaging units, and point-in-time costing reference rates."),
        "vendor_contacts" to ("Vendor Contacts" to "Vendor address book, service price list per vendor, and assignment of vendors to outsourced processes."),
        "crm_sales" to ("Customers & Sales Prospects" to "Prospect tracking, negotiation follow-up history, and garment customer contacts."),
        "sampling_order" to ("Pattern & Sampling Order" to "Sampling work orders for garment prototypes, initial cutting patterns, and sample approval."),
        "inventory" to ("Raw Materials & Fabric Stock" to "Fabric roll receiving, stock of yarn, buttons, zippers, and multi-unit (Yard/Kg/Pcs) handling."),
        "tech_pack_bom" to ("BOM Specification & Tech Pack" to "Stitching specification sheets, Bill of Materials (BOM), and size guides."),
        "costing_hpp" to ("COGS & Cost Calculation" to "Automatic costing: raw materials + sewing cost per minute + finishing & confidential profit margin."),
        "production_mrp" to ("Machine Schedule & Mass Work Orders" to "Queue allocation for 10 sewing machines, daily working-hour targets, and issuing cutting/sewing work orders."),
        "operator_exec" to ("Operator Work Log" to "Compact sewing operator interface for logging cutting output, sewing output, and daily progress."),
        "quality_control" to ("QC Inspection & Defects" to "Recording defective garments (reject/scrap), printing barcode labels for passed inspection, and grading."),
        "fulfillment" to ("Packing & Delivery Note" to "Steam-press finishing, per-carton quantity verification, and printing the courier Delivery Note."),
        "invoicing" to ("Invoicing & Billing" to "Issuing billing invoices, down-payment (DP) terms, and settlement.")
    )

    /** Kode aksi (`ModuleActionCode.name`) → label. */
    val actions: Map<String, String> = mapOf(
        "ADD" to "Add Order",
        "EDIT" to "Log Progress",
        "APPROVE" to "Approve Work Order",
        "DELETE" to "Delete Record"
    )

    /** Kunci kosakata (`VocabularyKey.name`) → istilah. */
    val vocabulary: Map<String, String> = mapOf(
        "WORKPLACE" to "factory",
        "DOCUMENT" to "Document"
    )
}
