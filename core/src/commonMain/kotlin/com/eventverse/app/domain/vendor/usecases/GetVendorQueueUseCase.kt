package com.eventverse.app.domain.vendor.usecases

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.SubcontractFlowGateway
import com.eventverse.app.domain.vendor.VendorAssignmentQueue
import com.eventverse.app.domain.vendor.VendorAssignmentRepository
import com.eventverse.app.domain.vendor.VendorQueueItem

/** Antrean admin produksi: proses Vendor Luar yang menunggu vendor, lalu yang sudah ditugaskan. */
class GetVendorQueueUseCase(
    private val assignmentRepository: VendorAssignmentRepository,
    private val flowGateway: SubcontractFlowGateway
) {
    suspend operator fun invoke(tenantId: TenantId): Result<List<VendorQueueItem>> = runCatching {
        VendorAssignmentQueue.build(
            needs = flowGateway.openNeeds(tenantId),
            activeAssignments = assignmentRepository.findActive(tenantId)
        )
    }
}
