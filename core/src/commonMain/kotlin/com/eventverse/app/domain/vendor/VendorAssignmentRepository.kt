package com.eventverse.app.domain.vendor

import com.eventverse.app.domain.tenant.TenantId

interface VendorAssignmentRepository {
    suspend fun findById(tenantId: TenantId, id: VendorAssignmentId): VendorAssignment?

    /** Penugasan yang masih berlaku di seluruh tenant — bahan antrean admin produksi. */
    suspend fun findActive(tenantId: TenantId): List<VendorAssignment>

    /** Penugasan (aktif maupun batal) sebuah vendor — riwayat order di kartu kontaknya. */
    suspend fun findByVendor(tenantId: TenantId, vendorId: VendorId): List<VendorAssignment>
    suspend fun save(assignment: VendorAssignment): VendorAssignment
}
