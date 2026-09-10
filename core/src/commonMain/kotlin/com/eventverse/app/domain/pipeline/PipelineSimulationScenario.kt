package com.eventverse.app.domain.pipeline

/**
 * Operational simulation scenarios representing real factory operating conditions.
 * Allows simulating happy path vs exception cases (such as QC failures).
 */
enum class PipelineSimulationScenario(
    val id: String,
    val title: String,
    val badgeLabel: String,
    val description: String,
    val defectRatePercent: Double,
    val leadTimeImpactDays: Double,
    val activeFeedbackType: PipelineEdgeType?
) {
    NORMAL(
        id = "normal",
        title = "Normal (Happy Path - Lolos QC 100%)",
        badgeLabel = "Happy Path (Normal)",
        description = "Seluruh tahapan produksi berjalan lancar tanpa cacat signifikan. Reject rate terkendali di 1.2%.",
        defectRatePercent = 1.2,
        leadTimeImpactDays = 0.0,
        activeFeedbackType = null
    ),

    QC_FABRIC_DEFECT(
        id = "qc_fabric_defect",
        title = "QC Gagal: Cacat Bahan Baku (Feedback ke Rantai Pasok)",
        badgeLabel = "QC Gagal: Cacat Kain",
        description = "Ditemukan cacat tenun & ketidaksesuaian shading rol kain di QC (Reject 4.8%). Mengaktifkan loop balik ke Gudang/Rantai Pasok untuk retur suplier & pengadaan kain pengganti.",
        defectRatePercent = 4.8,
        leadTimeImpactDays = 2.2,
        activeFeedbackType = PipelineEdgeType.FEEDBACK_DEFECT
    ),

    QC_WORKMANSHIP_DEFECT(
        id = "qc_workmanship_defect",
        title = "QC Gagal: Cacat Pengerjaan (Rework ke Lantai Produksi)",
        badgeLabel = "QC Gagal: Rework Jahit",
        description = "Ditemukan jahitan loncat & obras miring pada 95 pcs baju. Mengaktifkan feedback loop ke Operator Jahit untuk stasiun alterasi/permak sebelum dipacking.",
        defectRatePercent = 3.6,
        leadTimeImpactDays = 0.9,
        activeFeedbackType = PipelineEdgeType.FEEDBACK_REWORK
    );

    val isDefectActive: Boolean get() = this != NORMAL
}
