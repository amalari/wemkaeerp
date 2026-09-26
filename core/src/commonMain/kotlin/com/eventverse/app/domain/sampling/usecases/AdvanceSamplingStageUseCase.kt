package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.transfer.FlowLegStatus
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TenantLocationConfigRepository
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsQuery
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsUseCase
import kotlinx.datetime.Instant

data class AdvanceSamplingStageCommand(
    val order: SamplingOrder,
    val target: SamplingPipelineStage,
    val stages: List<SamplingPipelineStage>,
    val processes: List<TenantOptionalProcess>,
    val actorEmail: String = "",
    val actorRole: String = "",
    /**
     * Melewati gerbang lokasi dengan alasan tercatat. Hanya untuk peran ber-`MANAGE`.
     *
     * Ada karena gerbang keras bisa mengunci lantai produksi ketika admin telat menerbitkan
     * dokumen, dan pabrik yang terhenti akan mencari jalan pintas di luar sistem. Lebih baik
     * jalan pintasnya ada di dalam, dan meninggalkan jejak.
     */
    val overrideReason: String? = null,
    val now: Instant
)

/**
 * Memindahkan SPK sampling ke tahap berikutnya, dengan gerbang perpindahan barang.
 *
 * ## Kenapa use case, bukan invarian entity
 *
 * [SamplingOrder.advancePipelineStage] sudah punya gerbangnya sendiri (lembar CAM wajib terisi),
 * dan gerbang itu murni: ia hanya membaca isi agregatnya sendiri. Status Surat Jalan milik
 * agregat lain. Menyuntikkannya sebagai parameter akan membuat [SamplingOrder] bergantung pada
 * agregat lain lewat pintu belakang — jadi pemeriksaan lintas-agregat tinggal di sini.
 *
 * ## Gerbang ini masih bocor, dan itu disengaja untuk sementara
 *
 * `pipelineStage` juga dimutasi tanpa melewati [SamplingOrder.advancePipelineStage] di empat
 * tempat: `assignMakloonVendor`, `recordVendorReturn`, `addFinishingDeposit`, dan
 * `completeQcInspection`. Yang pertama adalah persis kejadian makloon, jadi kebocorannya justru
 * ada di transisi yang paling mungkin lintas-lokasi. Menyalurkan keempatnya adalah pekerjaan
 * tersendiri; sampai itu selesai, gerbang ini hanya menutup `POST /{id}/stage`.
 */
class AdvanceSamplingStageUseCase(
    private val legsUseCase: GetFlowTransferLegsUseCase?
) {
    suspend operator fun invoke(command: AdvanceSamplingStageCommand): Result<SamplingOrder> =
        runCatching {
            requireLegReceived(command)
            command.order.advancePipelineStage(
                target = command.target,
                updatedAt = command.now,
                actorEmail = command.actorEmail,
                actorRole = auditRole(command)
            )
        }

    /**
     * Menolak perpindahan bila barang belum sampai di tahap tujuan.
     *
     * `DIKIRIM` **tetap ditolak**. Barang yang masih di jalan bukan barang yang bisa dikerjakan,
     * dan menerima status itu sebagai cukup akan membuat gerbangnya hanya seremonial.
     */
    private suspend fun requireLegReceived(command: AdvanceSamplingStageCommand) {
        if (command.overrideReason != null) return
        val useCase = legsUseCase ?: return

        val board = useCase(
            GetFlowTransferLegsQuery(
                tenantId = command.order.tenantId.value,
                subjectId = command.order.id.value,
                stages = command.stages,
                processes = command.processes,
                customerName = command.order.clientName
            )
        ).getOrNull() ?: return

        val blocking = board.legsInto(FlowNodeRef.Stage(command.target))
            .firstOrNull { it.status != FlowLegStatus.DITERIMA }
            ?: return

        val leg = blocking.leg
        val message = when (blocking.status) {
            FlowLegStatus.BELUM_TERBIT ->
                "Tahap ${command.target.displayName} ada di ${leg.destination.displayLabel}. " +
                    "Terbitkan Surat Jalan dari ${leg.origin.displayLabel} lebih dulu."
            FlowLegStatus.DIKIRIM ->
                "Barang menuju ${leg.destination.displayLabel} masih dalam perjalanan. " +
                    "Tandai Surat Jalan diterima sebelum pekerjaan dimulai di sana."
            FlowLegStatus.DITERIMA -> return
        }
        // IllegalArgumentException, bukan tipe khusus: rute `POST /{id}/stage` sudah
        // menerjemahkannya menjadi 422 berikut pesan domainnya, sama seperti gerbang CAM.
        throw IllegalArgumentException(message)
    }

    /** Override ikut tercatat di riwayat tahap, bukan hanya lewat begitu saja. */
    private fun auditRole(command: AdvanceSamplingStageCommand): String =
        command.overrideReason
            ?.let { "${command.actorRole} (lewat gerbang: $it)" }
            ?: command.actorRole
}
