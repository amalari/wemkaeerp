package com.eventverse.app.presentation.qc

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.eventverse.app.domain.sampling.QcInspectionKind
import com.eventverse.app.domain.sampling.QcInspectionReport
import com.eventverse.app.domain.sampling.QcInspectionResult
import com.eventverse.app.domain.sampling.QcPomMeasurement
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SizeMeasurement
import kotlinx.datetime.Instant

/** Satu titik ukur yang ditawarkan ke inspektor, beserta target dari size chart buyer. */
data class QcPomField(
    val key: String,
    val label: String,
    val targetCm: Double
)

/**
 * Titik ukur yang ditawarkan diturunkan dari size chart, bukan dari daftar tetap: target
 * bernilai 0 berarti buyer memang tidak menetapkan ukuran itu, dan memintanya diukur hanya
 * akan menghasilkan angka yang tidak punya pembanding.
 */
fun pomFieldsFor(chart: SizeMeasurement): List<QcPomField> = listOf(
    QcPomField("bodyLength", "Panjang Baju (Body Length)", chart.bodyLength),
    QcPomField("bodyWidth", "Lebar Dada (Body Width)", chart.bodyWidth),
    QcPomField("sleeveLength", "Panjang Tangan (Sleeve Length)", chart.sleeveLength),
    QcPomField("armHole", "Arm Hole", chart.armHole),
    QcPomField("shoulderWidth", "Lebar Bahu (Shoulder Width)", chart.shoulderWidth),
    QcPomField("neckWidth", "Lebar Leher (Neck Width)", chart.neckWidth),
    QcPomField("neckDrop", "Turun Leher (Neck Drop)", chart.neckDrop),
    QcPomField("sleeveOpening", "Bukaan Tangan (Sleeve Opening)", chart.sleeveOpening),
    QcPomField("ribHeight", "Tinggi Rib", chart.ribHeight),
    QcPomField("collarHeight", "Tinggi Kerah", chart.collarHeight),
    QcPomField("placketWidth", "Lebar Placket", chart.placketWidth)
).filter { it.targetCm > 0.0 }

/**
 * State satu lembar inspeksi — **satu lembar = satu pcs**.
 *
 * Seluruh isian lembar ini cuma dua jenis: angka ukur dan catatan, keduanya per titik ukur.
 * Tidak ada pilihan hasil, tidak ada isian nama, tidak ada checklist. Petugas mengukur,
 * menulis kalau ada yang aneh, lalu submit — hasilnya diturunkan sistem.
 *
 * Kolom ukuran dimulai kosong. Mengisinya dengan angka target akan membuat setiap baris tampil
 * "sesuai" sebelum meteran menyentuh baju, dan lembar yang tersimpan berisi pengukuran yang
 * tidak pernah dilakukan siapa pun.
 */
class QcInspectionFormState(
    val pomFields: List<QcPomField>,
    val pieceNo: Int,
    /**
     * Lembar sebelumnya untuk pcs yang sama, bila ini pemeriksaan ulang setelah perbaikan.
     *
     * Titik yang dulu lolos dibawa apa adanya dan ditandai [QcPomMeasurement.carriedOver];
     * yang wajib diukur ulang hanya titik yang dulu bermasalah — itulah yang diperbaiki.
     */
    private val previousReport: QcInspectionReport? = null
) {
    /** Angka yang diwarisi dari pemeriksaan sebelumnya, per kunci titik ukur. */
    private val carried: Map<String, QcPomMeasurement> =
        previousReport?.pomMeasurements
            ?.filter { !it.isFlagged }
            ?.mapNotNull { measurement ->
                pomFields.firstOrNull { it.label == measurement.pomName }?.let { it.key to measurement }
            }
            ?.toMap()
            .orEmpty()

    val isRecheck: Boolean get() = previousReport != null

    fun carriedValueOf(key: String): QcPomMeasurement? = carried[key]

    /** Titik yang wajib diisi lembar ini. Pada pemeriksaan ulang, hanya yang dulu bermasalah. */
    val requiredKeys: Set<String> =
        if (previousReport == null) pomFields.map { it.key }.toSet()
        else pomFields.map { it.key }.filterNot { it in carried }.toSet()

    private val actualByKey: SnapshotStateMap<String, String> = mutableStateMapOf()
    private val notesByKey: SnapshotStateMap<String, String> = mutableStateMapOf()

    fun actualOf(key: String): String = actualByKey[key].orEmpty()

    fun noteOf(key: String): String = notesByKey[key].orEmpty()

    fun setActual(key: String, raw: String) {
        // Meja QC memakai meteran kain: hanya angka dan satu pemisah desimal yang masuk akal.
        val sanitized = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
        if (sanitized.count { it == '.' } > 1) return
        actualByKey[key] = sanitized
    }

    fun setNote(key: String, raw: String) {
        notesByKey[key] = raw
    }

    /** Ketikan saat ini, untuk disimpan sebagai draf. */
    val draft: QcDraft
        get() = QcDraft(actuals = actualByKey.toMap(), notes = notesByKey.toMap())

    /** Kembalikan ketikan yang tertinggal. Hanya dipanggil sekali, saat form dirakit. */
    fun restore(draft: QcDraft) {
        actualByKey.clear()
        notesByKey.clear()
        actualByKey.putAll(draft.actuals)
        notesByKey.putAll(draft.notes)
    }

    /**
     * Isi lembar: angka yang diketik hari ini, ditambah angka warisan untuk titik yang tidak
     * perlu diukur ulang. Yang diwarisi ditandai supaya keduanya tidak tertukar saat dibaca.
     */
    val measurements: List<QcPomMeasurement>
        get() = pomFields.mapNotNull { field ->
            val typed = actualOf(field.key).toDoubleOrNull()
            if (typed != null) {
                return@mapNotNull QcPomMeasurement(
                    pomName = field.label,
                    targetCm = field.targetCm,
                    actualCm = typed,
                    notes = noteOf(field.key).trim()
                )
            }
            carried[field.key]?.copy(notes = noteOf(field.key).trim(), carriedOver = true)
        }

    /** Yang diketik hari ini saja — warisan tidak dihitung sebagai pekerjaan. */
    val filledCount: Int get() = requiredKeys.count { actualOf(it).toDoubleOrNull() != null }

    val requiredCount: Int get() = requiredKeys.size

    /** Seluruh titik yang wajib diukur sudah terisi. */
    val isComplete: Boolean get() = requiredKeys.isNotEmpty() && filledCount == requiredKeys.size

    /** Hasil yang akan tercatat kalau lembar ini disubmit sekarang. */
    val result: QcInspectionResult get() = QcInspectionReport.deriveResult(measurements)

    val flaggedCount: Int get() = measurements.count { it.isFlagged }

    fun buildReport(
        order: SamplingOrder,
        kind: QcInspectionKind,
        inspectorName: String,
        inspectedAt: Instant
    ): QcInspectionReport = QcInspectionReport(
        samplingOrderId = order.id.value,
        kind = kind,
        inspectorName = inspectorName,
        inspectedAt = inspectedAt,
        pieceNo = pieceNo,
        inspectedQty = 1,
        pomMeasurements = measurements,
        qcResult = result
    )
}
