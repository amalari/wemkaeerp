package com.eventverse.app.shared.production

import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.production.*
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Instant

object BulkWorkOrderCodec {

    fun encode(order: BulkWorkOrder): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(order.id.value),
        "tenantId" to jsonOf(order.tenantId.value),
        "spkNumber" to jsonOf(order.spkNumber.value),
        "clientName" to jsonOf(order.clientName),
        "styleName" to jsonOf(order.styleName),
        "status" to jsonOf(order.status.name),
        "dealId" to jsonOf(order.dealId),
        "goldenSampleOrderId" to jsonOf(order.goldenSampleOrderId?.value),
        "stockOwnership" to jsonOf(order.stockOwnership.name),
        "sizeBreakdown" to jsonArrayOf(order.sizeBreakdown.map(::encodeSizeLine)),
        "lineAllocations" to jsonArrayOf(order.lineAllocations.map(::encodeAllocation)),
        "stageProgress" to jsonArrayOf(order.stageProgress.map(::encodeProgress)),
        "targetOutputPerDay" to jsonOf(order.targetOutputPerDay),
        "plannedStartDate" to jsonOf(order.plannedStartDate?.toString()),
        "plannedFinishDate" to jsonOf(order.plannedFinishDate?.toString()),
        "notes" to jsonOf(order.notes),
        "createdAt" to jsonOf(order.createdAt.toString()),
        "updatedAt" to jsonOf(order.updatedAt.toString()),
        "releasedAt" to jsonOf(order.releasedAt?.toString()),
        "archivedAt" to jsonOf(order.archivedAt?.toString())
    )

    fun decode(obj: JsonValue.Obj): BulkWorkOrder {
        val createdAt = DateTimeCodec.parseInstantOrFallback(
            obj.string("createdAt"),
            Instant.fromEpochMilliseconds(0)
        )
        return BulkWorkOrder(
            id = BulkWorkOrderId(obj.string("id") ?: ""),
            tenantId = TenantId(obj.string("tenantId") ?: ""),
            spkNumber = BulkSpkNumber(obj.string("spkNumber") ?: ""),
            clientName = obj.string("clientName") ?: "",
            styleName = obj.string("styleName") ?: "",
            status = obj.string("status")
                ?.let { runCatching { BulkProductionStatus.valueOf(it) }.getOrNull() }
                ?: BulkProductionStatus.DRAFT,
            dealId = obj.string("dealId"),
            goldenSampleOrderId = obj.string("goldenSampleOrderId")
                ?.takeIf { it.isNotBlank() }
                ?.let { SamplingOrderId(it) },
            stockOwnership = obj.string("stockOwnership")
                ?.let { runCatching { StockOwnershipSemantics.valueOf(it) }.getOrNull() }
                ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL,
            sizeBreakdown = obj.objectArray("sizeBreakdown").mapNotNull(::decodeSizeLine),
            lineAllocations = obj.objectArray("lineAllocations").mapNotNull(::decodeAllocation),
            stageProgress = obj.objectArray("stageProgress")
                .mapNotNull(::decodeProgress)
                .ifEmpty { BulkWorkOrder.emptyProgress() }
                .let(::fillMissingStages),
            targetOutputPerDay = obj.int("targetOutputPerDay") ?: 0,
            plannedStartDate = DateTimeCodec.parseLocalDateOrNull(obj.string("plannedStartDate")),
            plannedFinishDate = DateTimeCodec.parseLocalDateOrNull(obj.string("plannedFinishDate")),
            notes = obj.string("notes") ?: "",
            createdAt = createdAt,
            updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), createdAt),
            releasedAt = DateTimeCodec.parseInstantOrNull(obj.string("releasedAt")),
            archivedAt = DateTimeCodec.parseInstantOrNull(obj.string("archivedAt"))
        )
    }

    // ── Bagian yang dipakai ulang oleh repository Postgres (kolom jsonb) ─────────────────────

    fun encodeSizeBreakdown(lines: List<BulkSizeLine>): String =
        jsonArrayOf(lines.map(::encodeSizeLine)).encode()

    fun decodeSizeBreakdown(raw: String?): List<BulkSizeLine> =
        runCatching {
            if (raw.isNullOrBlank()) emptyList()
            else JsonParser.parseArray(raw).filterIsInstance<JsonValue.Obj>().mapNotNull(::decodeSizeLine)
        }.getOrDefault(emptyList())

    fun encodeAllocations(items: List<MachineLineAllocation>): String =
        jsonArrayOf(items.map(::encodeAllocation)).encode()

    fun decodeAllocations(raw: String?): List<MachineLineAllocation> =
        runCatching {
            if (raw.isNullOrBlank()) emptyList()
            else JsonParser.parseArray(raw).filterIsInstance<JsonValue.Obj>().mapNotNull(::decodeAllocation)
        }.getOrDefault(emptyList())

    fun encodeStageProgress(items: List<ProductionStageProgress>): String =
        jsonArrayOf(items.map(::encodeProgress)).encode()

    fun decodeStageProgress(raw: String?): List<ProductionStageProgress> =
        runCatching {
            if (raw.isNullOrBlank()) BulkWorkOrder.emptyProgress()
            else JsonParser.parseArray(raw)
                .filterIsInstance<JsonValue.Obj>()
                .mapNotNull(::decodeProgress)
                .ifEmpty { BulkWorkOrder.emptyProgress() }
        }.getOrDefault(BulkWorkOrder.emptyProgress()).let(::fillMissingStages)

    // ── Encoder / decoder per potongan ──────────────────────────────────────────────────────

    private fun encodeSizeLine(line: BulkSizeLine): JsonValue.Obj = jsonObjectOf(
        "sizeLabel" to jsonOf(line.sizeLabel),
        "orderedPcs" to jsonOf(line.orderedPcs)
    )

    /**
     * Baris rusak dibuang, bukan membatalkan seluruh SPK.
     *
     * `BulkSizeLine` menolak qty <= 0 lewat `require`, jadi satu baris cacat di jsonb lama akan
     * melempar dan membuat SPK-nya mustahil dibuka. Kehilangan satu baris jauh lebih murah
     * daripada kehilangan seluruh dokumen kerja.
     */
    private fun decodeSizeLine(obj: JsonValue.Obj): BulkSizeLine? = runCatching {
        BulkSizeLine(
            sizeLabel = obj.string("sizeLabel") ?: "",
            orderedPcs = obj.int("orderedPcs") ?: 0
        )
    }.getOrNull()

    private fun encodeAllocation(a: MachineLineAllocation): JsonValue.Obj = jsonObjectOf(
        "lineName" to jsonOf(a.lineName.value),
        "machineCount" to jsonOf(a.machineCount),
        "assignedPcs" to jsonOf(a.assignedPcs),
        "startDate" to jsonOf(a.startDate?.toString()),
        "targetFinishDate" to jsonOf(a.targetFinishDate?.toString()),
        "operatorCount" to jsonOf(a.operatorCount),
        "notes" to jsonOf(a.notes)
    )

    private fun decodeAllocation(obj: JsonValue.Obj): MachineLineAllocation? = runCatching {
        MachineLineAllocation(
            lineName = ProductionLineName(obj.string("lineName") ?: ""),
            machineCount = obj.int("machineCount") ?: 0,
            assignedPcs = obj.int("assignedPcs") ?: 0,
            startDate = DateTimeCodec.parseLocalDateOrNull(obj.string("startDate")),
            targetFinishDate = DateTimeCodec.parseLocalDateOrNull(obj.string("targetFinishDate")),
            operatorCount = obj.int("operatorCount") ?: 0,
            notes = obj.string("notes") ?: ""
        )
    }.getOrNull()

    private fun encodeProgress(p: ProductionStageProgress): JsonValue.Obj = jsonObjectOf(
        "stage" to jsonOf(p.stage.name),
        "completedPcs" to jsonOf(p.completedPcs),
        "reworkPcs" to jsonOf(p.reworkPcs),
        "rejectPcs" to jsonOf(p.rejectPcs),
        "lastUpdatedAt" to jsonOf(p.lastUpdatedAt?.toString())
    )

    private fun decodeProgress(obj: JsonValue.Obj): ProductionStageProgress? = runCatching {
        val stage = obj.string("stage")?.let { runCatching { ProductionStage.valueOf(it) }.getOrNull() }
            ?: return@runCatching null
        ProductionStageProgress(
            stage = stage,
            completedPcs = obj.int("completedPcs") ?: 0,
            reworkPcs = obj.int("reworkPcs") ?: 0,
            rejectPcs = obj.int("rejectPcs") ?: 0,
            lastUpdatedAt = DateTimeCodec.parseInstantOrNull(obj.string("lastUpdatedAt"))
        )
    }.getOrNull()

    /**
     * Menjamin ketiga tahap selalu hadir dan urut.
     *
     * `BulkWorkOrder.wipPieces` menjumlah seluruh `ProductionStage.entries`; tahap yang hilang
     * dari jsonb akan dibaca sebagai progres nol dan memunculkan WIP palsu sebesar satu pesanan penuh.
     */
    private fun fillMissingStages(items: List<ProductionStageProgress>): List<ProductionStageProgress> =
        ProductionStage.entries.map { stage ->
            items.firstOrNull { it.stage == stage } ?: ProductionStageProgress(stage)
        }
}
