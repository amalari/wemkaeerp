package com.eventverse.app

import com.eventverse.app.domain.builder.BriefFeature
import com.eventverse.app.domain.builder.ModuleBrief
import com.eventverse.app.domain.builder.ModuleBriefLimits
import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.evidence.EvidenceSource

/**
 * Kasus emas brief modul (TRD-BLDR-003 FR-8): ≥6 kasus berbeda lintas vertikal — garment FOB, garment CMT,
 * garment D2C, klinik, bengkel, dan sablon-bordir.
 *
 * Setiap kasus menyatakan narasi input, brief diharapkan, konsep terlarang, anchor evidence, dan coverage tags.
 * Golden answer buatan tangan wajib lulus 100% di [BuilderBriefEvalGrader].
 */
data class BuilderBriefGoldenCase(
    val name: String,
    /** Narasi input (cerita pengguna). */
    val narrative: String,
    /** ID modul yang di-eval. */
    val moduleId: String,
    /** Judul modul untuk kontekstualisasi. */
    val moduleName: String,
    /** Pack yang dipakai. */
    val packCode: String,
    /** `true` = pack garment bawaan; kriteria kemurnian vertikal tidak dinilai untuk kasus ini. */
    val garmentPack: Boolean = false,
    /** Ringkasan yang diharapkan. */
    val expectedSummary: String,
    /** Fitur yang diharapkan (text + evidence anchor). */
    val expectedFeatures: List<BriefFeatureSpec>,
    /** Tahap yang diharapkan (name + evidence anchor). */
    val expectedStages: List<StageSketchSpec>,
    /**
     * Konsep terlarang: fitur/tahap yang tidak boleh muncul di brief ini.
     * Grader menolak bila ditemukan.
     */
    val forbiddenConcepts: Set<String> = emptySet(),
    /**
     * Anchor evidence: kutipan teks di narasi yang boleh jadi bukti.
     * Grader memeriksa kutipan brief terhadap anchor ini.
     */
    val evidenceAnchors: Set<String> = emptySet(),
    /** Threshold cakupan: berapa % expected harus ada di output agar PASS. */
    val coverageThreshold: Float = 1.0f
)

/** Spesifikasi satu fitur dalam golden case. */
data class BriefFeatureSpec(
    val text: String,
    /** Anchor evidence: substring di narrative yang jadi bukti. */
    val evidenceAnchor: String? = null
)

/** Spesifikasi satu tahap dalam golden case. */
data class StageSketchSpec(
    val name: String,
    /** Anchor evidence: substring di narrative yang jadi bukti. */
    val evidenceAnchor: String? = null
)

/**
 * Fixture brief dari golden case — dibuat untuk testing grader.
 * Berisi evidence yang valid (anchor ditemukan di narrative).
 */
fun BuilderBriefGoldenCase.toFixture(): ModuleBrief {
    val features = expectedFeatures.map { spec ->
        val evidence = spec.evidenceAnchor?.let { anchor ->
            EvidenceRef(EvidenceSource.NARRATIVE, quote = anchor)
        }
        BriefFeature(spec.text, evidence)
    }
    val stages = expectedStages.map { spec ->
        val evidence = spec.evidenceAnchor?.let { anchor ->
            EvidenceRef(EvidenceSource.NARRATIVE, quote = anchor)
        }
        StageSketch(name = spec.name, evidence = evidence)
    }
    return ModuleBrief(
        summary = expectedSummary,
        features = features,
        stages = stages,
        source = BriefSource.AI,
        contractVersion = ModuleBriefLimits.CONTRACT_VERSION_EVIDENCE
    )
}

