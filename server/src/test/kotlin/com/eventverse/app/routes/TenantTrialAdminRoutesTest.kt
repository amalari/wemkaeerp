package com.eventverse.app.routes

import com.eventverse.app.asSuperadmin
import com.eventverse.app.asTenant
import com.eventverse.app.module
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Jam trial (V88): papan pantau + tuas perpanjangan untuk superadmin. Fixture lewat `module()`
 * (Postgres dev) — GET hanya baca; extend dites ke slug yang tidak ada (404) supaya test tidak
 * memutasi data dev.
 */
class TenantTrialAdminRoutesTest {

    @Test
    fun trials_list_is_superadmin_only() = testApplication {
        application { module() }

        val response = client.get("/api/admin/trials") { asSuperadmin() }

        assertEquals(200, response.status.value)
        assertTrue(response.bodyAsText().startsWith("["), response.bodyAsText())
    }

    @Test
    fun trials_list_without_session_is_unauthorized() = testApplication {
        application { module() }

        val response = client.get("/api/admin/trials")

        assertEquals(401, response.status.value)
    }

    @Test
    fun trials_list_for_tenant_bound_user_is_forbidden() = testApplication {
        application { module() }

        val response = client.get("/api/admin/trials") { asTenant("wemade-demo") }

        assertEquals(403, response.status.value, "trial lintas tenant = platform superadmin saja")
    }

    @Test
    fun extend_unknown_slug_is_not_found() = testApplication {
        application { module() }

        val response = client.post("/api/admin/trials/slug-tidak-ada/extend?days=7") { asSuperadmin() }

        assertEquals(404, response.status.value)
    }
}
