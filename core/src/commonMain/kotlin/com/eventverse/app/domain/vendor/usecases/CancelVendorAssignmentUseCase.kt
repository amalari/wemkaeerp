package com.eventverse.app.domain.vendor.usecases

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.SubcontractFlowGateway
import com.eventverse.app.domain.vendor.VendorAssignment
import com.eventverse.app.domain.vendor.VendorAssignmentId
import com.eventverse.app.domain.vendor.VendorAssignmentRepository
import kotlinx.datetime.Clock

data class CancelVendorAssignmentCommand(
    val tenantId: TenantId,
    val assignmentId: VendorAssignmentId
)

/**
 * Membatalkan penunjukan vendor sebelum barang dikirim. Prosesnya kembali ke antrean
 * "Menunggu Vendor" karena `vendorRef` di alur ikut dikosongkan.
 */
class CancelVendorAssignmentUseCase(
    private val assignmentRepository: VendorAssignmentRepository,
    private val flowGateway: SubcontractFlowGateway
) {
    suspend operator fun invoke(command: CancelVendorAssignmentCommand): Result<VendorAssignment> = runCatching {
        val assignment = assignmentRepository.findById(command.tenantId, command.assignmentId)
            ?: error("Penugasan vendor '${command.assignmentId.value}' tidak ditemukan")

        check(!flowGateway.isDispatched(command.tenantId, assignment.subjectId, assignment.processCode)) {
            "Surat Jalan ke ${assignment.vendorName.value} sudah terbit; batalkan dokumennya lebih dulu"
        }

        val cancelled = assignmentRepository.save(assignment.cancel(Clock.System.now()))
        flowGateway.linkVendor(command.tenantId, assignment.subjectId, assignment.processCode, null)
        cancelled
    }
}
