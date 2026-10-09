package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.orgchart.Department
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.DepartmentModuleAssignment

/**
 * Keadaan pemuatan layar Hak Akses (TRD-PLAT-010 K1/K5). Empat keadaan yang berbeda dan TIDAK dicampur:
 * [Loading], [Empty] (server menjawab sukses, tenant belum punya jabatan maupun divisi), [Loaded], [Failed].
 *
 * Tidak ada preset lokal: jabatan datang dari server, yang sudah memutuskan per pack. Galat tidak pernah
 * diubah menjadi data contoh.
 */
sealed interface RbacLoadState {
    data object Loading : RbacLoadState
    data object Empty : RbacLoadState
    data object Loaded : RbacLoadState
    data class Failed(val message: String) : RbacLoadState

    companion object {
        /** Salah satu sumber gagal = [Failed] (bukan setengah data); semuanya kosong = [Empty]. */
        fun from(
            roles: Result<List<CustomRole>>,
            departments: Result<List<Department>>,
            assignments: Result<Map<BusinessModule, List<DepartmentModuleAssignment>>>
        ): RbacLoadState {
            val r = roles.getOrElse { return Failed(message(it)) }
            val d = departments.getOrElse { return Failed(message(it)) }
            assignments.getOrElse { return Failed(message(it)) }
            return if (r.isEmpty() && d.isEmpty()) Empty else Loaded
        }

        private fun message(cause: Throwable): String =
            cause.message?.takeIf { it.isNotBlank() } ?: "Gagal memuat hak akses."
    }
}
