package com.eventverse.app.presentation.operator

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.firstWorkWith
import com.eventverse.app.domain.sampling.stageFrame
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageTrait

/**
 * Cara SPK keluar dari kolom "Sedang Dikerjakan" di tiap meja. Tiap bagian memakai alat yang
 * sudah ada — lembar kerja tahap, setoran, form QC — bukan tombol "selesai" generik, karena
 * yang tercatat saat selesai berbeda per bagian.
 */
sealed interface DeskFinishAction {
    val label: String

    /** Rajut: lembar hasil turun mesin (gramasi, waktu, size chart, tenselity). */
    data class Worksheet(val target: StageCode) : DeskFinishAction {
        override val label = "Turun Mesin, Isi Lembar"
    }

    /** Linking: setoran pcs; SPK pindah sendiri ke Cuci begitu setoran genap. */
    data object Deposit : DeskFinishAction {
        override val label = "Input Setoran"
    }

    /** QC: lembar inspeksi; lolos memindahkan SPK ke Pengemasan. */
    data object QcInspection : DeskFinishAction {
        override val label = "Isi Lembar QC"
    }

    /** Kemas: barang ditaruh di penyimpanan — lokasi dan penerima simpan wajib dicatat. */
    data object Store : DeskFinishAction {
        override val label = "Selesai Kemas, Simpan Barang"
    }

    /** Cuci, Setrika: cukup serahkan ke tahap berikutnya. */
    data class Handoff(val target: StageDefinition) : DeskFinishAction {
        // Tanpa panah Unicode: Fredoka yang dibundel tidak punya glyph U+2192.
        override val label = "Selesai, Serahkan ke ${target.displayName}"
    }
}

/**
 * Aksi "selesai" meja ini, dari **peran** tahap pada [frame] (kerangka pabrik), bukan nama rajut:
 * perakitan (`SEWING`) → setoran, QC akhir → lembar QC, pengemasan (`FULFILLMENT`) → simpan.
 * [route] = rute desain kartu yang diselesaikan: meja Cuci menyerahkan ke QC, bukan Setrika,
 * bila desain itu men-× tag Sampling pada Setrika.
 *
 * Lembar "turun mesin" masih khas rajut (kode Rajut); lembar per tahap = data di Tahap 3.
 */
fun StageDefinition.finishAction(
    frame: List<StageDefinition> = SamplingRoute.DEFAULT_STAGES,
    route: SamplingRoute = SamplingRoute.FULL
): DeskFinishAction? {
    fun next() = route.nextAfter(code)?.let { c -> frame.firstOrNull { it.code == c } }
    return when {
        code == KNITTING -> route.nextAfter(code)?.let(DeskFinishAction::Worksheet)
        code == frame.firstWorkWith(ModuleArchetype.SEWING)?.code -> DeskFinishAction.Deposit
        code == frame.firstWorkWith(ModuleArchetype.QUALITY_CONTROL)?.code -> DeskFinishAction.QcInspection
        code == frame.firstWorkWith(ModuleArchetype.FULFILLMENT)?.code -> DeskFinishAction.Store
        has(StageTrait.OPERATOR_DESK) -> next()?.let(DeskFinishAction::Handoff)
        else -> null
    }
}

/** Label meja = label ringkas tahap (istilah lantai; keputusan 2026-09-29). */
val StageDefinition.deskLabel: String get() = shortLabel

private val KNITTING = SamplingPipelineStage.MACHINE_KNITTING.toStageCode()

/** Label meja untuk kode di riwayat SPK — dibaca dari kerangka SPK itu sendiri, kode apa adanya bila tak dikenal. */
fun SamplingOrder.deskLabelOf(code: StageCode): String = stageFrame.firstOrNull { it.code == code }?.deskLabel ?: code.value

/** Ada meja operator sebelum [this] pada [frame] — syarat tombol kirim rework. */
fun StageDefinition.hasEarlierDesk(frame: List<StageDefinition>): Boolean =
    frame.takeWhile { it.code != code }.any { it.has(StageTrait.OPERATOR_DESK) }
