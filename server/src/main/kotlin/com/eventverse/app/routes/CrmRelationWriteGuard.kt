package com.eventverse.app.routes

import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.customfield.RelationTargetResolver
import com.eventverse.app.domain.orgchart.EmployeeRepository
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.shared.json.JsonValue
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/**
 * Validasi tulis nilai field `RELATION` (C7, TRD-FIELD-001 FR-2, K2; TRD-FIELD-004 FR-3): setiap sel rujukan yang
 * diisi **wajib** menunjuk record yang ada di tenant yang sama DAN boleh dilihat pemanggil. Per modul target:
 * [authorizeRelationTarget] (VIEW atas modul target 403, modul ada di pack 404, jangkauan data target dihitung
 * sekali) lalu [RelationTargetResolver] dengan jangkauan itu — tanpa JOIN lintas schema. Tidak ditemukan **atau
 * di luar jangkauan** = **400 yang sama** (tanpa oracle keberadaan); bukan disimpan diam-diam. Sel kosong dilewati.
 *
 * Dipanggil route CRM **setelah** gerbang RBAC & jangkauan CRM sendiri, sebelum use case menyimpan.
 * `true` = semua target sah (atau tidak ada field rujukan); `false` = jawaban (400/403/404) sudah dikirim.
 */
internal suspend fun ApplicationCall.rejectMissingRelationTargets(
    tenant: TenantContext,
    customFieldRepository: CustomFieldDefinitionRepository,
    values: Map<CustomFieldId, JsonValue.Obj?>,
    resolver: RelationTargetResolver,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository,
    employeeRepository: EmployeeRepository
): Boolean {
    if (values.isEmpty()) return true
    val relations = customFieldRepository
        .findActiveByResource(tenant.tenantId, OwnerResource.CRM_SALES)
        .filter { it.type.kind == FieldType.RELATION && !it.isArchived }
    val accessByModule = HashMap<String, RelationTargetAccess>()
    for (def in relations) {
        val recordId = values[def.id]?.string("v")?.takeIf { it.isNotBlank() } ?: continue
        val targetResource = def.type.targetResource ?: continue
        val moduleCode = targetResource.substringBefore(':')
        val access = accessByModule[moduleCode]
            ?: authorizeRelationTarget(tenant, moduleCode, roleRepository, moduleAssignmentRepository, employeeRepository, "crm-relation-write")
                ?.also { accessByModule[moduleCode] = it }
            ?: return false
        if (!resolver.exists(tenant.tenantId, targetResource, recordId, access.reachableOwnerIds)) {
            respond(
                HttpStatusCode.BadRequest,
                "\"${def.label}\" menunjuk record '$recordId' yang tidak ditemukan pada modul '$targetResource'"
            )
            return false
        }
    }
    return true
}
