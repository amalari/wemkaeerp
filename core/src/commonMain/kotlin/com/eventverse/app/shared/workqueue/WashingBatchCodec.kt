package com.eventverse.app.shared.workqueue

import com.eventverse.app.domain.workqueue.WashingBatch
import com.eventverse.app.domain.workqueue.WashingBatchId
import com.eventverse.app.domain.workqueue.WashingBatchItem
import com.eventverse.app.domain.workqueue.WashingBatchStatus
import com.eventverse.app.domain.workqueue.WashingSortOutput
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant

object WashingBatchCodec {
    private val EPOCH = Instant.fromEpochMilliseconds(0)

    fun encodeItem(item: WashingBatchItem): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(item.id),
        "workCardId" to jsonOf(item.workCardId.value),
        "subjectId" to jsonOf(item.subjectId),
        "orderNumber" to jsonOf(item.orderNumber),
        "articleName" to jsonOf(item.articleName),
        "bundleNo" to jsonOf(item.bundleNo),
        "sizeLabel" to jsonOf(item.sizeLabel),
        "inputPcs" to jsonOf(item.inputPcs),
        "bundlePhotoKey" to jsonOf(item.bundlePhotoKey),
        "createdAt" to jsonOf(item.createdAt.toString())
    )

    fun decodeItem(obj: JsonValue.Obj): WashingBatchItem = WashingBatchItem(
        id = obj.string("id") ?: "",
        workCardId = WorkCardId(obj.string("workCardId") ?: ""),
        subjectId = obj.string("subjectId") ?: "",
        orderNumber = obj.string("orderNumber") ?: "",
        articleName = obj.string("articleName") ?: "",
        bundleNo = obj.int("bundleNo") ?: 1,
        sizeLabel = obj.string("sizeLabel") ?: "",
        inputPcs = obj.int("inputPcs") ?: 0,
        bundlePhotoKey = obj.string("bundlePhotoKey") ?: "",
        createdAt = DateTimeCodec.parseInstantOrNull(obj.string("createdAt")) ?: EPOCH
    )

    fun encodeSortOutput(output: WashingSortOutput): JsonValue.Obj = jsonObjectOf(
        "subjectId" to jsonOf(output.subjectId),
        "orderNumber" to jsonOf(output.orderNumber),
        "sizeLabel" to jsonOf(output.sizeLabel),
        "outputPcs" to jsonOf(output.outputPcs),
        "scrapPcs" to jsonOf(output.scrapPcs),
        "defectPcs" to jsonOf(output.defectPcs),
        "notes" to jsonOf(output.notes)
    )

    fun decodeSortOutput(obj: JsonValue.Obj): WashingSortOutput = WashingSortOutput(
        subjectId = obj.string("subjectId") ?: "",
        orderNumber = obj.string("orderNumber") ?: "",
        sizeLabel = obj.string("sizeLabel") ?: "",
        outputPcs = obj.int("outputPcs") ?: 0,
        scrapPcs = obj.int("scrapPcs") ?: 0,
        defectPcs = obj.int("defectPcs") ?: 0,
        notes = obj.string("notes") ?: ""
    )

    fun encodeBatch(batch: WashingBatch): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(batch.id.value),
        "tenantId" to jsonOf(batch.tenantId),
        "batchCode" to jsonOf(batch.batchCode),
        "machineDrumNo" to jsonOf(batch.machineDrumNo),
        "washRecipe" to jsonOf(batch.washRecipe),
        "operatorName" to jsonOf(batch.operatorName),
        "totalBundles" to jsonOf(batch.totalBundles),
        "totalInputPcs" to jsonOf(batch.totalInputPcs),
        "totalOutputPcs" to jsonOf(batch.totalOutputPcs),
        "missingPcs" to jsonOf(batch.missingPcs),
        "status" to jsonOf(batch.status.name),
        "notes" to jsonOf(batch.notes),
        "items" to jsonArrayOf(batch.items.map(::encodeItem)),
        "sortOutputs" to jsonArrayOf(batch.sortOutputs.map(::encodeSortOutput)),
        "createdAt" to jsonOf(batch.createdAt.toString()),
        "completedAt" to (batch.completedAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null)
    )

    fun decodeBatch(obj: JsonValue.Obj): WashingBatch = WashingBatch(
        id = WashingBatchId(obj.string("id") ?: ""),
        tenantId = obj.string("tenantId") ?: "",
        batchCode = obj.string("batchCode") ?: "",
        machineDrumNo = obj.string("machineDrumNo") ?: "",
        washRecipe = obj.string("washRecipe") ?: "",
        operatorName = obj.string("operatorName") ?: "",
        items = obj.objectArray("items").map(::decodeItem),
        sortOutputs = obj.objectArray("sortOutputs").map(::decodeSortOutput),
        totalOutputPcs = obj.int("totalOutputPcs") ?: 0,
        missingPcs = obj.int("missingPcs") ?: 0,
        status = runCatching {
            WashingBatchStatus.valueOf(obj.string("status") ?: WashingBatchStatus.IN_WASHER.name)
        }.getOrDefault(WashingBatchStatus.IN_WASHER),
        notes = obj.string("notes") ?: "",
        createdAt = DateTimeCodec.parseInstantOrNull(obj.string("createdAt")) ?: EPOCH,
        completedAt = DateTimeCodec.parseInstantOrNull(obj.string("completedAt"))
    )
}
