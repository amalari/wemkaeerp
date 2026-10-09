package com.eventverse.app.infrastructure

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.usecases.CreateDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.HandoffDiscoveryDraftUseCase
import com.eventverse.app.domain.discovery.usecases.LockDiscoveryDraftUseCase
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.UnresolvableBlueprintException
import com.eventverse.app.domain.pack.usecases.LockDomainPackUseCase
import com.eventverse.app.domain.pack.usecases.SaveDomainPackDraftUseCase
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.tables.DomainPacksTable
import com.eventverse.app.infrastructure.tables.TenantsTable
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.update
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TRD-PLAT-008 pada Postgres sungguhan (DB scratch): tenant ber-blueprint non-garment bisa dibaca ulang lewat
 * `findById`/`findBySlug`/`findAll()`, satu baris rusak tidak meracuni daftar, dan tenant garment tak berubah.
 * Pack klinik **dilepas dari registry** setelah dikunci, meniru proses baru yang belum memuat pack data (K4).
 */
class PostgresTenantBlueprintIntegrationTest {

    private val suffix = abs(System.nanoTime() % 1_000_000).toString()
    private val packCode = DomainPackCode("klinikbp$suffix")
    private val packs = PostgresDomainPackRepository()
    private val tenants = PostgresTenantRepository()
    private val createdSlugs = mutableListOf<String>()

    private lateinit var starter: Blueprint
    private lateinit var longCodeStarter: Blueprint

