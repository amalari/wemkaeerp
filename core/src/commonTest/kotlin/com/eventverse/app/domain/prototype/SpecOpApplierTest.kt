package com.eventverse.app.domain.prototype

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Butir B3: lima operasi spec diuji pada **dua template** (tiket servis = non-garment, SPK sampling =
 * garment) sesuai Kontrak 6. Spesifikasi hasil wajib tetap lolos validasi konstruktornya.
 */
class SpecOpApplierTest {
    private val ticket = PrototypeContractSamples.ticketScreen
    private val garment = InteractiveScreenFactory.kanban(
        "spk", "SPK Sampling",
        listOf(mapOf("Kolom" to "Baru", "Kartu" to "SP-1"), mapOf("Kolom" to "Selesai", "Kartu" to "SP-2")),
        KanbanHints(listOf("Baru", "Dikerjakan", "Selesai"), mapOf("Baru" to setOf("Dikerjakan")))
    )!!

    // ---- AddEnumOption ---------------------------------------------------------------------

    @Test
    fun addEnumOption_insertsAfterPosition_andUpdatesKanbanColumns() {
        val next = SpecOpApplier.apply(garment, SpecOp.AddEnumOption("item", "Kolom", "Pressing", after = "Dikerjakan")).getOrThrow()
        assertEquals(listOf("Baru", "Dikerjakan", "Pressing", "Selesai"), next.spec.entity("item")!!.field("Kolom")!!.options)
        assertEquals(listOf("Baru", "Dikerjakan", "Pressing", "Selesai"), next.spec.screens.single().kanban!!.columns)
        assertEquals("Selesai", next.seed.getValue("item").last()["Kolom"], "seed tak tersentuh oleh penambahan")
    }

    @Test
    fun addEnumOption_appendsAtEnd_whenAfterNull_andWorksOnBothTemplates() {
        listOf(ticket, garment).forEach { screen ->
            val entityId = screen.spec.entities.single().id
            val field = screen.spec.entity(entityId)!!.fields.first { it.type == FieldType.ENUM }
            val next = SpecOpApplier.apply(screen, SpecOp.AddEnumOption(entityId, field.key, "Ekstra")).getOrThrow()
            assertEquals(field.options + "Ekstra", next.spec.entity(entityId)!!.field(field.key)!!.options)
        }
    }

    @Test
    fun addEnumOption_rejectsDuplicateUnknownAfterUnknownEntity() {
        assertTrue(SpecOpApplier.apply(garment, SpecOp.AddEnumOption("item", "Kolom", "Baru")).isFailure, "opsi ganda ditolak")
        assertTrue(SpecOpApplier.apply(garment, SpecOp.AddEnumOption("item", "Kolom", "X", after = "Hantu")).isFailure, "'after' tak dikenal ditolak")
        assertTrue(SpecOpApplier.apply(garment, SpecOp.AddEnumOption("hantu", "Kolom", "X")).isFailure, "entitas tak dikenal ditolak")
        assertTrue(SpecOpApplier.apply(garment, SpecOp.AddEnumOption("item", "Kartu", "X")).isFailure, "field non-ENUM ditolak")
    }
    // ---- RenameEnumOption ------------------------------------------------------------------

    @Test
    fun renameEnumOption_rewritesFieldColumnsMachineAndSeed() {
        val next = SpecOpApplier.apply(garment, SpecOp.RenameEnumOption("item", "Kolom", "Selesai", "Arsip")).getOrThrow()
        assertEquals(listOf("Baru", "Dikerjakan", "Arsip"), next.spec.entity("item")!!.field("Kolom")!!.options)
        assertEquals(listOf("Baru", "Dikerjakan", "Arsip"), next.spec.screens.single().kanban!!.columns)
        val sm = assertNotNull(next.spec.entity("item")!!.stateMachine)
        assertTrue(sm.allows("Baru", "Dikerjakan") && !sm.allows("Baru", "Selesai"))
        assertEquals("Arsip", next.seed.getValue("item").last()["Kolom"], "seed ikut ditulis ulang")
    }

    @Test
    fun renameEnumOption_rejectsUnknownFromAndExistingTo() {
        assertTrue(SpecOpApplier.apply(garment, SpecOp.RenameEnumOption("item", "Kolom", "Hantu", "X")).isFailure)
        assertTrue(SpecOpApplier.apply(garment, SpecOp.RenameEnumOption("item", "Kolom", "Selesai", "Baru")).isFailure)
    }

    // ---- AddTransition ---------------------------------------------------------------------

