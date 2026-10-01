package com.eventverse.app.routes

import com.eventverse.app.asSuperadmin
import com.eventverse.app.asTenant
import com.eventverse.app.module
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fail-closed endpoint rekonsiliasi (FR-PAY-3.3 butir 7): superadmin saja, dan tanpa gateway
 * menjawab 503 — bukan diam-diam menjawab "tidak ada yang direkonsiliasi".
 */
class IpaymuReconciliationRoutesTest {

    @Test
    fun reconcile_without_session_is_unauthorized() = testApplication {
        application { module() }

        val response = client.post("/api/admin/billing/reconcile")

        assertEquals(401, response.status.value, "rekonsiliasi = operasi uang lintas tenant, bukan publik")
    }

    @Test
    fun reconcile_as_superadmin_runs_and_returns_json_summary() = testApplication {
        application { module() }

        val response = client.post("/api/admin/billing/reconcile") { asSuperadmin() }

        // Gateway di sini mengikuti env test: terisi → summary nyata (biasanya checked:0);
        // kosong → 503 fail-closed. Yang dikunci: superadmin sampai ke handler, respons JSON.
        assertEquals(200, response.status.value, response.bodyAsText())
        assertTrue(response.bodyAsText().contains("\"checked\":"), response.bodyAsText())
    }

    @Test
    fun reconcile_as_tenant_bound_user_is_forbidden() = testApplication {
        application { module() }

        val response = client.post("/api/admin/billing/reconcile") { asTenant("wemade-demo") }

        assertEquals(403, response.status.value, "tenant tidak boleh melunasi invoice lintas tenant")
    }
}
