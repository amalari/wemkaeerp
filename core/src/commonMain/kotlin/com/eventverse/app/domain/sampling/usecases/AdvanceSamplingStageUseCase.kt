package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.ExitStages
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.freezePhaseTags
import com.eventverse.app.domain.sampling.freezeStageFlow
import com.eventverse.app.domain.sampling.samplingRoute
import com.eventverse.app.domain.sampling.stageDefinition
import com.eventverse.app.domain.sampling.stageFrame
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.TenantStageFlow
import com.eventverse.app.domain.transfer.FlowLegStatus
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TenantLocationConfigRepository
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsQuery
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsUseCase
import kotlinx.datetime.Instant

data class AdvanceSamplingStageCommand(
    val order: SamplingOrder,
    val target: StageCode,
    /** Kerangka tahap berurutan untuk penurunan leg — lihat `SamplingRoute.DEFAULT_FRAME`. */
    val stages: List<StageCode>,
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
    /**
     * Diisi hanya oleh use case kustodi penyimpanan (`StoreSampleUseCase`,
     * `ReleaseSampleFromStorageUseCase`) — bukti bahwa penanggung jawabnya sudah tercatat.
     */
    val custodyRecorded: Boolean = false,
    /**
     * Template tag fase pabrik, dibekukan ke order saat SPK masuk Program CAM. `null` = pemanggil
     * tidak memuat template (jalur kustodi di ujung alur, yang tagnya pasti sudah beku).
     */
    val tenantPhaseTags: StagePhaseTags? = null,
    /** Kerangka tahap pabrik, dibekukan ke order bersama tag fase. `null` = tidak dimuat pemanggil. */
    val tenantStageFlow: TenantStageFlow? = null,
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
            requireCustodyPath(command)
            val order = frozenIfEnteringFloor(command)
            requireLegReceived(command.copy(order = order))
            order.advancePipelineStage(
                target = command.target,
                updatedAt = command.now,
                actorEmail = command.actorEmail,
                actorRole = auditRole(command)
            )
        }

    private fun frozenIfEnteringFloor(command: AdvanceSamplingStageCommand): SamplingOrder {
        // Beku saat kartu pertama kali menyentuh lantai: target bukan lagi tahap masuk (rajut: Program CAM).
        val frame = command.tenantStageFlow?.stages ?: command.order.stageFrame
        if (frame.firstOrNull { it.code == command.target }?.kind == StageKind.ENTRY_ANCHOR) return command.order
        val withTags = command.tenantPhaseTags?.let { command.order.freezePhaseTags(it) } ?: command.order
        return command.tenantStageFlow?.let { withTags.freezeStageFlow(it) } ?: withTags
    }

    /**
     * Masuk dan keluar penyimpanan wajib lewat jalur kustodi, bukan pindah tahap biasa.
     *
     * Dua perpindahan itu adalah titik barang paling sering "keselip": selesai packing lalu
     * ditaruh entah di mana, atau dibawa keluar tanpa ada yang mengaku. Jalur kustodi memaksa
     * penerima simpan dan PIC kirim tercatat; pindah tahap biasa tidak tahu soal keduanya.
     */
    private fun requireCustodyPath(command: AdvanceSamplingStageCommand) {
        if (command.custodyRecorded) return
        when (command.target) {
            ExitStages.STORAGE -> throw IllegalArgumentException(
                "Masukkan ke penyimpanan lewat \"Simpan\" - lokasi dan penerima simpan wajib dicatat."
            )
            ExitStages.DELIVERY -> throw IllegalArgumentException(
                "Pengiriman ke buyer hanya dari penyimpanan, lewat \"Rilis Kirim\" dengan PIC tercatat."
            )
            else -> Unit
        }
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
                skippedStages = command.order.samplingRoute.skipped,
                customerName = command.order.clientName
            )
        ).getOrNull() ?: return

        val blocking = board.legsInto(FlowNodeRef.Stage(command.target))
            .firstOrNull { it.status != FlowLegStatus.DITERIMA }
            ?: return

        val leg = blocking.leg
        val message = when (blocking.status) {
            FlowLegStatus.BELUM_TERBIT ->
                "Tahap ${command.order.stageDefinition(command.target).displayName} ada di ${leg.destination.displayLabel}. " +
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
