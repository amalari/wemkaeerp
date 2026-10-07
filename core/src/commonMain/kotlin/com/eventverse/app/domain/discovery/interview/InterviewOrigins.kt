package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.isReferenced
import com.eventverse.app.domain.rbac.ModuleKind

/**
 * Asal modul yang sah bila pengguna **memilih** [m] untuk sebuah peran di pack ini — satu kebenaran yang dipakai
 * penebak deterministik dan ringkasan server (klien tidak menebak asal: tebakan klien yang keliru ditolak validator).
 * Modul rujukan → `REUSE_PLATFORM`; modul bawaan operasional → `REUSE_PACK`; modul tata kelola/fondasi bawaan →
 * `REUSE_PLATFORM`; modul milik pack itu sendiri (tidak ada di pack bawaan) → `NEW`.
 */
fun DomainPack.suggestedOrigin(m: ModuleDefinition): ModuleOrigin {
    if (isReferenced(m.id)) return ModuleOrigin.REUSE_PLATFORM
    val shipped = DomainPackRegistry.shipped.firstNotNullOfOrNull { it.module(m.id) } ?: return ModuleOrigin.NEW
    return if (shipped.kind == ModuleKind.OPERATIONAL) ModuleOrigin.REUSE_PACK else ModuleOrigin.REUSE_PLATFORM
}
