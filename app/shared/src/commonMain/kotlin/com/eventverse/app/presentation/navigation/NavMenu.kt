package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.ModuleCategory
import com.eventverse.app.presentation.workspace.badgeLabel

/** Judul seksi menu administrasi. Diletakkan paling atas, di atas seluruh seksi kategori modul. */
const val SYSTEM_SECTION_TITLE: String = "SISTEM & STRUKTUR"

/** Badge untuk item administrasi yang sedang diperlihatkan-tapi-terkunci oleh mode audit. */
private const val ADMIN_LOCKED_BADGE = "Admin"

/**
 * Satu baris menu, sebagai data murni — tanpa lambda dan tanpa tipe Compose.
 *
 * Pemisahan ini disengaja: penyusunan menu adalah keputusan wewenang, dan keputusan wewenang harus
 * bisa diuji tanpa merender apa pun. `App.kt` yang menerjemahkannya menjadi `ClayNavItem`.
 */
data class NavMenuEntry(
    val screen: AppNavScreen,
    /** Null untuk layar administrasi yang tidak dijaga matriks RBAC. */
    val accessLevel: AccessLevel? = null,
    val badge: String? = null,
    /** True berarti baris ditampilkan teredam dengan ikon gembok dan tidak dapat diklik. */
    val locked: Boolean = false
)

/** Satu kelompok menu di bawah satu section header. Tidak pernah kosong — lihat [buildNavMenu]. */
data class NavMenuSection(
    val title: String,
    val entries: List<NavMenuEntry>
)

/**
 * Menyusun isi drawer dari wewenang efektif, bukan dari daftar statis.
 *
 * Tiga aturan yang dikandungnya:
 *
 * 1. **Modul tanpa akses dihilangkan, bukan diredupkan.** Staf gudang tidak perlu tahu ada layar
 *    HPP. Mode audit membalikkannya, karena saat menguji konfigurasi, menu yang hilang dan menu
 *    yang tak pernah ada terlihat sama.
 * 2. **Seksi kategori yang seluruh modulnya tersaring ikut hilang.** Header tanpa isi lebih buruk
 *    daripada tidak ada header — ia menjanjikan sesuatu yang tidak ada.
 * 3. **Saat menyamar sebagai sebuah jabatan, menu administrasi ikut disembunyikan.** Kalau tidak,
 *    tampilannya bukan tampilan jabatan itu, melainkan tampilan jabatan itu plus hak admin. Aman
 *    disembunyikan karena switcher persona hidup di top bar: penguji selalu bisa kembali menjadi
 *    dirinya sendiri.
 *
 * Kategori dan urutan modul dibaca dari [BusinessModule.category] — bukan didaftar ulang di sini,
 * supaya modul ke-sepuluh tidak diam-diam tertelan.
 */
fun buildNavMenu(
    permissions: Map<BusinessModule, ModuleAccessConfig>,
    auditView: Boolean,
    isImpersonating: Boolean
): List<NavMenuSection> {
    val sections = mutableListOf<NavMenuSection>()

    val adminEntries = when {
        !isImpersonating -> ADMIN_SCREENS.map { NavMenuEntry(screen = it) }
        auditView -> ADMIN_SCREENS.map {
            NavMenuEntry(screen = it, badge = ADMIN_LOCKED_BADGE, locked = true)
        }
        else -> emptyList()
    }
    if (adminEntries.isNotEmpty()) {
        sections += NavMenuSection(title = SYSTEM_SECTION_TITLE, entries = adminEntries)
    }

    val screensByModule = AppNavScreen.entries.mapNotNull { screen ->
        screen.businessModule?.let { it to screen }
    }.toMap()

    ModuleCategory.entries.forEach { category ->
        val entries = BusinessModule.entries
            .filter { it.category == category }
            .mapNotNull { module ->
                val screen = screensByModule[module] ?: return@mapNotNull null
                val access = permissions[module] ?: ModuleAccessConfig()
                if (!access.isAccessible && !auditView) return@mapNotNull null

                NavMenuEntry(
                    screen = screen,
                    accessLevel = access.level,
                    badge = access.level.badgeLabel(),
                    locked = !access.isAccessible
                )
            }

        if (entries.isNotEmpty()) {
            sections += NavMenuSection(title = category.displayName, entries = entries)
        }
    }

    return sections
}

/** Layar tata kelola: tidak dijaga matriks RBAC karena justru dipakai untuk memperbaikinya. */
private val ADMIN_SCREENS = listOf(
    AppNavScreen.ORG_CHART,
    AppNavScreen.DYNAMIC_RBAC,
    AppNavScreen.FACTORY_FLOW
)