object BuilderBriefGoldenCases {
    val all: List<BuilderBriefGoldenCase> = listOf(
        // 1. Garment FOB
        BuilderBriefGoldenCase(
            name = "garment-fob-cutting",
            narrative = "Kami konveksi pakaian jadi; kain kami beli sendiri dari supplier lokal, mulai dari potong bahan lalu jahit di tim produksi, QC, dan kirim ke buyer.",
            moduleId = "cutting",
            moduleName = "Pemotongan",
            packCode = "garment",
            garmentPack = true,
            expectedSummary = "Modul Pemotongan: memotong kain sesuai pola pakaian.",
            expectedFeatures = listOf(
                BriefFeatureSpec("Spreading kain", "kain kami beli"),
                BriefFeatureSpec("Pemotongan pola", "potong bahan"),
                BriefFeatureSpec("Sortasi & bundling", "QC")
            ),
            expectedStages = listOf(
                StageSketchSpec("Spreading", "kain kami beli"),
                StageSketchSpec("Potong", "potong bahan"),
                StageSketchSpec("Sortasi", "QC")
            ),
            forbiddenConcepts = setOf("kasir", "resep", "pasien"),
            evidenceAnchors = setOf("kain kami beli", "potong bahan", "QC")
        ),
        // 2. Garment CMT
        BuilderBriefGoldenCase(
            name = "garment-cmt-sewing",
            narrative = "Kami terima makloon jahit; kain dari buyer, kami hanya terima, jahit, dan kirim balik.",
            moduleId = "sewing",
            moduleName = "Penjahitan",
            packCode = "garment",
            garmentPack = true,
            expectedSummary = "Modul Penjahitan: merakit potongan kain menjadi pakaian jadi.",
            expectedFeatures = listOf(
                BriefFeatureSpec("Terima kain makloon", "kain dari buyer"),
                BriefFeatureSpec("Penjahitan", "jahit"),
                BriefFeatureSpec("QC jahitan", "kirim balik")
            ),
            expectedStages = listOf(
                StageSketchSpec("Input", "kain dari buyer"),
                StageSketchSpec("Jahit", "jahit"),
                StageSketchSpec("QC", "kirim balik")
            ),
            forbiddenConcepts = setOf("pembelian kain", "supplier"),
            evidenceAnchors = setOf("kain dari buyer", "jahit", "kirim balik")
        ),
        // 3. Garment D2C
        BuilderBriefGoldenCase(
            name = "garment-d2c-fulfillment",
            narrative = "Brand distro kami produksi sendiri, jual di Shopee dan Tokopedia. Kain dari supplier, produksi sendiri, packing dan kirim ke customer.",
            moduleId = "fulfillment",
            moduleName = "Packing & Pengiriman",
            packCode = "garment",
            garmentPack = true,
            expectedSummary = "Modul Packing & Pengiriman:.pack dan kirim pesanan ke customer.",
            expectedFeatures = listOf(
                BriefFeatureSpec("Packing pesanan", "packing"),
                BriefFeatureSpec("Cetak label", "kirim ke customer"),
                BriefFeatureSpec("Integrasi marketplace", "Shopee")
            ),
            expectedStages = listOf(
                StageSketchSpec("Packing", "packing"),
                StageSketchSpec("Label", "kirim ke customer"),
                StageSketchSpec("Kirim", "kirim ke customer")
            ),
            forbiddenConcepts = setOf("dokter", "pasien", "resep"),
            evidenceAnchors = setOf("packing", "kirim ke customer", "Shopee", "Tokopedia")
        ),
        // 4. Klinik
        BuilderBriefGoldenCase(
            name = "klinik-registration",
            narrative = "Klinik gigi: pasien datang daftar, masuk antrian, diperiksa dokter, dapat resep, lalu bayar di kasir.",
            moduleId = "klinik_registration",
            moduleName = "Pendaftaran Pasien",
            packCode = "clinic",
            garmentPack = false,
            expectedSummary = "Modul Pendaftaran Pasien: daftar dan antrian pasien.",
            expectedFeatures = listOf(
                BriefFeatureSpec("Input data pasien", "pasien datang"),
                BriefFeatureSpec("Antrian", "masuk antrian"),
                BriefFeatureSpec("Cetak kartu", "dokter")
            ),
            expectedStages = listOf(
                StageSketchSpec("Daftar", "pasien datang"),
                StageSketchSpec("Antri", "masuk antrian"),
                StageSketchSpec("Panggil", "dokter")
            ),
            forbiddenConcepts = setOf("jahit", "kain", "potong"),
            evidenceAnchors = setOf("pasien datang", "masuk antrian", "dokter")
        ),
        // 5. Bengkel
        BuilderBriefGoldenCase(
            name = "bengkel-service",
            narrative = "Bengkel motor: konsumen datang buat booking servis, masuk antrian, servis, dapat nota, lalu bayar.",
            moduleId = "bengkel_service",
            moduleName = "Servis",
            packCode = "workshop",
            garmentPack = false,
            expectedSummary = "Modul Servis: booking dan pelaksanaan servis kendaraan.",
            expectedFeatures = listOf(
                BriefFeatureSpec("Booking servis", "booking servis"),
                BriefFeatureSpec("Antrian", "masuk antrian"),
                BriefFeatureSpec("Nota servis", "dapat nota"),
                BriefFeatureSpec("Pembayaran", "bayar")
            ),
            expectedStages = listOf(
                StageSketchSpec("Booking", "booking servis"),
                StageSketchSpec("Servis", "servis"),
                StageSketchSpec("Nota", "dapat nota"),
                StageSketchSpec("Bayar", "bayar")
            ),
            forbiddenConcepts = setOf("jahit", "kain", "dokter", "resep"),
            evidenceAnchors = setOf("booking servis", "masuk antrian", "dapat nota", "bayar")
        ),
        // 6. Sablon-Bordir
        BuilderBriefGoldenCase(
            name = "sablon-bordir-production",
            narrative = "Usaha sablon dan bordir: terima desain dari客户, cetak di kaos atau bordir di topi, QC, lalu kirim.",
            moduleId = "sablon_production",
            moduleName = "Produksi Sablon",
            packCode = "garment",
            garmentPack = false,
            expectedSummary = "Modul Produksi Sablon: cetak desain pada media.",
            expectedFeatures = listOf(
                BriefFeatureSpec("Terima desain", "terima desain"),
                BriefFeatureSpec("Sablon", "cetak di kaos"),
                BriefFeatureSpec("QC", "bordir di topi"),
                BriefFeatureSpec("Packing", "kirim")
            ),
            expectedStages = listOf(
                StageSketchSpec("Terima Order", "terima desain"),
                StageSketchSpec("Cetak/Bordir", "cetak di kaos"),
                StageSketchSpec("QC", "QC"),
                StageSketchSpec("Kirim", "lalu kirim")
            ),
            forbiddenConcepts = setOf("dokter", "pasien", "resep", "motor"),
            evidenceAnchors = setOf("terima desain", "cetak di kaos", "bordir di topi", "lalu kirim")
        )
    )
}
