package com.eventverse.app.tenant

import com.eventverse.app.asTenant
import com.eventverse.app.domain.discovery.handoff.InMemoryPrototypeRowRepository
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.MultiSelectValues
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TRD-FIELD-004 FR-5.1: semua kontribusi generated otomatis diuji terhadap guard FILE/RELATION. */
class GeneratedRouteWriteGuardTest {
    @AfterTest
    fun cleanup() {
        TenantPackContributions.all.forEach { DomainPackRegistry.unregister(it.pack.code) }
    }

    @Test
    fun `every generated file and relation field rejects untrusted values before persistence`() = testApplication {
        val tenants = InMemoryTenantRepository()
        val packs = InMemoryDomainPackRepository()
        val repositories = TenantPackContributions.all.flatMap { contribution ->
            contribution.rows.keys.map { it to InMemoryPrototypeRowRepository() }
        }.toMap()

        runBlocking {
            TenantPackContributions.all.forEachIndexed { index, contribution ->
                packs.save(StoredDomainPack(contribution.pack, 1, DomainPackStatus.LOCKED, null))
                tenants.save(
                    Tenant(
                        TenantId("ten-generated-$index"),
                        TenantSlug("generated-$index"),
                        TenantName("Generated Guard $index"),
                        TenantStatus.ACTIVE,
                        SubscriptionTier.PRO,
                        domainPack = contribution.pack.code
                    )
                )
            }
        }

        application {
            module(
                tenantRepository = tenants,
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = InMemoryRoleRepository(),
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(),
                domainPackRepository = packs,
                fieldFileRecordRows = repositories
            )
        }

        TenantPackContributions.all.forEachIndexed { index, contribution ->
            val slug = "generated-$index"
            val moduleCode = contribution.pack.modules.single().id.value
            contribution.spec.entities.forEach { entity ->
                val guardedFields = entity.fields.filter { it.type == FieldType.FILE || it.type == FieldType.RELATION }
                assertTrue(guardedFields.isNotEmpty(), "${contribution.pack.code.value}/${entity.id} tidak menguji FILE/RELATION")
                guardedFields.forEach { guarded ->
                    val values = validRequiredValues(entity.fields).toMutableMap().apply {
                        put(
                            guarded.key,
                            if (guarded.type == FieldType.FILE) {
                                "fields/tenant-asing/$moduleCode/record-asing/${guarded.key}-abc123-file.pdf"
                            } else {
                                "target-tidak-ada"
                            }
                        )
                    }
                    val table = if (entity.id.endsWith("s")) entity.id else "${entity.id}s"
                    val response = client.post("/api/tenant/modules/$moduleCode/$table") {
                        asTenant(slug)
                        contentType(ContentType.Application.Json)
                        setBody(jsonObjectOf("values" to jsonStringMapOf(values)).encode())
                    }
                    assertEquals(
                        HttpStatusCode.BadRequest,
                        response.status,
                        "$moduleCode/${entity.id}.${guarded.key} harus ditolak sebelum save"
                    )
                }
                val repository = checkNotNull(repositories[moduleCode])
                assertTrue(repository.list(TenantId("ten-generated-$index")).isEmpty(), "$moduleCode tidak boleh menyimpan input tertolak")
            }
        }
    }

    private fun validRequiredValues(fields: List<FieldSpec>): Map<String, String> = fields
        .filter { it.required }
        .associate { field ->
            field.key to when (field.type) {
                FieldType.TEXT, FieldType.LONG_TEXT -> "uji"
                FieldType.NUMBER -> "1"
                FieldType.DATE -> if (field.withTime) "2026-01-01T10:00" else "2026-01-01"
                FieldType.TIME -> "10:00"
                FieldType.ENUM -> field.optionLabels.first()
                FieldType.MULTI_SELECT -> MultiSelectValues.encode(
                    selected = listOf(field.optionLabels.first()),
                    options = field.optionLabels
                )
                FieldType.BOOL -> "tidak"
                FieldType.USER_REF -> "usr-uji"
                FieldType.FILE, FieldType.RELATION -> ""
            }
        }
}
