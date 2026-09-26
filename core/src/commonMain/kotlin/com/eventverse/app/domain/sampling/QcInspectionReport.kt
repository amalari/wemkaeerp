package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.DefectLiability
import kotlinx.datetime.Instant

/**
 * Dua meja inspeksi yang berbeda pekerjaannya, bukan dua nama untuk hal yang sama.
 *
 * [KNITTING] memeriksa panel rajut mentah begitu turun mesin — cacat yang ketahuan di sini
 * belum menanggung ongkos linking dan finishing. [FINISHING] memeriksa baju yang sudah jadi
 * utuh, dan lembarnyalah yang dikirim ke buyer.
 */
enum class QcInspectionKind(val displayName: String, val shortLabel: String) {
    KNITTING("Inspeksi QC Rajut", "QC Rajut"),
    FINISHING("Inspeksi QC Finishing", "QC Finishing")
}

enum class QcInspectionResult(val displayName: String) {
    PASSED("Lolos QC (Passed)"),
    REWORK("Perlu Perbaikan (Rework)"),
    REJECT("Ditolak / Rajut Ulang (Reject)");
}

data class QcPomMeasurement(
    val pomName: String,
    val targetCm: Double,
    val actualCm: Double,
    val toleranceCm: Double = 1.0,
    /**
     * Catatan untuk titik ukur ini saja.
     *
     * Ditempelkan ke barisnya, bukan dikumpulkan di satu kolom di bawah, karena "jahitan bahu
     * kiri bergelombang" hanya berarti kalau jelas ia menempel pada Lebar Bahu. Catatan global
     * memaksa petugas menulis ulang nama titiknya di dalam kalimat, dan itu yang biasanya tidak
     * dilakukan saat sedang buru-buru.
     */
    val notes: String = "",
    /**
     * Angka ini dibawa dari pemeriksaan sebelumnya pada pcs yang sama, bukan diukur ulang.
     *
     * Ditandai supaya berita acara tidak mengaku lebih dari yang terjadi: saat satu titik
     * diperbaiki di lantai produksi, titik lain tidak otomatis ikut diukur lagi — dan pembaca
     * lembar berhak tahu mana angka hasil meteran hari ini dan mana yang diwarisi.
     */
    val carriedOver: Boolean = false
) {
    val deviationCm: Double get() = kotlin.math.abs(actualCm - targetCm)
    val isWithinTolerance: Boolean get() = deviationCm <= toleranceCm
    val hasNote: Boolean get() = notes.isNotBlank()

    /** Titik ini bermasalah bila ukurannya lewat toleransi atau petugas menuliskan sesuatu. */
    val isFlagged: Boolean get() = !isWithinTolerance || hasNote
}

/**
 * Berita acara satu kali inspeksi QC. Satu SPK bisa punya beberapa lembar (setelah rework,
 * setelah revisi) dan tiap lembar berdiri sendiri — karena itu riwayatnya disimpan utuh,
 * bukan ditimpa.
 *
 * [defectsFound] disimpan sebagai `QcDefectType.name` agar pertanggungjawaban biaya rework
 * ([DefectLiability]) bisa dipulihkan kembali. Data lama yang terlanjur menyimpan label
 * berbahasa Indonesia tetap terbaca lewat [resolveDefectType].
 */
