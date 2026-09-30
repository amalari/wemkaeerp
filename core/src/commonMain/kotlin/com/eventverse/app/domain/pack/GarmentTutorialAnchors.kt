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

    val all: Set<TutorialAnchorId> = setOf(CRM_DIRECTORY_TABS, CRM_KPI_ROW, CRM_SEARCH, CRM_ADD_LEAD, CRM_KANBAN_COLUMNS)
}
