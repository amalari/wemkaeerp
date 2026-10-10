package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId

/**
 * C7 (TRD-FIELD-001 K2): port domain untuk memverifikasi bahwa satu record target rujukan benar-benar
 * ada di tenant yang sama sebelum nilai field [FieldType.Relation] disimpan (fail-closed; FR-2).
 *
 * Didefinisikan di domain, diimplementasikan infrastruktur server (Track B — jalur baca modul target).
 * Modul **pemegang field** yang memanggil ini saat tulis nilai; modul **target** tetap pemilik
 * satu-satunya keberadaan record-nya (tanpa FK/JOIN lintas schema — pagar J3, FR-1).
 */
interface RelationTargetResolver {
    /**
     * True bila record [targetRecordId] ada pada resource [targetResource] dalam [tenantId] **dan** terjangkau
     * pemanggil. [reachableOwnerIds] = jangkauan `DataScope` pemanggil atas modul target (`null` = seluruh tenant);
     * parameter **wajib** (TRD-FIELD-004 FR-3.2, tanpa default) agar tiap pemanggil memutuskan jangkauannya —
     * record di luar jangkauan dijawab `false`, sama dengan "tidak ditemukan" (tanpa oracle keberadaan).
     */
    suspend fun exists(
        tenantId: TenantId,
        targetResource: String,
        targetRecordId: String,
        reachableOwnerIds: Set<OrgNodeId>?
    ): Boolean
}
