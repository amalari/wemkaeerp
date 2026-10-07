package com.eventverse.app.infrastructure

import com.eventverse.app.domain.builder.BriefSnapshot
import com.eventverse.app.domain.builder.BuildRequest
import com.eventverse.app.domain.builder.BuildRequestId
import com.eventverse.app.domain.builder.BuildRequestStatus
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Brief beku pada `builder.build_requests` (V94) terhadap Postgres sungguhan. Hanya jalan bila `DB_NAME` berisi `scratch`
 * (tabel ini tidak boleh ditulisi database kerja; lihat `LayananChangeRequestApiIntegrationTest`).
 */
class PostgresBuildRequestBriefIntegrationTest {

    private val suffix = abs(System.nanoTime() % 1_000_000).toString()

    @Test
    fun `brief beku bulak-balik di Postgres, permintaan lama tetap terbaca tanpa brief, dan ubah status tidak menghapus brief`() = runBlocking<Unit> {
        if (!System.getenv("DB_NAME").orEmpty().contains("scratch")) return@runBlocking
        DatabaseFactory.init()
        val tenantId = TenantId("ten-brief-$suffix")
        PostgresTenantRepository().save(Tenant(tenantId, TenantSlug("brief-$suffix"), TenantName("Uji Brief"), TenantStatus.ACTIVE, SubscriptionTier.PRO)).getOrThrow()
        val repo = PostgresBuilderBuildRequestRepository()
        val taken = Instant.parse("2026-10-08T01:02:03Z")

        repo.save(BuildRequest(BuildRequestId("br-$suffix-a"), tenantId, "klinik_poli", "Pack kustom",
            brief = BriefSnapshot("# Brief\n\n## Belum jelas\n- [Poli] ?", """{"packCode":"klinik"}""", taken)))
        repo.save(BuildRequest(BuildRequestId("br-$suffix-b"), tenantId, "klinik_kasir", "Permintaan lama"))

        val rows = repo.findByTenant(tenantId).associateBy { it.id.value }
        val withBrief = requireNotNull(rows["br-$suffix-a"]?.brief)
        assertEquals("# Brief\n\n## Belum jelas\n- [Poli] ?", withBrief.markdown)
        assertEquals("""{"packCode":"klinik"}""", withBrief.json)
        assertEquals(taken, withBrief.takenAt)
        assertNull(rows["br-$suffix-b"]?.brief, "permintaan tanpa brief tetap null")

        repo.save(rows.getValue("br-$suffix-a").copy(status = BuildRequestStatus.IN_PROGRESS))
        val after = repo.findAll().first { it.id.value == "br-$suffix-a" }
        assertEquals(BuildRequestStatus.IN_PROGRESS, after.status)
        assertEquals(withBrief, after.brief, "mengubah status tidak menyentuh brief yang dibekukan")
    }
}
