package com.eventverse.app.infrastructure

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.Vendor
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorName
import com.eventverse.app.domain.vendor.VendorRepository
import com.eventverse.app.infrastructure.tables.VendorsTable
import com.eventverse.app.shared.vendor.VendorCodec
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** Kontak vendor per tenant (tabel V64). Daftar harga disimpan sebagai satu kolom JSON. */
class PostgresVendorRepository : VendorRepository {

    override suspend fun findById(tenantId: TenantId, id: VendorId): Vendor? =
        DatabaseFactory.dbQuery(tenantId) {
            VendorsTable.selectAll()
                .where { (VendorsTable.tenantId eq tenantId.value) and (VendorsTable.id eq id.value) }
                .singleOrNull()
                ?.let(::hydrate)
        }

    override suspend fun listContacts(tenantId: TenantId, includeInactive: Boolean): List<Vendor> =
        DatabaseFactory.dbQuery(tenantId) {
            VendorsTable.selectAll()
                .where {
                    val ofTenant = VendorsTable.tenantId eq tenantId.value
                    if (includeInactive) ofTenant else ofTenant and (VendorsTable.isActive eq true)
                }
                .orderBy(VendorsTable.name to SortOrder.ASC)
                .map(::hydrate)
        }

    override suspend fun save(vendor: Vendor): Vendor = DatabaseFactory.dbQuery(vendor.tenantId) {
        val exists = VendorsTable.selectAll()
            .where { (VendorsTable.tenantId eq vendor.tenantId.value) and (VendorsTable.id eq vendor.id.value) }
            .any()
        val ratesJson = VendorCodec.encodeRates(vendor.rates).encode()

        if (exists) {
            VendorsTable.update({ (VendorsTable.tenantId eq vendor.tenantId.value) and (VendorsTable.id eq vendor.id.value) }) {
                it[name] = vendor.name.value
                it[phone] = vendor.phone
                it[address] = vendor.address
                it[notes] = vendor.notes
                it[rates] = ratesJson
                it[isActive] = vendor.isActive
                it[updatedAt] = vendor.updatedAt
            }
        } else {
            VendorsTable.insert {
                it[id] = vendor.id.value
                it[tenantId] = vendor.tenantId.value
                it[name] = vendor.name.value
                it[phone] = vendor.phone
                it[address] = vendor.address
                it[notes] = vendor.notes
                it[rates] = ratesJson
                it[isActive] = vendor.isActive
                it[createdAt] = vendor.createdAt
                it[updatedAt] = vendor.updatedAt
            }
        }
        vendor
    }

    private fun hydrate(row: ResultRow): Vendor = Vendor(
        id = VendorId(row[VendorsTable.id]),
        tenantId = TenantId(row[VendorsTable.tenantId]),
        name = VendorName(row[VendorsTable.name]),
        phone = row[VendorsTable.phone],
        address = row[VendorsTable.address],
        notes = row[VendorsTable.notes],
        rates = VendorCodec.decodeRates(row[VendorsTable.rates]),
        isActive = row[VendorsTable.isActive],
        createdAt = row[VendorsTable.createdAt],
        updatedAt = row[VendorsTable.updatedAt]
    )
}
