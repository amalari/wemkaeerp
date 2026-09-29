package com.eventverse.app.routes

/**
 * **Utang gerbang RBAC** (B5, 2026-09-29): route tenant yang **tidak** menolak pengguna tanpa wewenang
 * modul. Diisi dari probe pertama (218 route; 65 menolak dengan benar).
 *
 * Aturan ratchet (`RouteGateTest`):
 * - route baru yang tidak menolak dan tidak ada di sini → **merah** (gerbang wajib sejak hari pertama);
 * - route di sini yang kini menolak → **merah** sampai barisnya dihapus (utang hanya boleh berkurang).
 *
 * Kosongkan daftar ini per modul: tambahkan `requireModuleAccess` (tulis: fail-closed) **sebelum** body dibaca.
 */
internal object RouteGateLedger {

    /** Terbuka untuk setiap anggota tenant **dengan sengaja** — dibutuhkan setiap layar untuk menyusun menu. */
    val openByDesign: Set<String> = setOf(
        "GET /api/tenant/info",
        "GET /api/tenant/entitlement",
        // Katalog template kerangka tahap bawaan platform (IndustryStageTemplates) — bukan data tenant.
        "GET /api/tenant/stage-templates"
    )

    val ungated: Set<String> = setOf(
        "GET /api/tenant/departments/archived",
        "GET /api/tenant/departments/{id}",
        "GET /api/tenant/module-assignments",
        "GET /api/tenant/roles",
        "GET /api/tenant/roles/{id}",
    )
}
