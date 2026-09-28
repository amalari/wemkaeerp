package com.eventverse.app.presentation.sampling.components

import com.eventverse.app.domain.sampling.StageInputRow
import com.eventverse.app.domain.sampling.StageInputSection
import com.eventverse.app.domain.sampling.StageSectionNames

/** Data representasi satu tab bagian garmen pada tahap Program CAM. */
data class CamPartTab(
    val id: String,
    val name: String,
    val program: String = "",
    val feederInstructions: List<String> = emptyList(),
    val tenselities: List<String> = emptyList(),
    val gramasi: String = "",
    val waktu: String = "",
    val material: String = ""
) {
    /** Tab dianggap full/lengkap jika kode program dan minimal 1 instruksi panah sudah terisi. */
    val isComplete: Boolean get() = program.isNotBlank() && feederInstructions.isNotEmpty()
}

/** Representasi satu baris bahan baku tambahan non-perbagian (mis. zipper, kancing, aksesoris). */
data class AdditionalMaterialItem(
    val id: String = "",
    val materialName: String = "",
    val quantity: String = "",
    val notes: String = ""
) {
    val isFilled: Boolean get() = materialName.isNotBlank() || quantity.isNotBlank()
}

/** Isi lembar Program CAM & R&D: tab per bagian, catatan rumus pola, hasil ukuran jadi, dan bahan baku tambahan. */
data class CamProgramSheet(
    val tabs: List<CamPartTab>,
    val formulaNote: String,
    val finishedMeasurements: List<StageInputRow> = emptyList(),
    val additionalMaterials: List<AdditionalMaterialItem> = emptyList()
)

val SUGGESTED_CAM_PARTS = listOf(
    "Badan Depan", "Badan Belakang", "Depan", "Belakang", "Lengan", "Kerah",
    "Rib Bawah", "Manset", "Saku", "Tudung / Hoodie", "Placket"
)

private fun StageInputSection?.rowsForPart(name: String): List<StageInputRow> =
    this?.rows?.filter {
        it.label.startsWith("$name •", ignoreCase = true) || it.label.equals(name, ignoreCase = true)
    }.orEmpty()

private fun List<StageInputRow>.asTags(): List<String> =
    flatMap { row -> row.value.split("\n").map { it.trim() }.filter { it.isNotBlank() } }

private fun partName(label: String): String =
    if (label.contains(" • ")) label.substringBefore(" • ").trim() else label.trim()

