package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deploy & rollback (FR-M2-1/2/3): pack shipped → ACTIVE + pin versi; rollback append-only dengan
 * gerbang data. Fixture garment (`wemade-demo`); pack kustom dibahas di discovery-M2 §2.
 */
class DeploymentUseCaseTest {

    private val demo = TenantId("ten-wemade-demo")

    private val drafts = FakeTenantDrafts()
    private val deployments = FakeDeploymentRepo()
    private val buildRequests = FakeBuildRequestRepo()
    private val tenants = FakeTenantRepo()
    private val deploy = DeployTenantUseCase(drafts, deployments, buildRequests, tenants)
    private val rollback = RollbackDeploymentUseCase(deployments, FakeProbe())

    private fun seedDraft() = runTest {
        drafts.rows[DiscoveryDraftId("draft-${demo.value}")] = StoredDiscoveryDraft(
            id = DiscoveryDraftId("draft-${demo.value}"),
            ownerUserId = UserId("usr-owner"),
            draft = DiscoveryDraft(pack = GarmentDomainPack.pack, blueprint = GarmentBlueprints.DEFAULT),
            tenantId = demo
        )
    }

    // ---- opsi B: brief beku pada tiap permintaan pembuatan (pack kustom non-garment) -----------------

    private val klinikPack = com.eventverse.app.domain.discovery.interview.InterviewFixtures.klinikPack

    private fun seedKlinikDraft() = runTest {
        val base = com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf(klinikPack, null)
        val blueprint = base.blueprint.copy(modules = listOf(
            com.eventverse.app.domain.blueprint.BlueprintModule("klinik_poli", true),
            com.eventverse.app.domain.blueprint.BlueprintModule("klinik_kasir", true)
        ))
        drafts.rows[DiscoveryDraftId("draft-${demo.value}")] = StoredDiscoveryDraft(
            id = DiscoveryDraftId("draft-${demo.value}"), ownerUserId = UserId("usr-owner"),
            draft = base.copy(blueprint = blueprint), tenantId = demo
        )
    }

    private fun chatMsg(chats: InMemoryBuilderChatRepository, role: ChatRole, text: String, module: String? = null,
                        applied: Boolean = false, summary: List<String> = emptyList()) = runTest {
        val conv = chats.conversationFor(demo)
        chats.append(ChatMessage(ChatMessageId("m-${text.hashCode()}"), conv.id, demo, role, text, moduleId = module,
            appliedDraftId = if (applied) "d1" else null, proposedSummary = summary))
    }

    @Test
    fun deploy_customPack_freezesAModuleScopedBriefWithChatContext_onEachBuildRequest() = runTest {
        seedKlinikDraft()
        val chats = InMemoryBuilderChatRepository()
        chatMsg(chats, ChatRole.USER, "Kami klinik gigi, pasien antre per poli.")
        chatMsg(chats, ChatRole.AGENT, "patch poli", module = "klinik_poli", applied = true, summary = listOf("Tambah isian: Nomor Rekam Medis (text)"))
        chatMsg(chats, ChatRole.AGENT, "patch kasir", module = "klinik_kasir", applied = true, summary = listOf("Tambah isian: Metode Bayar (enum)"))
        val withBriefs = DeployTenantUseCase(drafts, deployments, buildRequests, tenants, briefs = BuildRequestBriefs(chats))

        val dep = withBriefs(demo).getOrThrow()

        assertEquals(DeploymentStatus.BLOCKED_ON_BUILD, dep.status)
        assertEquals(1, dep.packVersion, "regresi: deployment BLOCKED_ON_BUILD wajib membawa versi pack (dulu deploy pack kustom selalu melempar)")
        assertEquals("dep-${demo.value}-1", dep.id.value, "id deployment bersih, bukan hasil toString kelas bernilai")
        assertEquals(setOf("br-${demo.value}-1-klinik_poli", "br-${demo.value}-1-klinik_kasir"), buildRequests.rows.map { it.id.value }.toSet(),
            "regresi: id permintaan dulu berbunyi br-<tenant>-DeploymentNumber(value=1)-<modul>")
        val byModule = buildRequests.rows.associateBy { it.moduleId }
        assertEquals(setOf("klinik_poli", "klinik_kasir"), byModule.keys)
        val poli = requireNotNull(byModule.getValue("klinik_poli").brief) { "tiap permintaan membawa brief beku" }
        assertTrue(poli.markdown.contains("Kami klinik gigi, pasien antre per poli."), "cerita ikut")
        assertTrue(poli.markdown.contains("Nomor Rekam Medis"), "keputusan modul ini ikut")
        assertFalse(poli.markdown.contains("Metode Bayar"), "keputusan modul LAIN tidak bocor ke brief modul ini")
        assertTrue(poli.markdown.contains("perlu dibangun"), "modul kustom ditandai perlu dibangun")
        assertTrue(poli.json.contains("\"context\""), "JSON untuk mesin ikut dibekukan")
        assertTrue(byModule.getValue("klinik_kasir").brief!!.markdown.contains("Metode Bayar"))
    }

