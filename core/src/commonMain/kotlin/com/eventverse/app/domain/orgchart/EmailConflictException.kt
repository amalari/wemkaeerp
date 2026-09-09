package com.eventverse.app.domain.orgchart

/**
 * Domain Exception yang dilempar ketika pendaftaran atau perubahan email karyawan
 * berbenturan dengan email yang sudah ada di tenant tersebut (baik aktif maupun diarsipkan).
 */
class EmailConflictException(
    val email: String,
    val existingEmployeeId: String,
    val existingEmployeeName: String,
    val existingDepartmentName: String,
    val existingRoleTitle: String,
    val isArchived: Boolean,
    message: String = if (isArchived) {
        "Email '$email' sudah terdaftar pada karyawan $existingEmployeeName (Divisi $existingDepartmentName, Status: Diarsipkan). Silakan pulihkan karyawan tersebut atau gunakan email lain."
    } else {
        "Email '$email' sudah digunakan oleh karyawan $existingEmployeeName (Divisi $existingDepartmentName, Status: Aktif). Silakan gunakan email lain."
    }
) : IllegalArgumentException(message)
