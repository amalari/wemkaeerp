package com.eventverse.app.presentation.rbac

import com.eventverse.app.domain.rbac.CustomRole

/**
 * Isi tab "Per Jabatan": tiga keadaan yang TIDAK boleh dicampur. Tenant yang benar-benar tanpa jabatan
 * ([NoRoles], tindakannya "Buat jabatan") berbeda dari pencarian yang tak cocok ([NoMatch], tindakannya
 * mengubah kata kunci). Murni, diuji tanpa Compose.
 */
internal sealed interface RbacRoleListState {
    data object NoRoles : RbacRoleListState
    data object NoMatch : RbacRoleListState
    data class Showing(val roles: List<CustomRole>) : RbacRoleListState

    companion object {
        fun of(roles: List<CustomRole>, query: String): RbacRoleListState {
            if (roles.isEmpty()) return NoRoles
            val shown = roles.filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }
            return if (shown.isEmpty()) NoMatch else Showing(shown)
        }
    }
}
