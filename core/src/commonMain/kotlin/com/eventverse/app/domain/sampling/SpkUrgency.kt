package com.eventverse.app.domain.sampling

import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline
import kotlin.math.ceil

/**
 * Prioritas "seberapa darurat satu SPK" — bukan dari sisa hari kalender, melainkan dari **slack**:
 * sisa hari dikurangi estimasi hari kerja yang masih dibutuhkan sampai deadline.
 *
 * ```
 * sisaMenit = totalMenitStandar × faktorSisa(stage)      // pekerjaan yang masih di depan
 * butuhHari = ⌈ sisaMenit × qtyPcs ÷ menitPerHari ⌉
 * slack     = hariMenujuDeadline − butuhHari
 * ```
 *
 * Pendekatan ini (Slack Time Remaining) menjawab contoh yang tidak bisa dijawab ambang hari
 * tetap: SPK yang deadline-nya lusa tapi qty-nya besar harus mulai lebih dulu daripada SPK yang
 * deadline-nya besok tapi isinya kecil — karena lead time-nya termakan beban, bukan tanggal.
 */
interface StageWorkProfile {

    /** Fraksi pekerjaan yang masih tersisa saat SPK berada di [stage] (1 = belum ada yang dikerjakan). */
    fun remainingFactor(stage: SamplingPipelineStage): Double
}

/**
 * Default awal sebelum ada data durasi nyata: perkiraan proporsi beban tahap rajut sampling.
 *
 * Angkanya titik mulai, bukan kebenaran — begitu `StageTransitionAudit` mengumpulkan cukup sejarah
 * (durasi kerja nyata per tahap), profil terukur tinggal menggantikan objek ini di wiring server
 * tanpa menyentuh rumus, kartu, maupun test. Test mengunci *sifatnya* (monoton turun), bukan nilainya.
 */
object DefaultStageWorkProfile : StageWorkProfile {
    override fun remainingFactor(stage: SamplingPipelineStage): Double = when (stage) {
        SamplingPipelineStage.NEW_INTAKE, SamplingPipelineStage.FLOW_REVIEW -> 1.00
        SamplingPipelineStage.CAM_PROGRAMMING -> 0.85
        SamplingPipelineStage.MACHINE_KNITTING -> 0.55
        SamplingPipelineStage.LINKING_ASSEMBLY -> 0.35
        SamplingPipelineStage.CUCI_SOFTENER -> 0.25
        SamplingPipelineStage.SETRIKA_UAP -> 0.15
        SamplingPipelineStage.QC_FINISHING -> 0.08
        SamplingPipelineStage.PENGEMASAN -> 0.04
        SamplingPipelineStage.IN_DELIVERY -> 0.02
        SamplingPipelineStage.ACC_APPROVED -> 0.00
    }
}

/**
 * Kapasitas kerja tersedia per hari. Default 480 menit = satu meja kerja × 8 jam; menit standar
 * yang belum punya tarif finishing memang hanya mengcover rajut, dan kartu tetap mencetak angkanya
 * sebagai estimasi yang bisa dikoreksi lantai.
 */
@JvmInline
value class FactoryDailyCapacity(val minutesPerDay: Int) {
    init {
        require(minutesPerDay > 0) { "Kapasitas harian harus positif, diterima $minutesPerDay" }
    }

    companion object {
        val DEFAULT = FactoryDailyCapacity(480)
    }
}

enum class SpkUrgencyLevel(val displayName: String) {
    /** Masih ada ≥ 1 hari kerja jeda sebelum kerja menabrak deadline. */
    AMAN("AMAN"),

    /** Kerja harus selesai tepat di hari deadline — tanpa jeda sama sekali. */
    SEGERA("SEGERA"),

    /** Kapasitas sampai deadline tidak cukup; tanpa intervensi SPK ini telat. */
    URGENT("URGENT"),

    /** SPK tidak punya deadline sama sekali — tidak layak diperingkat. */
    TANPA_DEADLINE("TANPA DEADLINE");
}

/** Proyeksi minimal satu SPK untuk perhitungan urgensi — cukup ringan untuk seluruh SPK aktif sekaligus. */
data class SpkUrgencyInput(
    val spkId: String,
    val stage: SamplingPipelineStage,
    val deadline: LocalDate?,
    val totalStdMinutes: Int,
    val qtyPcs: Int
)

data class SpkSlackAssessment(
    val spkId: String,
    /** Hari kerja jeda sebelum kerja menabrak deadline; negatif = sudah pasti telat. `null` = tanpa deadline. */
    val slackDays: Int?,
    val level: SpkUrgencyLevel,
    /** Urutan prioritas di antara seluruh SPK aktif — 1 = paling genting. */
    val rank: Int,
    val activeCount: Int
)

/**
 * Menilai seluruh SPK aktif sekaligus dan mengurutkannya — warna kartu bersifat *relatif* terhadap
 * antrean yang sedang berjalan, bukan terhadap kalender. SPK tanpa deadline tetap masuk daftar
 * tapi diletakkan paling akhir dan diberi level [SpkUrgencyLevel.TANPA_DEADLINE].
 */
fun assessUrgency(
    inputs: List<SpkUrgencyInput>,
    today: LocalDate,
    capacity: FactoryDailyCapacity = FactoryDailyCapacity.DEFAULT,
    profile: StageWorkProfile = DefaultStageWorkProfile
): List<SpkSlackAssessment> {
    val scored = inputs.map { input ->
        val slack = input.deadline?.let { deadline ->
            val daysLeft = deadline.toEpochDays() - today.toEpochDays()
            val remainingMinutes = input.totalStdMinutes * profile.remainingFactor(input.stage)
            val neededDays = if (remainingMinutes <= 0.0 || input.qtyPcs <= 0) 0
            else ceil(remainingMinutes * input.qtyPcs / capacity.minutesPerDay).toInt()
            daysLeft - neededDays
        }
        input to slack
    }

    val sorted = scored.sortedWith(
        compareBy(
            { (_, slack) -> slack == null },
            { (_, slack) -> slack ?: Int.MAX_VALUE },
            { (input, _) -> input.deadline?.toEpochDays() ?: Long.MAX_VALUE },
            { (input, _) -> input.spkId }
        )
    )

    return sorted.mapIndexed { index, (input, slack) ->
        SpkSlackAssessment(
            spkId = input.spkId,
            slackDays = slack,
            level = when {
                slack == null -> SpkUrgencyLevel.TANPA_DEADLINE
                slack < 0 -> SpkUrgencyLevel.URGENT
                slack == 0 -> SpkUrgencyLevel.SEGERA
                else -> SpkUrgencyLevel.AMAN
            },
            rank = index + 1,
            activeCount = sorted.size
        )
    }
}