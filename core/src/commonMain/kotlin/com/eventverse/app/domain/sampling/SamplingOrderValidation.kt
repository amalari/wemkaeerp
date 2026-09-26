package com.eventverse.app.domain.sampling

/**
 * Validasi gerbang persetujuan ACC dan penerbitan SPK untuk [SamplingOrder].
 * Dipisahkan dari entity utama agar mematuhi batas ukuran file domain (<400 baris).
 */

fun SamplingOrder.missingApprovalRequirements(sizeMatrix: List<SizeChartRow> = this.sizeMatrix): List<String> = buildList {
    if (mockupFrontKey.isNullOrBlank()) {
        add("Foto mockup Tampak Depan wajib diunggah (Tampak Belakang opsional).")
    }
    if (firstCompleteSizeColumn(sizeMatrix) == null) {
        add("Size chart wajib punya minimal 1 ukuran dengan seluruh baris pengukuran terisi lengkap.")
    }
    if (calculateTotalSampleQuantity(sizeMatrix) < 1) {
        add("Jumlah sampel minimal 1 pcs.")
    }
}

val SamplingOrder.isReadyForAcc: Boolean get() = missingApprovalRequirements().isEmpty()

fun SamplingOrder.missingSpkRequirements(sizeMatrix: List<SizeChartRow> = this.sizeMatrix): List<String> = buildList {
    if (styleName.isBlank()) {
        add("Nama desain tidak boleh kosong.")
    }
    if (mockupFrontKey.isNullOrBlank()) {
        add("Foto mockup Tampak Depan wajib diunggah.")
    }
    if (!hasAtLeastOneCompleteMeasurementColumn(sizeMatrix)) {
        add("Size chart wajib memiliki minimal 1 ukuran dengan seluruh baris spesifikasi (POM) terisi lengkap (misal: ALL SIZE terisi seluruhnya).")
    }
    val totalQty = calculateTotalSampleQuantity(sizeMatrix, sampleQuantity)
    if (totalQty < 1) {
        add("Jumlah sampel minimal 1 pcs. Silakan tentukan alokasi kuantitas pada kolom ukuran yang aktif di tabel Size Chart.")
    }
    if (deadlineDelivery == null) {
        add("Target deadline pengiriman sampel wajib diisi.")
    }
}

fun SamplingOrder.spkValidationWarnings(): List<String> = buildList {
    if (mockupBackKey.isNullOrBlank()) {
        add("Foto mockup Tampak Belakang belum diunggah (opsional).")
    }
}

val SamplingOrder.isReadyForSpk: Boolean get() = missingSpkRequirements().isEmpty()
