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

    // ---- Bentuk kontrak v2 (blok kaya + port data) — tetap non-garment (bengkel servis) ----

    /** Entitas order servis: satu field per gaya kartu, mesin status di "Status", plus FILE (C8). */
    val orderEntity = EntitySpec(
        id = "order",
        label = "Order Servis",
        fields = listOf(
            FieldSpec("Nomor", "No. Order", FieldType.TEXT, required = true),
            FieldSpec("Pelanggan", "Pelanggan", FieldType.TEXT, required = true),
            FieldSpec("Total", "Total Biaya", FieldType.NUMBER),
            FieldSpec("Target", "Target Selesai", FieldType.DATE),
            FieldSpec("Mendesak", "Mendesak", FieldType.BOOL),
            FieldSpec("Status", "Status", FieldType.ENUM, listOf("Baru", "Dikerjakan", "Selesai")),
            // C8 (TRD-FIELD-002): nilai = FileRef; baris contoh sengaja TANPA kunci ini (seed FILE
            // wajib kosong — referensi karangan ke objek yang tidak ada ditolak validator).
            FieldSpec("Lampiran", "Lampiran", FieldType.FILE),
            // C7 (TRD-FIELD-001): rujukan LOGIS — id baris target saja, tanpa FK lintas schema. Target
            // "order" = entitas di modul yang sama (sampel bentuk "entityId"); keberadaan record target
            // diverifikasi server saat tulis nilai (fail-closed), bukan oleh DB.
            FieldSpec("Rujukan", "Rujukan Order", FieldType.RELATION, target = "order")
        ),
        stateMachine = StateMachine("Status", mapOf("Baru" to setOf("Dikerjakan"), "Dikerjakan" to setOf("Selesai", "Baru")))
    )

    /** Kanban berkartu bertipe + metadata kolom (tint = data tenant, WIP) + form detail, dan tabel inline. */
    val orderSpec = PrototypeSpec(
        listOf(orderEntity),
        listOf(
            ScreenSpec(
                "antrian", "Antrian Servis", WidgetKind.KANBAN, "order",
                KanbanConfig(
                    "Status", listOf("Baru", "Dikerjakan", "Selesai"), "Nomor", listOf("Pelanggan", "Total"),
                    card = listOf(
                        CardElement("Nomor", CardStyle.TITLE),
                        CardElement("Pelanggan", CardStyle.TEXT),
                        CardElement("Total", CardStyle.NUMBER),
                        CardElement("Target", CardStyle.DATE),
                        CardElement("Status", CardStyle.BADGE),
                        CardElement("Mendesak", CardStyle.FLAG)
                    ),
                    columnMeta = mapOf("Dikerjakan" to ColumnMeta(tintHex = 0xFF2563EB, wipLimit = 3)),
                    detailForm = FormConfig(listOf("Nomor", "Pelanggan", "Total", "Target", "Mendesak", "Status"), "Simpan perubahan")
                )
            ),
            ScreenSpec(
                "daftar", "Daftar Order", WidgetKind.TABLE, "order",
                table = TableConfig(
                    listOf("Nomor", "Pelanggan", "Total", "Target", "Status"), "Status",
                    inlineCreate = true, editableFields = listOf("Pelanggan", "Total", "Target")
                )
            )
        )
    )

    val orderSeed: Map<String, List<PrototypeRow>> = mapOf(
        "order" to listOf(
            PrototypeRow("o-1", mapOf("Nomor" to "SV-001", "Pelanggan" to "Rina", "Total" to "350000", "Target" to "2026-10-10", "Mendesak" to "ya", "Status" to "Baru")),
            PrototypeRow("o-2", mapOf("Nomor" to "SV-002", "Pelanggan" to "Doni", "Total" to "120000", "Target" to "2026-10-12", "Mendesak" to "tidak", "Status" to "Dikerjakan"))
        )
    )

    /** Bawaan: data hidup di memori (demo). */
    val orderScreen = InteractiveScreen(orderSpec, orderSeed)

    /** Varian pilot: data dari API (pola plan induk §3.2; basePath contoh). */
    val orderScreenApi = InteractiveScreen(orderSpec, orderSeed, DataBinding.Api("/api/tenant/modules/servis/service_orders"))

    /** Dua operasi baru kontrak v2, masing-masing satu contoh sah terhadap [orderScreen]. */
    val richOps: List<SpecOp> = listOf(
        SpecOp.ShowFieldOnCard("order", "Mendesak", CardStyle.FLAG),
        SpecOp.SetFieldRequired("order", "Target", true)
    )
}
