package com.eventverse.app.domain.vendor

import com.eventverse.app.domain.tenant.TenantId

interface VendorRepository {
    suspend fun findById(tenantId: TenantId, id: VendorId): Vendor?

    /** Buku kontak vendor tenant; vendor nonaktif hanya ikut bila diminta. */
    suspend fun listContacts(tenantId: TenantId, includeInactive: Boolean = false): List<Vendor>
    suspend fun save(vendor: Vendor): Vendor
}
