package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.KanbanHints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C8 (TRD-FIELD-002 Track A, Kontrak 7 — pack non-garment): tipe `FILE` hanya masuk lewat **data pack**
 * (petunjuk dideklarasikan, baris contoh tetap kosong), dan penerjemah usulan **tidak pernah mengarang**
 * petunjuk/field FILE sendiri — kontrol unggah baru ada di Track C.
 */
class PackSuggestionMappingFileTest {

    /** Bentuk persis pilot `layanan` (papan berbinding Api): fields dideklarasikan, baris contoh kosong. */
    @Test
    fun fileHint_declaredByPack_passesThrough_withEmptySeed_andPassesValidation() {
        val suggestion = ScreenSuggestion(
            ModuleId("servis_order"), "Order Servis", WidgetKind.KANBAN,
            kanbanHints = KanbanHints(
                columns = listOf("Baru", "Dikerjakan", "Selesai"),
                groupField = "status",
                fields = listOf(
                    FieldHint("nomor", FieldType.TEXT, required = true),
                    FieldHint("lampiran", FieldType.FILE)
                )
            ),
            rationale = "Order servis melampirkan foto kerusakan; unggah lewat form, bukan baris contoh."
        )

        val proposal = PackSuggestionMapping.map(suggestion).single { it.widget == WidgetKind.KANBAN }
        val lampiran = proposal.entity!!.fields.single { it.key == "lampiran" }
        assertEquals(FieldType.FILE, lampiran.type, "petunjuk tipe FILE diteruskan utuh, bukan ditebak jadi TEXT")
        assertTrue(proposal.seed.isEmpty(), "tanpa baris contoh: seed FILE kosong")
        assertTrue(ScreenProposalValidator.validate(proposal, "$.proposal", null, null).isEmpty(), "usulan hasil pemetaan harus sah")
    }

    @Test
    fun mapping_withoutDeclaredFields_neverInventsFile() {
        val suggestion = ScreenSuggestion(
            ModuleId("bengkel_antrian"), "Antrian Bengkel", WidgetKind.TABLE,
            sampleRows = listOf(
                mapOf("nomor" to "A-1", "plat" to "B 1234 XYZ", "status" to "Menunggu"),
                mapOf("nomor" to "A-2", "plat" to "D 5678 ABC", "status" to "Selesai")
            ),
            rationale = "Antrian dibaca berderet; kolom diturunkan dari baris contoh."
        )

        val proposal = PackSuggestionMapping.map(suggestion).single { it.widget == WidgetKind.TABLE }
        assertTrue(proposal.entity!!.fields.none { it.type == FieldType.FILE }, "kolom turunan tidak pernah jadi FILE")
    }
}
