package com.eventverse.app.relation

import com.eventverse.app.domain.discovery.handoff.InMemoryPrototypeRowRepository
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.InMemoryCrmLeadRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
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
        assertFalse(resolver.exists(tenantId, "modul_hantu", "rec-1"))
    }

    @Test fun `modul governance bukan target rujukan`() = runBlocking {
        // org_chart = governance; sumbernya sengaja tidak ada, tapi ditolak karena bukan modul operasional.
        assertFalse(resolver.exists(tenantId, "org_chart", "rec-1"))
    }

    @Test fun `record target ada diterima`() = runBlocking {
        assertTrue(resolver.exists(tenantId, "quality_control", "rec-1"))
    }

    @Test fun `record target hilang ditolak`() = runBlocking {
        assertFalse(resolver.exists(tenantId, "quality_control", "rec-tidak-ada"))
    }

    @Test fun `modul operasional tanpa sumber ditolak - tanpa fallback`() = runBlocking {
        // sampling_order modul operasional yang sah, tapi belum punya sumber baris di router ini.
        assertFalse(resolver.exists(tenantId, "sampling_order", "rec-1"))
    }

    @Test fun `id target kosong ditolak`() = runBlocking {
        assertFalse(resolver.exists(tenantId, "quality_control", ""))
    }
}
