package com.eventverse.app

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.BasisRef
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewAnswer
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleHandoff
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import com.eventverse.app.infrastructure.discovery.InterviewStepGuesses
import kotlinx.coroutines.runBlocking

/**
 * Pack & draf peserta eval wawancara (plan IV-C0). Cermin `InterviewFixtures` milik core — sengaja
 * ditulis ulang di sini karena fixture `commonTest` core tidak ikut classpath test server.
 *
 * Modul bersama `org_chart` diambil dari pack bawaan (identik, sebagaimana registri mensyaratkan) supaya
 * kasus REUSE_PLATFORM bisa dinilai terhadap kebenaran registri, bukan salinan tangan.
 */
object InterviewEvalPacks {

    private val UTAMA = ModuleSectionCode("UTAMA")
    private val IN = PortType("Permintaan")
    private val OUT = PortType("Catatan")

    private fun operational(id: String, name: String, hierarchical: Boolean = true) = ModuleDefinition(
        id = ModuleId(id), displayName = name, description = name, section = UTAMA, kind = ModuleKind.OPERATIONAL,
        iconKey = "clipboard",
        scopeCapability = if (hierarchical) ScopeCapability.HIERARCHICAL else ScopeCapability.GLOBAL_ONLY,
        supportedScopes = if (hierarchical) setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA) else setOf(DataScope.ALL_TENANT_DATA),
        slot = SlotCode(id)
    )

    private fun pack(code: String, name: String, vararg ops: ModuleDefinition): DomainPack {
        val orgChart = requireNotNull(GarmentDomainPack.pack.module(GarmentModules.ORG_CHART)) { "org_chart hilang dari pack bawaan" }
        val govSection = requireNotNull(GarmentDomainPack.pack.sections.firstOrNull { it.code == orgChart.section })
        return DomainPack(
            code = DomainPackCode(code), displayName = name,
            phases = listOf(PhaseDefinition(PhaseCode("OPERASI"), 1, "1. Operasi", "Alur harian", 0xFF2563EB)),
            slots = ops.map { SlotDefinition(it.slot!!, it.displayName, PhaseCode("OPERASI"), IN, OUT) },
            portTypes = setOf(IN, OUT), wiredPortTypes = setOf(IN, OUT),
            sections = listOf(govSection, ModuleSection(UTAMA, "Operasional", 2, 0xFF2563EB, 0xFFEFF6FF)),
            modules = listOf(orgChart) + ops.toList()
        )
    }

    val klinikPack: DomainPack = pack(
        "klinik", "Klinik",
        operational("klinik_pendaftaran", "Pendaftaran"), operational("klinik_poli", "Poli"),
        operational("klinik_kasir", "Kasir", hierarchical = false)
    )

    val bengkelPack: DomainPack = pack(
        "bengkel", "Bengkel",
        operational("bengkel_penerimaan", "Penerimaan Unit"), operational("bengkel_servis", "Servis")
    )

    fun draftOf(pack: DomainPack, session: InterviewSession?): DiscoveryDraft = DiscoveryDraft(
        pack = pack,
        blueprint = Blueprint(
            code = BlueprintCode("${pack.code.value}_starter"), pack = pack.code, displayName = "s", shortBadge = "s",
            description = "s", targetClientProfile = "s", modules = listOf(BlueprintModule(pack.modules.last().id.value, true))
        ),
        interview = session
    )

    /** Narasi kasus — satu sumber kebenaran untuk kutipan basis sesi emas (harus substring cerita). */
    private fun caseNarrative(name: String): String = InterviewGoldenCases.all.first { it.name == name }.narrative

    /** Sesi klinik emas & sah versi berdasar-cerita — kunci jawaban kasus `klinik`; grader wajib memberinya 100%. */
    val klinikSession = InterviewSession(
        step = InterviewStep.DONE,
        divisions = listOf(
            DivisionDraft(DivisionCode("pendaftaran"), "Pendaftaran", ItemSource.GUESS, BasisRef(Basis.NARASI, quote = "pasien mendaftar antrean per poli")),
            DivisionDraft(DivisionCode("poli"), "Poli", ItemSource.GUESS, BasisRef(Basis.NARASI, quote = "antrean per poli")),
            DivisionDraft(DivisionCode("kasir"), "Kasir", ItemSource.ANSWER, BasisRef(Basis.NARASI, quote = "tagihan pembayaran kasir"))
        ),
        roles = listOf(
            RoleDraft(RoleKey("resepsionis"), "Resepsionis", DivisionCode("pendaftaran"), ItemSource.GUESS, isHead = true, basisRef = BasisRef(Basis.NARASI, quote = "pasien mendaftar")),
            RoleDraft(RoleKey("perawat"), "Perawat", DivisionCode("poli"), ItemSource.GUESS, basisRef = BasisRef(Basis.NARASI, quote = "stok obat")),
            RoleDraft(RoleKey("kasir"), "Kasir", DivisionCode("kasir"), ItemSource.ANSWER, basisRef = BasisRef(Basis.NARASI, quote = "pembayaran kasir"))
        ),
        links = listOf(
            RoleModuleLink(RoleKey("resepsionis"), ModuleId("klinik_pendaftaran"), ModuleOrigin.NEW, listOf("Nomor antrean"), Confirmation.CONFIRMED, 80, BasisRef(Basis.NARASI, quote = "mendaftar antrean")),
            RoleModuleLink(RoleKey("perawat"), ModuleId("klinik_poli"), ModuleOrigin.NEW, emptyList(), Confirmation.SKIPPED, 70, BasisRef(Basis.NARASI, quote = "antrean per poli")),
            RoleModuleLink(RoleKey("kasir"), ModuleId("klinik_kasir"), ModuleOrigin.NEW, emptyList(), Confirmation.CHANGED, null, BasisRef(Basis.NARASI, quote = "tagihan pembayaran kasir")),
            RoleModuleLink(RoleKey("resepsionis"), ModuleId("org_chart"), ModuleOrigin.REUSE_PLATFORM, emptyList(), Confirmation.CONFIRMED, null, BasisRef(Basis.JAWABAN, answerId = "g2"))
        ),
        handoffs = listOf(ModuleHandoff(ModuleId("klinik_pendaftaran"), ModuleId("klinik_poli"), PortType("Permintaan"), Confirmation.CONFIRMED, basisRef = BasisRef(Basis.NARASI, quote = "mendaftar antrean per poli"))),
        answers = listOf(
            InterviewAnswer(1, InterviewStep.G1_DIVISI, "g1", Confirmation.CONFIRMED),
            InterviewAnswer(2, InterviewStep.G2_PERAN, "g2", Confirmation.SKIPPED, "terima semua"),
            InterviewAnswer(3, InterviewStep.G3_MODUL, "g3", Confirmation.CONFIRMED),
            InterviewAnswer(4, InterviewStep.G4_SAMBUNGAN, "g4", Confirmation.CONFIRMED)
        ),
        version = InterviewSession.BASED_ON_STORY,
        narrative = caseNarrative("klinik")
    )

    /** Sesi bengkel emas versi berdasar-cerita: satu divisi, satu peran, satu tautan — cerita kecil, draf kecil (kasus negatif C6). */
    val bengkelSession = InterviewSession(
        step = InterviewStep.G3_MODUL,
        divisions = listOf(DivisionDraft(DivisionCode("servis"), "Servis", ItemSource.GUESS, BasisRef(Basis.NARASI, quote = "Bengkel servis motor"))),
        roles = listOf(RoleDraft(RoleKey("mekanik"), "Mekanik", DivisionCode("servis"), ItemSource.GUESS, isHead = true, basisRef = BasisRef(Basis.NARASI, quote = "Bengkel servis motor"))),
        links = listOf(RoleModuleLink(RoleKey("mekanik"), ModuleId("bengkel_servis"), ModuleOrigin.NEW, emptyList(), Confirmation.CONFIRMED, 75, BasisRef(Basis.NARASI, quote = "booking pesanan servis"))),
        answers = listOf(
            InterviewAnswer(1, InterviewStep.G1_DIVISI, "g1", Confirmation.CONFIRMED),
            InterviewAnswer(2, InterviewStep.G2_PERAN, "g2", Confirmation.CONFIRMED)
        ),
        version = InterviewSession.BASED_ON_STORY,
        narrative = caseNarrative("bengkel")
    )
}

