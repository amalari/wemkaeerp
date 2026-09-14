package com.eventverse.app.domain.techpack

import com.eventverse.app.domain.contracts.TechPackAndYieldData
import kotlinx.datetime.Instant

object TechPackMapper {

    fun toTechPackAndYieldData(techPack: TechPack, preparedAt: Instant): Result<TechPackAndYieldData> {
        if (techPack.status != TechPackStatus.RELEASED) {
            return Result.failure(
                IllegalStateException("Hanya Tech Pack berstatus RELEASED yang dapat dipancarkan ke modul hilir (status: ${techPack.status.displayName})")
            )
        }

        return Result.success(
            TechPackAndYieldData(
                techPackId = techPack.id.value,
                tenantId = techPack.tenantId,
                sourceSampleSpecId = techPack.sourceSampleSpecId,
                styleCode = techPack.styleCode.value,
                styleName = techPack.styleName,
                bomLines = techPack.bomLines,
                laborOperations = techPack.laborOperations,
                sizeYieldFactors = techPack.sizeYieldFactors,
                preparedAt = preparedAt
            )
        )
    }
}

fun TechPack.toTechPackAndYieldData(preparedAt: Instant): Result<TechPackAndYieldData> =
    TechPackMapper.toTechPackAndYieldData(this, preparedAt)
