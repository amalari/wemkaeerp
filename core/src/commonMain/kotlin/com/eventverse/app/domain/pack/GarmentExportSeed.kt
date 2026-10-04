package com.eventverse.app.domain.pack

/**
 * Data contoh **satu pabrik garment ekspor** (FOB, buyer luar negeri) untuk pratinjau `/builder/prototype`.
 * Semua layar diturunkan dari daftar di sini, sehingga satu PO / artikel / pembeli ditulis **sekali**
 * dan konsisten di CRM, Sampling, MRP, BOM, stok, QC, dan surat jalan. Nama pembeli fiktif.
 *
 * Ini data pack (tenant-variability-rules): pack lain membawa seed-nya sendiri; mesin tidak mengenal
 * satu pun istilah di bawah. Tanggal berupa teks tampilan — seed ini deterministik dan tidak bergantung jam.
 */
internal object GarmentExportSeed {

    enum class Stage(val label: String) { SAMPLING("Sampling"), CUTTING("Antre Potong"), SEWING("Berjalan"), DONE("Selesai") }

    data class Buyer(val name: String, val country: String)

    data class Style(val code: String, val name: String, val fabric: String, val fabricPerPcs: String)

    data class Order(
        val po: String, val buyer: Buyer, val style: Style, val qty: Int,
        val fobCents: Int, val etd: String, val stage: Stage, val note: String
    ) {
        val qtyText: String get() = "${qty.toString().reversed().chunked(3).joinToString(".").reversed()} pcs"
        val product: String get() = "$qtyText ${style.name}"
        val valueUsd: Long get() = qty.toLong() * fobCents / 100
    }

    private val nordic = Buyer("Nordic Wear AB", "Swedia")
    private val kestrel = Buyer("Kestrel Apparel GmbH", "Jerman")
    private val harbor = Buyer("Harbor & Pine Ltd", "Inggris")
    val prospect = Buyer("Sakura Trading Co.", "Jepang")

    val hoodie = Style("HF-210", "Hoodie Fleece Unisex", "Fleece Katun 280 gsm Heather Grey", "1,45 kg")
    val polo = Style("PL-118", "Polo Pique Combed", "Pique Combed 30s Putih", "0,32 kg")
    val bomber = Style("JB-077", "Jaket Bomber Windbreaker", "Taslan Nylon 70D Navy", "1,9 m")
    val henley = Style("HT-044", "Henley Tee Slub", "Slub Jersey Combed 24s", "0,28 kg")

    /** Urutan = urutan tampil di daftar PO. */
    val orders: List<Order> = listOf(
        Order("NW-26-0412", nordic, hoodie, 6000, 890, "15 Des 2026", Stage.SEWING, "Jahit — Lini 2"),
        Order("KA-26-0388", kestrel, polo, 4500, 640, "28 Nov 2026", Stage.CUTTING, "Meja potong 2"),
        Order("HP-26-0431", harbor, bomber, 3000, 1220, "20 Jan 2027", Stage.SAMPLING, "Menunggu approval PP sample"),
        Order("NW-26-0371", nordic, henley, 2400, 580, "10 Nov 2026", Stage.DONE, "Packing selesai")
    )

    /** Nilai order yang sedang diproduksi (potong/jahit; USD, FOB) — dihitung dari seed, tidak diketik. */
    fun activeValueText(): String =
        "USD " + orders.filter { it.stage == Stage.CUTTING || it.stage == Stage.SEWING }.sumOf { it.valueUsd }.toString().reversed().chunked(3).joinToString(".").reversed()

    fun order(po: String): Order = requireNotNull(orders.firstOrNull { it.po == po }) { "PO '$po' tidak ada di seed" }

    /** SPK sampling: (nomor, PO atau null untuk prospek, artikel, jenis contoh, kolom, pembeli, jumlah pcs, due, mendesak). */
    data class SampleJob(
        val no: String, val po: String?, val style: String, val kind: String, val column: String,
        val buyer: String, val qtyPcs: Int, val due: String, val urgent: Boolean
    )

    val sampleJobs: List<SampleJob> = listOf(
        SampleJob("SP-1051", "HP-26-0431", "Bomber JB-077", "PP sample", "Baru", "Harbor & Pine", 3, "24 Okt", false),
        SampleJob("SP-1052", null, "Crew Tee (prospek Sakura)", "Proto sample", "Baru", "Sakura Trading", 5, "est. Nov 2026", false),
        SampleJob("SP-1048", "KA-26-0388", "Polo PL-118", "Size-set sample", "Dikerjakan", "Kestrel Apparel", 4, "20 Okt", false),
        SampleJob("SP-1046", "NW-26-0412", "Hoodie HF-210", "Fit sample rev.2", "Dikerjakan", "Nordic Wear", 3, "22 Okt", true),
        SampleJob("SP-1043", "NW-26-0371", "Henley HT-044", "PP sample", "Selesai", "Nordic Wear", 2, "Disetujui — naik produksi", false)
    ).also { jobs -> jobs.forEach { j -> j.po?.let(::order) } }

