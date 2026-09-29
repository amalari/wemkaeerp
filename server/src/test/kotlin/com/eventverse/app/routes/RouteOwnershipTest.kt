package com.eventverse.app.routes

import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.application.pluginOrNull
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Setiap route `/api/tenant/…` yang terpasang wajib punya pemilik: modul, fitur terdaftar
 * ([com.eventverse.app.domain.pipeline.ModuleFeatureRegistry]), atau layanan platform (TRD-FLOW-002
 * Fase 4). Fitur baru yang lupa didaftarkan gagal di sini — dan karenanya tidak akan hilang dari
 * kanvas Factory Flow tanpa ketahuan.
 */
class RouteOwnershipTest {

    @Test
    fun everyTenantRoute_hasAnOwner() = testApplication {
        var paths = emptyList<String>()
        application {
            module(
                tenantRepository = InMemoryTenantRepository(),
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository()
            )
            monitor.subscribe(io.ktor.server.application.ApplicationStarted) {
                paths = pluginOrNull(RoutingRoot)?.getAllRoutes().orEmpty()
                    .map { route -> route.toString().substringBefore("/(").substringBefore("/[") }
            }
        }
        startApplication()

        val tenantPaths = paths.filter { it.startsWith("/api/tenant") }.distinct()
        assertTrue(tenantPaths.size > 20, "Enumerasi route gagal: hanya ${tenantPaths.size} route ditemukan")
        val orphans = tenantPaths.filter { RouteOwnership.ownerOf(it) == null }
        if (orphans.isNotEmpty()) {
            fail("Route tanpa pemilik — daftarkan di ModuleFeatureRegistry atau RouteOwnership:\n" + orphans.joinToString("\n"))
        }
    }
}
