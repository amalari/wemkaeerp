package com.eventverse.app.tenant

import com.eventverse.app.domain.discovery.handoff.InMemoryPrototypeRowRepository
import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.InMemoryCrmLeadRepository
import com.eventverse.app.relation.RegistryRelationTargetResolver
import com.eventverse.app.relation.RelationTargetRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * TRD-FIELD-004 B1 (FR-2.3, menutup F0): sumber baris produksi modul hasil generate datang dari
 * [TenantPackContributions.Contribution.rows] — bukan dari parameter tes `fieldFileRecordRows`.
 */
class TenantPackContributionsRowsTest {

    private fun contribution(rows: Map<String, PrototypeRowRepository>) = TenantPackContributions.Contribution(
        pack = LayananPilotPack.pack,
        tables = emptyMap(),
        routePrefixes = emptyMap(),
        rows = rows,
        registerRoutes = { _, _, _ -> }
    )

    @Test fun `rows_registeredContribution_exposesGeneratedModuleSourceWithoutInjection`() {
        val rows = TenantPackContributions.rows
        assertTrue(LayananPilotPack.CHANGE_REQUEST.value in rows.keys, "layanan_change_request wajib punya sumber baris produksi")
    }

    @Test fun `rows_everyContributionKey_belongsToItsOwnPack`() {
        TenantPackContributions.all.forEach { c ->
            c.rows.keys.forEach { code ->
                assertTrue(c.pack.module(ModuleId(code)) != null, "$code bukan modul pack ${c.pack.code.value}")
            }
        }
    }

    @Test fun `merge_keyOutsidePack_rejectedLoudly`() {
        val c = contribution(mapOf("quality_control" to InMemoryPrototypeRowRepository()))
        assertFailsWith<IllegalArgumentException> { TenantPackContributions.mergeRows(listOf(c)) }
    }

    @Test fun `merge_duplicateModuleAcrossContributions_rejectedLoudly`() {
        val key = LayananPilotPack.CHANGE_REQUEST.value
        val a = contribution(mapOf(key to InMemoryPrototypeRowRepository()))
        val b = contribution(mapOf(key to InMemoryPrototypeRowRepository()))
        assertFailsWith<IllegalArgumentException> { TenantPackContributions.mergeRows(listOf(a, b)) }
    }

    @Test fun `merge_testOverride_winsOverProduction`() {
        val key = LayananPilotPack.CHANGE_REQUEST.value
        val production = InMemoryPrototypeRowRepository()
        val override = InMemoryPrototypeRowRepository()
        val merged = TenantPackContributions.mergeRows(listOf(contribution(mapOf(key to production))), mapOf(key to override))
        assertSame(override, merged.getValue(key))
    }

    @Test fun `resolver_productionRowsOnly_seesGeneratedModuleAndIsTenantIsolated`() = runBlocking {
        val key = LayananPilotPack.CHANGE_REQUEST.value
        val repo = InMemoryPrototypeRowRepository()
        val tenantA = TenantId("ten-a")
        val tenantB = TenantId("ten-b")
        repo.save(tenantA, PrototypeRow("cr-1", mapOf("judul" to "x")))
        val merged = TenantPackContributions.mergeRows(listOf(contribution(mapOf(key to repo))))
        val resolver = RegistryRelationTargetResolver(RelationTargetRegistry.default(InMemoryCrmLeadRepository(), merged))
        // Modul harus dikenal proses (pack layanan terdaftar) agar resolver menerima targetnya.
        val registered = DomainPackRegistry.find(LayananPilotPack.CODE) == null
        if (registered) DomainPackRegistry.register(LayananPilotPack.pack)
        try {
            assertTrue(resolver.exists(tenantA, "$key:change_request", "cr-1", reachableOwnerIds = null))
            assertEquals(false, resolver.exists(tenantB, "$key:change_request", "cr-1", reachableOwnerIds = null), "id tenant lain ditolak")
            assertEquals(false, resolver.exists(tenantA, "$key:change_request", "tak-ada", reachableOwnerIds = null))
        } finally {
            if (registered) DomainPackRegistry.unregister(LayananPilotPack.CODE)
        }
    }
}
