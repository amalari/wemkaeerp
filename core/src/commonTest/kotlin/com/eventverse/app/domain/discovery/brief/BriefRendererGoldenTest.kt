package com.eventverse.app.domain.discovery.brief

import com.eventverse.app.domain.prototype.PrototypeContractSamples
import com.eventverse.app.domain.prototype.SpecOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Butir B4: Markdown brief dikunci **byte per byte** (golden test) untuk dua template — tiket servis
 * (non-garment) dan papan SPK sampling (garment). Seluruh istilah vertikal datang dari data; renderer
 * sendiri tidak mengenal satu industri pun.
 */
class BriefRendererGoldenTest {

    // ---- Non-garment: tiket servis ---------------------------------------------------------

    private val layanan = RequirementsBrief(
        packCode = "layanan",
        modules = listOf(
            BriefModule(
                moduleId = "tiket",
                displayName = "Tiket Servis",
                screens = listOf(BriefScreen("Papan Tiket", "KANBAN", "tiket"), BriefScreen("Tambah Tiket", "FORM", "tiket")),
                entities = listOf(
                    BriefEntity(
                        id = "tiket", label = "Tiket",
                        fields = listOf(
                            BriefField("Judul", "TEXT", required = true, options = emptyList()),
                            BriefField("Peminta", "TEXT", required = false, options = emptyList()),
                            BriefField("Status tiket", "ENUM", required = false, options = listOf("Baru", "Diproses", "Selesai"))
                        ),
                        statusField = "Status",
                        transitions = mapOf("Baru" to listOf("Diproses"), "Diproses" to listOf("Selesai", "Baru"))
                    )
                )
            ),
            BriefModule(
                moduleId = "notifikasi",
                displayName = "Notifikasi WA",
                screens = listOf(BriefScreen("Kirim Uji Coba", "FORM", null)),
                entities = emptyList()
            )
        ),
        changes = PrototypeContractSamples.sampleLog,
        coverage = listOf(
            BriefCoverage("tiket", "Tiket Servis", covered = true, monthlyIdr = 350_000, gapLowIdr = null, gapHighIdr = null),
            BriefCoverage("notifikasi", "Notifikasi WA", covered = false, monthlyIdr = null, gapLowIdr = 100_000, gapHighIdr = 250_000)
        ),
        customNeeds = listOf("Integrasi mesin absensi (CUSTOM_EXTENSION)")
    )

    private val layananGolden = """
        # Brief Kebutuhan — layanan

        ## Ringkasan
        - Modul: 2 (1 sudah ada, 1 perlu dibangun)
        - Layar: 3
        - Perubahan klien: 2 (1 diterapkan, 1 ditolak)
        - Kebutuhan kustom: 1

        ## Modul & layar
        ### Tiket Servis (`tiket`)
        - Layar: Papan Tiket (KANBAN) → entitas `tiket`
        - Layar: Tambah Tiket (FORM) → entitas `tiket`
        - Entitas `tiket` — Tiket
          - Judul: TEXT, wajib
          - Peminta: TEXT
          - Status tiket: ENUM (opsi: Baru | Diproses | Selesai)
          - Status `Status`: Baru → Diproses
          - Status `Status`: Diproses → Selesai, Baru

        ### Notifikasi WA (`notifikasi`)
        - Layar: Kirim Uji Coba (FORM) → tanpa entitas

        ## Perubahan dari klien
        - 2026-10-04T09:00:00Z — tambah status 'Revisi' pada 'Status' setelah 'Diproses': diterapkan
        - 2026-10-04T09:01:00Z — izinkan 'Baru' ke 'Hantu' pada 'Status': ditolak (Status 'Hantu' tidak ada.)

        ## Cakupan katalog
        - Tiket Servis: sudah ada — Rp 350.000/bulan
        - Notifikasi WA: perlu dibangun — estimasi Rp 100.000–Rp 250.000/bulan

        ## Kebutuhan kustom
        - Integrasi mesin absensi (CUSTOM_EXTENSION)
    """.trimIndent()
    // ---- Garment: papan SPK sampling -------------------------------------------------------

