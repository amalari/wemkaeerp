package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId

/**
 * Kemampuan opsional sebuah [PrototypeRowRepository]: menyebut **pemilik** (karyawan pembuat/PIC) sebuah record
 * (TRD-FIELD-004 FR-1.1). Dibutuhkan rute generik unggah/unduh FILE untuk modul `HIERARCHICAL`, di mana
 * `DataScope` pemanggil (`OWN_DATA_ONLY`/`SUBORDINATE_DATA`) hanya bisa ditegakkan bila pemilik record diketahui.
 *
 * Penyimpan yang **tidak** mengimplementasikan ini = modul hierarkis tanpa sumber pemilik = rute generik
 * menolak 403 (fail-closed, bukan 404 dan bukan jangkauan "semua"). Modul `GLOBAL_ONLY` tidak membutuhkannya.
 */
interface RecordOwnerSource {
    /** Pemilik record [recordId] pada [tenantId]; `null` = record ada tetapi tanpa pemilik (hanya terjangkau scope penuh). */
    suspend fun ownerOf(tenantId: TenantId, recordId: String): OrgNodeId?
}
