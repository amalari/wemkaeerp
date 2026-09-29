package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import kotlin.jvm.JvmInline

/**
 * Identitas modul (B6, TRD-PLAT-001 FR-1) = `code` huruf kecil (`crm_sales`). Kunci NAME yang tersimpan di RBAC &
 * entitlement (`CRM_SALES`) selalu `value.uppercase()` — dibuktikan untuk ke-15 modul garment — sehingga keduanya
 * terbaca tanpa tabel pemetaan maupun migrasi.
 */
@JvmInline
value class ModuleId(val value: String) {
    init { require(Regex("^[a-z][a-z0-9_]{0,63}$").matches(value)) { "ModuleId '$value' harus huruf kecil/angka/underscore" } }

    /** Bentuk NAME yang tersimpan di `custom_roles`, `department_module_assignments`, entitlement. */
    val storedName: String get() = value.uppercase()
}

/** Seksi menu (dulu `enum ModuleCategory`). Urutan tampil = [order]. */
@JvmInline
value class ModuleSectionCode(val value: String) {
    init { require(value.isNotBlank()) { "ModuleSectionCode kosong" } }
}

data class ModuleSection(val code: ModuleSectionCode, val displayName: String, val order: Int)

/**
 * Satu modul yang dikirim pack (dulu satu entri `enum BusinessModule`).
 *
 * [kind] dan [scopeCapability] tetap enum: keduanya konsep **platform** (kuota, kanvas, pilihan cakupan data), bukan
 * kosakata industri (tenant-variability-rules Kontrak 1). [slot] = slot kanvas; `null` untuk governance/foundation.
 */
data class ModuleDefinition(
    val id: ModuleId,
    val displayName: String,
    val description: String,
    val section: ModuleSectionCode,
    val kind: ModuleKind,
    val iconKey: String,
    val scopeCapability: ScopeCapability,
    val supportedScopes: Set<DataScope>,
    val slot: SlotCode?
) {
    init {
        require(displayName.isNotBlank()) { "Nama modul ${id.value} kosong" }
        require(supportedScopes.isNotEmpty()) { "Modul ${id.value} tanpa cakupan data" }
        require((kind == ModuleKind.OPERATIONAL) == (slot != null)) { "Modul ${id.value}: hanya modul operasional yang punya slot kanvas" }
    }
}