    @Test
    fun addTransition_onlyBetweenExistingOptions_andReducerThenAllowsTheMove() {
        val store = garment.newStore()
        assertTrue(PrototypeReducer.moveCard(garment.spec, store, "item", "spk-1", "Kolom", "Selesai").isFailure, "sebelum operasi masih ditolak")
        val next = SpecOpApplier.apply(garment, SpecOp.AddTransition("item", "Kolom", "Baru", "Selesai")).getOrThrow()
        assertTrue(PrototypeReducer.moveCard(next.spec, store, "item", "spk-1", "Kolom", "Selesai").isSuccess, "setelah operasi dilalui reducer")
    }

    @Test
    fun addTransition_rejectsUnknownOptionsSameStatusAndMissingMachine() {
        assertTrue(SpecOpApplier.apply(garment, SpecOp.AddTransition("item", "Kolom", "Baru", "Hantu")).isFailure)
        assertTrue(SpecOpApplier.apply(garment, SpecOp.AddTransition("item", "Kolom", "Baru", "Baru")).isFailure, "pindah ke status sama memang selalu boleh")
        val plain = assertNotNull(InteractiveScreenFactory.kanban("p", "P", listOf(mapOf("Kolom" to "A", "Judul" to "x"))))
        assertTrue(SpecOpApplier.apply(plain, SpecOp.AddTransition("item", "Kolom", "Baru", "A")).isFailure, "tanpa mesin status tidak ada aturan untuk ditambah")
    }
    // ---- AddField & RenameFieldLabel -------------------------------------------------------

    @Test
    fun addField_addsToEntityOnly_screensUnchanged_documentedDecision() {
        val next = SpecOpApplier.apply(ticket, SpecOp.AddField("tiket", FieldSpec("Prioritas", "Prioritas", FieldType.ENUM, listOf("Rendah", "Tinggi")))).getOrThrow()
        assertNotNull(next.spec.entity("tiket")!!.field("Prioritas"))
        assertEquals(ticket.spec.screens, next.spec.screens, "keputusan B3: layar tidak berubah otomatis")
        assertTrue(SpecOpApplier.apply(ticket, SpecOp.AddField("tiket", FieldSpec("Judul", "Judul", FieldType.TEXT))).isFailure, "field ganda ditolak")
    }

    @Test
    fun renameFieldLabel_onlyChangesDisplayLabel_keyStays() {
        val next = SpecOpApplier.apply(ticket, SpecOp.RenameFieldLabel("tiket", "Peminta", "Dilaporkan oleh")).getOrThrow()
        val f = assertNotNull(next.spec.entity("tiket")!!.field("Peminta"))
        assertEquals("Dilaporkan oleh", f.label)
        assertTrue(SpecOpApplier.apply(ticket, SpecOp.RenameFieldLabel("tiket", "Hantu", "X")).isFailure)
        assertTrue(SpecOpApplier.apply(ticket, SpecOp.RenameFieldLabel("tiket", "Peminta", " ")).isFailure)
    }

    // ---- applyAll: yang gagal tidak membatalkan yang sah ------------------------------------

    @Test
    fun applyAll_keepsApplyingValidOps_afterAFailedOne() {
        val ops = listOf(
            SpecOp.AddEnumOption("item", "Kolom", "Pressing", after = "Selesai"),
            SpecOp.AddTransition("item", "Kolom", "Baru", "Hantu"),
            SpecOp.RenameFieldLabel("item", "Kartu", "No. SPK")
        )
        val applied = SpecOpApplier.applyAll(garment, ops, "2026-10-04T11:00:00Z")
        assertTrue(applied.log[0].ok && !applied.log[1].ok && applied.log[2].ok)
        assertEquals("No. SPK", applied.screen.spec.entity("item")!!.field("Kartu")!!.label)
        assertEquals(listOf("Baru", "Dikerjakan", "Selesai", "Pressing"), applied.screen.spec.screens.single().kanban!!.columns)
    }

    /** C3 Irisan 2: LONG_TEXT tipe satu kelarga — sah lewat SpecOp.AddField, label bisa diganti. */
    @Test
    fun addField_longText_typePreserved_labelRenameable_duplicateStillRejected() {
        val next = SpecOpApplier.apply(ticket, SpecOp.AddField("tiket", FieldSpec("riwayat_perbaikan", "Riwayat perbaikan", FieldType.LONG_TEXT))).getOrThrow()
        assertEquals(FieldType.LONG_TEXT, assertNotNull(next.spec.entity("tiket")!!.field("riwayat_perbaikan")).type)
        val renamed = SpecOpApplier.apply(next, SpecOp.RenameFieldLabel("tiket", "riwayat_perbaikan", "Catatan teknisi")).getOrThrow()
        assertEquals("Catatan teknisi", renamed.spec.entity("tiket")!!.field("riwayat_perbaikan")!!.label)
        assertTrue(
            SpecOpApplier.apply(ticket, SpecOp.AddField("tiket", FieldSpec("Judul", "Judul", FieldType.LONG_TEXT))).isFailure,
            "field ganda tetap ditolak lintas tipe"
        )
    }
}