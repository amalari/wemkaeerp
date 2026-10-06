package com.eventverse.app

/**
 * Kasus emas discovery (plan SP-C4): **≥ 10 narasi** lintas vertikal — garment ×3 (FOB, CMT, D2C),
 * sablon/bordir, klinik, bengkel, katering, retail, jasa/IT, sekolah, logistik.
 *
 * Dipakai dua penilai dengan kriteria yang sama ([DiscoveryEvalGrader]): baseline deterministik
 * (`DiscoveryEvalsTest`, wajib 100%) dan evals LLM hidup (`KoogDiscoveryLiveEvalsTest`, opt-in).
 * [expectedCapabilities] memuat **satu kumpulan sinonim per kemampuan**: agent deterministik menamai
 * modul dari kata kunci narasi, agent LLM menamainya semantik — grader menguji kemampuan, bukan ejaan.
 */
data class DiscoveryGoldenCase(
    val name: String,
    val narrative: String,
    val industryHint: String?,
    val expectedPackCode: String,
    /** `true` = pack garment bawaan; kriteria kemurnian vertikal tidak dinilai untuk kasus ini. */
    val garmentPack: Boolean = false,
    val expectedBlueprintCode: String? = null,
    val expectedCapabilities: List<Set<String>> = emptyList(),
    /**
     * Himpunan widget yang masuk akal per kemampuan (§6 "jenis tampilan ∈ himpunan per peran"), kunci =
     * salah satu sinonim kemampuan. Kosong = himpunan data umum ({KANBAN, TABLE, FORM, CHECKLIST,
     * DASHBOARD}) yang berlaku bila layar ada.
     */
    val allowedWidgets: Map<String, Set<String>> = emptyMap()
)

object DiscoveryGoldenCases {

    /** Rekalibrasi 2026-10-07: dokumen cetak (kuitansi, surat jalan) adalah tampilan sah untuk modul mana pun. */
    private val DATA_WIDGETS = setOf("KANBAN", "TABLE", "FORM", "CHECKLIST", "DASHBOARD", "PRINT")

    val all: List<DiscoveryGoldenCase> = listOf(
        DiscoveryGoldenCase(
            name = "garment-fob",
            narrative = "Kami konveksi pakaian jadi; kain kami beli sendiri, mulai potong sampai jahit dan kirim.",
            industryHint = null,
            expectedPackCode = "garment",
            garmentPack = true,
            expectedBlueprintCode = "fob_full_package"
        ),
        DiscoveryGoldenCase(
            name = "garment-cmt",
            narrative = "Kami konveksi makloon, kain dari buyer, cukup jahit saja.",
            industryHint = null,
            expectedPackCode = "garment",
            garmentPack = true,
            expectedBlueprintCode = "cmt_makloon"
        ),
        DiscoveryGoldenCase(
            name = "garment-d2c",
            narrative = "Brand distro pakaian: kami produksi sendiri lalu jual lewat marketplace.",
            industryHint = null,
            expectedPackCode = "garment",
            garmentPack = true,
            expectedBlueprintCode = "brand_d2c"
        ),
        DiscoveryGoldenCase(
            name = "sablon-bordir",
            narrative = "Usaha sablon dan bordir manual: order sablon kaos masuk harian, dikerjakan per gelombang.",
            industryHint = "sablon",
            expectedPackCode = "garment",
            garmentPack = true,
            expectedBlueprintCode = "fob_full_package"
        ),
        DiscoveryGoldenCase(
            name = "klinik",
            narrative = "Kami klinik gigi: pasien mendaftar antrean per poli, ada stok obat, dan tagihan pembayaran kasir.",
            industryHint = "klinik",
            expectedPackCode = "klinik",
            expectedCapabilities = listOf(setOf("antrean", "pendaftaran", "jadwal"), setOf("tagihan", "kasir", "pembayaran")),
            // Pendaftaran pasien lazim berupa formulir; antrean tetap tidak boleh dasbor/daftar periksa.
            allowedWidgets = mapOf("antrean" to setOf("KANBAN", "TABLE", "FORM"))
        ),
        DiscoveryGoldenCase(
            name = "bengkel",
            narrative = "Bengkel servis motor, pelanggan bisa booking pesanan servis lewat telepon.",
            industryHint = "bengkel",
            expectedPackCode = "bengkel",
            expectedCapabilities = listOf(setOf("pesanan", "booking", "servis"))
        ),
        DiscoveryGoldenCase(
            name = "katering",
            narrative = "Katering harian: pesanan langganan tiap minggu dan laporan pengiriman bulanan.",
            industryHint = "katering",
            expectedPackCode = "katering",
            expectedCapabilities = listOf(setOf("pesanan"), setOf("laporan", "rekap", "pengiriman"))
        ),
        DiscoveryGoldenCase(
            name = "retail",
            narrative = "Toko kelontong dengan kasir: pencatatan penjualan harian dan cek stok barang gudang.",
            industryHint = "retail",
            expectedPackCode = "retail",
            expectedCapabilities = listOf(setOf("tagihan", "penjualan", "kasir"), setOf("stok", "persediaan"))
        ),
        DiscoveryGoldenCase(
            name = "jasa-it",
            narrative = "Studio jasa IT: pencatatan pesanan perbaikan perangkat klien dan laporan pekerjaan teknisi bulanan.",
            industryHint = null,
            expectedPackCode = "kustom",
            expectedCapabilities = listOf(setOf("pesanan", "tiket"), setOf("laporan", "rekap"))
        ),
        DiscoveryGoldenCase(
            name = "sekolah",
            narrative = "Sekolah kursus komputer: pendaftaran siswa baru tiap semester dan pembayaran SPP bulanan.",
            industryHint = "sekolah",
            expectedPackCode = "sekolah",
            expectedCapabilities = listOf(setOf("pesanan", "pendaftaran"), setOf("tagihan", "pembayaran"))
        ),
        DiscoveryGoldenCase(
            name = "logistik",
            narrative = "Jasa logistik pengiriman paket: pesanan penjemputan harian dan laporan pengiriman mingguan.",
            industryHint = "logistik",
            expectedPackCode = "logistik",
            expectedCapabilities = listOf(setOf("pesanan"), setOf("laporan", "rekap"))
        )
    )

    /** Himpunan widget sah bila kasus tidak menentukan sendiri. */
    fun allowedFor(case: DiscoveryGoldenCase, capabilitySynonyms: Set<String>): Set<String> =
        case.allowedWidgets.entries
            .firstOrNull { (cap, _) -> capabilitySynonyms.any { it == cap } }
            ?.value
            ?: DATA_WIDGETS
}
