package com.eventverse.app.routes

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.FieldKey
import com.eventverse.app.domain.customfield.CrmFieldType
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.discovery.handoff.InMemoryPrototypeRowRepository
import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.storage.ObjectStorage
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryCrmLeadRepository
import com.eventverse.app.infrastructure.InMemoryCustomFieldDefinitionRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Fixture bersama tes gerbang record/jangkauan FILE & RELATION (TRD-FIELD-004 Track A):
 * tenant `abc` (slug `gate-abc`), peran `role-gate`, dua karyawan (`emp-staff` = pemanggil, `emp-other`),
 * storage fake yang mencatat setiap panggilan. Semua in-memory, tanpa Postgres.
 */
internal class RecordingFileStorage : ObjectStorage {
    val stored = ConcurrentHashMap<String, Pair<ByteArray, String>>()
    val downloadCalls = mutableListOf<String>()
    override val isConfigured: Boolean get() = true
    override suspend fun put(key: String, bytes: ByteArray, contentType: String): Result<Unit> {
        stored[key] = bytes to contentType
        return Result.success(Unit)
    }
    override suspend fun downloadUrl(key: String): Result<String> {
        downloadCalls += key
        return Result.success("https://presigned.example.com/$key")
    }
}

internal object FieldGate {
    const val SLUG = "gate-abc"
    const val ROLE_ID = "role-gate"
    const val STAFF_EMAIL = "staff@wemade.test"
    val tenantId = TenantId("abc")
    val staffId = OrgNodeId("emp-staff")
    val otherId = OrgNodeId("emp-other")

    fun employees(): List<OrgNode> = listOf(
        OrgNode(staffId, "Staf Uji", STAFF_EMAIL, null, HierarchyLevel.STAFF_OPERATOR, "Staf", tenantId = tenantId),
        OrgNode(otherId, "Rekan Uji", "rekan@wemade.test", null, HierarchyLevel.STAFF_OPERATOR, "Staf", tenantId = tenantId)
    )

    fun rows(vararg records: PrototypeRow, forTenant: TenantId = tenantId): PrototypeRowRepository =
        InMemoryPrototypeRowRepository().apply { runBlocking { records.forEach { save(forTenant, it) } } }

    fun lead(id: String, owner: OrgNodeId? = null, attrs: CustomAttributes = CustomAttributes.EMPTY) = CrmLead(
        id = LeadId(id), tenantId = tenantId, ownerEmployeeId = owner, customAttributes = attrs,
        createdAt = Instant.fromEpochMilliseconds(0), updatedAt = Instant.fromEpochMilliseconds(0)
    )

    fun definition(id: String, key: String, type: CrmFieldType) = CustomFieldDefinition(
        id = CustomFieldId(id), tenantId = tenantId, ownerResource = OwnerResource.CRM_SALES,
        key = FieldKey(key), label = key.replaceFirstChar { it.uppercase() }, type = type, position = 1.0
    )
}

internal fun ApplicationTestBuilder.installFieldGateApp(
    permissions: Map<ModuleId, ModuleAccessConfig>,
    storage: RecordingFileStorage = RecordingFileStorage(),
    pack: DomainPackCode = GarmentDomainPack.CODE,
    rows: Map<String, PrototypeRowRepository> = emptyMap(),
    leads: InMemoryCrmLeadRepository = InMemoryCrmLeadRepository(),
    definitions: InMemoryCustomFieldDefinitionRepository = InMemoryCustomFieldDefinitionRepository()
): RecordingFileStorage {
    val tenants = InMemoryTenantRepository()
    val roles = InMemoryRoleRepository()
    val employees = InMemoryEmployeeRepository()
    // Pack data (non-garment) dimuat malas oleh plugin tenant dari repository, bukan dari kode.
    val packs = InMemoryDomainPackRepository()
    if (pack == LayananPilotPack.CODE) {
        DomainPackRegistry.unregister(LayananPilotPack.CODE)
        runBlocking { packs.save(StoredDomainPack(LayananPilotPack.pack, 1, DomainPackStatus.LOCKED, null)) }
    }
    runBlocking {
        tenants.save(Tenant(FieldGate.tenantId, TenantSlug(FieldGate.SLUG), TenantName("Tenant Gerbang"), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = pack))
        roles.save(CustomRole(
            id = RoleId(FieldGate.ROLE_ID), tenantId = FieldGate.tenantId, name = "Staf Uji", description = "",
            modulePermissions = permissions
        ))
        employees.saveAll(FieldGate.tenantId, FieldGate.employees())
    }
    application {
        module(
            tenantRepository = tenants,
            pipelineRepository = InMemoryTenantPipelineRepository(),
            entitlementRepository = InMemoryTenantEntitlementRepository(),
            roleRepository = roles,
            moduleAssignmentRepository = InMemoryModuleAssignmentRepository(),
            employeeRepository = employees,
            domainPackRepository = packs,
            crmLeadRepository = leads,
            customFieldDefinitionRepository = definitions,
            objectStorage = storage,
            fieldFileRecordRows = rows
        )
    }
    return storage
}
