package com.eventverse.app.domain.pack

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

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.BusinessModules

/**
 * **Satu-satunya** parser kunci modul tersimpan (B6c, TRD-PLAT-001 FR-1/FR-3). Menggantikan 14 parser yang dulu
 * tersebar — masing-masing sedikit berbeda soal huruf besar, dan semuanya membuang nilai tak dikenal tanpa jejak.
 *
 * - **NAME** (`CRM_SALES`) — `custom_roles`, `department_module_assignments`, entitlement, `/me/access`, telemetri.
 *   Pencocokan persis (perilaku `valueOf` lama).
 * - **code** (`crm_sales`) — katalog modul, node pipeline. Tidak peka huruf besar (perilaku `fromCode` lama); data A & B
 *   diaudit 2026-09-29: tidak ada nilai huruf campuran.
 *
 * Nilai tak dikenal tetap **ditolak** (fail-closed: tidak ada akses yang tercipta), tetapi kini **dilaporkan** ke
 * [unknownSink] beserta lokasinya — dulu akses bisa hilang tanpa ada yang tahu sebabnya.
 */
object ModuleIdCodec {

    fun interface UnknownSink { fun report(location: String, raw: String) }

    /** Server memasang logger; klien & test membiarkan no-op. */
    var unknownSink: UnknownSink = UnknownSink { _, _ -> }

    fun fromStoredName(raw: String?, location: String): BusinessModule? {
        if (raw == null) return null
        return BusinessModules.entries.firstOrNull { it.storedName == raw } ?: run { unknownSink.report(location, raw); null }
    }

    fun fromCode(raw: String?, location: String): BusinessModule? {
        if (raw == null) return null
        return BusinessModules.entries.firstOrNull { it.value.equals(raw, ignoreCase = true) } ?: run { unknownSink.report(location, raw); null }
    }

    /** Pencarian code yang **boleh** gagal (mis. node plugin kustom): tanpa laporan, karena tidak dikenal = wajar. */
    fun standardOrNull(code: String?): BusinessModule? =
        code?.let { c -> BusinessModules.entries.firstOrNull { it.value.equals(c, ignoreCase = true) } }

    fun storedName(module: BusinessModule): String = module.storedName
}
