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
import com.eventverse.app.domain.rbac.ModuleCategory
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
 * Dua aturan yang dikandungnya:
 *
 * 1. **Modul tanpa akses dihilangkan, bukan diredupkan.** Staf gudang tidak perlu tahu ada layar
 *    HPP. Mode audit membalikkannya, karena saat menguji konfigurasi, menu yang hilang dan menu
 *    yang tak pernah ada terlihat sama.
 * 2. **Seksi kategori yang seluruh modulnya tersaring ikut hilang.** Header tanpa isi lebih buruk
 *    daripada tidak ada header — ia menjanjikan sesuatu yang tidak ada.
 *
 * Kategori dan urutan modul dibaca dari [BusinessModule.category] — bukan didaftar ulang di sini,
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

    ModuleCategory.entries.forEach { category ->
        val entries = BusinessModules.entries
            .filter { it.category == category }
            .flatMap { module ->
                val access = permissions[module] ?: ModuleAccessConfig()
                if (!access.isAccessible && !auditView) return@flatMap emptyList()

                screensByModule[module].orEmpty().map { screen ->
                    NavMenuEntry(
                        screen = screen,
                        accessLevel = access.level,
                        badge = access.level.badgeLabel(),
                        locked = !access.isAccessible
                    )
                }
            }

        if (entries.isNotEmpty()) {
            sections += NavMenuSection(title = category.displayName, entries = entries)
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
fun firstAccessibleScreen(sections: List<NavMenuSection>): AppNavScreen? =
    sections.firstNotNullOfOrNull { section ->
        section.entries.firstOrNull { !it.locked }?.screen
    }
