package com.eventverse.app.domain.pipeline

/**
 * Attribution of responsibility when quality defects occur.
 */
enum class DefectLiability(
    val code: String,
    val displayName: String,
    val description: String
) {
    FACTORY_WORKMANSHIP(
        code = "factory_workmanship",
        displayName = "Tanggung Jawab Pabrik (Jahitan / Pemotongan)",
        description = "Cacat pengerjaan operator pabrik; pabrik menanggung biaya pengerjaan ulang (rework)."
    ),
    CLIENT_SUPPLIED_DEFECT(
        code = "client_supplied_defect",
        displayName = "Cacat Bahan Bawaan Buyer (Makloon CMT)",
        description = "Cacat tenun/serat kain yang dibawa klien; bukan kelalaian pabrik, dikomunikasikan ke buyer."
    ),
    SUPPLIER_VENDOR_DEFECT(
        code = "supplier_vendor_defect",
        displayName = "Cacat Pabrik Kain Rekanan (FOB)",
        description = "Kain susut/belang dari supplier pabrik; klaim retur / debit note ke penjual kain."
    );
}