    @Test
    fun deploy_customPack_withoutChat_stillGetsBrief_andFailingBriefNeverBlocksDeploy() = runTest {
        seedKlinikDraft()
        val noChat = DeployTenantUseCase(drafts, deployments, buildRequests, tenants, briefs = BuildRequestBriefs(InMemoryBuilderChatRepository()))
        noChat(demo).getOrThrow()
        val brief = requireNotNull(buildRequests.rows.first().brief)
        assertFalse(brief.markdown.contains("Konteks & keputusan"), "tanpa chat tidak ada bagian konteks")

        buildRequests.rows.clear(); deployments.rows.clear(); unlock()
        val exploding = object : BuilderChatRepository by InMemoryBuilderChatRepository() {
            override suspend fun conversationFor(tenantId: TenantId): BuilderConversation = error("DB chat mati")
        }
        val dep = DeployTenantUseCase(drafts, deployments, buildRequests, tenants, briefs = BuildRequestBriefs(exploding))(demo).getOrThrow()
        assertEquals(DeploymentStatus.BLOCKED_ON_BUILD, dep.status, "deploy tetap jalan")
        assertTrue(buildRequests.rows.isNotEmpty() && buildRequests.rows.all { it.brief == null }, "permintaan lahir tanpa brief, bukan gagal")
    }

