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
        // Wewenang pemanggil sendiri — setiap anggota tenant butuh ini untuk menyusun menunya.
        "GET /api/tenant/me/access",
        // Kosakata vertikal tenant (nama modul, fase, port) — setiap anggota butuh untuk menyusun menu & kanvas (B7).
        "GET /api/tenant/pack",
        // Katalog template kerangka tahap bawaan platform (IndustryStageTemplates) — bukan data tenant.
        "GET /api/tenant/stage-templates",
        // AI helper (TRD-HELP-001): disaring per tutorial dengan wewenang pemanggil; tanpa wewenang = 200 tanpa saran.
        "POST /api/tenant/help/ask"
    )

    val ungated: Set<String> = emptySet()
}
