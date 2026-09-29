package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.AccessLevel.MANAGE
import com.eventverse.app.domain.rbac.AccessLevel.OPERATE
import com.eventverse.app.domain.rbac.AccessLevel.VIEW
import com.eventverse.app.domain.rbac.BusinessModule.CRM_SALES
import com.eventverse.app.domain.rbac.BusinessModule.OPERATOR_EXEC
import com.eventverse.app.domain.rbac.BusinessModule.PRODUCTION_MRP
import com.eventverse.app.domain.rbac.BusinessModule.QUALITY_CONTROL
import com.eventverse.app.domain.rbac.BusinessModule.SAMPLING_ORDER
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kebijakan gerbang terpusat sebagai fungsi murni: siapa boleh apa, tanpa server. */
class TenantRouteGatePolicyTest {

    private fun rule(m: HttpMethod, p: String) = TenantRouteGatePolicy.ruleFor(m, p)

    @Test
    fun spkReads_areOpenToEveryScreenThatShowsSpk() {
        val r = rule(HttpMethod.Get, "/api/tenant/sampling/orders/o1")!!
        assertEquals(VIEW, r.level)
        assertEquals(setOf(SAMPLING_ORDER, OPERATOR_EXEC, QUALITY_CONTROL, CRM_SALES), r.modules.toSet())
    }

    @Test
    fun floorActions_allowOperatorAndQc_butEditingSpkIsSamplingOnly() {
        listOf("/work/start", "/work/release", "/finishing/deposits", "/qc/inspect", "/store", "/release", "/stage", "/rework").forEach { a ->
            val r = rule(HttpMethod.Post, "/api/tenant/sampling/orders/o1$a")!!
            assertEquals(OPERATE, r.level, a)
            assertTrue(OPERATOR_EXEC in r.modules && QUALITY_CONTROL in r.modules && CRM_SALES !in r.modules, a)
        }
        listOf("" to HttpMethod.Put, "/technical-spec" to HttpMethod.Put, "/revision" to HttpMethod.Post, "/approve" to HttpMethod.Post).forEach { (a, m) ->
            assertEquals(listOf(SAMPLING_ORDER), rule(m, "/api/tenant/sampling/orders/o1$a")!!.modules, a)
        }
        assertEquals(listOf(SAMPLING_ORDER), rule(HttpMethod.Post, "/api/tenant/sampling/orders")!!.modules, "membuat SPK")
    }

    @Test
    fun processCatalogWrites_needSamplingManage_andStageFlowWritesAreLeftToTheirOwnFailClosedGuard() {
        assertEquals(MANAGE, rule(HttpMethod.Post, "/api/tenant/process-catalog")!!.level)
        assertEquals(MANAGE, rule(HttpMethod.Put, "/api/tenant/process-catalog/phase-tags")!!.level)
        assertNull(rule(HttpMethod.Post, "/api/tenant/stage-flow/stages"))
    }

    @Test
    fun workQueue_isProductionFloorOnly_andUnrelatedPathsAreNotGovernedHere() {
        assertEquals(setOf(OPERATOR_EXEC, PRODUCTION_MRP), rule(HttpMethod.Post, "/api/tenant/work-queue/cards/output")!!.modules.toSet())
        assertNull(rule(HttpMethod.Get, "/api/tenant/invoicing"))
        assertNull(rule(HttpMethod.Get, "/api/tenant/samplingx"), "prefix mirip bukan milik SPK")
    }
}
