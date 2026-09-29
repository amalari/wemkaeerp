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
        "DELETE /api/tenant/production/work-orders/{id}/lines/{lineName}",
        "DELETE /api/tenant/tech-pack/{id}",
        "GET /api/tenant/billing-preview",
        "GET /api/tenant/customization-requests",
        "GET /api/tenant/departments/archived",
        "GET /api/tenant/departments/{id}",
        "GET /api/tenant/fulfillment/route-settings",
        "GET /api/tenant/fulfillment/transfers",
        "GET /api/tenant/module-assignments",
        "GET /api/tenant/pipeline",
        "GET /api/tenant/pipeline/modules",
        "GET /api/tenant/production/work-orders",
        "GET /api/tenant/production/work-orders/telemetry",
        "GET /api/tenant/production/work-orders/{id}",
        "GET /api/tenant/roles",
        "GET /api/tenant/roles/{id}",
        "GET /api/tenant/tech-pack",
        "GET /api/tenant/tech-pack/by-style/{styleCode}/latest-released",
        "GET /api/tenant/tech-pack/by-style/{styleCode}/versions",
        "GET /api/tenant/tech-pack/{id}",
        "GET /api/tenant/tech-pack/{id}/explosion",
        "GET /api/tenant/transfers/manifests",
        "GET /api/tenant/transfers/manifests/{id}",
        "POST /api/tenant/customization-requests",
        "POST /api/tenant/production/work-orders/launch-from-deal",
        "POST /api/tenant/production/work-orders/{id}/lines",
        "POST /api/tenant/production/work-orders/{id}/progress",
        "POST /api/tenant/tech-pack/blank",
        "POST /api/tenant/tech-pack/from-sampling",
        "POST /api/tenant/tech-pack/{id}/release",
        "POST /api/tenant/tech-pack/{id}/resolve-materials",
        "POST /api/tenant/tech-pack/{id}/revise",
        "POST /api/tenant/transfers/manifests/customer-dispatch",
        "POST /api/tenant/transfers/manifests/internal",
        "POST /api/tenant/transfers/manifests/makloon-outbound",
        "POST /api/tenant/transfers/manifests/{id}/receive",
        "PUT /api/tenant/tech-pack/{id}/bom",
        "PUT /api/tenant/tech-pack/{id}/labor",
        "PUT /api/tenant/tech-pack/{id}/size-yield",
    )
}
