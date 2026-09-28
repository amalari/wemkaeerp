package com.eventverse.app.presentation.operator

import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.isOperatorDesk

/**
 * Cara SPK keluar dari kolom "Sedang Dikerjakan" di tiap meja. Tiap bagian memakai alat yang
 * sudah ada — lembar kerja tahap, setoran, form QC — bukan tombol "selesai" generik, karena
 * yang tercatat saat selesai berbeda per bagian.
 */
sealed interface DeskFinishAction {
    val label: String

    /** Rajut: lembar hasil turun mesin (gramasi, waktu, size chart, tenselity). */
    data class Worksheet(val target: SamplingPipelineStage) : DeskFinishAction {
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
    data class Handoff(val target: SamplingPipelineStage) : DeskFinishAction {
        // Tanpa panah Unicode: Fredoka yang dibundel tidak punya glyph U+2192.
        override val label = "Selesai, Serahkan ke ${target.displayName}"
    }
}

/**
 * Aksi "selesai" meja ini. [route] = rute desain kartu yang diselesaikan: meja Cuci
 * menyerahkan ke QC, bukan Setrika, bila desain itu men-× tag Sampling pada Setrika.
 */
fun SamplingPipelineStage.finishAction(route: SamplingRoute = SamplingRoute.FULL): DeskFinishAction? = when {
    this == SamplingPipelineStage.MACHINE_KNITTING -> DeskFinishAction.Worksheet(SamplingPipelineStage.LINKING_ASSEMBLY)
    this == SamplingPipelineStage.LINKING_ASSEMBLY -> DeskFinishAction.Deposit
    this == SamplingPipelineStage.QC_FINISHING -> DeskFinishAction.QcInspection
    this == SamplingPipelineStage.PENGEMASAN -> DeskFinishAction.Store
    isOperatorDesk -> route.nextAfter(this)?.let(DeskFinishAction::Handoff)
    else -> null
}

/** Nama pendek meja untuk chip pemilih — nama tahap penuh terlalu panjang untuk enam chip. */
val SamplingPipelineStage.deskLabel: String
    get() = when (this) {
        SamplingPipelineStage.MACHINE_KNITTING -> "Rajut"
        SamplingPipelineStage.LINKING_ASSEMBLY -> "Linking"
        SamplingPipelineStage.CUCI_SOFTENER -> "Cuci"
        SamplingPipelineStage.SETRIKA_UAP -> "Setrika"
        SamplingPipelineStage.QC_FINISHING -> "QC"
        SamplingPipelineStage.PENGEMASAN -> "Kemas"
        else -> displayName
    }
