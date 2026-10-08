package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.tenant.TenantId

/**
 * Jembatan Strangler Fig TRD-FLOW-003 (tahap S0–S2): kode rute lama identik dengan nama enum (D3),
 * sehingga nilai yang sudah tersimpan tetap sah tanpa backfill.
 *
 * Semua anggota file ini **dihapus di S3** bersama `SackRoute`.
 */
fun SackRoute.toRouteCode(): HandoverRouteCode = HandoverRouteCode(name)

/**
 * Rute yang dikenal pembaca lama: tepat isi `SackRoute`. Dipakai sebagai nilai bawaan penyedia rute pada
 * use case sampai penyedia sungguhan (rute per tenant dari database, Track B) dipasang.
 */
fun legacySackRoutes(tenantId: TenantId): TenantHandoverRoutes = TenantHandoverRoutes(
    tenantId,
    SackRoute.entries.map { HandoverRoute(it.toRouteCode(), it.displayName, sortOrder = it.ordinal) }
)

/**
 * Pembacaan lama `transfer.leg`. **Bisa melempar** untuk rute di luar `SackRoute` (mis. rute bordir);
 * itulah yang dicari pemindai S3 dan alasan pembacanya dipindahkan ke [InternalTransfer.route].
 */
val InternalTransfer.leg: SackRoute
    get() = SackRoute.entries.firstOrNull { it.name == route.value }
        ?: error("Rute '${route.value}' bukan SackRoute; pakai InternalTransfer.route")

/** Label tampilan untuk kode [code]: nama baku bila rute lama, selain itu kodenya sendiri (bukan tebakan lain). */
fun legacyRouteLabel(code: HandoverRouteCode): String =
    SackRoute.entries.firstOrNull { it.name == code.value }?.displayName ?: code.value
