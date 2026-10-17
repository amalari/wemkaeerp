package com.eventverse.app.domain.pack

import com.eventverse.app.domain.tutorial.TutorialAnchorId

/**
 * Titik sorot coach mark di layar modul pack garment. UI memasang `Modifier.tutorialAnchor(...)` hanya dengan
 * konstanta ini; `TutorialCatalogTest` memastikan setiap anchor yang dirujuk tutorial terdaftar di [all].
 */
object GarmentTutorialAnchors {
    val CRM_DIRECTORY_TABS = TutorialAnchorId("crm.directory_tabs")
    val CRM_KPI_ROW = TutorialAnchorId("crm.kpi_row")
    val CRM_SEARCH = TutorialAnchorId("crm.search")
    val CRM_ADD_LEAD = TutorialAnchorId("crm.add_lead")
    val CRM_KANBAN_COLUMNS = TutorialAnchorId("crm.kanban_columns")

    val SAMPLING_HEADER = TutorialAnchorId("sampling.header")
    val SAMPLING_BOARD = TutorialAnchorId("sampling.board")
    val SAMPLING_FLOW_TEMPLATE = TutorialAnchorId("sampling.flow_template")

    val COSTING_NEW_SHEET = TutorialAnchorId("costing.new_sheet")
    val COSTING_SEARCH = TutorialAnchorId("costing.search")
    /** Hanya ada di tata letak desktop; di mobile langkahnya jatuh ke callout tengah. */
    val COSTING_TABS = TutorialAnchorId("costing.tabs")

    // Factory Flow
    val FACTORY_FLOW_CANVAS = TutorialAnchorId("factory_flow.canvas")
    val FACTORY_FLOW_HEADER = TutorialAnchorId("factory_flow.header")

    // Master Data
    val MASTER_DATA_CATEGORIES = TutorialAnchorId("master_data.categories")
    val MASTER_DATA_SEARCH = TutorialAnchorId("master_data.search")

    // Vendor Contacts
    val VENDOR_LIST = TutorialAnchorId("vendor.list")
    val VENDOR_ADD = TutorialAnchorId("vendor.add")

    // Inventory
    val INVENTORY_SEARCH = TutorialAnchorId("inventory.search")
    val INVENTORY_TABLE = TutorialAnchorId("inventory.table")

    // Tech Pack BOM
    val TECH_PACK_BOM_ROWS = TutorialAnchorId("techpack.bom_rows")
    val TECH_PACK_BOM_ADD = TutorialAnchorId("techpack.bom_add")

    // Production MRP
    val MRP_SCHEDULE = TutorialAnchorId("mrp.schedule")
    val MRP_BOARD = TutorialAnchorId("mrp.board")

    // Operator
    val OPERATOR_TASK_LIST = TutorialAnchorId("operator.task_list")
    val OPERATOR_START_STOP = TutorialAnchorId("operator.start_stop")

    // Quality Control
    val QC_INSPECT = TutorialAnchorId("qc.inspect")
    val QC_GRADE = TutorialAnchorId("qc.grade")

    // Fulfillment
    val FULFILLMENT_PACK_LIST = TutorialAnchorId("fulfillment.pack_list")
    val FULFILLMENT_SURAT_JALAN = TutorialAnchorId("fulfillment.surat_jalan")

    // Invoicing
    val INVOICE_LIST = TutorialAnchorId("invoice.list")
    val INVOICE_NEW = TutorialAnchorId("invoice.new")

    val all: Set<TutorialAnchorId> = setOf(
        CRM_DIRECTORY_TABS, CRM_KPI_ROW, CRM_SEARCH, CRM_ADD_LEAD, CRM_KANBAN_COLUMNS,
        SAMPLING_HEADER, SAMPLING_BOARD, SAMPLING_FLOW_TEMPLATE,
        COSTING_NEW_SHEET, COSTING_SEARCH, COSTING_TABS,
        FACTORY_FLOW_CANVAS, FACTORY_FLOW_HEADER,
        MASTER_DATA_CATEGORIES, MASTER_DATA_SEARCH,
        VENDOR_LIST, VENDOR_ADD,
        INVENTORY_SEARCH, INVENTORY_TABLE,
        TECH_PACK_BOM_ROWS, TECH_PACK_BOM_ADD,
        MRP_SCHEDULE, MRP_BOARD,
        OPERATOR_TASK_LIST, OPERATOR_START_STOP,
        QC_INSPECT, QC_GRADE,
        FULFILLMENT_PACK_LIST, FULFILLMENT_SURAT_JALAN,
        INVOICE_LIST, INVOICE_NEW,
    )
}
