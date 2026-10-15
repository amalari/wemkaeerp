package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.InterviewFixtures
import com.eventverse.app.domain.discovery.interview.ModuleHandoff
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/** Fixture skenario: pack klinik (NON-garment, Kontrak 6) dengan tiga modul aktif dan layar berproposal. */
object ScenarioTestFixtures {
    val tenant = TenantId("bordir-uji")
    const val DAFTAR = "klinik_pendaftaran"
    const val POLI = "klinik_poli"
    const val KASIR = "klinik_kasir"

    fun field(key: String, label: String, type: FieldType, options: List<String> = emptyList()) =
        FieldProposal(key, label, type, options = options)

    fun screen(screenId: String, module: String, vararg fields: FieldProposal) = PrototypeScreen(
        screenId = screenId, moduleId = ModuleId(module), title = screenId, widget = "FORM",
        proposal = ScreenProposal(
            screenId, ModuleId(module), screenId, WidgetKind.FORM, "uji",
            EntityProposal("e_$screenId", screenId, fields.toList()), ViewProposal.Form(fields.map { it.key })
        ),
        source = ProposalSource.Deterministic
    )

    val daftarScreen = screen(
        "s_daftar", DAFTAR,
        field("nama", "Nama", FieldType.TEXT), field("keluhan", "Keluhan", FieldType.LONG_TEXT),
        field("umur", "Umur", FieldType.NUMBER), field("jenis", "Jenis", FieldType.ENUM, listOf("umum", "bpjs"))
    )
    val poliScreen = screen(
        "s_poli", POLI,
        field("nama", "Nama", FieldType.TEXT), field("catatan", "Keluhan", FieldType.LONG_TEXT),
        field("umur", "Umur", FieldType.NUMBER), field("jenis", "Jenis", FieldType.ENUM, listOf("umum", "bpjs")),
        field("tindakan", "Tindakan", FieldType.TEXT)
    )
    val kasirScreen = screen(
        "s_kasir", KASIR,
        field("pasien", "Pasien", FieldType.TEXT), field("total", "Total", FieldType.NUMBER), field("tindakan", "Tindakan", FieldType.TEXT)
    )

    fun handoff(from: String, to: String) = ModuleHandoff(ModuleId(from), ModuleId(to), PortType("Permintaan"), Confirmation.CONFIRMED)

    fun draftOf(
        pack: DomainPack = InterviewFixtures.klinikPack,
        screens: List<PrototypeScreen> = listOf(daftarScreen, poliScreen, kasirScreen),
        handoffs: List<ModuleHandoff> = listOf(handoff(DAFTAR, POLI), handoff(POLI, KASIR)),
        scenarios: List<ScenarioSketch> = emptyList(),
        active: List<String> = listOf(DAFTAR, POLI, KASIR)
    ): DiscoveryDraft = DiscoveryDraft(
        pack = pack,
        blueprint = Blueprint(
            code = BlueprintCode("${pack.code.value}_starter"), pack = pack.code, displayName = "s", shortBadge = "s",
            description = "s", targetClientProfile = "s", modules = active.map { BlueprintModule(it, true) }
        ),
        screens = screens,
        interview = InterviewFixtures.klinikSession.copy(handoffs = handoffs),
        scenarios = scenarios
    )

    fun step(module: String, screen: String?, label: String = module, carries: List<FieldCarry> = emptyList(), reworkTo: Int? = null) =
        ScenarioStep(ModuleId(module), screen, label, carries, reworkTo)

    fun carry(from: String, to: String) = FieldCarry(from, to)

    /** Skenario sah di [draftOf]: daftar -> poli -> kasir. */
    fun validScenario(
        id: String = "scn-1", source: ScenarioSource = ScenarioSource.AI, confirmed: Boolean = false
    ) = ScenarioSketch(
        ScenarioId(id), "Pasien sampai bayar",
        listOf(
            step(DAFTAR, "s_daftar", "Daftar", listOf(carry("nama", "nama"), carry("keluhan", "catatan"))),
            step(POLI, "s_poli", "Periksa", listOf(carry("tindakan", "tindakan"))),
            step(KASIR, "s_kasir", "Bayar")
        ),
        source, confirmed
    )

    fun pathsOf(issues: List<ScenarioIssue>) = issues.map { it.path }
}

/** Repo draf in-memory; mencatat jumlah `save` untuk tes "tidak menulis". */
class FakeDraftRepository(initial: StoredDiscoveryDraft? = null) : DiscoveryDraftRepository {
    var stored: StoredDiscoveryDraft? = initial
    var saves = 0

    override suspend fun findById(id: DiscoveryDraftId) = stored?.takeIf { it.id == id }
    override suspend fun findByOwner(ownerUserId: UserId) = listOfNotNull(stored).filter { it.ownerUserId == ownerUserId }
    override suspend fun findByTenant(tenantId: TenantId) = stored?.takeIf { it.tenantId == tenantId }
    override suspend fun findAll() = listOfNotNull(stored)
    override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft { saves++; this.stored = stored; return stored }

    companion object {
        fun of(draft: DiscoveryDraft, status: DiscoveryDraftStatus = DiscoveryDraftStatus.DRAFT) = FakeDraftRepository(
            StoredDiscoveryDraft(
                DiscoveryDraftId("d-1"), UserId("owner-1"), draft, status,
                tenantId = ScenarioTestFixtures.tenant, updatedAt = Instant.fromEpochSeconds(0)
            )
        )
    }
}

/** Pengusul palsu: mengembalikan jawaban berurutan, mencatat permintaan. */
class FakeProposer(private val responses: List<Result<List<ScenarioSketch>>>) : ScenarioProposer {
    constructor(first: Result<List<ScenarioSketch>>, second: Result<List<ScenarioSketch>>? = null) : this(listOfNotNull(first, second))
    constructor() : this(listOf(Result.success(emptyList())))

    val requests = mutableListOf<ScenarioProposalRequest>()
    override suspend fun propose(request: ScenarioProposalRequest): Result<List<ScenarioSketch>> {
        requests += request
        return responses.getOrElse(requests.size - 1) { responses.last() }
    }
}
