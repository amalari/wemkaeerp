package com.eventverse.app.shared.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.ModuleIdCodec

import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.pipeline.ModuleTelemetry
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/** Format wire `GET /api/tenant/pipeline/telemetry`, dipakai server dan klien. */
object ModuleTelemetryCodec {

    fun encode(readings: List<ModuleTelemetry>): JsonValue.Obj =
        jsonObjectOf("modules" to jsonArrayOf(readings.map(::encodeOne)))

    /** Entri dengan modul/status yang tidak dikenal dilewati — klien lama tetap jalan. */
    fun decode(payload: JsonValue.Obj): List<ModuleTelemetry> = payload.objectArray("modules").mapNotNull { o ->
        val module = ModuleIdCodec.fromStoredName(o.string("module"), "pipeline/telemetry") ?: return@mapNotNull null
        val health = o.string("healthStatus")?.let { n -> FlowHealthStatus.entries.firstOrNull { it.name == n } } ?: return@mapNotNull null
        ModuleTelemetry(
            module = module,
            wipPieces = o.int("wipPieces") ?: 0,
            cycleTimeHours = o.double("cycleTimeHours") ?: 0.0,
            healthStatus = health,
            stageWip = o.obj("stageWip")?.entries.orEmpty().mapNotNull { (code, n) ->
                val count = (n as? JsonValue.Num)?.asInt ?: return@mapNotNull null
                runCatching { StageCode(code) }.getOrNull()?.let { it to count }
            }.toMap()
        )
    }

    private fun encodeOne(t: ModuleTelemetry) = jsonObjectOf(
        "module" to jsonOf(t.module.name),
        "wipPieces" to jsonOf(t.wipPieces),
        "cycleTimeHours" to jsonOf(t.cycleTimeHours),
        "healthStatus" to jsonOf(t.healthStatus.name),
        "stageWip" to jsonObjectOf(*t.stageWip.map { (code, n) -> code.value to jsonOf(n) }.toTypedArray())
    )
}
