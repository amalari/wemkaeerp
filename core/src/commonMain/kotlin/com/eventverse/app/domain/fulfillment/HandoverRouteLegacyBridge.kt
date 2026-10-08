package com.eventverse.app.domain.fulfillment

/**
 * Jembatan Strangler Fig TRD-FLOW-003 (tahap S0–S2): kode rute lama identik dengan nama enum (D3),
 * sehingga nilai yang sudah tersimpan tetap sah tanpa backfill.
 *
 * Satu arah (enum → kode) dan **tidak melempar**. **Dihapus di S3** bersama `SackRoute`.
 */
fun SackRoute.toRouteCode(): HandoverRouteCode = HandoverRouteCode(name)