/**
 * Fungsi penebak untuk pelari eval — kontrak bentuk plan induk §6 (`InterviewGuesser.guess`).
 * `AgentInterviewGuesser` (C3) menyediakan metode bertanda tangan sama; baseline deterministik B1
 * nanti menyusul tanpa mengubah pelari. Hasilnya hanya **usulan** untuk langkah [InterviewStep] ini.
 */
fun interface InterviewGuessFn {
    suspend fun guess(step: InterviewStep, pack: DomainPack, draft: DiscoveryDraft, narrative: String): Result<InterviewStepGuesses>
}

/**
 * Draf awal wawancara satu kasus: sesi kosong **versi berdasar-cerita** di F0 dengan narasi kasus —
 * titik start alur produksi sejak C6. Klinik/bengkel memakai pack fixture; lainnya dari agent deterministik SP.
 */
fun draftFor(case: InterviewEvalCase): DiscoveryDraft {
    val start = InterviewSession(
        step = InterviewStep.F0_BISNIS,
        version = InterviewSession.BASED_ON_STORY,
        narrative = case.narrative
    )
    return when (case.name) {
        "klinik" -> InterviewEvalPacks.draftOf(InterviewEvalPacks.klinikPack, start)
        "bengkel" -> InterviewEvalPacks.draftOf(InterviewEvalPacks.bengkelPack, start)
        "garment-fob" -> DiscoveryDraft(GarmentDomainPack.pack, GarmentBlueprints.FOB_FULL_PACKAGE, interview = start)
        "garment-cmt" -> DiscoveryDraft(GarmentDomainPack.pack, GarmentBlueprints.CMT_MAKLOON, interview = start)
        "garment-d2c" -> DiscoveryDraft(GarmentDomainPack.pack, GarmentBlueprints.BRAND_D2C, interview = start)
        else -> runBlocking {
            val draft = DeterministicDiscoveryAgent().draft(DiscoveryRequest(case.narrative, case.industryHint)).getOrThrow()
            draft.copy(interview = start)
        }
    }
}
