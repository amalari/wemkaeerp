package com.eventverse.app.shared.builder

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Kontrak Track 0 TRD-PLAT-014: bentuk JSON `GET /api/builder/audit` dibekukan di sini.
 * Server (T1) memproduksi bentuk ini; klien (T3) mendekodenya lewat codec yang sama.
 */
class BuilderAuditTrailCodecTest {

    private val fixture = """
        {"entries":[
          {"id":"audit-ten-1-1760000000000","actorUserId":"user-9","actorRole":"PLATFORM_SUPERADMIN",
           "targetTenantId":"ten-1","action":"builder_deployment_activated",
           "summary":"deploy #1 ACTIVE v2","occurredAt":"2026-10-14T10:00:00Z"},
          {"id":"audit-ten-1-1760000001000","actorUserId":"user-1","actorRole":"TENANT_ADMIN",
           "targetTenantId":"ten-1","action":"platform_act_as_started",
           "summary":"operator platform membuka workspace","occurredAt":"2026-10-14T11:30:00Z"}
        ]}
    """.trimIndent()

    private val expected = listOf(
        AuditLogEntry(
            id = "audit-ten-1-1760000000000",
            actorUserId = "user-9",
            actorRole = Role.PLATFORM_SUPERADMIN,
            targetTenantId = TenantId("ten-1"),
            action = AuditAction.BUILDER_DEPLOYMENT_ACTIVATED,
            summary = "deploy #1 ACTIVE v2",
            occurredAt = Instant.parse("2026-10-14T10:00:00Z"),
        ),
        AuditLogEntry(
            id = "audit-ten-1-1760000001000",
            actorUserId = "user-1",
            actorRole = Role.TENANT_ADMIN,
            targetTenantId = TenantId("ten-1"),
            action = AuditAction.PLATFORM_ACT_AS_STARTED,
            summary = "operator platform membuka workspace",
            occurredAt = Instant.parse("2026-10-14T11:30:00Z"),
        ),
    )

    @Test
    fun `decode fixture should produce domain entries`() {
        assertEquals(expected, BuilderAuditTrailCodec.decode(JsonParser.parse(fixture)))
    }

    @Test
    fun `round trip encode decode should be stable`() {
        val encoded = BuilderAuditTrailCodec.encode(expected)
        assertEquals(expected, BuilderAuditTrailCodec.decode(encoded))
        assertEquals(encoded, BuilderAuditTrailCodec.encode(BuilderAuditTrailCodec.decode(encoded)))
    }

    @Test
    fun `unknown action code should be rejected with path`() {
        val bad = """{"entries":[{"id":"a","actorUserId":"u","actorRole":"TENANT_ADMIN",
            "targetTenantId":"ten-1","action":"tidak_ada","summary":"s","occurredAt":"2026-10-14T10:00:00Z"}]}"""
        val e = assertFailsWith<BuilderAuditDecodeException> { BuilderAuditTrailCodec.decode(JsonParser.parse(bad)) }
        assertTrue(e.path.contains("action"), "path harus menunjuk field action, dapat: ${e.path}")
    }

    @Test
    fun `unknown role should be rejected`() {
        val bad = """{"entries":[{"id":"a","actorUserId":"u","actorRole":"DEVELOPER",
            "targetTenantId":"ten-1","action":"builder_invoice_issued","summary":"s","occurredAt":"2026-10-14T10:00:00Z"}]}"""
        assertFailsWith<BuilderAuditDecodeException> { BuilderAuditTrailCodec.decode(JsonParser.parse(bad)) }
    }

    @Test
    fun `malformed instant should be rejected`() {
        val bad = """{"entries":[{"id":"a","actorUserId":"u","actorRole":"TENANT_ADMIN",
            "targetTenantId":"ten-1","action":"builder_invoice_issued","summary":"s","occurredAt":"14/10/2026"}]}"""
        assertFailsWith<BuilderAuditDecodeException> { BuilderAuditTrailCodec.decode(JsonParser.parse(bad)) }
    }

    @Test
    fun `missing required field should be rejected with path`() {
        val bad = """{"entries":[{"id":"a","actorUserId":"u","actorRole":"TENANT_ADMIN",
            "targetTenantId":"ten-1","summary":"s","occurredAt":"2026-10-14T10:00:00Z"}]}"""
        val e = assertFailsWith<BuilderAuditDecodeException> { BuilderAuditTrailCodec.decode(JsonParser.parse(bad)) }
        assertTrue(e.path.contains("action"), "path harus menunjuk field yang hilang, dapat: ${e.path}")
    }

    @Test
    fun `missing entries key should be rejected`() {
        val e = assertFailsWith<BuilderAuditDecodeException> { BuilderAuditTrailCodec.decode(JsonParser.parse("""{"x":1}""")) }
        assertEquals("$.entries", e.path)
    }

    @Test
    fun `non object body should be rejected`() {
        assertFailsWith<BuilderAuditDecodeException> { BuilderAuditTrailCodec.decode(JsonParser.parse("[]")) }
    }
}
