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

    val all: Set<TutorialAnchorId> = setOf(
        CRM_DIRECTORY_TABS, CRM_KPI_ROW, CRM_SEARCH, CRM_ADD_LEAD, CRM_KANBAN_COLUMNS,
        SAMPLING_HEADER, SAMPLING_BOARD, SAMPLING_FLOW_TEMPLATE,
        COSTING_NEW_SHEET, COSTING_SEARCH, COSTING_TABS,
    )
}