    private val garment = RequirementsBrief(
        packCode = "garment-ekspor",
        modules = listOf(
            BriefModule(
                moduleId = "sampling_order",
                displayName = "SPK Sampling",
                screens = listOf(BriefScreen("Papan SPK Sampling", "KANBAN", "item")),
                entities = listOf(
                    BriefEntity(
                        id = "item", label = "SPK Sampling",
                        fields = listOf(
                            BriefField("Kolom", "ENUM", required = false, options = listOf("Baru", "Dikerjakan", "Selesai")),
                            BriefField("Kartu", "TEXT", required = false, options = emptyList()),
                            BriefField("Detail", "TEXT", required = false, options = emptyList())
                        ),
                        statusField = "Kolom",
                        transitions = mapOf(
                            "Baru" to listOf("Dikerjakan"),
                            "Dikerjakan" to listOf("Baru", "Selesai"),
                            "Selesai" to listOf("Dikerjakan")
                        )
                    )
                )
            )
        ),
        changes = listOf(
            CaptureEntry("2026-10-04T10:00:00Z", SpecOp.AddEnumOption("item", "Kolom", "Pressing", after = "Selesai"), ok = true, message = null)
        ),
        coverage = listOf(BriefCoverage("sampling_order", "Sampling Order", covered = false, monthlyIdr = null, gapLowIdr = 500_000, gapHighIdr = 900_000)),
        customNeeds = listOf("Bordir komputer 12 kepala (CUSTOM_EXTENSION)")
    )

    private val garmentGolden = """
        # Brief Kebutuhan — garment-ekspor

        ## Ringkasan
        - Modul: 1 (0 sudah ada, 1 perlu dibangun)
        - Layar: 1
        - Perubahan klien: 1 (1 diterapkan, 0 ditolak)
        - Kebutuhan kustom: 1

        ## Modul & layar
        ### SPK Sampling (`sampling_order`)
        - Layar: Papan SPK Sampling (KANBAN) → entitas `item`
        - Entitas `item` — SPK Sampling
          - Kolom: ENUM (opsi: Baru | Dikerjakan | Selesai)
          - Kartu: TEXT
          - Detail: TEXT
          - Status `Kolom`: Baru → Dikerjakan
          - Status `Kolom`: Dikerjakan → Baru, Selesai
          - Status `Kolom`: Selesai → Dikerjakan

        ## Perubahan dari klien
        - 2026-10-04T10:00:00Z — tambah status 'Pressing' pada 'Kolom' setelah 'Selesai': diterapkan

        ## Cakupan katalog
        - Sampling Order: perlu dibangun — estimasi Rp 500.000–Rp 900.000/bulan

        ## Kebutuhan kustom
        - Bordir komputer 12 kepala (CUSTOM_EXTENSION)
    """.trimIndent()

    @Test
    fun golden_layanan_isBytePerByteExact() {
        assertEquals(layananGolden + "\n", BriefRenderer.markdown(layanan), "renderer mengakhiri output dengan newline")
    }

    @Test
    fun golden_garment_isBytePerByteExact_andDeterministic() {
        val md = BriefRenderer.markdown(garment)
        assertEquals(garmentGolden + "\n", md)
        assertEquals(md, BriefRenderer.markdown(garment), "masukan sama → keluaran byte per byte sama")
    }

    @Test
    fun moduleWithoutChanges_isStillPrinted_andEmptySectionsExplained() {
        val kosong = layanan.copy(changes = emptyList(), customNeeds = emptyList())
        val md = BriefRenderer.markdown(kosong)
        assertTrue("### Tiket Servis (`tiket`)" in md, "modul tanpa perubahan tetap tercetak")
        assertTrue("## Perubahan dari klien" in md && "_Tidak ada perubahan._" in md)
        assertTrue("- Kebutuhan kustom: 0" in md && "_Tidak ada._" in md)
    }
}