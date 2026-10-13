package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.i18n.LocalizedText
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

@JvmInline
value class ModuleSectionCode(val value: String) {
    init { require(value.isNotBlank()) { "ModuleSectionCode kosong" } }
}

/**
 * Seksi menu (dulu `enum ModuleCategory`). Urutan tampil = [order]. [colorHex]/[tintHex] = aksen & latar seksi di kartu
 * modul — warna **data vertikal** (design-system-rules Kontrak 1, pengecualian 1), sama seperti `PhaseDefinition.colorHex`.
 */
data class ModuleSection(
    val code: ModuleSectionCode,
    val displayName: String,
    val order: Int,
    val colorHex: Long,
    val tintHex: Long,
    /** Jembatan Strangler Fig TRD-I18N-002 (P3): nama seksi dua bahasa; [displayName] = teks `id`. */
    val displayNameText: LocalizedText = LocalizedText.of(displayName)
) {
    init { require(displayNameText.idText == displayName) { "Seksi ${code.value}: displayNameText.id harus sama dengan displayName" } }
}

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
    val slot: SlotCode?,
    /** Jembatan Strangler Fig TRD-I18N-002 (P3): nama & deskripsi dua bahasa; [displayName]/[description] = teks `id`. */
    val displayNameText: LocalizedText = LocalizedText.of(displayName),
    val descriptionText: LocalizedText? = description.takeIf { it.isNotBlank() }?.let { LocalizedText.of(it) },
    /**
     * Judul baris menu untuk layar **utama** modul (TRD-I18N-003). Bukan cermin [displayName] — judul menu sengaja pendek dan
     * boleh berbeda dari nama penuh. `null` = pack tidak menyediakan; menu memakai cadangan yang ditetapkan `menuTitle`.
     * Layar fitur memakai `ModuleFeature.menuLabelText`.
     */
    val menuLabelText: LocalizedText? = null
) {
    init {
        require(displayName.isNotBlank()) { "Nama modul ${id.value} kosong" }
        require(displayNameText.idText == displayName) { "Modul ${id.value}: displayNameText.id harus sama dengan displayName" }
        require(descriptionText == null || descriptionText.idText == description) { "Modul ${id.value}: descriptionText.id harus sama dengan description" }
        require(supportedScopes.isNotEmpty()) { "Modul ${id.value} tanpa cakupan data" }
        require((kind == ModuleKind.OPERATIONAL) == (slot != null)) { "Modul ${id.value}: hanya modul operasional yang punya slot kanvas" }
    }
}
