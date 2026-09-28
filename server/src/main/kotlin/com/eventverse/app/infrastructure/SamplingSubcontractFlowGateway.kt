package com.eventverse.app.infrastructure

import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.process.TenantStagePhaseTagsRepository
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.sampling.customizeProcessFlow
import com.eventverse.app.domain.sampling.effectivePhaseTags
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowLegStatus
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsQuery
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsUseCase
import com.eventverse.app.domain.vendor.SubcontractFlowGateway
import com.eventverse.app.domain.vendor.SubcontractNeed
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import kotlinx.datetime.Clock

/**
 * [SubcontractFlowGateway] untuk SPK sampling.
 *
 * Alur efektif sebuah SPK adalah salinan kustomnya bila ada, kalau tidak template pabrik —
 * aturan yang sama dengan `SamplingFlowRoutes`. Menulis vendor ke SPK yang masih memakai
 * template otomatis menjadikannya alur kustom: vendor adalah keputusan per order, dan menulisnya
 * ke template akan menunjuk vendor yang sama untuk seluruh SPK pabrik.
 */
class SamplingSubcontractFlowGateway(
    private val orderRepository: SamplingOrderRepository,
    private val processCatalogRepository: TenantProcessCatalogRepository,
    private val flowLegsUseCase: GetFlowTransferLegsUseCase,
    private val phaseTagsRepository: TenantStagePhaseTagsRepository? = null
) : SubcontractFlowGateway {

    override suspend fun openNeeds(tenantId: TenantId): List<SubcontractNeed> {
        val template = templateOf(tenantId)
        return orderRepository.findAll(tenantId)
            .filter { it.status in OPEN_STATUSES }
            .flatMap { order -> subcontracted(order, template).map { process -> needOf(order, process) } }
    }

    override suspend fun findNeed(tenantId: TenantId, subjectId: String, processCode: String): SubcontractNeed? {
        val order = ownedOrder(tenantId, subjectId) ?: return null
        return subcontracted(order, templateOf(tenantId))
            .firstOrNull { it.code.equals(processCode, ignoreCase = true) }
            ?.let { needOf(order, it) }
    }

    override suspend fun isDispatched(tenantId: TenantId, subjectId: String, processCode: String): Boolean {
        val order = ownedOrder(tenantId, subjectId) ?: return false
        val process = effective(order, templateOf(tenantId)).firstOrNull { it.code.equals(processCode, ignoreCase = true) }
            ?: return false

        val board = flowLegsUseCase(
            GetFlowTransferLegsQuery(
                tenantId = tenantId.value,
                subjectId = order.id.value,
                stages = SamplingRoute.DEFAULT_FRAME,
                processes = effective(order, templateOf(tenantId)),
                skippedStages = order.effectivePhaseTags(
                    phaseTagsRepository?.findByTenantId(tenantId) ?: StagePhaseTags.DEFAULT
                ).skippedSamplingStages,
                customerName = order.clientName
            )
        ).getOrNull() ?: return false

        return board.legsInto(FlowNodeRef.Process(process.code)).any { it.status != FlowLegStatus.BELUM_TERBIT }
    }

    override suspend fun linkVendor(tenantId: TenantId, subjectId: String, processCode: String, vendorRef: String?) {
        val order = ownedOrder(tenantId, subjectId) ?: error("Order '$subjectId' tidak ditemukan")
        val updated = effective(order, templateOf(tenantId)).map { process ->
            if (process.code.equals(processCode, ignoreCase = true)) process.copy(vendorRef = vendorRef) else process
        }
        orderRepository.save(order.customizeProcessFlow(updated, Clock.System.now()))
    }

    private suspend fun templateOf(tenantId: TenantId): List<TenantOptionalProcess> =
        processCatalogRepository.findByTenantId(tenantId)?.processes.orEmpty()

    /** `findById` tidak menyaring tenant; penyaringan di sini mencegah menulis ke SPK tenant lain. */
    private suspend fun ownedOrder(tenantId: TenantId, subjectId: String): SamplingOrder? =
        runCatching { SamplingOrderId(subjectId) }.getOrNull()
            ?.let { orderRepository.findById(it) }
            ?.takeIf { it.tenantId == tenantId }

    private fun effective(order: SamplingOrder, template: List<TenantOptionalProcess>): List<TenantOptionalProcess> =
        order.customFlowProcesses ?: template

    private fun subcontracted(order: SamplingOrder, template: List<TenantOptionalProcess>): List<TenantOptionalProcess> =
        effective(order, template).filter { it.executionMode == WorkExecutionMode.SUBCONTRACTED && it.hasSamplingPlacement }

    private fun needOf(order: SamplingOrder, process: TenantOptionalProcess) = SubcontractNeed(
        subjectId = order.id.value,
        subjectLabel = order.spkNumber.value,
        clientName = order.clientName,
        styleName = order.styleName,
        processCode = process.code,
        processName = process.displayName,
        quantityPcs = order.sampleQuantity.coerceAtLeast(1),
        dueDate = order.deadlineFinishing ?: order.deadlineDelivery
    )

    private companion object {
        /** SPK yang sudah ACC atau batal tidak lagi butuh vendor. */
        val OPEN_STATUSES = setOf(SamplingStatus.DRAFT, SamplingStatus.IN_PROGRESS, SamplingStatus.REVISION)
    }
}
