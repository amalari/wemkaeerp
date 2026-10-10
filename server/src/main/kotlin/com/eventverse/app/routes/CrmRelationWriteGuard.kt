package com.eventverse.app.routes

import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.customfield.RelationTargetResolver
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/**
 * Validasi tulis nilai field `RELATION` (C7, TRD-FIELD-001 FR-2, K2): setiap sel rujukan yang
 * diisi **wajib** menunjuk record yang benar-benar ada di tenant yang sama — diverifikasi lewat
 * [RelationTargetResolver] (jalur baca modul target, tanpa JOIN lintas schema). Tidak ditemukan =
 * **400** fail-closed, bukan disimpan diam-diam. Sel kosong (belum diisi) dilewati.
 *
 * Dipanggil route CRM **setelah** gerbang RBAC & jangkauan data, sebelum use case menyimpan.
 * `true` = semua target sah (atau tidak ada field rujukan); `false` = 400 sudah dikirim.
 */
internal suspend fun ApplicationCall.rejectMissingRelationTargets(
    tenantId: TenantId,
    customFieldRepository: CustomFieldDefinitionRepository,
    values: Map<CustomFieldId, JsonValue.Obj?>,
    resolver: RelationTargetResolver
): Boolean {
    if (values.isEmpty()) return true
    val relations = customFieldRepository
        .findActiveByResource(tenantId, OwnerResource.CRM_SALES)
        .filter { it.type.kind == FieldType.RELATION && !it.isArchived }
    for (def in relations) {
        val recordId = values[def.id]?.string("v")?.takeIf { it.isNotBlank() } ?: continue
        val targetResource = def.type.targetResource ?: continue
        if (!resolver.exists(tenantId, targetResource, recordId)) {
            respond(
                HttpStatusCode.BadRequest,
                "\"${def.label}\" menunjuk record '$recordId' yang tidak ditemukan pada modul '$targetResource'"
            )
            return false
        }
    }
    return true
}
