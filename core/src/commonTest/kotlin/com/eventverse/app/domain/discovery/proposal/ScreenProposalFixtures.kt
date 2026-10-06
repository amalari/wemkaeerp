package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.ColumnMeta
import com.eventverse.app.domain.prototype.CountSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.TileSpec
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability

/**
 * Fixture **non-garment** (klinik) untuk kontrak `ScreenProposal` — tenant-variability Kontrak 6: test yang hanya
 * memakai data konveksi tidak membuktikan apa pun tentang bisnis lain. Tak satu pun istilah konveksi di sini.
 */
object ScreenProposalFixtures {
    val moduleId = ModuleId("klinik_antrean")

    private val status = FieldProposal("status", "Status", FieldType.ENUM, options = listOf("Menunggu", "Diperiksa", "Selesai"))

    val pasien = EntityProposal(
        id = "pasien", label = "Pasien",
        fields = listOf(
            FieldProposal("nama", "Nama pasien", FieldType.TEXT, required = true),
            FieldProposal("keluhan", "Keluhan", FieldType.TEXT),
            FieldProposal("tanggal_kunjungan", "Tanggal kunjungan", FieldType.DATE),
            FieldProposal("prioritas", "Mendesak", FieldType.BOOL),
            status
        ),
        statusField = "status",
        transitions = mapOf("Menunggu" to listOf("Diperiksa"), "Diperiksa" to listOf("Selesai"))
    )

    private val tagihan = EntityProposal(
        id = "tagihan", label = "Tagihan",
        fields = listOf(
            FieldProposal("pasien", "Pasien", FieldType.TEXT, required = true),
            FieldProposal("jumlah", "Jumlah (Rp)", FieldType.NUMBER, required = true),
            FieldProposal("status", "Pembayaran", FieldType.ENUM, options = listOf("Belum bayar", "Lunas"))
        ),
        statusField = "status"
    )

    fun kanbanAntrean() = ScreenProposal(
        screenId = "klinik-antrean-papan", moduleId = moduleId, title = "Antrean pasien", widget = WidgetKind.KANBAN,
        rationale = "Dipilih karena antrean pasien bergerak dari menunggu sampai selesai diperiksa.",
        entity = pasien,
        view = ViewProposal.Kanban(
            card = listOf(
                CardElement("nama", CardStyle.TITLE), CardElement("keluhan", CardStyle.TEXT),
                CardElement("tanggal_kunjungan", CardStyle.DATE), CardElement("prioritas", CardStyle.FLAG)
            ),
            columnMeta = mapOf("Diperiksa" to ColumnMeta(tintHex = 0xFF2563EB, wipLimit = 3)),
            detailFormFields = listOf("nama", "keluhan", "status")
        ),
        seed = listOf(
            mapOf("nama" to "Ibu Sari", "keluhan" to "Demam", "tanggal_kunjungan" to "2026-10-05", "prioritas" to "ya", "status" to "Menunggu"),
            mapOf("nama" to "Pak Budi", "status" to "Diperiksa")
        )
    )

    fun tabelTagihan() = ScreenProposal(
        screenId = "klinik-tagihan", moduleId = moduleId, title = "Tagihan", widget = WidgetKind.TABLE,
        rationale = "Dipilih karena tagihan dicek berderet dan dibandingkan jumlahnya.",
        entity = tagihan,
        view = ViewProposal.Table(columns = listOf("pasien", "jumlah", "status"), inlineCreate = true, editableFields = listOf("jumlah")),
        seed = listOf(mapOf("pasien" to "Ibu Sari", "jumlah" to "150000", "status" to "Belum bayar"))
    )

    fun formPendaftaran() = ScreenProposal(
        screenId = "klinik-daftar", moduleId = moduleId, title = "Pendaftaran pasien", widget = WidgetKind.FORM,
        rationale = "Dipilih karena pendaftaran mengisi beberapa data sekali jalan.",
        entity = pasien,
        view = ViewProposal.Form(fields = listOf("nama", "keluhan", "tanggal_kunjungan", "prioritas", "status"), submitLabel = "Daftarkan")
    )