    /** Baris layar. Semua rujukan PO/artikel diambil dari seed, bukan diketik ulang. */
    fun crmRows(): List<Map<String, String>> = orders.map { o ->
        mapOf(
            "No. PO" to o.po, "Pembeli" to o.buyer.name, "Produk" to o.product, "Target Kirim" to o.etd,
            "Status" to when (o.stage) {
                Stage.SAMPLING -> "Sampling"; Stage.CUTTING, Stage.SEWING -> "Produksi"; Stage.DONE -> "Siap kirim"
            }
        )
    } + mapOf(
        "No. PO" to "PRS-26-021", "Pembeli" to prospect.name, "Produk" to "8.000 pcs Crew Tee",
        "Target Kirim" to "Mar 2027", "Status" to "Prospek"
    )

    fun samplingRows(): List<Map<String, String>> = sampleJobs.map { j ->
        mapOf(
            "Kolom" to j.column,
            "Nomor" to j.no,
            "Artikel" to j.style,
            "Jenis" to j.kind,
            "Pembeli" to j.buyer,
            "Jumlah" to "${j.qtyPcs} pcs",
            "Due" to j.due,
            "Mendesak" to if (j.urgent) "ya" else "tidak"
        )
    }

    fun mrpRows(): List<Map<String, String>> = orders.filter { it.stage != Stage.SAMPLING }.map { o ->
        mapOf("Kolom" to o.stage.label, "Kartu" to "${o.po} · ${o.style.name}", "Detail" to "${o.qtyText} · ${o.note}")
    }

    fun suratJalanRow(): Map<String, String> {
        val o = orders.first { it.stage == Stage.DONE }
        return mapOf(
            "Dokumen" to "Packing List & Surat Jalan SJ-2209", "Nomor" to "0009/EXP/XI/2026",
            "Penerima" to "${o.buyer.name} — ${o.buyer.country}",
            "Isi" to "80 karton — ${o.qtyText} ${o.style.name} (PO ${o.po})",
            "Ekspedisi" to "FOB Tanjung Priok · kontainer 20' · ETD ${o.etd}"
        )
    }

    fun bomRows(): List<Map<String, String>> {
        val h = hoodie
        return listOf(
            mapOf("Komponen" to h.fabric, "Spesifikasi" to "Heather Grey · 280 gsm", "Pemakaian" to "${h.fabricPerPcs}/pcs", "Status" to "Final"),
            mapOf("Komponen" to "Rib 1x1 Cotton", "Spesifikasi" to "Heather Grey · 240 gsm", "Pemakaian" to "0,12 kg/pcs", "Status" to "Final"),
            mapOf("Komponen" to "Drawcord flat 8 mm", "Spesifikasi" to "Putih · ujung metal", "Pemakaian" to "1,2 m/pcs", "Status" to "Draft"),
            mapOf("Komponen" to "Label woven + care label", "Spesifikasi" to "Sesuai spec ${nordic.name}", "Pemakaian" to "2 pcs/pcs", "Status" to "Final")
        )
    }

    fun inventoryRows(): List<Map<String, String>> = listOf(
        mapOf("Bahan" to hoodie.fabric, "Stok" to "2.100 kg", "Kepemilikan" to "Titipan buyer", "Status" to "Konsinyasi"),
        mapOf("Bahan" to polo.fabric, "Stok" to "1.250 kg", "Kepemilikan" to "Milik pabrik", "Status" to "Tersedia"),
        mapOf("Bahan" to "Benang Polyester 120", "Stok" to "96 koni", "Kepemilikan" to "Milik pabrik", "Status" to "Tersedia"),
        mapOf("Bahan" to "Drawcord flat 8 mm", "Stok" to "900 m", "Kepemilikan" to "Milik pabrik", "Status" to "Menipis")
    )

    fun qcRows(): List<Map<String, String>> = listOf(
        "Jahitan 10–12 SPI, tidak ada loncat" to "ya",
        "Label komposisi & care sesuai spec ${nordic.name}" to "ya",
        "Lolos metal detector (syarat ekspor)" to "tidak",
        "Ukuran dalam toleransi ±1 cm (AQL 2.5)" to "ya"
    ).map { (butir, selesai) -> mapOf("Butir" to butir, "Selesai" to selesai) }

    fun linimRows(): List<Map<String, String>> {
        val po = order("NW-26-0412")
        return listOf(
            mapOf("Kolom" to "Lini 2", "Kartu" to "Rian — jahit kerah", "Detail" to "${po.po} · 320 pcs hari ini"),
            mapOf("Kolom" to "Lini 2", "Kartu" to "Sinta — jahit badan", "Detail" to "${po.po} · 280 pcs hari ini"),
            mapOf("Kolom" to "Lini 4", "Kartu" to "Agus — pasang lengan", "Detail" to "${po.po} · 255 pcs hari ini")
        )
    }
}
