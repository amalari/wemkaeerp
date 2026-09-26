package com.eventverse.app.shared.techpack

import com.eventverse.app.domain.customfield.CustomAttributesCodec
import com.eventverse.app.domain.techpack.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.contracts.TechPackAndYieldDataCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Clock

object TechPackCodec {

    fun encode(techPack: TechPack): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(techPack.id.value),
        "tenantId" to jsonOf(techPack.tenantId.value),
        "styleCode" to jsonOf(techPack.styleCode.value),
        "styleName" to jsonOf(techPack.styleName),
        "clientName" to jsonOf(techPack.clientName),
        "status" to jsonOf(techPack.status.name),
        "version" to jsonOf(techPack.version),
        "sourceSampleSpecId" to jsonOf(techPack.sourceSampleSpecId),
        "sourceSpkNumber" to jsonOf(techPack.sourceSpkNumber),
        "bomLines" to jsonArrayOf(techPack.bomLines.map(TechPackAndYieldDataCodec::encodeBomLine)),
        "laborOperations" to jsonArrayOf(techPack.laborOperations.map(TechPackAndYieldDataCodec::encodeLaborOperation)),
        "sizeYieldFactors" to jsonArrayOf(techPack.sizeYieldFactors.map(TechPackAndYieldDataCodec::encodeSizeYieldFactor)),
        "customAttributes" to techPack.customAttributes.toJsonValue(),
        "notes" to jsonOf(techPack.notes),
        "createdByUserId" to jsonOf(techPack.createdByUserId),
        "createdAt" to jsonOf(techPack.createdAt.toString()),
        "updatedAt" to jsonOf(techPack.updatedAt.toString()),
        "releasedAt" to jsonOf(techPack.releasedAt?.toString()),
        "archivedAt" to jsonOf(techPack.archivedAt?.toString())
    )

    fun decode(obj: JsonValue.Obj): TechPack {
        val id = TechPackId(obj.string("id") ?: "")
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val styleCode = StyleCode(obj.string("styleCode") ?: "STY-001")
        val styleName = obj.string("styleName") ?: ""
        val clientName = obj.string("clientName") ?: ""
        val status = TechPackStatus.fromCode(obj.string("status")) ?: TechPackStatus.DRAFT
        val version = obj.int("version") ?: 1
        val sourceSampleSpecId = obj.string("sourceSampleSpecId")
        val sourceSpkNumber = obj.string("sourceSpkNumber") ?: ""

        val bomLines = obj.objectArray("bomLines").map(TechPackAndYieldDataCodec::decodeBomLine)
        val laborOps = obj.objectArray("laborOperations").map(TechPackAndYieldDataCodec::decodeLaborOperation)
        val sizeFactors = obj.objectArray("sizeYieldFactors").map(TechPackAndYieldDataCodec::decodeSizeYieldFactor)

        val customAttributes = obj.obj("customAttributes")?.let {
            com.eventverse.app.domain.customfield.CustomAttributes.fromJsonValue(it)
        } ?: com.eventverse.app.domain.customfield.CustomAttributes.EMPTY

        val notes = obj.string("notes") ?: ""
        val createdByUserId = obj.string("createdByUserId")

        val createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), Clock.System.now())
        val updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), createdAt)
        val releasedAt = DateTimeCodec.parseInstantOrNull(obj.string("releasedAt"))
        val archivedAt = DateTimeCodec.parseInstantOrNull(obj.string("archivedAt"))

        return TechPack(
            id = id,
            tenantId = tenantId,
            styleCode = styleCode,
            styleName = styleName,
            clientName = clientName,
            status = status,
            version = version,
            sourceSampleSpecId = sourceSampleSpecId,
            sourceSpkNumber = sourceSpkNumber,
            bomLines = bomLines,
            laborOperations = laborOps,
            sizeYieldFactors = sizeFactors,
            customAttributes = customAttributes,
            notes = notes,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            releasedAt = releasedAt,
            archivedAt = archivedAt
        )
    }

    fun encodePage(page: TechPackPage): JsonValue.Obj = jsonObjectOf(
        "items" to jsonArrayOf(page.items.map(::encode)),
        "totalCount" to jsonOf(page.totalCount),
        "page" to jsonOf(page.page),
        "pageSize" to jsonOf(page.pageSize),
        "totalPages" to jsonOf(page.totalPages)
    )

    fun decodePage(obj: JsonValue.Obj): TechPackPage {
        val items = obj.objectArray("items").map(::decode)
        val totalCount = obj.long("totalCount") ?: items.size.toLong()
        val page = obj.int("page") ?: 1
        val pageSize = obj.int("pageSize") ?: 20

        return TechPackPage(
            items = items,
            totalCount = totalCount,
            page = page,
            pageSize = pageSize
        )
    }
}
