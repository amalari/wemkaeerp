package com.eventverse.app.domain.vendor.usecases

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.SubcontractFlowGateway
import com.eventverse.app.domain.vendor.VendorAssignment
import com.eventverse.app.domain.vendor.VendorAssignmentId
import com.eventverse.app.domain.vendor.VendorAssignmentRepository
import com.eventverse.app.domain.vendor.VendorId
import com.eventverse.app.domain.vendor.VendorPriceSource
import com.eventverse.app.domain.vendor.VendorPriceUnit
import com.eventverse.app.domain.vendor.VendorRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

data class AssignVendorToProcessCommand(
    val tenantId: TenantId,
    val subjectId: String,
    val processCode: String,
    val vendorId: VendorId,
    /** `null` = ambil dari daftar harga vendor yang berlaku pada [today]. */
    val pricePerUnitIdr: Long? = null,
    /** `null` = satuan dari daftar harga; wajib diisi bila harga diisi manual tanpa daftar harga. */
    val unit: VendorPriceUnit? = null,
    val unitsPerPiece: Int = 1,
    /** `null` = jumlah pcs order apa adanya. */
    val quantityPcs: Int? = null,
    val expectedReturnAt: LocalDate? = null,
    val notes: String = "",
    val assignedByUserId: String = "",
    val today: LocalDate,
    val idGenerator: () -> String = { "vas-${Clock.System.now().toEpochMilliseconds()}" }
)

/**
 * Admin produksi menunjuk vendor untuk satu proses subkon.
 *
 * Urutan yang dijaga: (1) prosesnya memang menunggu vendor, (2) barang belum keluar pabrik,
 * (3) vendornya aktif, (4) harga ditentukan — dari daftar harga atau hasil nego, dan asalnya
 * dicatat, (5) penugasan lama pada proses yang sama dibatalkan, (6) vendor ditulis ke alur
 * sehingga leg Surat Jalan ke vendor terbuka.
 */
class AssignVendorToProcessUseCase(
    private val vendorRepository: VendorRepository,
    private val assignmentRepository: VendorAssignmentRepository,
    private val flowGateway: SubcontractFlowGateway
) {
    suspend operator fun invoke(command: AssignVendorToProcessCommand): Result<VendorAssignment> = runCatching {
        val need = flowGateway.findNeed(command.tenantId, command.subjectId, command.processCode)
            ?: error("Proses ${command.processCode} pada order ini tidak ditandai Vendor Luar")

        check(!flowGateway.isDispatched(command.tenantId, need.subjectId, need.processCode)) {
            "Surat Jalan ke vendor untuk ${need.processName} sudah terbit. " +
                "Proses retur lebih dulu sebelum mengganti vendor."
        }

        val vendor = vendorRepository.findById(command.tenantId, command.vendorId)
            ?: error("Vendor '${command.vendorId.value}' tidak ditemukan")
        check(vendor.isActive) { "Vendor ${vendor.name.value} sudah dinonaktifkan" }

        val listed = vendor.ratesFor(need.processCode, command.today)
            .firstOrNull { command.unit == null || it.unit == command.unit }

        val unit = command.unit ?: listed?.unit
            ?: error("${vendor.name.value} belum punya harga untuk ${need.processName}; isi harga dan satuan manual")
        val price = command.pricePerUnitIdr ?: listed?.priceIdr
            ?: error("${vendor.name.value} belum punya harga ${unit.displayName} untuk ${need.processName}")
        val source = if (listed != null && listed.unit == unit && listed.priceIdr == price) {
            VendorPriceSource.PRICE_LIST
        } else {
            VendorPriceSource.NEGOTIATED
        }

        val now = Clock.System.now()
        assignmentRepository.findActive(command.tenantId)
            .filter { it.covers(need.subjectId, need.processCode) }
            .forEach { assignmentRepository.save(it.cancel(now)) }

        val assignment = assignmentRepository.save(
            VendorAssignment(
                id = VendorAssignmentId(command.idGenerator()),
                tenantId = command.tenantId,
                subjectId = need.subjectId,
                subjectLabel = need.subjectLabel,
                processCode = need.processCode,
                processName = need.processName,
                vendorId = vendor.id,
                vendorName = vendor.name,
                vendorPhone = vendor.phone,
                pricePerUnitIdr = price,
                unit = unit,
                quantityPcs = command.quantityPcs ?: need.quantityPcs,
                unitsPerPiece = command.unitsPerPiece,
                priceSource = source,
                expectedReturnAt = command.expectedReturnAt,
                notes = command.notes.trim(),
                assignedByUserId = command.assignedByUserId,
                assignedAt = now
            )
        )

        flowGateway.linkVendor(command.tenantId, need.subjectId, need.processCode, vendor.name.value)
        assignment
    }
}
