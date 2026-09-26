// FILE-SIZE-EXEMPT: katalog aset — data terurut, bukan logika. Lihat .claude/rules/file-size-rules.md §3
package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.pipeline.DefectLiability

/**
 * Katalog bawaan jenis cacat konveksi dan pemetaan stasiun tujuan perbaikan (Defect Routing).
 * Sesuai desain rak perbaikan 3-tingkat di lantai inspeksi QC.
 */
object WorkDefectCatalog {

    val OBRAS_LEPAS = WorkDefectSpec(
        code = DefectCode("obras_lepas"),
        displayName = "Obras Lepas / Tepian Rontok",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("OBRAS"),
        description = "Jahitan overlock putus atau kain tidak terjepit pisau obras"
    )

    val JAHITAN_MELINTIR = WorkDefectSpec(
        code = DefectCode("jahitan_melintir"),
        displayName = "Jahitan Melintir / Sambungan Miring",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("JAHIT_LURUS"),
        description = "Tarikan benang tidak rata pada lengan atau badan"
    )

    val KERAH_MIRING = WorkDefectSpec(
        code = DefectCode("kerah_miring"),
        displayName = "Kerah Miring / Melenceng",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("JAHIT_LURUS"),
        description = "Pemasangan rib atau daun kerah tidak simetris"
    )

    val BENANG_LONCAT = WorkDefectSpec(
        code = DefectCode("benang_loncat"),
        displayName = "Benang Loncat (Skip Stitch)",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("JAHIT_LURUS"),
        description = "Setelan jarum meleset sehingga jeratan benang terputus"
    )

    val SAMBUNGAN_RAJUT_LEPAS = WorkDefectSpec(
        code = DefectCode("sambungan_rajut_lepas"),
        displayName = "Sambungan Rajut Bolong / Lepas",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("SUNTEK"),
        description = "Jeratan jarum suntek linking meleset dari lubang panel"
    )

    val KANCING_COPOT = WorkDefectSpec(
        code = DefectCode("kancing_copot"),
        displayName = "Kancing Copot / Kendor",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("PASANG_KANCING"),
        description = "Ikatan benang kancing kurang rapat atau lepas"
    )

    val LUBANG_KANCING_SEMPIT = WorkDefectSpec(
        code = DefectCode("lubang_kancing_sempit"),
        displayName = "Lubang Kancing Sempit / Belum Putus",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("LUBANG_KANCING"),
        description = "Pisau buttonhole belum memotong sempurna atau jahitan sempit"
    )

    val ZIPER_MACET = WorkDefectSpec(
        code = DefectCode("ziper_macet"),
        displayName = "Ziper Macet / Miring",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("PASANG_ZIPER"),
        description = "Gigi resleting terjepit jahitan atau tarikan melengkung"
    )

    val NODA_OLI = WorkDefectSpec(
        code = DefectCode("noda_oli"),
        displayName = "Noda Minyak / Oli Ringan",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("STEAM"),
        description = "Bercak minyak mesin yang butuh spotting gun & steam ulang (tanpa ke penjahit)"
    )

    val KAIN_KUSUT = WorkDefectSpec(
        code = DefectCode("kain_kusut"),
        displayName = "Kain Kusut Setelah Rework",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("STEAM"),
        description = "Pakaian lecek setelah dibongkar dan butuh setrika ulang"
    )

    val LABEL_TERBALIK = WorkDefectSpec(
        code = DefectCode("label_terbalik"),
        displayName = "Label Terbalik / Miring",
        liability = DefectLiability.FACTORY_WORKMANSHIP,
        targetStationCode = WorkStationCode("PASANG_LABEL"),
        description = "Pemasangan woven label atau care label salah posisi"
    )

    val CACAT_SERAT_BAHAN = WorkDefectSpec(
        code = DefectCode("cacat_serat_bahan"),
        displayName = "Cacat Serat Kain Bawaan",
        liability = DefectLiability.CLIENT_SUPPLIED_DEFECT,
        targetStationCode = WorkStationCode("QC_FINAL"),
        description = "Cacat serat/anyaman bawaan bahan klien yang tidak dapat diperbaiki"
    )

    val BELANG_WARNA = WorkDefectSpec(
        code = DefectCode("belang_warna"),
        displayName = "Belang Warna Kain Supplier",
        liability = DefectLiability.SUPPLIER_VENDOR_DEFECT,
        targetStationCode = WorkStationCode("QC_FINAL"),
        description = "Perbedaan lot celup warna dari pabrik kain mitra"
    )

    private val BUILTIN_DEFECTS: List<WorkDefectSpec> = listOf(
        OBRAS_LEPAS,
        JAHITAN_MELINTIR,
        KERAH_MIRING,
        BENANG_LONCAT,
        SAMBUNGAN_RAJUT_LEPAS,
        KANCING_COPOT,
        LUBANG_KANCING_SEMPIT,
        ZIPER_MACET,
        NODA_OLI,
        KAIN_KUSUT,
        LABEL_TERBALIK,
        CACAT_SERAT_BAHAN,
        BELANG_WARNA
    )

    fun standardDefects(): List<WorkDefectSpec> = BUILTIN_DEFECTS

    fun resolve(code: DefectCode, custom: List<WorkDefectSpec> = emptyList()): WorkDefectSpec? {
        return custom.firstOrNull { it.code == code }
            ?: BUILTIN_DEFECTS.firstOrNull { it.code == code }
    }

    fun route(code: DefectCode, custom: List<WorkDefectSpec> = emptyList()): WorkStationCode? {
        return resolve(code, custom)?.targetStationCode
    }
}
