package com.eventverse.app.infrastructure

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the `ops` schema boundary introduced in V16.
 *
 * The boundary is easy to undo by accident — a new migration that creates a cost table without
 * naming a schema, or a column added to the billing view "just to show it on the admin screen".
 * Neither breaks anything visible, and both quietly put our cost figures back within reach of a
 * tenant-facing connection. These assertions fail instead.
 */
class OpsSchemaBoundaryTest {

    @BeforeTest
    fun setup() {
        DatabaseFactory.init()
    }

    private fun schemaOf(table: String): String? = transaction {
        exec(
            "SELECT schemaname FROM pg_tables WHERE tablename = '$table'"
        ) { rs -> if (rs.next()) rs.getString(1) else null }
    }

    private fun columnsOf(view: String): Set<String> = transaction {
        exec(
            "SELECT column_name FROM information_schema.columns WHERE table_name = '$view'"
        ) { rs ->
            buildSet { while (rs.next()) add(rs.getString(1)) }
        }
    }.orEmpty()

    @Test
    fun costBearingTablesShouldLiveInTheOpsSchema() = runBlocking<Unit> {
        listOf(
            "module_build_records",
            "module_build_effort_entries",
            "module_sizing_weights",
            "module_pricing_quotes",
            "prospect_leads",
            "prospect_flow_translations",
            "prospect_price_estimates",
            // Funnel discovery (plan Fase A/C): narasi prospek dan pola layar internal memuat
            // kosakata prospek — milik platform, bukan milik satu tenant. Tanpa baris ini, tabel
            // baru bisa "pindah" ke public tanpa ada test yang gagal.
            "discovery_drafts",
            "discovery_demands",
            "prototype_patterns"
        ).forEach { table ->
            assertEquals("ops", schemaOf(table), "$table escaped the ops schema")
        }
    }

    @Test
    fun customerFacingTablesShouldStayInPublic() = runBlocking<Unit> {
        // The catalogue holds list prices, which are the customer's business; customisation
        // requests are owned by the tenant and read under RLS. Moving either into ops would make a
        // tenant-scoped request unable to read its own data.
        assertEquals("public", schemaOf("module_catalog_entries"))
        assertEquals("public", schemaOf("module_customization_requests"))
        assertEquals("public", schemaOf("tenants"))
        assertEquals("factory_flow", schemaOf("tenant_pipelines"), "B8: kanvas pipeline tinggal di schema modul factory_flow")
    }

    @Test
    fun theBillingViewShouldExposeNoCostColumn() = runBlocking<Unit> {
        val columns = columnsOf("tenant_billable_quotes")
        assertTrue(columns.isNotEmpty(), "tenant_billable_quotes is missing")

        // The whole reason the view exists: a tenant's bill needs the result, never the inputs.
        listOf("build_cost_idr", "basis_hours", "margin_percent", "expected_tenant_count")
            .forEach { forbidden ->
                assertTrue(
                    forbidden !in columns,
                    "cost column '$forbidden' leaked into the tenant-facing billing view"
                )
            }
        assertTrue("monthly_price_idr" in columns)
    }

    @Test
    fun theTenantScopedRoleShouldExistAndBeNeitherSuperuserNorBypassRls() = runBlocking<Unit> {
        // PostgreSQL skips every RLS policy for a superuser. A role that is exempt would make the
        // whole apply_tenant_rls() layer decorative — which is exactly the state
        // docs/tenant-isolation-rls-status.md was written about.
        val flags = transaction {
            exec(
                "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'wemade_app'"
            ) { rs -> if (rs.next()) rs.getBoolean(1) to rs.getBoolean(2) else null }
        }

        assertTrue(flags != null, "role wemade_app was never created; V16 did not run")
        assertEquals(false to false, flags, "wemade_app can bypass RLS and is useless as a boundary")
    }
}
