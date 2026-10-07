package com.eventverse.app.domain.pack

/**
 * Rujukan pack ke **modul bersama** yang ditawarkan platform (B6, PROPOSAL-iv-B6): pack memakai modul itu tanpa
 * menyalinnya, sehingga slot, fase, dan port garment tidak mencemari kosakata pack.
 *
 * - [platformModuleId]: modul yang ada di `DomainPack.sharedModules` pack bawaan (opt-in platform).
 * - [portMapping]: port **pack** → port slot modul itu; harus lengkap (semua port masuk dan keluar) dan satu-satu.
 * - [label]: nama modul itu **di pack ini** (kosakata pack). Nama dari platform bisa berisi istilah vertikal lain
 *   ("Costing HPP"), jadi tampilan dan tebakan wawancara memakai label ini, bukan nama platform.
 *
 * Data saja: dokumen pack menyimpannya, tetapi **belum** dihubungkan ke RBAC, entitlement, kuota, atau kanvas
 * (keputusan 2026-10-07: menunggu persetujuan). Aturannya milik [ModuleReferenceRules].
 */
data class ModuleReference(
    val platformModuleId: ModuleId,
    val label: String,
    val portMapping: Map<PortType, PortType>
)
