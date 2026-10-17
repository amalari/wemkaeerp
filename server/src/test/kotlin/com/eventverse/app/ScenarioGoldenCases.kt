package com.eventverse.app

import com.eventverse.app.domain.builder.BriefFeature
import com.eventverse.app.domain.builder.ModuleBrief
import com.eventverse.app.domain.builder.ModuleBriefLimits
import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.evidence.EvidenceSource
import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.domain.discovery.scenario.ScenarioStep
import com.eventverse.app.domain.discovery.scenario.FieldCarry
import com.eventverse.app.domain.discovery.scenario.ScenarioLimits
import com.eventverse.app.domain.pack.ModuleId

/**
 * Kasus emas skenario (TRD-BLDR-003 FR-8): ≥6 kasus berbeda lintas vertikal — garment FOB, garment CMT,
 * garment D2C, klinik, bengkel, dan sablon-bordir.
 *
 * Setiap kasus menyatakan narasi input, skenario diharapkan, konsep terlarang, anchor evidence, dan coverage tags.
 * Golden answer buatan tangan wajib lulus 100% di [ScenarioEvalGrader].
 */
data class ScenarioGoldenCase(
    val name: String,
    /** Narasi input (cerita pengguna). */
    val narrative: String,
    /** Pack yang dipakai. */
    val packCode: String,
    /** `true` = pack garment bawaan; kriteria kemurnian vertikal tidak dinilai untuk kasus ini. */
    val garmentPack: Boolean = false,
    /** Skenario yang diharapkan (id, title, steps dengan evidence). */
    val expectedScenarios: List<ScenarioSpec>,
    /**
     * Konsep terlarang: modul/fitur yang tidak boleh muncul di skenario ini.
     * Grader menolak bila ditemukan.
     */
    val forbiddenModules: Set<String> = emptySet(),
    /**
     * Anchor evidence: substring teks di narasi yang boleh jadi bukti.
     * Grader memeriksa evidence quote terhadap anchor ini.
     */
    val evidenceAnchors: Set<String> = emptySet()
)

/** Spesifikasi satu skenario dalam golden case. */
data class ScenarioSpec(
    val title: String,
    val steps: List<StepSpec>
)

/** Spesifikasi satu langkah dalam skenario. */
data class StepSpec(
    val moduleId: String,
    val label: String,
    /** Anchor evidence: substring di narrative yang jadi bukti. */
    val evidenceAnchor: String? = null,
    /** carries ke langkah berikutnya. */
    val carries: List<FieldCarrySpec> = emptyList()
)

/** Spesifikasi satu carry. */
data class FieldCarrySpec(
    val fromField: String,
    val toField: String
)

/**
 * Fixture skenario dari golden case — dibuat untuk testing grader.
 * Berisi evidence yang valid (anchor ditemukan di narrative).
 */
fun ScenarioGoldenCase.toFixture(): List<ScenarioSketch> {
    return expectedScenarios.map { spec ->
        val steps = spec.steps.map { stepSpec ->
            val evidence = stepSpec.evidenceAnchor?.let { anchor ->
                EvidenceRef(EvidenceSource.NARRATIVE, quote = anchor)
            }
            ScenarioStep(
                moduleId = ModuleId(stepSpec.moduleId),
                screenId = null, // kerangka
                label = stepSpec.label,
                carries = stepSpec.carries.map { FieldCarry(it.fromField, it.toField) },
                evidence = evidence
            )
        }
        ScenarioSketch(
            id = ScenarioId(name),
            title = spec.title,
            steps = steps,
            source = ScenarioSource.AI,
            contractVersion = ScenarioLimits.CONTRACT_VERSION_EVIDENCE
        )
    }
}