    fun checklistPersiapan() = ScreenProposal(
        screenId = "klinik-persiapan", moduleId = moduleId, title = "Persiapan ruang", widget = WidgetKind.CHECKLIST,
        rationale = "Dipilih karena persiapan ruang adalah daftar langkah yang dicentang.",
        entity = EntityProposal(
            "persiapan", "Persiapan",
            listOf(FieldProposal("butir", "Butir", FieldType.TEXT, required = true), FieldProposal("selesai", "Selesai", FieldType.BOOL))
        ),
        view = ViewProposal.Checklist("butir", "selesai"),
        seed = listOf(mapOf("butir" to "Sterilkan alat", "selesai" to "ya"))
    )

    fun dasborHarian() = ScreenProposal(
        screenId = "klinik-dasbor", moduleId = moduleId, title = "Ringkasan hari ini", widget = WidgetKind.DASHBOARD,
        rationale = "Dipilih karena pemilik ingin angka ringkas tanpa membuka daftar.",
        entity = null,
        view = ViewProposal.Dashboard(
            listOf(TileSpec("Pasien menunggu", count = CountSpec("klinik_antrean", field = "status", equals = "Menunggu")), TileSpec("Dokter", value = "2"))
        )
    )

    fun cetakKuitansi() = ScreenProposal(
        screenId = "klinik-kuitansi", moduleId = moduleId, title = "Kuitansi", widget = WidgetKind.PRINT,
        rationale = "Dipilih karena kuitansi diserahkan ke pasien dalam bentuk cetak.",
        entity = tagihan, view = ViewProposal.Print(listOf("pasien", "jumlah"))
    )

    fun layarKustom() = ScreenProposal(
        screenId = "klinik-kustom", moduleId = moduleId, title = "Layar kustom", widget = WidgetKind.CUSTOM_SCREEN,
        rationale = "Dipilih karena tata letak ini belum punya jenis baku.", entity = null, view = ViewProposal.None
    )

    fun semua() = listOf(kanbanAntrean(), tabelTagihan(), formPendaftaran(), checklistPersiapan(), dasborHarian(), cetakKuitansi(), layarKustom())

    /** Pack klinik kecil: satu modul `klinik_antrean`, cukup untuk validasi lintas-bagian draf. */
    fun pack(): DomainPack = DomainPack(
        code = DomainPackCode("klinik"), displayName = "Klinik",
        phases = listOf(PhaseDefinition(PhaseCode("OPERASI"), 1, "1. Operasi", "Alur harian", 0xFF2563EB)),
        slots = listOf(SlotDefinition(SlotCode("klinik_antrean"), "Antrean", PhaseCode("OPERASI"), PortType("Permintaan"), PortType("Catatan"))),
        portTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
        wiredPortTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
        sections = listOf(ModuleSection(ModuleSectionCode("UTAMA"), "Operasional", 1, 0xFF2563EB, 0xFFEFF6FF)),
        modules = listOf(
            ModuleDefinition(
                id = moduleId, displayName = "Antrean", description = "Antrean pasien", section = ModuleSectionCode("UTAMA"),
                kind = ModuleKind.OPERATIONAL, iconKey = "clipboard", scopeCapability = ScopeCapability.HIERARCHICAL,
                supportedScopes = setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA), slot = SlotCode("klinik_antrean")
            )
        )
    )

    fun draft(vararg screens: PrototypeScreen): DiscoveryDraft {
        val pack = pack()
        val blueprint = Blueprint(
            code = BlueprintCode("klinik_starter"), pack = pack.code, displayName = "s", shortBadge = "s",
            description = "s", targetClientProfile = "s", modules = listOf(BlueprintModule("klinik_antrean", true))
        )
        return DiscoveryDraft(pack, blueprint, screens.toList())
    }

    /** Layar draf dari usulan: deskriptor mengikuti usulan (satu kebenaran), sumber Deterministik. */
    fun screenOf(p: ScreenProposal, source: ProposalSource? = ProposalSource.Deterministic) =
        PrototypeScreen(p.screenId, p.moduleId, p.title, p.widget.code, p, source)
}
