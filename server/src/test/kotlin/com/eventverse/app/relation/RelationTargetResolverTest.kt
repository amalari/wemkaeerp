package com.eventverse.app.relation

import com.eventverse.app.domain.crm.BrandName
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.discovery.handoff.InMemoryPrototypeRowRepository
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.InMemoryCrmLeadRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Resolver target rujukan (C7, TRD-FIELD-001 FR-2): target "ada dan dapat dirujuk" — fail-closed.
 * `targetResource` = kode modul; governance/foundation bukan target; modul tanpa sumber = false.
 */
class RelationTargetResolverTest {

    private val tenantId = TenantId("ten-relation-resolver")
    private val rows = InMemoryPrototypeRowRepository().apply {
        runBlocking { save(tenantId, PrototypeRow("rec-1", mapOf("nama" to "Contoh"))) }
    }
    private val resolver = RegistryRelationTargetResolver(
        RelationTargetRegistry.default(InMemoryCrmLeadRepository(), mapOf("quality_control" to rows))
    )

    @Test fun `target modul tak dikenal ditolak`() = runBlocking {
        assertFalse(resolver.exists(tenantId, "modul_hantu", "rec-1", reachableOwnerIds = null))
    }

    @Test fun `modul governance bukan target rujukan`() = runBlocking {
        // org_chart = governance; sumbernya sengaja tidak ada, tapi ditolak karena bukan modul operasional.
        assertFalse(resolver.exists(tenantId, "org_chart", "rec-1", reachableOwnerIds = null))
    }

    @Test fun `record target ada diterima`() = runBlocking {
        assertTrue(resolver.exists(tenantId, "quality_control", "rec-1", reachableOwnerIds = null))
    }

    @Test fun `record target hilang ditolak`() = runBlocking {
        assertFalse(resolver.exists(tenantId, "quality_control", "rec-tidak-ada", reachableOwnerIds = null))
    }

    @Test fun `modul operasional tanpa sumber ditolak - tanpa fallback`() = runBlocking {
        // sampling_order modul operasional yang sah, tapi belum punya sumber baris di router ini.
        assertFalse(resolver.exists(tenantId, "sampling_order", "rec-1", reachableOwnerIds = null))
    }

    @Test fun `id target kosong ditolak`() = runBlocking {
        assertFalse(resolver.exists(tenantId, "quality_control", "", reachableOwnerIds = null))
    }

    // ---- Sumber opsi CRM menghormati jangkauan pemilik (regresi TRD-FIELD-001 FR-4) -----------------

    @Test fun `opsi CRM menghormati jangkauan pemilik - bukan seluruh tenant`() = runBlocking {
        // Data leak yang dicegah: `options` TIDAK boleh memakai findActive(tenantId, null) untuk
        // pemanggil ber-scope sempit; reachableOwnerIds wajib diteruskan ke query.
        val leads = InMemoryCrmLeadRepository()
        leads.save(lead("lead-a", "emp-a"))
        leads.save(lead("lead-b", "emp-b"))
        val source = CrmLeadRelationSource(leads)

        val all = source.options(tenantId, entity = "leads", reachableOwnerIds = null, query = "", limit = 20)
        assertEquals(setOf("lead-a", "lead-b"), all.map { it.id }.toSet())

        val scoped = source.options(tenantId, entity = "leads", reachableOwnerIds = setOf(OrgNodeId("emp-a")), query = "", limit = 20)
        assertEquals(listOf("lead-a"), scoped.map { it.id })
    }

    @Test fun `exists CRM menghormati jangkauan pemilik - di luar jangkauan sama dengan tidak ada`() = runBlocking {
        val leads = InMemoryCrmLeadRepository()
        leads.save(lead("lead-a", "emp-a"))
        leads.save(lead("lead-b", "emp-b"))
        leads.save(lead("lead-tanpa-pemilik", null))
        val source = CrmLeadRelationSource(leads)
        val reachA = setOf(OrgNodeId("emp-a"))

        assertTrue(source.exists(tenantId, "lead-a", reachableOwnerIds = reachA))
        assertFalse(source.exists(tenantId, "lead-b", reachableOwnerIds = reachA), "milik emp-b di luar jangkauan")
        assertFalse(source.exists(tenantId, "lead-tanpa-pemilik", reachableOwnerIds = reachA), "tanpa pemilik hanya terjangkau scope penuh")
        assertFalse(source.exists(tenantId, "lead-hantu", reachableOwnerIds = reachA))
        // Scope penuh (null) melihat semuanya, termasuk lead tanpa pemilik.
        assertTrue(source.exists(tenantId, "lead-b", reachableOwnerIds = null))
        assertTrue(source.exists(tenantId, "lead-tanpa-pemilik", reachableOwnerIds = null))
        // Himpunan kosong (pemanggil tak punya karyawan) = tak menjangkau apa pun.
        assertFalse(source.exists(tenantId, "lead-a", reachableOwnerIds = emptySet()))
    }

    @Test fun `resolver meneruskan jangkauan ke sumber modul target`() = runBlocking {
        val leads = InMemoryCrmLeadRepository()
        leads.save(lead("lead-b", "emp-b"))
        val crmResolver = RegistryRelationTargetResolver(RelationTargetRegistry.default(leads, emptyMap()))
        assertFalse(crmResolver.exists(tenantId, "crm_sales", "lead-b", setOf(OrgNodeId("emp-a"))))
        assertTrue(crmResolver.exists(tenantId, "crm_sales", "lead-b", null))
    }

    private fun lead(id: String, owner: String?) = CrmLead(
        id = LeadId(id),
        tenantId = tenantId,
        brandName = BrandName(id),
        ownerEmployeeId = owner?.let { OrgNodeId(it) },
        createdAt = Instant.fromEpochMilliseconds(0),
        updatedAt = Instant.fromEpochMilliseconds(0)
    )
}