data class QcInspectionReport(
    val id: String = "",
    val samplingOrderId: String = "",
    val kind: QcInspectionKind = QcInspectionKind.FINISHING,
    val inspectorName: String,
    val inspectedAt: Instant,
    /**
     * Pcs keberapa dari SPK ini yang diperiksa lembar ini (1-based).
     *
     * Inspeksi sampling dikerjakan satu baju satu lembar — 3 pcs berarti 3 lembar. Menyimpan
     * nomornya membuat "sudah 2 dari 3" bisa dijawab tanpa menebak dari jumlah baris.
     */
    val pieceNo: Int = 1,
    /** Selalu 1 di jalur sampling; disediakan untuk jalur lot yang menutup banyak pcs sekaligus. */
    val inspectedQty: Int = 1,
    val pomMeasurements: List<QcPomMeasurement> = emptyList(),
    val defectsFound: List<String> = emptyList(),
    val qcResult: QcInspectionResult = QcInspectionResult.PASSED,
    val qcNotes: String = "",
    val verifiedPhotoFrontKey: String? = null,
    val verifiedPhotoBackKey: String? = null
) {
    val defectTypes: List<QcDefectType> get() = defectsFound.mapNotNull(::resolveDefectType)

    /** Label siap tampil: tipe yang dikenal memakai nama katalog, sisanya apa adanya. */
    val defectLabels: List<String>
        get() = defectsFound.map { raw -> resolveDefectType(raw)?.displayName ?: raw }

    /** Cacat yang biayanya jatuh ke pabrik — dasar rework tanpa merugikan penjahit. */
    val factoryLiableDefects: List<QcDefectType>
        get() = defectTypes.filter { it.liability == DefectLiability.FACTORY_WORKMANSHIP }

    val outOfToleranceMeasurements: List<QcPomMeasurement>
        get() = pomMeasurements.filter { !it.isWithinTolerance }

    val hasSizeDeviation: Boolean get() = outOfToleranceMeasurements.isNotEmpty()

    /** Titik ukur yang bermasalah — entah karena lewat toleransi, entah karena dicatat. */
    val flaggedMeasurements: List<QcPomMeasurement> get() = pomMeasurements.filter { it.isFlagged }

    /** Hasil yang seharusnya, menurut temuan yang tercatat di lembar ini. */
    val derivedResult: QcInspectionResult get() = deriveResult(pomMeasurements, qcNotes)

    /**
     * Lembar lama (hasil checklist cacat terstruktur) bisa saja punya kombinasi yang tidak
     * mengikuti aturan catatan. Dibiarkan terbaca apa adanya, tapi ketidakcocokannya bisa
     * ditanyakan lewat sini alih-alih diam-diam ditimpa.
     */
    val isResultConsistentWithNotes: Boolean get() = qcResult == derivedResult

    /**
     * Syarat yang belum terpenuhi untuk menyatakan lolos. Kosong berarti boleh diluluskan.
     *
     * Pengukuran wajib ada karena lembar inilah yang dikirim ke buyer sebagai bukti — lembar
     * lolos tanpa satu pun angka ukur adalah pernyataan kosong.
     */
    val missingPassRequirements: List<String>
        get() = buildList {
            if (qcResult != QcInspectionResult.PASSED) return@buildList
            if (pomMeasurements.isEmpty()) {
                add("Minimal satu titik ukur (POM) wajib diisi sebelum QC dinyatakan lolos.")
            }
        }

    val isReadyToPass: Boolean get() = missingPassRequirements.isEmpty()

    companion object {
        /**
         * Aturan tunggal penentu hasil: **kolom catatan yang terisi berarti ada temuan**,
         * dan temuan berarti tidak lolos.
         *
         * Petugas jadi tidak perlu memilih apa pun — ia menulis apa yang dilihatnya, atau tidak
         * menulis sama sekali. Ini menghapus seluruh kelas kesalahan "menulis cacat panjang
         * lebar lalu menekan LOLOS", yang di lembar tiga-tombol adalah satu salah-klik saja.
         *
         * Ukuran yang lewat toleransi juga menggagalkan tanpa perlu ditulis apa-apa — itu
         * seluruh gunanya mengukur, dan mencatat deviasi 5 cm lalu menandainya lolos adalah
         * kontradiksi yang tidak perlu diberi jalan.
         *
         * Konsekuensi yang harus diterima: catatan netral ("warna sesuai lot B") ikut
         * menggagalkan. Karena itu kolomnya diberi nama temuan/cacat, bukan "catatan" umum.
         */
        fun deriveResult(
            measurements: List<QcPomMeasurement>,
            notes: String = ""
        ): QcInspectionResult = when {
            measurements.any { it.isFlagged } -> QcInspectionResult.REWORK
            notes.isNotBlank() -> QcInspectionResult.REWORK
            else -> QcInspectionResult.PASSED
        }
    }
}

/** Sumbangan satu petugas pada satu SPK: dipakai untuk baris "Budi — 10 pcs". */
data class QcInspectorContribution(
    val inspectorName: String,
    val qtyPcs: Int,
    val passedQty: Int
)

