package com.eventverse.app.shared.process

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.process.TenantProcessCatalog
import com.eventverse.app.domain.sampling.parseLegacyStageCodeOrNull
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Codec JSON bersama katalog proses opsional — dipakai server (route Ktor) dan
 * client (repository Ktor di app/shared) agar kontrak wire tidak pernah drift.
 */
object ProcessCatalogCodec {

    fun encodeProcess(process: TenantOptionalProcess): JsonValue = jsonObjectOf(
        "processId" to jsonOf(process.processId),
        "tenantId" to jsonOf(process.tenantId.value),
        "code" to jsonOf(process.code),
        "displayName" to jsonOf(process.displayName),
        "archetype" to jsonOf(process.archetype.name),
        "samplingAnchorAfter" to jsonOf(process.samplingAnchorAfter?.value),
        "stationAnchorAfter" to jsonOf(process.stationAnchorAfter?.value),
        "executionMode" to jsonOf(process.executionMode.name),
        "vendorRef" to jsonOf(process.vendorRef),
        "piecerateTariffIdr" to jsonOf(process.piecerateTariffIdr),
        "standardMinutesPerPiece" to jsonOf(process.standardMinutesPerPiece)
    )

    fun encodeProcesses(processes: List<TenantOptionalProcess>): JsonValue.Arr =
        JsonValue.Arr(processes.map(::encodeProcess))

    fun encodeCatalog(catalog: TenantProcessCatalog): JsonValue =
        encodeProcesses(catalog.processes)

    /** Dekode toleran: field jangkar invalid diabaikan (null) alih-alih meledak. */
    fun decodeProcess(obj: JsonValue.Obj, fallbackTenantId: TenantId): TenantOptionalProcess? = runCatching {
        val code = obj.string("code") ?: return@runCatching null
        val tenantId = obj.string("tenantId")?.let(::TenantId) ?: fallbackTenantId
        TenantOptionalProcess(
            processId = obj.string("processId") ?: AddOptionalProcessId.processIdFor(code),
            tenantId = tenantId,
            code = code,
            displayName = obj.string("displayName") ?: code,
            archetype = obj.string("archetype")
                ?.let { runCatching { ModuleArchetype.valueOf(it) }.getOrNull() }
                ?: ModuleArchetype.CUSTOM_EXTENSION,
            samplingAnchorAfter = obj.string("samplingAnchorAfter")
                ?.let { parseLegacyStageCodeOrNull(it) },
            stationAnchorAfter = obj.string("stationAnchorAfter")
                ?.takeIf { it.isNotBlank() }
                ?.let { WorkStationCode(it) },
            executionMode = obj.string("executionMode")
                ?.let { runCatching { WorkExecutionMode.valueOf(it) }.getOrNull() }
                ?: WorkExecutionMode.IN_HOUSE,
            vendorRef = obj.string("vendorRef"),
            piecerateTariffIdr = obj.long("piecerateTariffIdr") ?: 0L,
            standardMinutesPerPiece = obj.double("standardMinutesPerPiece") ?: 0.0
        )
    }.getOrNull()

    fun decodeProcesses(items: List<JsonValue>, fallbackTenantId: TenantId): List<TenantOptionalProcess> =
        items.filterIsInstance<JsonValue.Obj>().mapNotNull { decodeProcess(it, fallbackTenantId) }

    fun decodeCatalog(array: JsonValue.Arr, fallbackTenantId: TenantId): TenantProcessCatalog =
        decodeProcesses(array.items, fallbackTenantId)
            .fold(TenantProcessCatalog(tenantId = fallbackTenantId)) { catalog, process ->
                runCatching { catalog.addProcess(process) }.getOrDefault(catalog)
            }
}

/** Meniru aturan ID deterministik [com.eventverse.app.domain.process.usecases.AddOptionalProcessUseCase]. */
private object AddOptionalProcessId {
    fun processIdFor(code: String): String = "proc-${code.trim().lowercase()}"
}