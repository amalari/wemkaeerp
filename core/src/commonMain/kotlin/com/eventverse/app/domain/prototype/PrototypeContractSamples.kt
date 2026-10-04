package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.brief.CaptureEntry

/**
 * Contoh kontrak v1 untuk **test lintas jalur** (UI, server, handoff). Sengaja **non-garment** (tiket
 * servis) — bukti bahwa kontrak tidak mengenal satu industri — dan kecil. Jangan dipakai sebagai data
 * demo; data demo milik pack. Dikirim di `commonMain` supaya modul `app` dan `server` bisa memakainya
 * tanpa bergantung pada source set test `core`.
 */
object PrototypeContractSamples {

    private val status = FieldSpec("Status", "Status tiket", FieldType.ENUM, listOf("Baru", "Diproses", "Selesai"))

    val ticketEntity = EntitySpec(
        id = "tiket",
        label = "Tiket",
        fields = listOf(
            FieldSpec("Judul", "Judul", FieldType.TEXT, required = true),
            FieldSpec("Peminta", "Peminta", FieldType.TEXT),
            status
        ),
        stateMachine = StateMachine("Status", mapOf("Baru" to setOf("Diproses"), "Diproses" to setOf("Selesai", "Baru")))
    )

    /** Papan kanban + form tambah tiket, satu entitas (baris baru dari form tampil di papan). */
    val ticketSpec = PrototypeSpec(
        listOf(ticketEntity),
        listOf(
            ScreenSpec("papan", "Papan Tiket", WidgetKind.KANBAN, "tiket", KanbanConfig("Status", listOf("Baru", "Diproses", "Selesai"), "Judul", listOf("Peminta"))),
            ScreenSpec("form", "Tambah Tiket", WidgetKind.FORM, "tiket", form = FormConfig(listOf("Judul", "Peminta", "Status"), "Buat tiket"))
        )
    )

    val ticketSeed: Map<String, List<PrototypeRow>> = mapOf(
        "tiket" to listOf(
            PrototypeRow("t-1", mapOf("Judul" to "AC ruang rapat mati", "Peminta" to "Rina", "Status" to "Baru")),
            PrototypeRow("t-2", mapOf("Judul" to "Printer macet", "Peminta" to "Doni", "Status" to "Diproses"))
        )
    )

    val ticketScreen = InteractiveScreen(ticketSpec, ticketSeed)

    /** Kelima jenis operasi, masing-masing satu contoh sah terhadap [ticketScreen]. */
    val sampleOps: List<SpecOp> = listOf(
        SpecOp.AddEnumOption("tiket", "Status", "Revisi", after = "Diproses"),
        SpecOp.RenameEnumOption("tiket", "Status", "Selesai", "Ditutup"),
        SpecOp.AddTransition("tiket", "Status", "Baru", "Selesai"),
        SpecOp.AddField("tiket", FieldSpec("Prioritas", "Prioritas", FieldType.ENUM, listOf("Rendah", "Tinggi"))),
        SpecOp.RenameFieldLabel("tiket", "Peminta", "Dilaporkan oleh")
    )

    val sampleLog: List<CaptureEntry> = listOf(
        CaptureEntry("2026-10-04T09:00:00Z", sampleOps[0], ok = true, message = null),
        CaptureEntry("2026-10-04T09:01:00Z", SpecOp.AddTransition("tiket", "Status", "Baru", "Hantu"), ok = false, message = "Status 'Hantu' tidak ada.")
    )
}
