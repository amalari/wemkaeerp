package com.eventverse.app

import com.eventverse.app.domain.discovery.interview.InterviewLimits
import com.eventverse.app.domain.discovery.interview.ModuleOrigin

/**
 * Satu kunci jawaban peran (plan IV-C0): [roleSynonyms] menangkap variasi nama peran, [divisionSynonyms]
 * membatasi divisi tempat peran itu berada. Sinonim, bukan ejaan — tebakan deterministik dan LLM sama-sama
 * dinilai dari makna (pelajaran SP-C4).
 */
data class RoleExpectation(val roleSynonyms: Set<String>, val divisionSynonyms: Set<String>)

/**
 * Satu kunci jawaban tautan peran→modul: [moduleSynonyms] mencocokkan id **atau** nama modul,
 * [origins] himpunan asal yang masuk akal untuk kasus ini (modul bawaan diklaim NEW itu bohong,
 * modul kustom diklaim REUSE_PACK juga bohong — keduanya ditolak validator, dinilai grader).
 */
data class LinkExpectation(val roleSynonyms: Set<String>, val moduleSynonyms: Set<String>, val origins: Set<ModuleOrigin>)

/**
 * Kasus emas wawancara (plan IV-C0): narasi + kunci jawaban per langkah. ≥ 10 usaha, memuat kasus
 * **tekstil-adjacent** (sablon/bordir) yang sejak keputusan 2026-10-07 **tidak punya pack baku** —
 * dinilai dari kemampuan dan asal modul, bukan dari pack garment.
 *
 * [maxTurns] & [maxDivisions] adalah kasus negatif (C6): cerita kecil → draf kecil; modul lazim yang
 * tidak disebut narasi tidak boleh memunculkan divisi/tautan baru di luar batas ini.
 */
data class InterviewEvalCase(
    val name: String,
    val narrative: String,
    val industryHint: String? = null,
    /** `true` = pack garment bawaan; kriteria kemurnian vertikal tidak dinilai untuk kasus ini. */
    val garmentPack: Boolean = false,
    val expectedDivisions: List<Set<String>> = emptyList(),
    val expectedRoles: List<RoleExpectation> = emptyList(),
    val expectedLinks: List<LinkExpectation> = emptyList(),
    val maxTurns: Int = InterviewLimits.TURNS,
    val maxDivisions: Int? = null
)

object InterviewGoldenCases {

    private val REUSE = setOf(ModuleOrigin.REUSE_PACK, ModuleOrigin.EXTEND, ModuleOrigin.REUSE_PLATFORM)
    private val NEW_ONLY = setOf(ModuleOrigin.NEW)

