package com.eventverse.app.shared.builder

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant

/**
 * Kontrak JSON `GET /api/builder/audit` — **dibekukan di Track 0** (TRD-PLAT-014 FR-5-1,
 * PLAN `PLAN-builder-console-v2-navigation.md` §4 T0). Server (T1) wajib menghasilkan bentuk
 * ini; klien (T3) hanya membaca lewat codec ini — bukan parser ad-hoc.
 *
 * Bentuk respons:
 * ```json
 * {"entries":[{"id":"…","actorUserId":"…","actorRole":"PLATFORM_SUPERADMIN",
 *   "targetTenantId":"ten-1","action":"builder_deployment_activated",
 *   "summary":"deploy #1 ACTIVE v2","occurredAt":"2026-10-14T10:00:00Z"}]}
 * ```
 *
 * Aturan dekode **ketat** (Kontrak 4 tenant-variability-rules — tidak ada fallback senyap):
 *  - `action` memakai `AuditAction.code`; kode tak dikenal → [BuilderAuditDecodeException].
 *  - `actorRole` memakai `Role.name`; nama tak dikenal → exception.
 *  - `occurredAt` ISO-8601 (`Instant.parse`); cacat → exception.
 *  - Field wajib hilang/kosong → exception dengan path.
 *
 * Parameter endpoint: `?limit=N` default 50, diklem 1..200 **di server** (bukan klien).
 * Gate: `MANAGE_BUILDER`, fail-closed (TRD FR-5-1); owner hanya menerima entri tenant sesinya.
 */
object BuilderAuditTrailCodec {

    fun decode(body: JsonValue): List<AuditLogEntry> {
        val root = (body as? JsonValue.Obj) ?: throw BuilderAuditDecodeException("$", "Respons audit bukan objek JSON")
        val items = root.objectArray("entries")
        if (!root.has("entries")) throw BuilderAuditDecodeException("$.entries", "Respons audit tidak memuat entries")
        return items.mapIndexed { i, e -> decodeEntry(e, i) }
    }

    fun encode(entries: List<AuditLogEntry>): JsonValue.Obj = jsonObjectOf(
        "entries" to jsonArrayOf(entries.map(::encodeEntry)),
    )

    private fun decodeEntry(obj: JsonValue.Obj, index: Int): AuditLogEntry {
        val p = { field: String -> "$.entries[$index].$field" }
        val id = required(obj, "id", p)
        val actorUserId = required(obj, "actorUserId", p)
        val roleRaw = required(obj, "actorRole", p)
        val role = runCatching { Role.valueOf(roleRaw) }.getOrElse {
            throw BuilderAuditDecodeException(p("actorRole"), "Peran tak dikenal: $roleRaw")
        }
        val targetTenantId = required(obj, "targetTenantId", p)
        val actionRaw = required(obj, "action", p)
        val action = AuditAction.fromCode(actionRaw)
            ?: throw BuilderAuditDecodeException(p("action"), "Aksi audit tak dikenal: $actionRaw")
        val summary = required(obj, "summary", p)
        val occurredRaw = required(obj, "occurredAt", p)
        val occurredAt = runCatching { Instant.parse(occurredRaw) }.getOrElse {
            throw BuilderAuditDecodeException(p("occurredAt"), "Waktu tidak ISO-8601: $occurredRaw")
        }
        return try {
            AuditLogEntry(
                id = id,
                actorUserId = actorUserId,
                actorRole = role,
                targetTenantId = TenantId(targetTenantId),
                action = action,
                summary = summary,
                occurredAt = occurredAt,
            )
        } catch (e: IllegalArgumentException) {
            throw BuilderAuditDecodeException("$.entries[$index]", e.message ?: "Entri audit tidak sah")
        }
    }

    private fun required(obj: JsonValue.Obj, field: String, path: (String) -> String): String =
        obj.string(field)?.takeIf { it.isNotBlank() }
            ?: throw BuilderAuditDecodeException(path(field), "Field '$field' hilang atau kosong")

    private fun encodeEntry(e: AuditLogEntry): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(e.id),
        "actorUserId" to jsonOf(e.actorUserId),
        "actorRole" to jsonOf(e.actorRole.name),
        "targetTenantId" to jsonOf(e.targetTenantId.value),
        "action" to jsonOf(e.action.code),
        "summary" to jsonOf(e.summary),
        "occurredAt" to jsonOf(e.occurredAt.toString()),
    )
}

/** Pelanggaran kontrak audit (TRD-PLAT-014 Track 0). [path] menunjuk lokasi JSON yang ditolak. */
class BuilderAuditDecodeException(val path: String, message: String) : IllegalStateException("$path: $message")
