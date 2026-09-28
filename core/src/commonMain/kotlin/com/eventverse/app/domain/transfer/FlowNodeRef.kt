package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.toSamplingStageOrNull
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.workqueue.WorkStationCode

/**
 * Rujukan ke satu simpul alur, apa pun lapisannya.
 *
 * Alur produksi hidup di dua lapisan yang berbeda bentuk — tahap sampling
 * ([SamplingPipelineStage]) dan stasiun kerja lantai produksi ([WorkStationCode]) — ditambah
 * proses opsional yang tenant sisipkan sendiri. Ketiganya sama-sama "tempat pekerjaan terjadi",
 * dan sama-sama perlu diberi lokasi fisik.
 *
 * Kunci polimorfik ini ada supaya pemetaan lokasi punya **satu** peta, bukan satu peta per
 * lapisan. Dua peta yang harus dijaga konsisten tanpa ada yang menegakkan konsistensinya adalah
 * dua sumber kebenaran, dan cepat atau lambat keduanya akan berbeda.
 *
 * [key] adalah bentuk tersimpannya: stabil, bisa di-round-trip, dan aman jadi kolom teks.
 */
sealed interface FlowNodeRef {

    /** Bentuk tersimpan, mis. `STAGE:MACHINE_KNITTING`. Stabil lintas rilis. */
    val key: String

    /** Nama yang bisa dibaca manusia di layar pemetaan lokasi. */
    val displayName: String

    /**
     * Tahap pada kerangka alur tenant (papan kanban SPK), dirujuk lewat [StageCode] — bukan
     * enum — supaya tahap template industri lain dan tahap sisipan tenant bisa punya lokasi.
     * Key tersimpan tidak berubah: `STAGE:<code>`, dan kode rajut identik dengan nama enum lama.
     */
    data class Stage(val code: StageCode) : FlowNodeRef {
        /** Jembatan TRD-FLOW-001 Tahap 2 untuk pemanggil yang belum pindah dari enum. */
        constructor(stage: SamplingPipelineStage) : this(stage.toStageCode())

        override val key: String get() = "$KIND_STAGE$SEPARATOR${code.value}"

        // Sementara nama diambil dari enum; kode di luar enum tampil apa adanya. Di Tahap 3 nama
        // datang dari TenantStageFlow milik tenant, yang memang pemilik nama tahapnya.
        override val displayName: String get() = code.toSamplingStageOrNull()?.displayName ?: code.value
    }

    /** Proses opsional yang tenant sisipkan (Bordir, Sablon, Laundry, dan sebagainya). */
    data class Process(val code: String) : FlowNodeRef {
        init {
            require(code.isNotBlank()) { "Kode proses tidak boleh kosong" }
            require(!code.contains(SEPARATOR)) { "Kode proses tidak boleh memuat '$SEPARATOR': $code" }
        }

        override val key: String get() = "$KIND_PROCESS$SEPARATOR$code"
        override val displayName: String get() = code
    }

    /** Stasiun kerja bawaan pada lini produksi masal. */
    data class Station(val code: WorkStationCode) : FlowNodeRef {
        init {
            require(!code.value.contains(SEPARATOR)) {
                "Kode stasiun tidak boleh memuat '$SEPARATOR': ${code.value}"
            }
        }

        override val key: String get() = "$KIND_STATION$SEPARATOR${code.value}"
        override val displayName: String get() = code.value
    }

    companion object {
        const val SEPARATOR = ":"
        const val KIND_STAGE = "STAGE"
        const val KIND_PROCESS = "PROC"
        const val KIND_STATION = "STATION"

        /**
         * Membaca kembali [key] menjadi rujukan. Mengembalikan `null` untuk kunci yang tidak
         * dikenal — termasuk nama tahap yang sudah dihapus dari enum — supaya baris pemetaan
         * lama tidak menjatuhkan seluruh konfigurasi tenant.
         */
        fun parse(key: String): FlowNodeRef? {
            val kind = key.substringBefore(SEPARATOR, missingDelimiterValue = "")
            val value = key.substringAfter(SEPARATOR, missingDelimiterValue = "")
            if (kind.isBlank() || value.isBlank()) return null
            return when (kind) {
                // Masih hanya nama enum persis (bukan alias, bukan kode bebas): baris pemetaan dengan
                // nama tahap yang sudah dihapus tetap dibuang, sama seperti sebelumnya. Dilonggarkan
                // di Tahap 3, saat tahap non-rajut benar-benar ada.
                KIND_STAGE -> SamplingPipelineStage.entries.firstOrNull { it.name == value }
                    ?.let { Stage(it.toStageCode()) }
                KIND_PROCESS -> Process(value)
                KIND_STATION -> Station(WorkStationCode(value))
                else -> null
            }
        }
    }
}
