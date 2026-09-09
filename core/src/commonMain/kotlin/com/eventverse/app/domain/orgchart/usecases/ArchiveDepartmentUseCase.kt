package com.eventverse.app.domain.orgchart.usecases

import com.eventverse.app.domain.orgchart.DepartmentId
import com.eventverse.app.domain.orgchart.DepartmentRepository
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * Arsipkan divisi tanpa menghapus data dari database.
 *
 * Mengikuti pola Odoo: set archived_at = NOW() alih-alih DELETE.
 * Diblokir jika masih ada karyawan AKTIF di divisi tersebut.
 */
class ArchiveDepartmentUseCase(
    private val departmentRepository: DepartmentRepository,
    private val employeeRepository: EmployeeRepository? = null
) {
    suspend operator fun invoke(tenantId: TenantId, id: DepartmentId): Result<Unit> = runCatching {
        val existing = departmentRepository.findById(tenantId, id)
            ?: error("Divisi dengan ID '${id.value}' tidak ditemukan")

        // Blokir jika masih ada karyawan aktif di divisi ini
        if (employeeRepository != null) {
            val activeEmployees = employeeRepository.findByDepartment(tenantId, id)
            require(activeEmployees.isEmpty()) {
                "Tidak dapat mengarsipkan divisi '${existing.displayName}' karena masih memiliki ${activeEmployees.size} karyawan aktif. Pindahkan atau arsipkan karyawan terlebih dahulu."
            }
        }

        departmentRepository.archive(tenantId, id).getOrThrow()
    }
}

/**
 * Pulihkan divisi yang sebelumnya diarsipkan kembali ke status aktif.
 */
class RestoreDepartmentUseCase(
    private val departmentRepository: DepartmentRepository
) {
    suspend operator fun invoke(tenantId: TenantId, id: DepartmentId): Result<Unit> = runCatching {
        departmentRepository.findById(tenantId, id)
            ?: error("Divisi dengan ID '${id.value}' tidak ditemukan")
        departmentRepository.restore(tenantId, id).getOrThrow()
    }
}
