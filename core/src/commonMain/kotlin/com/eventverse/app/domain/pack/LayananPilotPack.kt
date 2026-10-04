package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.EntitySpec
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
 * Spec-nya sengaja memuat kelima [FieldType] supaya keluaran generator untuk setiap tipe benar-benar
 * dikompilasi dan dites, bukan hanya diklaim.
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
        vocabulary = mapOf(VocabularyKey.WORKPLACE to "tim", VocabularyKey.DOCUMENT to "Permintaan")
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
            FieldSpec("catatan", "Catatan", FieldType.TEXT)
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
                form = FormConfig(listOf("judul", "peminta", "prioritas", "status", "perkiraan_jam", "target_selesai", "mendesak", "catatan"), "Simpan permintaan"))
        )
    )
}