object ScenarioGoldenCases {
    val all: List<ScenarioGoldenCase> = listOf(
        // 1. Garment FOB - full package
        ScenarioGoldenCase(
            name = "garment-fob-full",
            narrative = "Konveksi pakaian jadi; kain beli sendiri, potong, jahit, QC, pack, kirim ke buyer.",
            packCode = "garment",
            garmentPack = true,
            expectedScenarios = listOf(
                ScenarioSpec(
                    title = "Produksi Lengkap",
                    steps = listOf(
                        StepSpec("mrp", "Input Order", "kain beli sendiri"),
                        StepSpec("cutting", "Potong Kain", "potong"),
                        StepSpec("sewing", "Jahit", "jahit"),
                        StepSpec("quality_control", "QC", "QC"),
                        StepSpec("fulfillment", "Pack & Kirim", "kirim ke buyer")
                    )
                )
            ),
            forbiddenModules = setOf("klinik", "bengkel", "apotek"),
            evidenceAnchors = setOf("kain beli", "potong", "jahit", "QC", "kirim")
        ),
        // 2. Garment CMT - makloon
        ScenarioGoldenCase(
            name = "garment-cmt-makloon",
            narrative = "Makloon jahit; kain dari buyer, terima, jahit, QC, kirim balik.",
            packCode = "garment",
            garmentPack = true,
            expectedScenarios = listOf(
                ScenarioSpec(
                    title = "Makloon Jahit",
                    steps = listOf(
                        StepSpec("mrp", "Terima Kain", "kain dari buyer"),
                        StepSpec("sewing", "Jahit", "jahit"),
                        StepSpec("quality_control", "QC", "kirim balik"),
                        StepSpec("fulfillment", "Kirim Balik", "kirim balik")
                    )
                )
            ),
            forbiddenModules = setOf("pembelian", "supplier", "klinik"),
            evidenceAnchors = setOf("kain dari buyer", "jahit", "kirim balik")
        ),
        // 3. Garment D2C - marketplace
        ScenarioGoldenCase(
            name = "garment-d2c-marketplace",
            narrative = "Brand distro; produksi sendiri, jual di Shopee Tokopedia, packing dan kirim ke customer.",
            packCode = "garment",
            garmentPack = true,
            expectedScenarios = listOf(
                ScenarioSpec(
                    title = "D2C Marketplace",
                    steps = listOf(
                        StepSpec("mrp", "Input Pesanan", "produksi sendiri"),
                        StepSpec("sewing", "Produksi", "produksi sendiri"),
                        StepSpec("fulfillment", "Packing & Kirim", "Shopee", carries = listOf(
                            FieldCarrySpec("no_resi", "no_resi")
                        )),
                        StepSpec("invoicing", "Buat Invoice", "kirim ke customer")
                    )
                )
            ),
            forbiddenModules = setOf("klinik", "bengkel", "apotek"),
            evidenceAnchors = setOf("produksi sendiri", "Shopee", "kirim ke customer")
        ),
        // 4. Klinik
        ScenarioGoldenCase(
            name = "klinik-patient-flow",
            narrative = "Klinik gigi: pasien daftar, masuk antrian, diperiksa dokter, dapat resep, lalu bayar.",
            packCode = "clinic",
            garmentPack = false,
            expectedScenarios = listOf(
                ScenarioSpec(
                    title = "Pelayanan Pasien",
                    steps = listOf(
                        StepSpec("klinik_registration", "Daftar Pasien", "pasien daftar"),
                        StepSpec("klinik_queue", "Antrian", "masuk antrian"),
                        StepSpec("klinik_exam", "Pemeriksaan", "dokter"),
                        StepSpec("klinik_prescription", "Resep", "dapat resep"),
                        StepSpec("klinik_cashier", "Pembayaran", "bayar")
                    )
                )
            ),
            forbiddenModules = setOf("sewing", "cutting", "fulfillment"),
            evidenceAnchors = setOf("pasien daftar", "masuk antrian", "dokter", "resep", "bayar")
        ),
        // 5. Bengkel
        ScenarioGoldenCase(
            name = "bengkel-service-flow",
            narrative = "Bengkel motor: booking servis, antrian, servis, dapat nota, lalu bayar.",
            packCode = "workshop",
            garmentPack = false,
            expectedScenarios = listOf(
                ScenarioSpec(
                    title = "Servis Kendaraan",
                    steps = listOf(
                        StepSpec("workshop_booking", "Booking Servis", "booking servis"),
                        StepSpec("workshop_queue", "Antrian", "masuk antrian"),
                        StepSpec("workshop_execution", "Servis", "servis"),
                        StepSpec("workshop_invoice", "Nota Servis", "dapat nota"),
                        StepSpec("workshop_cashier", "Pembayaran", "bayar")
                    )
                )
            ),
            forbiddenModules = setOf("sewing", "cutting", "klinik"),
            evidenceAnchors = setOf("booking servis", "servis", "dapat nota", "bayar")
        ),
        // 6. Sablon-Bordir
        ScenarioGoldenCase(
            name = "sablon-bordir-production",
            narrative = "Sablon bordir: terima desain, cetak di kaos atau bordir di topi, QC, packing, kirim.",
            packCode = "garment",
            garmentPack = false,
            expectedScenarios = listOf(
                ScenarioSpec(
                    title = "Produksi Sablon",
                    steps = listOf(
                        StepSpec("design_input", "Terima Desain", "terima desain"),
                        StepSpec("sablon_production", "Sablon/Bordir", "cetak di kaos"),
                        StepSpec("quality_control", "QC", "bordir di topi"),
                        StepSpec("fulfillment", "Packing & Kirim", "kirim")
                    )
                )
            ),
            forbiddenModules = setOf("klinik", "bengkel", "apotek"),
            evidenceAnchors = setOf("terima desain", "cetak", "bordir", "kirim")
        )
    )
}
