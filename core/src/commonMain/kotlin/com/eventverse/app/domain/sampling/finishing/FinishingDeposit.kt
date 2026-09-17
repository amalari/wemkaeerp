package com.eventverse.app.domain.sampling.finishing

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId
import kotlin.jvm.JvmInline
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@JvmInline
value class FinishingDepositId(val value: String) {
    init {
        require(value.isNotBlank()) { "FinishingDepositId cannot be blank" }
        require(value.length <= 64) { "FinishingDepositId must be at most 64 characters" }
    }
}

/**
 * Satu setoran hasil kerja finishing internal (linking, pasang kancing, steam).
 *
 * Sengaja **bukan** bagian dari agregat `SamplingOrder`: dokumen ini dimiliki oleh operator
 * pembuatnya (`OPERATOR_EXEC` ber-[ScopeCapability.HIERARCHICAL]), sementara SPK sampling
 * dimiliki divisi sampling. Menggabungkan keduanya akan memaksa satu cakupan data untuk dua
 * kepemilikan yang berbeda.
 *
 * Tidak ada batching: operator menyetor berapa pun yang beres hari itu, dan sisa pekerjaan
 * dihitung dari akumulasi seluruh setoran — lihat [progressAgainst].
 */
data class FinishingDeposit(
    val id: FinishingDepositId,
    val tenantId: TenantId,
    val samplingOrderId: SamplingOrderId,
    val depositDate: LocalDate,
    val qtyPcs: Int,
    val weightKg: Double = 0.0,
    /** KEY object storage foto timbangan — bukan presigned URL yang kedaluwarsa. */
    val scalePhotoKey: String? = null,
    /** KEY object storage foto baju jadi. */
    val garmentPhotoKey: String? = null,
    val operatorName: String = "",
    val notes: String = "",
    val createdAt: Instant
) {
    init {
        require(qtyPcs > 0) { "Jumlah setoran minimal 1 pcs" }
        require(weightKg >= 0.0) { "Berat timbangan tidak boleh negatif" }
    }

    /** Bukti foto minimal ada satu — dipakai sebagai penanda mutu pencatatan, bukan gerbang wajib. */
    val hasPhotoEvidence: Boolean
        get() = !scalePhotoKey.isNullOrBlank() || !garmentPhotoKey.isNullOrBlank()
}

/**
 * Akumulasi setoran terhadap target rencana satu SPK.
 *
 * `remainingPcs == 0` adalah satu-satunya tanda tugas finishing tuntas dan boleh diteruskan
 * ke antrean QC — tidak ada tombol "selesai" manual yang bisa ditekan lebih cepat dari angkanya.
 */
data class FinishingProgress(
    val targetPcs: Int,
    val depositedPcs: Int,
    val totalWeightKg: Double,
    val depositCount: Int
) {
    val remainingPcs: Int get() = (targetPcs - depositedPcs).coerceAtLeast(0)

    val isComplete: Boolean get() = targetPcs > 0 && depositedPcs >= targetPcs

    /** Kelebihan setor — dicatat, bukan ditolak diam-diam, agar selisih fisik terlihat. */
    val overDepositedPcs: Int get() = (depositedPcs - targetPcs).coerceAtLeast(0)

    val progressRatio: Float
        get() = if (targetPcs <= 0) 0f else (depositedPcs.toFloat() / targetPcs.toFloat()).coerceIn(0f, 1f)

    companion object {
        fun empty(targetPcs: Int) = FinishingProgress(
            targetPcs = targetPcs,
            depositedPcs = 0,
            totalWeightKg = 0.0,
            depositCount = 0
        )
    }
}

/** Akumulasi murni — tanpa efek samping, aman dipanggil di UI maupun di use case. */
fun List<FinishingDeposit>.progressAgainst(targetPcs: Int): FinishingProgress = FinishingProgress(
    targetPcs = targetPcs,
    depositedPcs = sumOf { it.qtyPcs },
    totalWeightKg = sumOf { it.weightKg },
    depositCount = size
)
