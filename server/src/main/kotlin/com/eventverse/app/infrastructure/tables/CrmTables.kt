package com.eventverse.app.infrastructure.tables

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * CRM leads — `GarmentModules.CRM_SALES`. See V21 for the full design rationale (why a
 * hybrid core-entity table rather than a fully generic board, and why this is NOT the same
 * "leads" as `ProspectLeadsTable`).
 */
object CrmLeadsTable : Table("crm_leads") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)

    val brandName = varchar("brand_name", 150).default("")
    val contactPerson = varchar("contact_person", 150).default("")
    val whatsappNumber = varchar("whatsapp_number", 20).default("")
    val email = varchar("email", 150).default("")

    val stage = varchar("stage", 30).default("NEW_LEAD")
    val leadSource = varchar("source", 50).default("")

    val estimatedPcs = integer("estimated_pcs").nullable()
    val estimatedValueIdr = long("estimated_value_idr").nullable()

    val ownerEmployeeId = varchar("owner_employee_id", 64).references(EmployeesTable.id).nullable()
    val expectedCloseDate = date("expected_close_date").nullable()

    val customAttributes = jsonbText("custom_attributes").default("{}")

    val productCategory = varchar("product_category", 100).default("")
    val lastContactedAt = timestamp("last_contacted_at").nullable()

    val createdByUserId = varchar("created_by_user_id", 64).references(UsersTable.id).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val archivedAt = timestamp("archived_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object CrmLeadActivitiesTable : Table("crm_lead_activities") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
    val leadId = varchar("lead_id", 64).references(CrmLeadsTable.id)
    val authorEmployeeId = varchar("author_employee_id", 64).references(EmployeesTable.id).nullable()
    val authorName = varchar("author_name", 150).default("Sales")
    val content = text("content")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
