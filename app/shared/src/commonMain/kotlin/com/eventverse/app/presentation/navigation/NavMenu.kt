package com.eventverse.app.presentation.navigation

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.rbac.BusinessModules

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.presentation.workspace.badgeLabel

/**
 * Satu baris menu, sebagai data murni — tanpa lambda dan tanpa tipe Compose.
 *
 * Pemisahan ini disengaja: penyusunan menu adalah keputusan wewenang, dan keputusan wewenang harus
 * bisa diuji tanpa merender apa pun. `App.kt` yang menerjemahkannya menjadi `ClayNavItem`.
 */
data class NavMenuEntry(
    val screen: AppNavScreen,
    val accessLevel: AccessLevel? = null,
    val badge: String? = null,
    /** True berarti baris ditampilkan teredam dengan ikon gembok dan tidak dapat diklik. */
    val locked: Boolean = false,
    /** Modul yang menggerbangi baris ini (ikon, wewenang). */
    val module: BusinessModule? = screen.businessModule,
    /** Path tujuan. Modul tanpa layar khusus memakai rute generik `/m/{code}` (B6f). */
    val route: String = screen.route,
    val title: String = screen.title
)

/** Satu kelompok menu di bawah satu section header. Tidak pernah kosong — lihat [buildNavMenu]. */
data class NavMenuSection(
    val title: String,
    val entries: List<NavMenuEntry>
)

/**
 * Menyusun isi drawer dari wewenang efektif, bukan dari daftar statis.
 *
 * Dua aturan yang dikandungnya:
 *
 * 1. **Modul tanpa akses dihilangkan, bukan diredupkan.** Staf gudang tidak perlu tahu ada layar
 *    HPP. Mode audit membalikkannya, karena saat menguji konfigurasi, menu yang hilang dan menu
 *    yang tak pernah ada terlihat sama.
 * 2. **Seksi kategori yang seluruh modulnya tersaring ikut hilang.** Header tanpa isi lebih buruk
 *    daripada tidak ada header — ia menjanjikan sesuatu yang tidak ada.
 *
 * Seksi dan urutan modul dibaca dari Domain Pack (`pack.sections`, `pack.modules`) — bukan didaftar ulang di sini,
 * supaya modul berikutnya tidak diam-diam tertelan. Seksi "Sistem & Struktur" pun lahir dari
 * [ModuleCategory.GOVERNANCE] seperti seksi lainnya; sebelumnya ia berupa daftar layar yang ditulis
 * tangan di file ini dan karenanya tidak pernah tunduk pada wewenang siapa pun.
 *
 * Entitlement tenant **tidak** diperiksa di sini. Modul yang tidak disambungkan ke tenant sudah tiba
 * sebagai `AccessLevel.NONE` dari `AccessDecisionEngine`, sehingga tersaring oleh aturan 1 di atas.
 * Memeriksanya dua kali berarti dua tempat yang bisa menyimpang.
 */
fun buildNavMenu(
    permissions: Map<BusinessModule, ModuleAccessConfig>,
    auditView: Boolean
): List<NavMenuSection> {
    val sections = mutableListOf<NavMenuSection>()

    // Satu modul boleh menggerbangi beberapa layar (OPERATOR_EXEC → Lantai Produksi & Telusur);
    // `toMap()` dulu diam-diam membuang semua kecuali layar terakhir.
    val screensByModule = AppNavScreen.entries
        .filter { it.isNavMenuItem && it.businessModule != null }
        .groupBy { requireNotNull(it.businessModule) }

    // B6f: seksi & urutan dari Domain Pack, bukan enum. Modul tanpa layar khusus tetap muncul lewat `/m/{code}`.
    val pack = DomainPackRegistry.soleActivePack
    pack.sections.sortedBy { it.order }.forEach { section ->
        val entries = pack.modules
            .filter { it.section == section.code }
            .map { it.id }
            .flatMap { module ->
                val access = permissions[module] ?: ModuleAccessConfig()
                if (!access.isAccessible && !auditView) return@flatMap emptyList()
                val base = NavMenuEntry(
                    screen = AppNavScreen.MODULE,
                    accessLevel = access.level,
                    badge = access.level.badgeLabel(),
                    locked = !access.isAccessible,
                    module = module,
                    route = "${AppNavScreen.MODULE.route}/${module.code}",
                    title = module.displayName
                )
                screensByModule[module]?.map { screen -> base.copy(screen = screen, route = screen.route, title = screen.title) }
                    ?: listOf(base)
            }

        if (entries.isNotEmpty()) {
            sections += NavMenuSection(title = section.displayName, entries = entries)
        }
    }

    return sections
}

/**
 * Layar pertama yang benar-benar boleh dibuka pengguna ini.
 *
 * Dipakai sebagai tujuan setelah login. Sebelum ketiga layar tata kelola menjadi modul, tujuan itu
 * boleh berupa konstanta karena Bagan Organisasi selalu terbuka untuk semua orang; kini ia bisa
 * tertutup, dan mendaratkan orang di halaman "akses ditolak" tepat setelah login adalah cara buruk
 * menyambut mereka.
 *
 * Mengembalikan null bila tidak ada satu pun menu terbuka — keadaan yang sah (misalnya seluruh modul
 * dicabut dari tenant) dan harus ditangani pemanggil, bukan disamarkan dengan tujuan asal-asalan.
 */
fun firstAccessibleScreen(sections: List<NavMenuSection>): AppNavScreen? = firstAccessibleEntry(sections)?.screen

/** Baris pertama yang terbuka — membawa `route`, sehingga modul tanpa layar khusus pun bisa jadi pendaratan. */
fun firstAccessibleEntry(sections: List<NavMenuSection>): NavMenuEntry? =
    sections.firstNotNullOfOrNull { section -> section.entries.firstOrNull { !it.locked } }
