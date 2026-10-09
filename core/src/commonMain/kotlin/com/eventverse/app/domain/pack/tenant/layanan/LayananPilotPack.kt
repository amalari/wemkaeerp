package com.eventverse.app.domain.pack.tenant.layanan

import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.domain.pack.ModuleAction
import com.eventverse.app.domain.pack.ModuleActionCode
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.ColumnMeta
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.KanbanHints
import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.FormConfig
import com.eventverse.app.domain.prototype.KanbanConfig
import com.eventverse.app.domain.prototype.PrototypeSpec
import com.eventverse.app.domain.prototype.ScreenSpec
import com.eventverse.app.domain.prototype.StateMachine
import com.eventverse.app.domain.prototype.TableConfig
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability

/**
 * Pack **data** pilot Jalur C (PLAN-proto-C): modul netral industri "Permintaan Perubahan" untuk
 * membuktikan `HandoffScaffoldGenerator.generateFromSpec`. Dipakai tim software house sendiri melacak
 * permintaan customisasi klien. **Bukan** pack bawaan platform: ia didaftarkan lewat
 * [DomainPackRegistry.register] (prefiks `layanan_` wajib, lihat `violations`), bukan masuk `shipped`.
 *
 * Spec-nya sengaja memuat enam [FieldType] — TEXT, ENUM, NUMBER, DATE, BOOL, dan **FILE** (`lampiran`:
 * permintaan perubahan membawa berkas lampiran; seed-nya wajib kosong, unggah nyata lewat Track B/C) —
 * supaya keluaran generator untuk setiap tipe benar-benar dikompilasi dan dites, bukan hanya diklaim.
 * Konteks kedua non-garment untuk tipe FILE (Kontrak 7 variability, TRD-FIELD-002 Track A).
 */
object LayananPilotPack {
    val CODE = DomainPackCode("layanan")
    val CHANGE_REQUEST = ModuleId("layanan_change_request")

    private val section = ModuleSection(ModuleSectionCode("UTAMA"), "Layanan", 1, 0xFF2563EB, 0xFFEFF6FF)
    private val phase = PhaseDefinition(PhaseCode("OPERASI"), 1, "1. Operasi", "Permintaan masuk dan penyelesaiannya", 0xFF2563EB)

    val module = ModuleDefinition(
        id = CHANGE_REQUEST,
        displayName = "Permintaan Perubahan",
        description = "Melacak permintaan customisasi dari klien: dari masuk, ditinjau, disetujui, sampai selesai.",
        section = section.code,
        kind = ModuleKind.OPERATIONAL,
        iconKey = "clipboard",
        scopeCapability = ScopeCapability.GLOBAL_ONLY,
        supportedScopes = setOf(DataScope.ALL_TENANT_DATA),
        slot = SlotCode("layanan_change_request")
    )

    val pack: DomainPack = DomainPack(
        code = CODE,
        displayName = "Layanan",
        phases = listOf(phase),
        slots = listOf(SlotDefinition(SlotCode("layanan_change_request"), "Permintaan Perubahan", phase.code, PortType("Permintaan"), PortType("Catatan"))),
        portTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
        wiredPortTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
        sections = listOf(section),
        modules = listOf(module),
        actions = listOf(
            ModuleAction(ModuleActionCode.ADD, "Tambah Permintaan"),
            ModuleAction(ModuleActionCode.EDIT, "Ubah Permintaan"),
            ModuleAction(ModuleActionCode.APPROVE, "Setujui Permintaan"),
            ModuleAction(ModuleActionCode.DELETE, "Hapus Permintaan")
        ),
        vocabulary = mapOf(VocabularyKey.WORKPLACE to "tim", VocabularyKey.DOCUMENT to "Permintaan"),
        screenSuggestions = listOf(boardSuggestion())
    )

    /** Basis route CRUD hasil generator (V90); sama dengan `LayananChangeRequestRoutes`. */
    const val API_BASE_PATH = "/api/tenant/modules/layanan_change_request/change_requests"