    @BeforeTest
    fun setup() = runBlocking<Unit> {
        DatabaseFactory.init()
        val draft = DeterministicDiscoveryAgent().draft(
            DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = packCode.value)
        ).getOrThrow()
        starter = draft.blueprint
        // Kode 64 karakter = batas BlueprintCode; menguji pelebaran kolom V97 (sebelumnya VARCHAR(50)).
        longCodeStarter = starter.copy(code = BlueprintCode("s" + "x".repeat(63)))
        val pack = draft.pack.copy(blueprints = listOf(starter, longCodeStarter))
        SaveDomainPackDraftUseCase(packs)(pack, ownerTenantId = null).getOrThrow()
        LockDomainPackUseCase(packs)(packCode).getOrThrow()
        DomainPackRegistry.unregister(packCode) // proses baru: registry belum memuat pack data
    }

    @AfterTest
    fun cleanup() = runBlocking<Unit> {
        DomainPackRegistry.unregister(packCode)
        DatabaseFactory.dbQuery {
            // Pack hasil handoff dimiliki tenant ujinya (FK); lepas dulu sebelum tenant dihapus.
            createdSlugs.forEach { slug -> DomainPacksTable.deleteWhere { ownerTenantId eq "ten-$slug" } }
            createdSlugs.forEach { slug -> TenantsTable.deleteWhere { TenantsTable.slug eq slug } }
        }
    }

    private fun tenant(name: String, blueprint: Blueprint, pack: DomainPackCode) =
        Tenant(
            id = TenantId("ten-bp-$suffix-$name"), slug = TenantSlug("bp-$suffix-$name"), name = TenantName("Uji $name"),
            status = TenantStatus.ACTIVE, tier = SubscriptionTier.PRO, businessPreset = blueprint, domainPack = pack
        ).also { createdSlugs += it.slug.value }

    @Test
    fun `tenant ber-blueprint non-garment round-trip lewat findById findBySlug dan findAll`() = runBlocking<Unit> {
        val klinik = tenant("klinik", starter, packCode)
        val garment = tenant("cmt", GarmentBlueprints.CMT_MAKLOON, DomainPackCode("garment"))
        tenants.save(klinik).getOrThrow()
        tenants.save(garment).getOrThrow()

        assertEquals(starter, tenants.findBySlug(klinik.slug)?.businessPreset)
        assertEquals(starter, tenants.findById(klinik.id)?.businessPreset)
        val all = tenants.findAll() // tidak boleh melempar
        assertEquals(starter, all.single { it.id == klinik.id }.businessPreset)
        assertEquals(GarmentBlueprints.CMT_MAKLOON, all.single { it.id == garment.id }.businessPreset)
    }

    @Test
    fun `paritas garment - tiga starter tersimpan dan terbaca identik`() = runBlocking<Unit> {
        GarmentBlueprints.all.forEach { bp ->
            val t = tenant(bp.code.value.take(12).replace('_', '-'), bp, DomainPackCode("garment"))
            tenants.save(t).getOrThrow()
            assertEquals(t, tenants.findBySlug(t.slug))
        }
    }

    @Test
    fun `tenant ber-pack data yang masih memegang kode bawaan kolom tetap terbaca`() = runBlocking<Unit> {
        val t = tenant("default", GarmentBlueprints.DEFAULT, packCode)
        tenants.save(t).getOrThrow()
        assertEquals(GarmentBlueprints.DEFAULT, tenants.findBySlug(t.slug)?.businessPreset)
    }

    @Test
    fun `kode starter 64 karakter tersimpan penuh`() = runBlocking<Unit> {
        val t = tenant("long", longCodeStarter, packCode)
        tenants.save(t).getOrThrow()
        assertEquals(longCodeStarter, tenants.findBySlug(t.slug)?.businessPreset)
    }

    @Test
    fun `save menolak kode starter yang tak ter-resolve dengan pesan jelas`() = runBlocking<Unit> {
        // Blueprint klinik pada tenant garment: pack garment tidak memilikinya, dan bukan starter platform.
        val ditolak = tenants.save(tenant("salah", starter, DomainPackCode("garment")))
        val ex = assertFailsWith<UnresolvableBlueprintException> { ditolak.getOrThrow() }
        assertTrue(ex.message!!.contains(starter.code.value) && ex.message!!.contains("garment"))
        assertEquals(null, tenants.findBySlug(TenantSlug("bp-$suffix-salah")), "tidak ada yang tertulis")

        val tanpaKatalog = starter.copy(code = BlueprintCode("tak_terdaftar"))
        assertTrue(tenants.save(tenant("hantu", tanpaKatalog, packCode)).isFailure)
    }

    @Test
    fun `satu baris tak ter-resolve tidak meracuni findAll tetapi findBySlug-nya gagal jelas`() = runBlocking<Unit> {
        val sehat = tenant("sehat", starter, packCode)
        val rusak = tenant("rusak", starter, packCode)
        tenants.save(sehat).getOrThrow()
        tenants.save(rusak).getOrThrow()
        // Korupsi di luar aplikasi (mis. pack dihapus / kolom diedit): menembus penjaga tulis.
        DatabaseFactory.dbQuery { TenantsTable.update({ TenantsTable.slug eq rusak.slug.value }) { it[businessPreset] = "hilang_kode" } }

        val all = tenants.findAll()
        assertTrue(all.any { it.id == sehat.id }, "tenant sehat tetap terdaftar")
        assertTrue(all.none { it.id == rusak.id }, "baris rusak dilewati, bukan ditebak")
        val ex = assertFailsWith<UnresolvableBlueprintException> { tenants.findBySlug(rusak.slug) }
        assertTrue(ex.message!!.contains("hilang_kode"))
        assertNotNull(tenants.findBySlug(sehat.slug))
    }

    @Test
    fun `handoff pack non-garment menghasilkan tenant yang bisa dimuat ulang dan blueprint-nya ter-resolve`() = runBlocking<Unit> {
        val drafts = InMemoryDiscoveryDraftRepository()
        val owner = UserId("usr-handoff-$suffix")
        val hint = "klinikho$suffix"
        val id = DiscoveryDraftId("draft-ho-$suffix")
        CreateDiscoveryDraftUseCase(DeterministicDiscoveryAgent(), drafts)(
            DiscoveryRequest("Kami klinik gigi dengan antrean pasien dan kasir.", industryHint = hint), owner, id
        ).getOrThrow()
        LockDiscoveryDraftUseCase(drafts)(id, owner, isPlatformSuperadmin = false).getOrThrow()
        val draft: DiscoveryDraft = drafts.findById(id)!!.draft
        val slug = "bp-$suffix-ho"
        createdSlugs += slug

        val result = HandoffDiscoveryDraftUseCase(drafts, tenants, packs) { false }(
            id, isPlatformSuperadmin = true, tenantSlug = slug, companyName = "Klinik Handoff"
        ).getOrThrow()
        DomainPackRegistry.unregister(draft.pack.code) // proses baru

        assertEquals(draft.blueprint, result.tenant.businessPreset)
        val reloaded = assertNotNull(tenants.findBySlug(TenantSlug(slug)))
        assertEquals(draft.blueprint, reloaded.businessPreset)
        assertEquals(draft.pack.code, reloaded.domainPack)
        assertTrue(tenants.findAll().any { it.id == reloaded.id })
        assertEquals(listOf(draft.blueprint), packs.findEffective(draft.pack.code)?.pack?.blueprints)
    }
}
