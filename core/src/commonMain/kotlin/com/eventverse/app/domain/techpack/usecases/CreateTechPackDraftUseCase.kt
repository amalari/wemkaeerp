package com.eventverse.app.domain.techpack.usecases

import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.contracts.ApprovedSampleSpecification
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.techpack.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class CreateBlankTechPackCommand(
    val tenantId: TenantId,
    val styleName: String,
    val styleCode: StyleCode? = null,
    val clientName: String = "",
    val createdByUserId: String? = null,
    val now: Instant = Clock.System.now()
)

data class CreateTechPackFromSampleCommand(
    val tenantId: TenantId,
    val spec: ApprovedSampleSpecification,
    val styleCode: StyleCode? = null,
    val defaultOwnership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
    val defaultWasteAllowance: Ratio = Ratio.percent(5.0),
    val createdByUserId: String? = null,
    val now: Instant = Clock.System.now()
)

class CreateTechPackDraftUseCase(
    private val techPackRepository: TechPackRepository
) {
    suspend fun createBlank(command: CreateBlankTechPackCommand): Result<TechPack> = runCatching {
        require(command.styleName.isNotBlank()) { "Nama style tidak boleh kosong" }
        val code = command.styleCode ?: techPackRepository.reserveNextStyleCode(command.tenantId)
        val id = TechPackId("tp-${command.tenantId.value}-${code.value}-v1")

        val techPack = TechPack(
            id = id,
            tenantId = command.tenantId,
            styleCode = code,
            styleName = command.styleName.trim(),
            clientName = command.clientName.trim(),
            status = TechPackStatus.DRAFT,
            version = 1,
            createdByUserId = command.createdByUserId,
            createdAt = command.now,
            updatedAt = command.now
        )
        techPackRepository.save(techPack)
    }

    suspend fun createFromSample(command: CreateTechPackFromSampleCommand): Result<TechPack> = runCatching {
        val existing = techPackRepository.findBySourceSample(command.tenantId, command.spec.sourceOrderId)
        if (existing != null && existing.status == TechPackStatus.DRAFT) {
            return@runCatching existing
        }

        val code = command.styleCode
            ?: runCatching { StyleCode(command.spec.spkNumber) }.getOrNull()
            ?: techPackRepository.reserveNextStyleCode(command.tenantId)

        val id = TechPackId("tp-${command.tenantId.value}-${code.value}-v1")

        val draft = TechPackDraftFactory.createFromSample(
            spec = command.spec,
            techPackId = id,
            styleCode = code,
            defaultOwnership = command.defaultOwnership,
            defaultWasteAllowance = command.defaultWasteAllowance,
            now = command.now
        ).copy(
            createdByUserId = command.createdByUserId
        )

        techPackRepository.save(draft)
    }
}
