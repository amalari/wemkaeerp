package com.eventverse.app.domain.traceability.print

import com.eventverse.app.domain.sampling.SpkUrgencyLevel
import com.eventverse.app.domain.traceability.TraceCode
import kotlinx.datetime.LocalDate

/** Satu pasang `label : nilai` yang tercetak di grid ukuran kartu. */
data class SpkMeasurementRow(val label: String, val value: String)

/**
 * Satu halaman kartu — satu ukuran. Sama seperti lembar kerja rajut: satu mesin mengerjakan satu
 * ukuran dalam satu waktu, dan kartu yang memuat semua ukuran memaksa operator mencari barisnya.
 */
data class SpkSizeCard(
    val sizeLabel: String,
    val qtyPcs: Int,
    /** Kode telusur tier WORKSHEET milik ukuran ini — QR kartu mengarah ke halaman yang sama dengan lembar kerja. */
    val code: TraceCode,
    val humanCode: String,
    /** Titik ukur dari buyer (baris POM matriks, kolom ukuran ini). */
    val pomRows: List<SpkMeasurementRow>,
    /** Hasil ukuran tim sampling dari lembar Program CAM (section HASIL UKURAN JADI). */
    val samplingRows: List<SpkMeasurementRow>
)

/**
 * Isi Kartu SPK A6 — satu kartu per SPK, satu halaman per ukuran aktif.
 *
 * Angka urgensi di sini adalah **snapshot saat dicetak**: slack dihitung ulang setiap generate,
 * tidak pernah disimpan. Kartu yang dicetak Senin boleh saja keteteran hari Kamis — cetak ulang
 * adalah cara menyegarkannya, dan QR-nya tetap pintu ke status yang live.
 */
data class SpkCardContent(
    val spkNumber: String,
    val styleName: String,
    val clientName: String,
    /** Nomor revisi sampel (Rev 0 = sampel awal). */
    val revision: Int,
    val stageLabel: String,
    val stageNumber: Int,
    val stageCount: Int,
    val deadline: LocalDate?,
    val urgencyLevel: SpkUrgencyLevel,
    val slackDays: Int?,
    /** Urutan SPK ini di antara seluruh SPK aktif tenant saat kartu dicetak (1 = paling genting). */
    val rank: Int,
    val activeCount: Int,
    /** Kode warna benang dari instruksi feeder Program CAM. */
    val colorways: List<String>,
    val printedOn: LocalDate,
    val cards: List<SpkSizeCard>
) {
    init {
        require(cards.isNotEmpty()) { "Kartu SPK butuh minimal satu ukuran" }
    }

    val colorwayText: String get() = colorways.joinToString(" + ")
}