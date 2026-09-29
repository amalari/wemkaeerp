// FILE-SIZE-EXEMPT: katalog aset — data terurut, bukan logika. Lihat .claude/rules/file-size-rules.md §3
package com.eventverse.app.domain.workqueue

import com.eventverse.app.domain.pipeline.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype

/**
 * Katalog bawaan 11 stasiun kerja konveksi standar WeMade ERP.
 * Mendukung penggabungan stasiun kustom per tenant serta penentuan rute dinamis (dynamic bypass).
 */
object WorkStationCatalog {

    val CUTTING = WorkStationSpec(
        code = WorkStationCode("CUTTING"),
        displayName = "Meja Potong / Cutting",
        archetype = GarmentSlots.SEWING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 500L,
        standardMinutesPerPiece = 1.0,
        description = "Gelar kain, pemotongan pola, dan pengikatan bundle"
    )

    val KNITTING = WorkStationSpec(
        code = WorkStationCode("KNITTING"),
        displayName = "Mesin Rajut / Turun Mesin",
        archetype = GarmentSlots.SEWING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 1500L,
        standardMinutesPerPiece = 15.0,
        description = "Perajutan panel rajut mentah (flatbed/circular)"
    )

    val JAHIT_LURUS = WorkStationSpec(
        code = WorkStationCode("JAHIT_LURUS"),
        displayName = "Jahit Lurus / Perakitan Badan",
        archetype = GarmentSlots.SEWING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 2000L,
        standardMinutesPerPiece = 3.0,
        description = "Penyambungan badan, lengan, kerah, dan jahitan tindas"
    )

    val OBRAS = WorkStationSpec(
        code = WorkStationCode("OBRAS"),
        displayName = "Obras / Overlock",
        archetype = GarmentSlots.SEWING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 1000L,
        standardMinutesPerPiece = 2.0,
        description = "Merapikan dan menjepit tepian kain agar tidak rontok"
    )

    val SUNTEK = WorkStationSpec(
        code = WorkStationCode("SUNTEK"),
        displayName = "Suntek / Linking Rajut",
        archetype = GarmentSlots.SEWING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 2500L,
        standardMinutesPerPiece = 4.0,
        description = "Menyambung jeratan jarum panel rajut sweater/cardigan"
    )

    val LUBANG_KANCING = WorkStationSpec(
        code = WorkStationCode("LUBANG_KANCING"),
        displayName = "Lubang Kancing (Buttonhole)",
        archetype = GarmentSlots.SEWING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 300L,
        standardMinutesPerPiece = 0.5,
        description = "Pembuatan lubang kancing otomatis dengan pisau"
    )

    val PASANG_KANCING = WorkStationSpec(
        code = WorkStationCode("PASANG_KANCING"),
        displayName = "Pasang Kancing (Button Attach)",
        archetype = GarmentSlots.SEWING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 300L,
        standardMinutesPerPiece = 0.5,
        description = "Pemasangan butir kancing pada plaket baju"
    )

    val PASANG_ZIPER = WorkStationSpec(
        code = WorkStationCode("PASANG_ZIPER"),
        displayName = "Pasang Ziper (Zipper Attach)",
        archetype = GarmentSlots.SEWING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 1000L,
        standardMinutesPerPiece = 1.8,
        description = "Pemasangan resleting pada jaket atau celana"
    )

    val PASANG_LABEL = WorkStationSpec(
        code = WorkStationCode("PASANG_LABEL"),
        displayName = "Pasang Label & Trims",
        archetype = GarmentSlots.FINISHING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 300L,
        standardMinutesPerPiece = 0.5,
        description = "Pemasangan woven label, care label, dan size label"
    )

    val WASHING = WorkStationSpec(
        code = WorkStationCode("WASHING"),
        displayName = "Washing & Softener (Peleburan Lot)",
        archetype = GarmentSlots.FINISHING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
        piecerateTariffIdr = 800L,
        standardMinutesPerPiece = 2.5,
        isMergeGate = true,
        isBatchStation = true,
        description = "Tali bundle dilepas, masuk drum cuci masal, satuan berubah jadi lot PO"
    )

    val STEAM = WorkStationSpec(
        code = WorkStationCode("STEAM"),
        displayName = "Steam & Setrika Uap",
        archetype = GarmentSlots.FINISHING,
        inputTrackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
        outputTrackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
        piecerateTariffIdr = 500L,
        standardMinutesPerPiece = 1.2,
        description = "Penyetrikaan uap vakum industri untuk merapikan serat"
    )

    val QC_FINAL = WorkStationSpec(
        code = WorkStationCode("QC_FINAL"),
        displayName = "Pemeriksaan Mutu (QC Final)",
        archetype = GarmentSlots.QUALITY_CONTROL,
        inputTrackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
        outputTrackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
        piecerateTariffIdr = 400L,
        standardMinutesPerPiece = 1.0,
        description = "Gerbang inspeksi cacat, verifikasi kuantitas, dan penerbitan tiket reparasi"
    )