/** Parsing List<StageInputSection> menjadi [CamProgramSheet]. */
fun parseCamSections(sections: List<StageInputSection>): CamProgramSheet {
    fun sec(name: String) = sections.firstOrNull { it.section == name }
    val progSec = sec(StageSectionNames.PROGRAM)
    val feederSec = sec(StageSectionNames.FEEDER_INSTRUCTIONS)
    val tenselitySec = sec(StageSectionNames.TENSELITY)
    val materialSec = sec(StageSectionNames.PANEL_MATERIALS)
    val weightSec = sec(StageSectionNames.PANEL_WEIGHTS)
    val minuteSec = sec(StageSectionNames.PANEL_MINUTES)
    val formulaSec = sec(StageSectionNames.PATTERN_FORMULAS)
    val measurementSec = sec(StageSectionNames.FINISHED_MEASUREMENTS)
    val additionalSec = sec(StageSectionNames.ADDITIONAL_MATERIALS)

    val tabNames = linkedSetOf<String>()
    progSec?.rows?.forEach { if (it.label.isNotBlank()) tabNames.add(it.label.trim()) }
    feederSec?.rows?.forEach { partName(it.label).takeIf { n -> n.isNotBlank() }?.let(tabNames::add) }
    tenselitySec?.rows?.forEach { partName(it.label).takeIf { n -> n.isNotBlank() }?.let(tabNames::add) }
    materialSec?.rows?.forEach { partName(it.label).takeIf { n -> n.isNotBlank() }?.let(tabNames::add) }
    weightSec?.rows?.forEach { partName(it.label).takeIf { n -> n.isNotBlank() }?.let(tabNames::add) }
    minuteSec?.rows?.forEach { partName(it.label).takeIf { n -> n.isNotBlank() }?.let(tabNames::add) }

    val tabs = tabNames.mapIndexed { idx, name ->
        CamPartTab(
            id = "tab-$idx-$name",
            name = name,
            program = progSec?.rows?.firstOrNull { it.label.equals(name, ignoreCase = true) }?.value.orEmpty(),
            feederInstructions = feederSec.rowsForPart(name).asTags(),
            tenselities = tenselitySec.rowsForPart(name).asTags(),
            gramasi = weightSec.rowsForPart(name).firstOrNull()?.value.orEmpty(),
            waktu = minuteSec.rowsForPart(name).firstOrNull()?.value.orEmpty(),
            material = materialSec.rowsForPart(name).firstOrNull()?.value.orEmpty()
        )
    }

    val formulaNote = formulaSec?.rows?.joinToString("\n") { row ->
        if (row.label.isNotBlank() && row.label != "Catatan Pola") "${row.label}: ${row.value}" else row.value
    }.orEmpty()

    val additionalMaterials = additionalSec?.rows?.mapIndexed { idx, row ->
        val rawVal = row.value
        val (qty, notes) = when {
            rawVal.contains(" | ") -> rawVal.substringBefore(" | ").trim() to rawVal.substringAfter(" | ").trim()
            rawVal.contains(" • ") -> rawVal.substringBefore(" • ").trim() to rawVal.substringAfter(" • ").trim()
            else -> rawVal.trim() to ""
        }
        AdditionalMaterialItem(
            id = "add-mat-$idx",
            materialName = row.label,
            quantity = qty,
            notes = notes
        )
    }.orEmpty()

    return CamProgramSheet(
        tabs = tabs,
        formulaNote = formulaNote,
        finishedMeasurements = measurementSec?.rows.orEmpty(),
        additionalMaterials = additionalMaterials
    )
}

/**
 * Serialisasi [CamProgramSheet] kembali menjadi List<StageInputSection>.
 * Baris ukuran jadi dan bahan tambahan yang masih kosong ikut disimpan agar baris yang baru ditambah tidak hilang
 * saat state di-parse ulang; gerbang domain hanya menghitung baris yang [StageInputRow.isFilled].
 */
fun serializeCamSections(
    tabs: List<CamPartTab>,
    rumusPolaNote: String,
    finishedMeasurements: List<StageInputRow> = emptyList(),
    additionalMaterials: List<AdditionalMaterialItem> = emptyList()
): List<StageInputSection> {
    fun tagRows(pick: (CamPartTab) -> List<String>) = tabs.flatMap { tab ->
        pick(tab).filter { it.isNotBlank() }.map { StageInputRow(label = tab.name, value = it) }
    }
    fun singleRows(pick: (CamPartTab) -> String) = tabs.filter { pick(it).isNotBlank() }
        .map { StageInputRow(label = it.name, value = pick(it)) }

    val formulaRows = if (rumusPolaNote.isNotBlank()) {
        listOf(StageInputRow(label = "Catatan Pola", value = rumusPolaNote))
    } else emptyList()

    val additionalRows = additionalMaterials.map { item ->
        val valStr = if (item.notes.isNotBlank()) "${item.quantity} | ${item.notes}" else item.quantity
        StageInputRow(label = item.materialName, value = valStr)
    }

    return listOf(
        StageInputSection(StageSectionNames.PROGRAM, tabs.map { StageInputRow(it.name, it.program) }),
        StageInputSection(StageSectionNames.FEEDER_INSTRUCTIONS, tagRows { it.feederInstructions }),
        StageInputSection(StageSectionNames.TENSELITY, tagRows { it.tenselities }),
        StageInputSection(StageSectionNames.PANEL_MATERIALS, singleRows { it.material }),
        StageInputSection(StageSectionNames.PANEL_WEIGHTS, singleRows { it.gramasi }),
        StageInputSection(StageSectionNames.PANEL_MINUTES, singleRows { it.waktu }),
        StageInputSection(StageSectionNames.PATTERN_FORMULAS, formulaRows),
        StageInputSection(StageSectionNames.FINISHED_MEASUREMENTS, finishedMeasurements),
        StageInputSection(StageSectionNames.ADDITIONAL_MATERIALS, additionalRows)
    )
}
