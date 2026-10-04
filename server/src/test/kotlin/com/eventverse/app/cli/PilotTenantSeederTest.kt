package com.eventverse.app.cli

import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.UserRepository
import com.eventverse.app.domain.auth.Username
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.pack.LayananPilotPack
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PilotTenantSeederTest {
    private class FakeUsers : UserRepository {
        val all = mutableListOf<User>()
        override suspend fun findById(id: UserId) = all.firstOrNull { it.id == id }
        override suspend fun findByUsername(tenantId: TenantId?, username: Username) = all.firstOrNull { it.tenantId == tenantId && it.username == username }
        override suspend fun findByEmail(email: EmailAddress) = all.firstOrNull { it.email == email }
        override suspend fun save(user: User): Result<User> { all.removeAll { it.id == user.id }; all += user; return Result.success(user) }
        override suspend fun findAllByTenant(tenantId: TenantId) = all.filter { it.tenantId == tenantId }
    }

    private val packs = InMemoryDomainPackRepository()
    private val tenants = InMemoryTenantRepository()
    private val users = FakeUsers()
    private val drafts = InMemoryDiscoveryDraftRepository()
    private val seeder = PilotTenantSeeder(packs, tenants, users, drafts)

    @Test
    fun seed_onEmptyDatabase_createsPackTenantOwnerAndDraft() = runBlocking {
        val report = seeder.seed()
        assertEquals(DomainPackStatus.LOCKED, assertNotNull(packs.findEffective(LayananPilotPack.CODE)).status)
        val tenant = assertNotNull(tenants.findBySlug(TenantSlug("layanan-demo")))
        assertEquals(LayananPilotPack.CODE, tenant.domainPack)
        val draft = assertNotNull(drafts.findByTenant(tenant.id))
        assertEquals("default-layanan_change_request", draft.draft.screens.single().screenId)
        assertEquals(draft.ownerUserId, users.findAllByTenant(tenant.id).single().id)
        assertTrue(report.lines.all { "dibuat" in it }, report.lines.toString())
    }

    @Test
    fun seed_twice_isIdempotent_andNeverOverwritesTheDraft() = runBlocking {
        seeder.seed()
        val before = assertNotNull(drafts.findByTenant(TenantId("ten-layanan-demo")))
        val tenantCount = tenants.findAll().size // repository memori sudah berisi tenant demo bawaan
        val again = seeder.seed()
        assertEquals(tenantCount, tenants.findAll().size)
        assertEquals(1, users.findAllByTenant(TenantId("ten-layanan-demo")).size)
        assertEquals(1, assertNotNull(packs.findLatest(LayananPilotPack.CODE)).version, "isi pack sama: tidak naik versi")
        assertEquals(before, drafts.findByTenant(TenantId("ten-layanan-demo")))
        assertTrue(again.lines.none { "dibuat" in it }, again.lines.toString())
    }

    @Test
    fun seed_whenPackContentChanged_createsANewLockedVersion() = runBlocking {
        seeder.seed()
        val old = assertNotNull(packs.findLatest(LayananPilotPack.CODE))
        packs.save(old.copy(pack = old.pack.copy(displayName = "Layanan (lama)")))
        seeder.seed()
        val latest = assertNotNull(packs.findLatest(LayananPilotPack.CODE))
        assertEquals(2, latest.version)
        assertEquals(LayananPilotPack.pack, latest.pack)
    }

    @Test
    fun requireScratchDatabase_rejectsDevAndMissingNames() {
        listOf(null, "", "wemake_erp", "wemade").forEach { name ->
            assertFailsWith<IllegalArgumentException>("harus menolak '$name'") { PilotTenantSeeder.requireScratchDatabase(name) }
        }
        PilotTenantSeeder.requireScratchDatabase("wemake_dp_c_scratch")
    }

    @Test
    fun seed_doesNotTouchOtherTenants() = runBlocking {
        val untouched = tenants.findAll().filter { it.slug.value != "layanan-demo" }
        seeder.seed()
        seeder.seed()
        assertEquals(untouched, tenants.findAll().filter { it.slug.value != "layanan-demo" })
    }
}
