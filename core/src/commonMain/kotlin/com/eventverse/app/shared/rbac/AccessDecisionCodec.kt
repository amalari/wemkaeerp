package com.eventverse.app.shared.rbac

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.ModuleIdCodec

import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Format kabel `GET /api/tenant/me/access` — keputusan wewenang pemanggil per modul, dihitung di server (B5).
 * Entri dengan modul/level/sumber yang tidak dikenal **dilewati**, bukan ditebak: klien yang lebih lama tetap jalan,
 * dan modul yang tidak dikenalnya tertutup (tidak ada entri = `NONE`).
 */
object AccessDecisionCodec {

    fun encode(decisions: Map<BusinessModule, AccessDecision>): JsonValue.Obj = jsonObjectOf(
        "modules" to jsonObjectOf(*decisions.map { (m, d) ->
            m.name to jsonObjectOf(
                "config" to encodeConfig(d.config),
                "source" to jsonOf(d.source.name),
                "fromRole" to encodeConfig(d.fromRole),
                "fromDepartment" to encodeConfig(d.fromDepartment)
            )
        }.toTypedArray())
    )

    fun decode(payload: JsonValue.Obj): Map<BusinessModule, AccessDecision> =
        payload.obj("modules")?.entries.orEmpty().mapNotNull { (name, value) ->
            val module = ModuleIdCodec.fromStoredName(name, "me/access") ?: return@mapNotNull null
            val o = value as? JsonValue.Obj ?: return@mapNotNull null
            val source = AccessSource.entries.firstOrNull { it.name == o.string("source") } ?: return@mapNotNull null
            val config = o.obj("config")?.let(::decodeConfig) ?: return@mapNotNull null
            module to AccessDecision(
                config = config,
                source = source,
                fromRole = o.obj("fromRole")?.let(::decodeConfig) ?: ModuleAccessConfig(),
                fromDepartment = o.obj("fromDepartment")?.let(::decodeConfig) ?: ModuleAccessConfig()
            )
        }.toMap()

    private fun encodeConfig(c: ModuleAccessConfig) = jsonObjectOf(
        "level" to jsonOf(c.level.name),
        "scope" to jsonOf(c.scope.name),
        "allowedDesks" to (c.allowedDesks?.let { desks -> jsonArrayOf(desks.sorted().map { jsonOf(it) }) } ?: JsonValue.Null)
    )

    private fun decodeConfig(o: JsonValue.Obj): ModuleAccessConfig? {
        val level = AccessLevel.entries.firstOrNull { it.name == o.string("level") } ?: return null
        val scope = DataScope.entries.firstOrNull { it.name == o.string("scope") } ?: return null
        val desks = if (o.has("allowedDesks") && o["allowedDesks"] !is JsonValue.Null) o.stringArray("allowedDesks").toSet() else null
        return ModuleAccessConfig(level = level, scope = scope, allowedDesks = desks)
    }
}
