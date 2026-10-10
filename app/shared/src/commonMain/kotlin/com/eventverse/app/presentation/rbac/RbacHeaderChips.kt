package com.eventverse.app.presentation.rbac

/** Satu chip statistik di kepala layar Hak Akses. Buta UI: hanya label dan angka. */
data class RbacHeaderChip(val label: String, val value: Int)

/**
 * Chip statistik mana yang boleh tampil per keadaan pemuatan. Jumlah yang belum diketahui tidak boleh
 * tampil sebagai 0: saat [RbacLoadState.Loading] atau [RbacLoadState.Failed] belum ada angka nyata,
 * jadi seluruh chip disembunyikan (bukan "0 Jabatan" yang menyesatkan).
 */
object RbacHeaderChips {
    fun forState(loadState: RbacLoadState, totalRoles: Int, totalModules: Int, totalUsers: Int?): List<RbacHeaderChip> =
        when (loadState) {
            RbacLoadState.Loading, is RbacLoadState.Failed -> emptyList()
            RbacLoadState.Empty, RbacLoadState.Loaded -> buildList {
                add(RbacHeaderChip("Jabatan", totalRoles))
                add(RbacHeaderChip("Modul SaaS", totalModules))
                // Hanya angka nyata dari server; tak terbaca = disembunyikan.
                if (totalUsers != null) add(RbacHeaderChip("Total Karyawan", totalUsers))
            }
        }
}
