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
        SamplingPipelineStage.STORAGE_HOLDING -> 0.03
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

    /**
     * Punya deadline, tapi menit standar atau qty-nya belum diisi — beban kerjanya tidak diketahui.
     *
     * Dipisah dari [AMAN] dengan sengaja: menit 0 berarti "belum diukur", bukan "tidak ada kerja".
     * Menganggapnya nol membuat SPK yang datanya paling bolong justru tercetak paling tenang (hijau),
     * dan kartu hijau di meja adalah izin bagi operator untuk tidak mendahulukannya.
     */
    BELUM_DIESTIMASI("BELUM DIESTIMASI"),

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
    /** Hari kerja jeda sebelum kerja menabrak deadline; negatif = sudah pasti telat. `null` = tanpa deadline atau belum diestimasi. */
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
        val factor = profile.remainingFactor(input.stage)
        // Tahap tanpa sisa kerja (faktor 0) sah dinilai walau menitnya kosong: nol di sana memang nol.
        val estimable = factor <= 0.0 || (input.totalStdMinutes > 0 && input.qtyPcs > 0)
        val slack = input.deadline?.takeIf { estimable }?.let { deadline ->
            val daysLeft = deadline.toEpochDays() - today.toEpochDays()
            val remainingMinutes = input.totalStdMinutes * factor
            val neededDays = if (remainingMinutes <= 0.0) 0
            else ceil(remainingMinutes * input.qtyPcs / capacity.minutesPerDay).toInt()
            daysLeft - neededDays
        }
        val level = when {
            input.deadline == null -> SpkUrgencyLevel.TANPA_DEADLINE
            slack == null -> SpkUrgencyLevel.BELUM_DIESTIMASI
            slack < 0 -> SpkUrgencyLevel.URGENT
            slack == 0 -> SpkUrgencyLevel.SEGERA
            else -> SpkUrgencyLevel.AMAN
        }
        Triple(input, slack, level)
    }

    // Yang terhitung diurutkan menurut slack; yang belum diestimasi menyusul menurut deadline —
    // tetap terlihat, tapi tidak menyerobot SPK yang bebannya jelas; tanpa deadline paling akhir.
    val sorted = scored.sortedWith(
        compareBy(
            { (_, _, level) -> level.ordinal >= SpkUrgencyLevel.BELUM_DIESTIMASI.ordinal },
            { (input, _, _) -> input.deadline == null },
            { (_, slack, _) -> slack ?: Int.MAX_VALUE },
            { (input, _, _) -> input.deadline?.toEpochDays() ?: Long.MAX_VALUE },
            { (input, _, _) -> input.spkId }
        )
    )

    return sorted.mapIndexed { index, (input, slack, level) ->
        SpkSlackAssessment(
            spkId = input.spkId,
            slackDays = slack,
            level = level,
            rank = index + 1,
            activeCount = sorted.size
        )
    }
}

/**
 * Kebijakan urgensi SPK **sampel**: selalu [SpkUrgencyLevel.URGENT].
 *
 * Keputusan bisnis, bukan hasil hitung: sampel adalah gerbang menuju deal berikutnya, jadi lantai
 * harus selalu mendahulukannya. Rumus slack [assessUrgency] juga memang tidak bisa dipakai di sini —
 * menit standar baru diketahui *setelah* sampel dirajut. Rumus itu milik SPK massal, yang mewarisi
 * menit dari sampel yang ACC.
 *
 * Karena warnanya seragam, pembeda antar sampel adalah [SpkSlackAssessment.rank]: deadline terdekat
 * lebih dulu, tanpa deadline paling akhir.
 */
fun assessSamplingUrgency(inputs: List<SpkUrgencyInput>, today: LocalDate): List<SpkSlackAssessment> {
    val sorted = inputs.sortedWith(
        compareBy({ it.deadline == null }, { it.deadline?.toEpochDays() ?: Long.MAX_VALUE }, { it.spkId })
    )
    return sorted.mapIndexed { index, input ->
        SpkSlackAssessment(
            spkId = input.spkId,
            slackDays = input.deadline?.let { (it.toEpochDays() - today.toEpochDays()).toInt() },
            level = SpkUrgencyLevel.URGENT,
            rank = index + 1,
            activeCount = sorted.size
        )
    }
}
