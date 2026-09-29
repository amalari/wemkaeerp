package com.eventverse.app.routes

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

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.rbac.AccessLevel.MANAGE
import com.eventverse.app.domain.rbac.AccessLevel.OPERATE
import com.eventverse.app.domain.rbac.AccessLevel.VIEW
import com.eventverse.app.domain.pack.GarmentModules.COSTING_HPP
import com.eventverse.app.domain.pack.GarmentModules.CRM_SALES
import com.eventverse.app.domain.pack.GarmentModules.DYNAMIC_RBAC
import com.eventverse.app.domain.pack.GarmentModules.FACTORY_FLOW
import com.eventverse.app.domain.pack.GarmentModules.FULFILLMENT
import com.eventverse.app.domain.pack.GarmentModules.OPERATOR_EXEC
import com.eventverse.app.domain.pack.GarmentModules.PRODUCTION_MRP
import com.eventverse.app.domain.pack.GarmentModules.QUALITY_CONTROL
import com.eventverse.app.domain.pack.GarmentModules.SAMPLING_ORDER
import com.eventverse.app.domain.pack.GarmentModules.TECH_PACK_BOM
import io.ktor.http.HttpMethod

/**
 * Kebijakan gerbang terpusat untuk route tenant yang dipakai **banyak layar** (B5). Fungsi murni
 * `(method, path) → GateRule?` sehingga bisa diuji tanpa server; `null` = tidak diatur di sini.
 *
 * Daftar pembaca diturunkan dari pemakai nyata di klien (2026-09-29):
 * - SPK sampling dibaca workspace Sampling, meja **Operator**, **QC Inspector**, dan detail **Deal** (CRM).
 * - Kartu/label SPK (telusur) juga dicetak dari detail Deal.
 * - Kerangka tahap & katalog proses dibaca semua layar SPK dan kanvas Factory Flow level 2.
 * - Work order produksi juga dibaca & diluncurkan dari detail **Deal** (CRM).
 * - Tech pack dibaca staf HPP (memilih spesifikasi yang dihitung).
 * - `billing-preview` & `customization-requests` tidak dipanggil klien: hanya admin tata kelola.
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
            path.startsWith("/api/tenant/tech-pack") ->
                if (read) GateRule(VIEW, listOf(TECH_PACK_BOM, COSTING_HPP)) else GateRule(OPERATE, listOf(TECH_PACK_BOM))
            path.startsWith("/api/tenant/production") -> when {
                path.endsWith("/telemetry") -> GateRule(VIEW, listOf(PRODUCTION_MRP, FACTORY_FLOW))
                read -> GateRule(VIEW, listOf(PRODUCTION_MRP, CRM_SALES))
                path.endsWith("/launch-from-deal") -> GateRule(OPERATE, listOf(PRODUCTION_MRP, CRM_SALES))
                else -> GateRule(OPERATE, listOf(PRODUCTION_MRP))
            }
            // Surat jalan (fitur Fulfillment) & rute/transfer internal fulfillment.
            path.startsWith("/api/tenant/transfers") || path.startsWith("/api/tenant/fulfillment") ->
                GateRule(if (read) VIEW else OPERATE, listOf(FULFILLMENT))
            // Tulis topologi alur sudah fail-closed di PipelineRoutes (Factory Flow MANAGE).
            path.startsWith("/api/tenant/pipeline") -> if (read) GateRule(VIEW, listOf(FACTORY_FLOW)) else null
            path.startsWith("/api/tenant/billing-preview") || path.startsWith("/api/tenant/customization-requests") ->
                GateRule(MANAGE, listOf(DYNAMIC_RBAC))
            else -> null
        }
    }
}