    val PACKAGING = WorkStationSpec(
        code = WorkStationCode("PACKAGING"),
        displayName = "Packaging & Karton/Karung",
        archetype = GarmentSlots.FULFILLMENT,
        inputTrackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
        outputTrackingUnit = WorkTrackingUnit.LOT_ACCUMULATION,
        piecerateTariffIdr = 350L,
        standardMinutesPerPiece = 1.2,
        description = "Lipat, polybag OPP, silica gel, dan master karung/box ekspedisi"
    )

    // ---------------------------------------------------------------------------
    // Stasiun opsional (template) — TIDAK termasuk barisan bawaan (BUILTIN_STATIONS).
    // Tenant mengaktifkannya lewat katalog proses opsional dengan jangkar
    // `insertAfterCode`, sehingga bisa disisipkan di mana saja dalam alur
    // (misal: BORDIR antara QC_FINAL dan PACKAGING untuk pabrik rajut).
    // ---------------------------------------------------------------------------
    val BORDIR = WorkStationSpec(
        code = WorkStationCode("BORDIR"),
        displayName = "Bordir Komputer",
        archetype = GarmentSlots.CUSTOM_EXTENSION,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 1500L,
        standardMinutesPerPiece = 2.0,
        description = "Bordir komputer / logo pada badan atau dada garmen"
    )

    val SABLON = WorkStationSpec(
        code = WorkStationCode("SABLON"),
        displayName = "Sablon / Print",
        archetype = GarmentSlots.CUSTOM_EXTENSION,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 1200L,
        standardMinutesPerPiece = 1.5,
        description = "Sablon manual/otomatis pada panel atau garmen jadi"
    )

    val LAUNDRY = WorkStationSpec(
        code = WorkStationCode("LAUNDRY"),
        displayName = "Laundry / Garment Dyeing",
        archetype = GarmentSlots.FINISHING,
        inputTrackingUnit = WorkTrackingUnit.BUNDLE,
        outputTrackingUnit = WorkTrackingUnit.BUNDLE,
        piecerateTariffIdr = 2000L,
        standardMinutesPerPiece = 3.0,
        description = "Pencucian kimia, garment dyeing, atau enzyme wash vendor"
    )

    private val OPTIONAL_STATION_TEMPLATES: List<WorkStationSpec> = listOf(BORDIR, SABLON, LAUNDRY)

    /** Template stasiun opsional yang dapat tenant aktifkan dengan jangkar sisip. */
    fun optionalStations(): List<WorkStationSpec> = OPTIONAL_STATION_TEMPLATES

    private val BUILTIN_STATIONS: List<WorkStationSpec> = listOf(
        CUTTING,
        JAHIT_LURUS,
        OBRAS,
        SUNTEK,
        LUBANG_KANCING,
        PASANG_KANCING,
        PASANG_ZIPER,
        PASANG_LABEL,
        WASHING,
        STEAM,
        QC_FINAL,
        PACKAGING
    )

    fun standardStations(): List<WorkStationSpec> = BUILTIN_STATIONS

    fun line(customStations: List<WorkStationSpec> = emptyList()): List<WorkStationSpec> {
        val customMap = customStations.associateBy { it.code }
        val merged = BUILTIN_STATIONS.map { builtin ->
            customMap[builtin.code] ?: builtin
        }
        val additions = customStations.filter { custom ->
            BUILTIN_STATIONS.none { it.code == custom.code }
        }
        // Stasiun tambahan dengan jangkar disisipkan SETELAH jangkarnya (bisa berantai:
        // sisipan berikutnya melihat hasil sisipan sebelumnya). Tanpa jangkar = append,
        // menjaga kompatibilitas perilaku lama.
        var result = merged
        for (addition in additions) {
            val anchor = addition.insertAfterCode
            result = if (anchor == null) {
                result + addition
            } else {
                val anchorIndex = result.indexOfFirst { it.code == anchor }
                if (anchorIndex < 0) {
                    result + addition
                } else {
                    result.toMutableList().apply { add(anchorIndex + 1, addition) }
                }
            }
        }
        return result
    }

    fun resolve(code: WorkStationCode, customStations: List<WorkStationSpec> = emptyList()): WorkStationSpec? {
        return customStations.firstOrNull { it.code == code }
            ?: BUILTIN_STATIONS.firstOrNull { it.code == code }
    }

    /**
     * Menentukan stasiun berikutnya dalam jalur produksi.
     *
     * @param current Stasiun saat ini.
     * @param activeStations Himpunan stasiun yang aktif untuk SPK terkait (diturunkan dari Tech Pack).
     *                       Jika disediakan, stasiun yang tidak aktif otomatis dilewati (bypassed).
     * @param customStations Daftar stasiun kustom tenant (jika ada).
     */
    fun nextAfter(
        current: WorkStationCode,
        activeStations: Set<WorkStationCode>? = null,
        customStations: List<WorkStationSpec> = emptyList()
    ): WorkStationCode? {
        val fullLine = line(customStations)
        val currentIndex = fullLine.indexOfFirst { it.code == current }
        if (currentIndex < 0 || currentIndex >= fullLine.lastIndex) return null

        for (i in (currentIndex + 1)..fullLine.lastIndex) {
            val candidate = fullLine[i].code
            if (activeStations == null || candidate in activeStations) {
                return candidate
            }
        }
        return null
    }
}
