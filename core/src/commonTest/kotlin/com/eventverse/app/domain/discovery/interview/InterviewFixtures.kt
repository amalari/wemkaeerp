package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
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

/**
 * Fixture **non-garment** (Kontrak 6): klinik dan bengkel. Kosakatanya sengaja bukan konveksi supaya tes kemurnian
 * dan tes "tidak ada kosakata garment" bermakna. Modul bersama `org_chart` diambil dari pack bawaan (identik, sebagaimana
 * registri mensyaratkan) dan dipakai sebagai kasus REUSE_PLATFORM.
 */
object InterviewFixtures {

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
        operational("klinik_pendaftaran", "Pendaftaran"), operational("klinik_poli", "Poli"), operational("klinik_kasir", "Kasir", hierarchical = false)
    )
    val bengkelPack: DomainPack = pack("bengkel", "Bengkel", operational("bengkel_penerimaan", "Penerimaan Unit"), operational("bengkel_servis", "Servis"))

    fun draftOf(pack: DomainPack, session: InterviewSession?): DiscoveryDraft = DiscoveryDraft(
        pack = pack,
        blueprint = Blueprint(
            code = BlueprintCode("${pack.code.value}_starter"), pack = pack.code, displayName = "s", shortBadge = "s",
            description = "s", targetClientProfile = "s", modules = listOf(BlueprintModule(pack.modules.last().id.value, true))
        ),
        interview = session
    )

    /** Wawancara klinik lengkap & sah: tiga divisi, peran per divisi, modul, serah-terima — semua dikonfirmasi. */
    val klinikSession = InterviewSession(
        step = InterviewStep.DONE,
        divisions = listOf(
            DivisionDraft(DivisionCode("pendaftaran"), "Pendaftaran", ItemSource.GUESS),
            DivisionDraft(DivisionCode("poli"), "Poli", ItemSource.GUESS),
            DivisionDraft(DivisionCode("kasir"), "Kasir", ItemSource.ANSWER)
        ),
        roles = listOf(
            RoleDraft(RoleKey("resepsionis"), "Resepsionis", DivisionCode("pendaftaran"), ItemSource.GUESS, isHead = true),
            RoleDraft(RoleKey("perawat"), "Perawat", DivisionCode("poli"), ItemSource.GUESS),
            RoleDraft(RoleKey("kasir"), "Kasir", DivisionCode("kasir"), ItemSource.ANSWER)
        ),
        links = listOf(
            RoleModuleLink(RoleKey("resepsionis"), ModuleId("klinik_pendaftaran"), ModuleOrigin.NEW, listOf("Nomor antrean"), Confirmation.CONFIRMED, 80),
            RoleModuleLink(RoleKey("perawat"), ModuleId("klinik_poli"), ModuleOrigin.NEW, emptyList(), Confirmation.SKIPPED, 70),
            RoleModuleLink(RoleKey("kasir"), ModuleId("klinik_kasir"), ModuleOrigin.NEW, emptyList(), Confirmation.CHANGED),
            RoleModuleLink(RoleKey("resepsionis"), ModuleId("org_chart"), ModuleOrigin.REUSE_PLATFORM, emptyList(), Confirmation.CONFIRMED)
        ),
        handoffs = listOf(ModuleHandoff(ModuleId("klinik_pendaftaran"), ModuleId("klinik_poli"), PortType("Permintaan"), Confirmation.CONFIRMED)),
        answers = listOf(InterviewAnswer(1, InterviewStep.G1_DIVISI, "g1", Confirmation.CONFIRMED), InterviewAnswer(2, InterviewStep.G2_PERAN, "g2", Confirmation.SKIPPED, "terima semua"))
    )

    val bengkelSession = InterviewSession(
        step = InterviewStep.G2_PERAN,
        divisions = listOf(DivisionDraft(DivisionCode("servis"), "Servis", ItemSource.GUESS)),
        roles = listOf(RoleDraft(RoleKey("mekanik"), "Mekanik", DivisionCode("servis"), ItemSource.GUESS, isHead = true))
    )
}