    val all: List<InterviewEvalCase> = listOf(
        InterviewEvalCase(
            name = "garment-fob",
            narrative = "Kami konveksi pakaian jadi; kain kami beli sendiri, mulai potong sampai jahit dan kirim.",
            garmentPack = true,
            expectedDivisions = listOf(setOf("potong"), setOf("jahit", "produksi"), setOf("qc", "quality", "mutu", "inspeksi")),
            expectedRoles = listOf(
                RoleExpectation(setOf("potong"), setOf("potong")),
                RoleExpectation(setOf("penjahit", "operator jahit"), setOf("jahit", "produksi")),
                RoleExpectation(setOf("qc", "inspektur", "mutu"), setOf("qc", "quality", "mutu", "inspeksi"))
            ),
            expectedLinks = listOf(
                LinkExpectation(setOf("potong"), setOf("mrp", "production", "produksi", "potong"), REUSE),
                LinkExpectation(setOf("penjahit", "operator jahit"), setOf("operator_exec", "mrp", "production", "produksi", "jahit"), REUSE),
                LinkExpectation(setOf("qc", "inspektur", "mutu"), setOf("quality_control", "qc", "mutu"), REUSE)
            ),
            maxDivisions = 8
        ),
        InterviewEvalCase(
            name = "garment-cmt",
            narrative = "Kami konveksi makloon, kain dari buyer, cukup jahit saja.",
            garmentPack = true,
            expectedDivisions = listOf(setOf("jahit", "produksi"), setOf("gudang", "kain", "material")),
            expectedRoles = listOf(
                RoleExpectation(setOf("jahit", "penjahit", "operator"), setOf("jahit", "produksi")),
                RoleExpectation(setOf("gudang", "kain"), setOf("gudang", "kain", "material"))
            ),
            expectedLinks = listOf(
                LinkExpectation(setOf("jahit", "penjahit"), setOf("operator_exec", "mrp", "production", "produksi", "jahit"), REUSE),
                LinkExpectation(setOf("gudang", "kain"), setOf("inventory", "gudang", "kain", "material"), REUSE)
            ),
            maxDivisions = 8
        ),
        InterviewEvalCase(
            name = "garment-d2c",
            narrative = "Brand distro pakaian: kami produksi sendiri lalu jual lewat marketplace.",
            garmentPack = true,
            expectedDivisions = listOf(setOf("produksi", "jahit"), setOf("penjualan", "marketing", "toko")),
            expectedRoles = listOf(
                RoleExpectation(setOf("produksi", "jahit", "operator"), setOf("produksi", "jahit")),
                RoleExpectation(setOf("sales", "admin toko", "toko", "marketplace"), setOf("penjualan", "marketing", "toko"))
            ),
            expectedLinks = listOf(
                LinkExpectation(setOf("produksi", "jahit"), setOf("mrp", "production", "produksi", "operator_exec"), REUSE),
                LinkExpectation(setOf("sales", "admin toko", "toko"), setOf("crm", "penjualan", "invoicing", "marketplace"), REUSE)
            ),
            maxDivisions = 8
        ),
        InterviewEvalCase(
            name = "sablon-bordir",
            narrative = "Usaha sablon dan bordir manual: order sablon kaos masuk harian, dikerjakan per gelombang.",
            industryHint = "sablon",
            // Keputusan 2026-10-07: sablon/bordir tidak punya pack baku — dinilai dari kemampuan & asal modul.
            // Tidak ada modulnya di pack mana pun ⇒ asal yang masuk akal hanya NEW (validator menegakkan juga).
            expectedDivisions = listOf(setOf("produksi", "sablon", "bordir"), setOf("pesanan", "admin", "penjualan", "order")),
            expectedRoles = listOf(
                RoleExpectation(setOf("operator", "sablon", "bordir"), setOf("produksi", "sablon", "bordir")),
                RoleExpectation(setOf("admin", "customer service", "cs", "penerima"), setOf("pesanan", "admin", "penjualan", "order"))
            ),
            expectedLinks = listOf(
                LinkExpectation(setOf("operator", "sablon", "bordir"), setOf("sablon", "bordir", "produksi", "gelombang"), NEW_ONLY),
                LinkExpectation(setOf("admin", "customer service", "cs", "penerima"), setOf("pesanan", "order", "penerimaan"), NEW_ONLY)
            ),
            maxTurns = 6,
            maxDivisions = 6
        ),
        InterviewEvalCase(
            name = "klinik",
            narrative = "Kami klinik gigi: pasien mendaftar antrean per poli, ada stok obat, dan tagihan pembayaran kasir.",
            industryHint = "klinik",
            expectedDivisions = listOf(setOf("pendaftaran"), setOf("poli"), setOf("kasir")),
            expectedRoles = listOf(
                RoleExpectation(setOf("resepsionis", "pendaftaran"), setOf("pendaftaran")),
                RoleExpectation(setOf("perawat", "dokter"), setOf("poli")),
                RoleExpectation(setOf("kasir"), setOf("kasir"))
            ),
            expectedLinks = listOf(
                LinkExpectation(setOf("resepsionis", "pendaftaran"), setOf("pendaftaran"), NEW_ONLY),
                LinkExpectation(setOf("perawat", "dokter"), setOf("poli"), NEW_ONLY),
                LinkExpectation(setOf("kasir"), setOf("kasir"), NEW_ONLY),
                LinkExpectation(setOf("resepsionis", "pendaftaran"), setOf("org_chart"), setOf(ModuleOrigin.REUSE_PLATFORM))
            ),
            maxTurns = 6,
            maxDivisions = 6
        ),
        InterviewEvalCase(
            name = "bengkel",
            narrative = "Bengkel servis motor, pelanggan bisa booking pesanan servis lewat telepon.",
            industryHint = "bengkel",
            // Kasus negatif (C6): cerita kecil → draf kecil. Modul "lazim" (invoicing, inventory) yang tidak
            // disebut tidak boleh memunculkan divisi baru di luar batas maxDivisions.
            expectedDivisions = listOf(setOf("servis")),
            expectedRoles = listOf(RoleExpectation(setOf("mekanik"), setOf("servis"))),
            expectedLinks = listOf(LinkExpectation(setOf("mekanik"), setOf("servis"), NEW_ONLY)),
            maxTurns = 4,
            maxDivisions = 3
        ),
        InterviewEvalCase(
            name = "katering",
            narrative = "Katering harian: pesanan langganan tiap minggu dan laporan pengiriman bulanan.",
            industryHint = "katering",
            // Narasi hanya menyebut pesanan & laporan pengiriman - kunci mengikuti cerita, bukan keinginan.
            expectedDivisions = listOf(setOf("pesanan", "admin", "penjualan"), setOf("kirim", "pengiriman")),
            expectedRoles = listOf(
                RoleExpectation(setOf("admin", "customer service", "cs"), setOf("pesanan", "admin", "penjualan")),
                RoleExpectation(setOf("kurir", "driver", "kirim"), setOf("kirim", "pengiriman"))
            ),
            expectedLinks = listOf(
                LinkExpectation(setOf("admin", "customer service", "cs"), setOf("pesanan", "order", "langganan"), NEW_ONLY),
                LinkExpectation(setOf("kurir", "driver", "kirim"), setOf("kirim", "pengiriman", "laporan"), NEW_ONLY)
            ),
            maxDivisions = 6
        ),
        InterviewEvalCase(
            name = "retail",
            narrative = "Toko kelontong dengan kasir: pencatatan penjualan harian dan cek stok barang gudang.",
            industryHint = "retail",
            expectedDivisions = listOf(setOf("penjualan", "kasir"), setOf("gudang", "stok")),
            expectedRoles = listOf(
                RoleExpectation(setOf("kasir"), setOf("penjualan", "kasir")),
                RoleExpectation(setOf("gudang", "stok", "penjaga"), setOf("gudang", "stok"))
            ),
            expectedLinks = listOf(
                LinkExpectation(setOf("kasir"), setOf("kasir", "penjualan", "tagihan"), NEW_ONLY),
                LinkExpectation(setOf("gudang", "stok", "penjaga"), setOf("stok", "gudang", "barang"), NEW_ONLY)
            ),
            maxDivisions = 5
        ),
        InterviewEvalCase(
            name = "jasa-it",
            narrative = "Studio jasa IT: pencatatan pesanan perbaikan perangkat klien dan laporan pekerjaan teknisi bulanan.",
            expectedDivisions = listOf(setOf("pesanan", "admin"), setOf("teknisi", "teknikal", "servis")),
            expectedRoles = listOf(
                RoleExpectation(setOf("admin", "customer service", "cs"), setOf("pesanan", "admin")),
                RoleExpectation(setOf("teknisi"), setOf("teknisi", "teknikal", "servis"))
            ),
            expectedLinks = listOf(
                LinkExpectation(setOf("admin", "customer service", "cs"), setOf("pesanan", "tiket", "order", "perbaikan"), NEW_ONLY),
                LinkExpectation(setOf("teknisi"), setOf("teknisi", "pekerjaan", "laporan", "perbaikan"), NEW_ONLY)
            ),
            maxDivisions = 5
        ),
        InterviewEvalCase(
            name = "sekolah",
            narrative = "Sekolah kursus komputer: pendaftaran siswa baru tiap semester dan pembayaran SPP bulanan.",
            industryHint = "sekolah",
            expectedDivisions = listOf(setOf("pendaftaran", "administrasi"), setOf("keuangan", "pembayaran")),
            expectedRoles = listOf(
                RoleExpectation(setOf("admin", "pendaftaran"), setOf("pendaftaran", "administrasi")),
                RoleExpectation(setOf("bendahara", "kasir"), setOf("keuangan", "pembayaran"))
            ),
            expectedLinks = listOf(
                LinkExpectation(setOf("admin", "pendaftaran"), setOf("pendaftaran", "siswa", "peserta", "pesanan"), NEW_ONLY),
                LinkExpectation(setOf("bendahara", "kasir"), setOf("pembayaran", "spp", "tagihan", "keuangan"), NEW_ONLY)
            ),
            maxDivisions = 5
        ),
        InterviewEvalCase(
            name = "logistik",
            narrative = "Jasa logistik pengiriman paket: pesanan penjemputan harian dan laporan pengiriman mingguan.",
            industryHint = "logistik",
            expectedDivisions = listOf(setOf("pesanan", "operasional", "admin"), setOf("pengiriman", "kurir")),
            expectedRoles = listOf(
                RoleExpectation(setOf("admin", "customer service", "cs"), setOf("pesanan", "operasional", "admin")),
                RoleExpectation(setOf("kurir", "driver"), setOf("pengiriman", "kurir"))
            ),
            expectedLinks = listOf(
                LinkExpectation(setOf("admin", "customer service", "cs"), setOf("pesanan", "penjemputan", "order"), NEW_ONLY),
                LinkExpectation(setOf("kurir", "driver"), setOf("pengiriman", "kirim", "laporan"), NEW_ONLY)
            ),
            maxDivisions = 5
        )
    )
}
