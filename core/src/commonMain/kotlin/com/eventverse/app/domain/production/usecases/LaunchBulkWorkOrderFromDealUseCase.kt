package com.eventverse.app.domain.production.usecases

import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.PurchaseOrder
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.production.BulkProductionStatus
import com.eventverse.app.domain.production.BulkSizeLine
import com.eventverse.app.domain.production.BulkWorkOrder
import com.eventverse.app.domain.production.BulkWorkOrderId
import com.eventverse.app.domain.production.BulkWorkOrderRepository
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.tenant.TenantId
import kotlin.math.roundToInt
import kotlinx.datetime.Clock

data class LaunchBulkWorkOrderCommand(
    val tenantId: TenantId,
    val dealId: DealId,
    /**
     * Semantik kepemilikan bahan untuk SPK ini (Kontrak 3). Tenant makloon mengirim
     * [StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL] — kain titipan, nilai Rp 0 di neraca.
     */
    val stockOwnership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
    val notes: String = ""
)

/**
 * Menerbitkan SPK Produksi Massal dari sebuah deal yang sampelnya sudah di-ACC buyer.
 *
 * Inilah jembatan yang selama ini kosong di balik tombol `[ Luncurkan SPK Massal ]`: sebelumnya
 * tombol itu hanya menggeser stage deal ke `IN_PRODUCTION`, sehingga lantai produksi tidak
 * pernah menerima dokumen kerja apa pun.
 *
 * Idempotent per deal: satu deal hanya boleh punya satu SPK massal aktif. Klik kedua
 * mengembalikan SPK yang sudah ada, bukan menerbitkan kembar yang membuat kain dipotong dua kali.
 */
class LaunchBulkWorkOrderFromDealUseCase(
    private val workOrderRepository: BulkWorkOrderRepository,
    private val dealRepository: DealRepository,
    private val samplingOrderRepository: SamplingOrderRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(command: LaunchBulkWorkOrderCommand): Result<BulkWorkOrder> = runCatching {
        val existing = workOrderRepository.findByDealId(command.tenantId, command.dealId.value)
            .firstOrNull { it.status != BulkProductionStatus.CANCELLED && !it.isArchived }
        if (existing != null) return@runCatching existing

        val deal = dealRepository.findById(command.tenantId, command.dealId)
            ?: error("Deal tidak ditemukan: ${command.dealId.value}")

        // Golden Sample: sampel ACC milik deal ini. Tanpa itu, spesifikasi produksi tidak terkunci.
        val samplingOrders = samplingOrderRepository.findByDealId(command.tenantId, command.dealId.value)
        val goldenSample = samplingOrders.firstOrNull { it.status == SamplingStatus.ACC_APPROVED }
            ?: error(
                "Deal \"${deal.title.value}\" belum punya sampel ber-ACC buyer. " +
                    "Selesaikan ACC sampel dulu sebelum menerbitkan SPK massal."
            )

        val purchaseOrders = dealRepository.findPurchaseOrders(command.tenantId, command.dealId)
        val sizeBreakdown = deriveSizeBreakdown(purchaseOrders)
        require(sizeBreakdown.isNotEmpty()) {
            "Deal \"${deal.title.value}\" belum punya rincian PO. Tempel PO buyer dulu agar " +
                "jumlah per ukuran bisa diturunkan ke SPK massal."
        }

        val now = clock.now()
        val draft = BulkWorkOrder(
            id = BulkWorkOrderId("bwo_${command.dealId.value}_${now.toEpochMilliseconds()}"),
            tenantId = command.tenantId,
            spkNumber = workOrderRepository.nextSpkNumber(command.tenantId),
            clientName = deal.title.value,
            styleName = goldenSample.styleName,
            dealId = command.dealId.value,
            goldenSampleOrderId = goldenSample.id,
            sizeBreakdown = sizeBreakdown,
            stockOwnership = command.stockOwnership,
            stageProgress = BulkWorkOrder.emptyProgress(),
            notes = command.notes,
            createdAt = now,
            updatedAt = now
        )

        workOrderRepository.save(draft.release(now))
    }
}

/**
 * Menurunkan size breakdown dari baris-baris PO buyer.
 *
 * Deskripsi baris PO dipakai apa adanya sebagai label ukuran — **tidak** ditebak polanya.
 * Parser yang mencoba menerka "Kemeja PDH ukuran L" jadi "L" akan salah diam-diam pada format
 * PO yang tak terduga, dan salahnya baru ketahuan setelah kain dipotong. Admin tetap bisa
 * merapikan labelnya lewat `BulkWorkOrder.updateSizeBreakdown` selama SPK masih draft.
 *
 * Baris dengan kuantitas membulat ke 0 pcs dibuang, bukan dipaksa jadi 1 — nol di PO berarti nol.
 */
fun deriveSizeBreakdown(purchaseOrders: List<PurchaseOrder>): List<BulkSizeLine> =
    purchaseOrders
        .flatMap { it.lines }
        .mapNotNull { line ->
            val pcs = line.quantity.roundToInt()
            if (pcs <= 0) null else line.description.trim().uppercase() to pcs
        }
        .groupBy({ it.first }, { it.second })
        .map { (label, quantities) -> BulkSizeLine(sizeLabel = label, orderedPcs = quantities.sum()) }
