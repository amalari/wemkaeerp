package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId

/**
 * Arsipkan karyawan tanpa menghapus data dari database.
 *
 * Mengikuti pola Odoo: set archived_at = NOW() alih-alih DELETE.
 * Data historis (penggajian, absensi) tetap valid karena referensi ID masih ada.
 *
 * Subordinat langsung di-reparent ke atasan karyawan yang diarsipkan,
 * sama seperti perilaku hard-delete sebelumnya.
 */
class ArchiveEmployeeUseCase(
    private val employeeRepository: EmployeeRepository
) {
    suspend operator fun invoke(tenantId: TenantId, id: OrgNodeId): Result<Unit> = runCatching {
        val existing = employeeRepository.findById(tenantId, id)
            ?: error("Karyawan dengan ID '${id.value}' tidak ditemukan")

        // Reparent subordinat ke atasan karyawan yang diarsipkan
        val allEmployees = employeeRepository.findAllByTenant(tenantId)
        val subordinates = allEmployees.filter { it.reportsToId == id }
        if (subordinates.isNotEmpty()) {
            val remapped = subordinates.map { it.copy(reportsToId = existing.reportsToId) }
            employeeRepository.saveAll(tenantId, remapped).getOrThrow()
        }

        employeeRepository.archive(tenantId, id).getOrThrow()
    }
}

/**
 * Pulihkan karyawan yang sebelumnya diarsipkan kembali ke status aktif.
 */
class RestoreEmployeeUseCase(
    private val employeeRepository: EmployeeRepository
) {
    suspend operator fun invoke(tenantId: TenantId, id: OrgNodeId): Result<Unit> = runCatching {
        employeeRepository.findById(tenantId, id)
            ?: error("Karyawan dengan ID '${id.value}' tidak ditemukan")
        employeeRepository.restore(tenantId, id).getOrThrow()
    }
}
