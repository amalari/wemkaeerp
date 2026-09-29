package com.eventverse.app.routes

import com.eventverse.app.domain.rbac.AccessLevel.MANAGE
import com.eventverse.app.domain.rbac.AccessLevel.OPERATE
import com.eventverse.app.domain.rbac.AccessLevel.VIEW
import com.eventverse.app.domain.rbac.BusinessModule.CRM_SALES
import com.eventverse.app.domain.rbac.BusinessModule.FACTORY_FLOW
import com.eventverse.app.domain.rbac.BusinessModule.OPERATOR_EXEC
import com.eventverse.app.domain.rbac.BusinessModule.PRODUCTION_MRP
import com.eventverse.app.domain.rbac.BusinessModule.QUALITY_CONTROL
import com.eventverse.app.domain.rbac.BusinessModule.SAMPLING_ORDER
import io.ktor.http.HttpMethod

/**
 * Kebijakan gerbang terpusat untuk route tenant yang dipakai **banyak layar** (B5). Fungsi murni
 * `(method, path) → GateRule?` sehingga bisa diuji tanpa server; `null` = tidak diatur di sini.
 *
 * Daftar pembaca diturunkan dari pemakai nyata di klien (2026-09-29):
 * - SPK sampling dibaca workspace Sampling, meja **Operator**, **QC Inspector**, dan detail **Deal** (CRM).
 * - Kartu/label SPK (telusur) juga dicetak dari detail Deal.
 * - Kerangka tahap & katalog proses dibaca semua layar SPK dan kanvas Factory Flow level 2.
 */
internal object TenantRouteGatePolicy {

    private val SPK_READERS = listOf(SAMPLING_ORDER, OPERATOR_EXEC, QUALITY_CONTROL, CRM_SALES)
    private val FLOOR_WORKERS = listOf(SAMPLING_ORDER, OPERATOR_EXEC, QUALITY_CONTROL)
    private val PRODUCTION_FLOOR = listOf(OPERATOR_EXEC, PRODUCTION_MRP)

    /** Aksi lantai pada SPK — dikerjakan operator/QC di meja, bukan admin sampling. */
    private val FLOOR_ACTIONS = listOf("/work/start", "/work/release", "/finishing/", "/qc/inspect", "/store", "/release", "/stage", "/rework")

    fun ruleFor(method: HttpMethod, path: String): GateRule? {
        val read = method == HttpMethod.Get || method == HttpMethod.Head
        return when {
            path.startsWith("/api/tenant/sampling/orders") -> when {
                read -> GateRule(VIEW, SPK_READERS)
                FLOOR_ACTIONS.any { path.contains(it) } -> GateRule(OPERATE, FLOOR_WORKERS)
                else -> GateRule(OPERATE, listOf(SAMPLING_ORDER))
            }
            path.startsWith("/api/tenant/process-catalog") ->
                if (read) GateRule(VIEW, SPK_READERS + FACTORY_FLOW) else GateRule(MANAGE, listOf(SAMPLING_ORDER))
            // Tulis kerangka tahap sudah fail-closed di TenantStageFlowRoutes (Factory Flow MANAGE).
            path.startsWith("/api/tenant/stage-flow") -> if (read) GateRule(VIEW, SPK_READERS + FACTORY_FLOW) else null
            path.startsWith("/api/tenant/traceability") ->
                if (read) GateRule(VIEW, listOf(OPERATOR_EXEC, SAMPLING_ORDER, PRODUCTION_MRP, QUALITY_CONTROL, CRM_SALES))
                else GateRule(OPERATE, listOf(OPERATOR_EXEC, SAMPLING_ORDER, PRODUCTION_MRP))
            path.startsWith("/api/tenant/work-queue") -> GateRule(if (read) VIEW else OPERATE, PRODUCTION_FLOOR)
            else -> null
        }
    }
}