    /**
     * Papan pilot berbinding [DataBinding.Api]: tanpa baris contoh (data dari server), field
     * dideklarasikan dengan tipe sebenarnya (B2.1) agar kunci baris server (`status`, `judul`, …) cocok.
     * Mesin status dan opsi mengikuti [entity]; [PilotBoardParityTest] menjaga keduanya tak menyimpang.
     */
    private fun boardSuggestion() = ScreenSuggestion(
        moduleId = CHANGE_REQUEST,
        title = "Papan Permintaan",
        widget = WidgetKind.KANBAN,
        kanbanHints = KanbanHints(
            columns = listOf("Baru", "Ditinjau", "Disetujui", "Selesai"),
            transitions = mapOf(
                "Baru" to setOf("Ditinjau"),
                "Ditinjau" to setOf("Baru", "Disetujui"),
                "Disetujui" to setOf("Ditinjau", "Selesai"),
                "Selesai" to setOf("Disetujui")
            ),
            groupLabel = "Status",
            groupField = "status",
            fields = listOf(
                FieldHint("judul", FieldType.TEXT, required = true),
                FieldHint("peminta", FieldType.TEXT),
                FieldHint("prioritas", FieldType.ENUM, options = listOf("Rendah", "Sedang", "Tinggi")),
                FieldHint("perkiraan_jam", FieldType.NUMBER),
                FieldHint("target_selesai", FieldType.DATE),
                FieldHint("mendesak", FieldType.BOOL),
                FieldHint("catatan", FieldType.TEXT),
                FieldHint("lampiran", FieldType.FILE)
            ),
            card = listOf(
                CardElement("judul", CardStyle.TITLE),
                CardElement("prioritas", CardStyle.BADGE),
                CardElement("peminta", CardStyle.TEXT),
                CardElement("target_selesai", CardStyle.DATE),
                CardElement("mendesak", CardStyle.FLAG)
            ),
            columnMeta = mapOf("Ditinjau" to ColumnMeta(wipLimit = 5)),
            detailForm = FormConfig(listOf("judul", "peminta", "prioritas", "perkiraan_jam", "target_selesai", "mendesak", "catatan", "lampiran"), "Simpan")
        ),
        dataBinding = DataBinding.Api(API_BASE_PATH)
    )

    val entity = EntitySpec(
        id = "change_request",
        label = "Permintaan Perubahan",
        fields = listOf(
            FieldSpec("judul", "Judul", FieldType.TEXT, required = true),
            FieldSpec("peminta", "Peminta", FieldType.TEXT),
            FieldSpec("prioritas", "Prioritas", FieldType.ENUM, listOf("Rendah", "Sedang", "Tinggi")),
            FieldSpec("status", "Status", FieldType.ENUM, listOf("Baru", "Ditinjau", "Disetujui", "Selesai"), required = true),
            FieldSpec("perkiraan_jam", "Perkiraan jam", FieldType.NUMBER),
            FieldSpec("target_selesai", "Target selesai", FieldType.DATE),
            FieldSpec("mendesak", "Mendesak", FieldType.BOOL),
            FieldSpec("catatan", "Catatan", FieldType.TEXT),
            // C8 (TRD-FIELD-002): lampiran = FILE, konteks kedua non-garment. Tidak wajib, dan seed
            // papan kosong (binding Api) — unggah nyata lewat endpoint Track B, bukan baris contoh.
            FieldSpec("lampiran", "Lampiran", FieldType.FILE)
        ),
        stateMachine = StateMachine(
            "status",
            mapOf(
                "Baru" to setOf("Ditinjau"),
                "Ditinjau" to setOf("Baru", "Disetujui"),
                "Disetujui" to setOf("Ditinjau", "Selesai"),
                "Selesai" to setOf("Disetujui")
            )
        )
    )

    /** Spec prototype modul: papan status, daftar, dan form — sumber generator handoff. */
    val spec = PrototypeSpec(
        listOf(entity),
        listOf(
            ScreenSpec("papan", "Papan Permintaan", WidgetKind.KANBAN, "change_request",
                kanban = KanbanConfig("status", listOf("Baru", "Ditinjau", "Disetujui", "Selesai"), "judul", listOf("peminta", "prioritas"))),
            ScreenSpec("daftar", "Daftar Permintaan", WidgetKind.TABLE, "change_request",
                table = TableConfig(listOf("judul", "peminta", "prioritas", "status", "target_selesai"), "status")),
            ScreenSpec("form", "Tambah Permintaan", WidgetKind.FORM, "change_request",
                form = FormConfig(listOf("judul", "peminta", "prioritas", "status", "perkiraan_jam", "target_selesai", "mendesak", "catatan", "lampiran"), "Simpan permintaan"))
        )
    )
}
