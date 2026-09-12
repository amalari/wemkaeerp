package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val id = varchar("id", 64)
    val tenantId = varchar("tenant_id", 64).references(TenantsTable.id).nullable()
    val username = varchar("username", 50)
    val email = varchar("email", 150).uniqueIndex()
    val role = varchar("role", 50)
    val isActive = bool("is_active").default(true)

    /**
     * Divisi dan jabatan yang dikonfigurasi tenant.
     *
     * [role] di atas adalah enum tetap milik platform dan terlalu kasar untuk menentukan isi
     * layar. Dua kolom inilah yang dibaca ulang oleh `GET /me`, supaya identitas bertahan
     * setelah halaman di-reload alih-alih diturunkan kembali dari claim kasar.
     */
    val departmentId = varchar("department_id", 64).references(DepartmentsTable.id).nullable()
    val customRoleId = varchar("custom_role_id", 64).references(CustomRolesTable.id).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_tenant_username", tenantId, username)
    }
}
