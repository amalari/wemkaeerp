package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.DefectLiability

/**
 * Katalog cacat rajut & linking yang paling sering ditemukan di meja inspeksi.
 *
 * [liability] menentukan siapa menanggung biaya pengerjaan ulang:
 * cacat serat bawaan benang titipan buyer tidak boleh dibebankan ke penjahit.
 */
enum class QcDefectType(val displayName: String, val liability: DefectLiability) {
    BROKEN_NEEDLE("Jarum patah", DefectLiability.FACTORY_WORKMANSHIP),
    DROP_STITCH("Bolong / drop stitch", DefectLiability.FACTORY_WORKMANSHIP),
    LINKING_SKIP("Jahitan linking loncat", DefectLiability.FACTORY_WORKMANSHIP),
    LOOSE_BUTTON("Kancing kendor", DefectLiability.FACTORY_WORKMANSHIP),
    STEAM_MARK("Bekas setrika / steam", DefectLiability.FACTORY_WORKMANSHIP),
    COLOR_SHADING("Belang warna benang", DefectLiability.SUPPLIER_VENDOR_DEFECT),
    YARN_FLAW("Cacat serat benang bawaan", DefectLiability.CLIENT_SUPPLIED_DEFECT),
    OTHER("Lain-lain", DefectLiability.FACTORY_WORKMANSHIP);
}
