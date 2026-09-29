package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table

object TenantsTable : Table("tenants") {
    val id = varchar("id", 64)
    val slug = varchar("slug", 30).uniqueIndex()
    val name = varchar("name", 100)
    val status = varchar("status", 20).default("TRIAL")
    val tier = varchar("tier", 20).default("PRO")
    val activeMachineCount = integer("active_machine_count").default(0)

    /**
     * The tenant's garment business model (FOB / CMT / Brand D2C). Drives which pipeline
     * preset a tenant is provisioned with, so it has to be persisted rather than defaulted.
     */
    val businessPreset = varchar("business_preset", 50).default("fob_full_package")
    val industryTemplate = varchar("industry_template", 32).default("KNIT_SWEATER")

    /** Vertikal tenant (V75, B7). */
    val domainPack = varchar("domain_pack", 64).default("garment")

    override val primaryKey = PrimaryKey(id)
}
