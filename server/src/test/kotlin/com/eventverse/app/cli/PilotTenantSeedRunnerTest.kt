package com.eventverse.app.cli

import com.eventverse.app.tenant.layanan.PilotTenantSeeder
import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.PostgresDiscoveryDraftRepository
import com.eventverse.app.infrastructure.PostgresDomainPackRepository
import com.eventverse.app.infrastructure.PostgresTenantRepository
import com.eventverse.app.infrastructure.PostgresUserRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Penjalan semai tenant pilot terhadap **Postgres uji** (C4). Hanya jalan bila `DB_NAME` berisi `scratch`
 * (jalur normal: dilewati, seperti `LayananChangeRequestApiIntegrationTest`):
 *
 * ```bash
 * docker exec wemade-postgres psql -U postgres -c "CREATE DATABASE wemake_dp_c_scratch"
 * DB_NAME=wemake_dp_c_scratch ./gradlew :server:test --tests '*PilotTenantSeedRunnerTest*'
 * ```
 * Lalu jalankan server dengan `DB_NAME` yang sama; masuk sebagai Superadmin dan act-as `layanan-demo`.
 */
class PilotTenantSeedRunnerTest {
    private val dbName = System.getenv("DB_NAME")

    @Test
    fun seedAgainstScratchDatabase_isIdempotent() {
        if (dbName.orEmpty().contains("scratch").not()) return
        PilotTenantSeeder.requireScratchDatabase(dbName)
        DatabaseFactory.init()
        val tenants = PostgresTenantRepository()
        val drafts = PostgresDiscoveryDraftRepository()
        val seeder = PilotTenantSeeder(PostgresDomainPackRepository(), tenants, PostgresUserRepository(), drafts)
        runBlocking {
            seeder.seed().lines.forEach(::println)
            val second = seeder.seed()
            assertTrue(second.lines.none { "dibuat" in it }, second.lines.toString())
            val tenant = assertNotNull(tenants.findBySlug(TenantSlug("layanan-demo")))
            assertEquals(LayananPilotPack.CODE, tenant.domainPack)
            assertEquals("default-layanan_change_request", assertNotNull(drafts.findByTenant(tenant.id)).draft.screens.single().screenId)
        }
    }
}