/**
 * Rekap siapa memeriksa berapa, untuk satu jenis QC.
 *
 * Diurutkan dari kontribusi terbesar supaya di lantai produksi baris teratas selalu orang yang
 * benar-benar mengerjakan lot itu, bukan siapa pun yang kebetulan menyentuhnya paling awal.
 */
fun List<QcInspectionReport>.tallyBy(kind: QcInspectionKind): List<QcInspectorContribution> =
    filter { it.kind == kind }
        .groupBy { it.inspectorName.trim().ifBlank { "Tanpa nama" } }
        .map { (name, reports) ->
            QcInspectorContribution(
                inspectorName = name,
                qtyPcs = reports.sumOf { it.inspectedQty },
                passedQty = reports.filter { it.qcResult == QcInspectionResult.PASSED }.sumOf { it.inspectedQty }
            )
        }
        .sortedByDescending { it.qtyPcs }

/**
 * Berapa **pcs berbeda** yang sudah tersentuh pada satu jenis QC — ini jawaban untuk
 * "sudah 2 dari 3 pcs".
 *
 * Sengaja dibedakan dari [totalInspectionWorkFor]: satu baju yang diperiksa ulang setelah
 * rework menambah beban kerja petugas, tapi tidak menambah cakupan lot.
 */
/** Lembar terakhir untuk satu pcs — inilah yang menyatakan status pcs itu sekarang. */
fun List<QcInspectionReport>.latestReportForPiece(kind: QcInspectionKind, pieceNo: Int): QcInspectionReport? =
    filter { it.kind == kind && it.pieceNo == pieceNo }.maxByOrNull { it.inspectedAt }

/**
 * Pcs yang benar-benar **beres**: lembar terakhirnya lolos.
 *
 * Pcs yang diperiksa lalu dinyatakan perlu perbaikan tidak masuk hitungan ini. Ia sudah
 * tersentuh, tapi belum selesai — dan SPK yang seluruh pcs-nya perlu perbaikan bukan SPK
 * yang sudah rampung.
 */
fun List<QcInspectionReport>.passedPieceCountFor(kind: QcInspectionKind, targetQty: Int): Int =
    (1..targetQty.coerceAtLeast(1)).count { pieceNo ->
        latestReportForPiece(kind, pieceNo)?.qcResult == QcInspectionResult.PASSED
    }

/**
 * Nomor pcs yang perlu dikerjakan berikutnya: yang belum pernah diperiksa, atau yang
 * pemeriksaan terakhirnya menyatakan perlu perbaikan dan kini kembali dari lantai produksi.
 */
fun List<QcInspectionReport>.nextPieceNoFor(kind: QcInspectionKind, targetQty: Int): Int {
    val total = targetQty.coerceAtLeast(1)
    // Pcs yang belum tersentuh didahulukan: menuntaskan lot lebih dulu, baru menengok
    // yang dikembalikan — barang rework biasanya belum kembali ke meja saat ini juga.
    val untouched = (1..total).firstOrNull { latestReportForPiece(kind, it) == null }
    if (untouched != null) return untouched
    val needsRecheck = (1..total).firstOrNull {
        latestReportForPiece(kind, it)?.qcResult != QcInspectionResult.PASSED
    }
    return needsRecheck ?: total
}

/** Seluruh pcs sudah lolos — hanya ini yang boleh disebut selesai. */
fun List<QcInspectionReport>.isCompleteFor(kind: QcInspectionKind, targetQty: Int): Boolean =
    targetQty > 0 && passedPieceCountFor(kind, targetQty) >= targetQty

/** Total pcs yang tercatat diperiksa, termasuk pemeriksaan ulang. Ukuran beban kerja. */
fun List<QcInspectionReport>.totalInspectionWorkFor(kind: QcInspectionKind): Int =
    filter { it.kind == kind }.sumOf { it.inspectedQty }

/** Cocokkan dulu sebagai nama enum, lalu sebagai label katalog untuk data lama. */
private fun resolveDefectType(raw: String): QcDefectType? {
    val trimmed = raw.trim()
    return QcDefectType.entries.firstOrNull { it.name == trimmed }
        ?: QcDefectType.entries.firstOrNull { it.displayName.equals(trimmed, ignoreCase = true) }
}