    @Test
    fun deploy_shippedPack_createsNoBuildRequestsAndNoBrief() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        DeployTenantUseCase(drafts, deployments, buildRequests, tenants, briefs = BuildRequestBriefs(InMemoryBuilderChatRepository()))(demo).getOrThrow()
        assertTrue(buildRequests.rows.isEmpty())
    }

    // ---- opsi 2: deploy ulang = REVISI (menggantikan permintaan tertunda), bukan duplikat -------------------

    private fun redeployFixture(): Pair<InMemoryBuilderChatRepository, DeployTenantUseCase> {
        seedKlinikDraft()
        val chats = InMemoryBuilderChatRepository()
        chatMsg(chats, ChatRole.USER, "Kami klinik gigi, pasien antre per poli.")
        return chats to DeployTenantUseCase(drafts, deployments, buildRequests, tenants, briefs = BuildRequestBriefs(chats))
    }

    @Test
    fun redeploy_customPack_supersedesPendingRequests_andWritesARevisionBriefWithDiff() = runTest {
        val (chats, deployUc) = redeployFixture()
        deployUc(demo).getOrThrow()
        val firstIds = buildRequests.rows.associate { it.moduleId to it.id }
        assertTrue(buildRequests.rows.all { it.briefVersion == 1 && it.supersedes == null })

        // Klien terus beriterasi: keputusan baru pada modul poli, lalu deploy ulang.
        chatMsg(chats, ChatRole.AGENT, "patch poli", module = "klinik_poli", applied = true, summary = listOf("Tambah isian: Nomor Rekam Medis (text)"))
        val second = deployUc(demo).getOrThrow()

        assertEquals(2, second.packVersion)
        val byId = buildRequests.rows.associateBy { it.id }
        val old = firstIds.mapValues { byId.getValue(it.value) }
        val fresh = buildRequests.rows.filter { it.briefVersion == 2 }.associateBy { it.moduleId }
        assertEquals(setOf("klinik_poli", "klinik_kasir"), fresh.keys)
        assertTrue(old.values.all { it.status == BuildRequestStatus.SUPERSEDED }, "yang lama digantikan, bukan dibiarkan menggandakan antrean")
        assertEquals(fresh.mapValues { it.value.id }, old.mapValues { it.value.supersededBy }, "tiap yang lama menunjuk penggantinya")
        assertTrue(fresh.values.all { it.status == BuildRequestStatus.QUEUED && it.supersedes == firstIds[it.moduleId] })
        assertTrue(fresh.getValue("klinik_poli").reason.startsWith("Merevisi ${firstIds.getValue("klinik_poli").value} (status sebelumnya QUEUED)"))

        val poliMd = fresh.getValue("klinik_poli").brief!!.markdown
        assertTrue(poliMd.contains("## Revisi brief") && poliMd.contains("Versi 2 — menggantikan `${firstIds.getValue("klinik_poli").value}`"))
        assertTrue(poliMd.substringAfter("**Ditambahkan**").contains("Tambah isian: Nomor Rekam Medis (text)"), "selisih menyebut yang baru")
        assertTrue(fresh.getValue("klinik_kasir").brief!!.markdown.contains("Isi tidak berubah dibanding versi sebelumnya."), "modul yang tak berubah dinyatakan eksplisit")
        assertTrue(old.getValue("klinik_poli").brief!!.markdown.contains("Revisi brief").not(), "snapshot lama tetap beku, tidak ditulis ulang")

        val deps = deployments.rows.associateBy { it.number.value }
        assertEquals(DeploymentStatus.SUPERSEDED, deps.getValue(1).status, "deployment tertahan lama digantikan")
        assertEquals(DeploymentStatus.BLOCKED_ON_BUILD, deps.getValue(2).status)
    }

    @Test
    fun redeploy_keepsFinishedWork_flagsInProgress_andDropsModulesNoLongerActive() = runTest {
        val (_, deployUc) = redeployFixture()
        deployUc(demo).getOrThrow()
        val poli = buildRequests.rows.first { it.moduleId == "klinik_poli" }
        val kasir = buildRequests.rows.first { it.moduleId == "klinik_kasir" }
        buildRequests.save(poli.copy(status = BuildRequestStatus.IN_PROGRESS))      // developer sedang mengerjakan
        buildRequests.save(kasir.copy(status = BuildRequestStatus.SHIPPED))          // sudah selesai

        // Blueprint kini hanya poli (kasir dibuang dari rencana).
        val key = DiscoveryDraftId("draft-${demo.value}")
        val stored = drafts.rows.getValue(key)
        drafts.rows[key] = stored.copy(draft = stored.draft.copy(blueprint = stored.draft.blueprint.copy(
            modules = listOf(com.eventverse.app.domain.blueprint.BlueprintModule("klinik_poli", true)))))
        deployUc(demo).getOrThrow()

        val now = buildRequests.rows.associateBy { it.id }
        assertEquals(BuildRequestStatus.SUPERSEDED, now.getValue(poli.id).status)
        val revised = buildRequests.rows.first { it.briefVersion == 2 }
        assertTrue(revised.reason.contains("status sebelumnya IN_PROGRESS"), "operator tahu developer sudah mulai bekerja: ${revised.reason}")
        assertEquals(BuildRequestStatus.SHIPPED, now.getValue(kasir.id).status, "yang sudah selesai tidak disentuh")
        assertEquals(1, buildRequests.rows.count { it.moduleId == "klinik_kasir" }, "modul yang tak lagi aktif tidak dibuatkan permintaan baru")
    }

    @Test
    fun redeploy_pendingRequestOfRemovedModule_isDroppedWithoutReplacement() = runTest {
        val (_, deployUc) = redeployFixture()
        deployUc(demo).getOrThrow()
        val key = DiscoveryDraftId("draft-${demo.value}")
        val stored = drafts.rows.getValue(key)
        drafts.rows[key] = stored.copy(draft = stored.draft.copy(blueprint = stored.draft.blueprint.copy(
            modules = listOf(com.eventverse.app.domain.blueprint.BlueprintModule("klinik_poli", true)))))
        deployUc(demo).getOrThrow()
        val kasir = buildRequests.rows.single { it.moduleId == "klinik_kasir" }
        assertEquals(BuildRequestStatus.SUPERSEDED, kasir.status)
        assertEquals(null, kasir.supersededBy, "digugurkan tanpa pengganti")
    }

    private fun unlock() {
        val key = DiscoveryDraftId("draft-${demo.value}")
        drafts.rows[key] = drafts.rows.getValue(key).copy(status = DiscoveryDraftStatus.DRAFT)
    }

    @Test
    fun deploy_shippedPack_activates_andPinsVersionAndTenant() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)

        val deployment = deploy(demo).getOrThrow()

        assertEquals(DeploymentStatus.ACTIVE, deployment.status)
        assertEquals(1, deployment.packVersion)
        assertEquals(1, tenants.rows[demo]?.domainPackVersion, "versi pack dipin di tenant (kolom V81)")
        // Go-live (V89): tenant TETAP TRIAL — jam trial aplikasi baru dimulai di sini.
        assertEquals(TenantStatus.TRIAL, tenants.rows[demo]?.status, "status tetap TRIAL; ACTIVE = pembayaran")
        assertTrue(tenants.rows[demo]?.trialEndsAt != null, "go-live memulai jam trial 14 hari")
        assertEquals(DiscoveryDraftStatus.LOCKED, drafts.rows[DiscoveryDraftId("draft-${demo.value}")]?.status)
    }

    @Test
    fun redeploy_does_not_reset_the_trial_clock() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        deploy(demo).getOrThrow()
        val firstEndsAt = tenants.rows[demo]?.trialEndsAt

        unlock()
        deploy(demo).getOrThrow()

        assertEquals(firstEndsAt, tenants.rows[demo]?.trialEndsAt, "deploy ulang tidak mengatur ulang jam")
    }

    @Test
    fun deploy_lockedDraft_isRejected() = runTest {
        seedDraft()
        val key = DiscoveryDraftId("draft-${demo.value}")
        drafts.rows[key] = drafts.rows.getValue(key).copy(status = DiscoveryDraftStatus.LOCKED)

        assertTrue(deploy(demo).isFailure, "draf beku tidak bisa di-deploy ulang tanpa revisi")
    }


    @Test
    fun deploy_twice_supersedesPrevious_andIncrementsVersion() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        deploy(demo).getOrThrow()
        unlock()

        val second = deploy(demo).getOrThrow()
        assertEquals(2, second.packVersion, "versi pack = versi terakhir + 1")
        assertEquals(2, deployments.rows.count { it.tenantId == demo }, "dua deployment: #1 dan #2 (append-only)")
        assertEquals(DeploymentStatus.SUPERSEDED, deployments.rows.first { it.number.value == 1 }.status, "deployment #1 di-supersede")
    }

    @Test
    fun rollback_pinsPreviousVersion_appendOnly() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        deploy(demo).getOrThrow()
        unlock()
        deploy(demo).getOrThrow()

        rollback(demo).getOrThrow()

        val rows = deployments.rows.sortedBy { it.number.value }
        assertEquals(DeploymentStatus.ROLLED_BACK, rows.last { it.number.value == 2 }.status)
        assertEquals(DeploymentStatus.ACTIVE, rows.first { it.number.value == 1 }.status, "versi 1 aktif kembali")
    }

    @Test
    fun deploy_overImportedSnapshot_supersedesWithInheritedVersion() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        // Snapshot pra-Builder (V81): IMPORTED #1 tanpa packVersion.
        deployments.save(
            Deployment(
                id = DeploymentId("dep-${demo.value}-1"),
                tenantId = demo,
                number = DeploymentNumber(1),
                packCode = GarmentDomainPack.pack.code,
                appBuild = "build-uji",
                status = DeploymentStatus.IMPORTED
            )
        ).getOrThrow()

        val deployment = deploy(demo).getOrThrow()

        assertEquals(2, deployment.number.value)
        assertEquals(DeploymentStatus.ACTIVE, deployment.status)
        assertEquals(
            DeploymentStatus.SUPERSEDED,
            deployments.rows.first { it.number.value == 1 }.status,
            "snapshot pra-Builder di-supersede dengan mewarisi versi terkunci"
        )
        assertTrue(deployments.rows.first { it.number.value == 1 }.packVersion != null)
    }

    @Test
    fun rollback_withoutPreviousPackVersion_isRejected() = runTest {
        seedDraft()
        tenants.rows[demo] = tenant(status = TenantStatus.TRIAL)
        deploy(demo).getOrThrow()

        assertTrue(rollback(demo).isFailure, "snapshot #1 tidak punya packVersion — tidak ada yang di-pin")
    }


    private fun tenant(status: TenantStatus) = Tenant(
        id = demo,
        slug = TenantSlug("wemade-demo"),
        name = TenantName("Pabrik Uji"),
        status = status
    )

    private class FakeProbe : TenantOperationalDataProbe {
        override suspend fun hasOperationalData(tenantId: TenantId) = false
    }

    private class FakeTenantDrafts : DiscoveryDraftRepository {
        val rows = mutableMapOf<DiscoveryDraftId, StoredDiscoveryDraft>()

        override suspend fun findById(id: DiscoveryDraftId) = rows[id]
        override suspend fun findByOwner(owner: UserId) = rows.values.filter { it.ownerUserId == owner }
        override suspend fun findByTenant(tenantId: TenantId) = rows.values.lastOrNull { it.tenantId == tenantId }
        override suspend fun findAll() = rows.values.toList()
        override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft {
            rows[stored.id] = stored
            return stored
        }
    }

    /** Invarian sama dengan Postgres: tepat satu deployment aktif per tenant. */
    private class FakeDeploymentRepo : BuilderDeploymentRepository {
        val rows = mutableListOf<Deployment>()

        override suspend fun findByTenant(tenantId: TenantId) =
            rows.filter { it.tenantId == tenantId }.sortedByDescending { it.number.value }

        override suspend fun findActive(tenantId: TenantId) =
            findByTenant(tenantId).firstOrNull { it.isActive }

        override suspend fun nextNumber(tenantId: TenantId) =
            DeploymentNumber((findByTenant(tenantId).maxOfOrNull { it.number.value } ?: 0) + 1)

        override suspend fun save(deployment: Deployment): Result<Deployment> = runCatching {
            val otherActive = rows.any {
                it.tenantId == deployment.tenantId && it.id != deployment.id && it.isActive
            }
            if (deployment.isActive && otherActive) {
                throw DeploymentConflictException("Tenant ${deployment.tenantId.value} sudah punya deployment aktif")
            }
            rows.removeAll { it.id == deployment.id }
            rows.add(deployment)
            deployment
        }
    }

    private class FakeBuildRequestRepo : BuilderBuildRequestRepository {
        val rows = mutableListOf<BuildRequest>()
        override suspend fun findByTenant(tenantId: TenantId) = rows.filter { it.tenantId == tenantId }
        override suspend fun findAll() = rows.toList()
        override suspend fun save(request: BuildRequest): BuildRequest {
            rows.removeAll { it.id == request.id }   // upsert per id, seperti Postgres
            rows.add(request)
            return request
        }
    }

    private class FakeTenantRepo : TenantRepository {
        val rows = mutableMapOf<TenantId, Tenant>()
        override suspend fun findById(id: TenantId) = rows[id]
        override suspend fun findBySlug(slug: TenantSlug) = rows.values.firstOrNull { it.slug == slug }
        override suspend fun save(tenant: Tenant): Result<Tenant> = runCatching { rows[tenant.id] = tenant; tenant }
        override suspend fun existsBySlug(slug: TenantSlug) = rows.values.any { it.slug == slug }
        override suspend fun findAll() = rows.values.toList()
    }
}
