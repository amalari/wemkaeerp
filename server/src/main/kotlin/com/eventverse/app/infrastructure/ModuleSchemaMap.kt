package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ModuleId

/**
 * Batas modul di database (B8, TRD-PLAT-001 bagian B8): **satu PostgreSQL schema per modul**, bernama persis kode
 * modulnya (`crm_sales`). Fitur tinggal di schema modul induknya (washing → `operator_exec`, surat jalan →
 * `fulfillment`), sama dengan kepemilikan route di `RouteOwnership`/`ModuleFeatureRegistry`.
 *
 * Semua tetap satu database, jadi foreign key, transaksi, JOIN lintas modul, dan RLS tidak berubah. Schema hanya
 * memisahkan namespace.
 *
 * Daftar ini sumber kebenaran `ModuleSchemaOwnershipTest`: tabel baru yang tidak terdaftar di sini atau di [platform]
 * menggagalkan test. Modul hasil pack data (AI) mengikuti aturan yang sama: `klinik_antrean.*`.
 */
object ModuleSchemaMap {

    /** Tabel platform: tetap di `public`. Dirujuk hampir semua modul (tenants, users) atau dipakai bersama. */
    val platform: Set<String> = setOf(
        "tenants", "users", "domain_packs", "tenant_module_entitlements", "audit_logs",
        "custom_field_definitions", "custom_field_links", "module_catalog_entries", "module_customization_requests",
        "flyway_schema_history"
    )

    /**
     * Schema platform di luar modul (PLAN-builder-console M0): `builder` milik konsol WeMake Builder.
     * Bukan `BusinessModule` (governance-type), jadi tidak masuk [byModule] — tetapi tabel ber-`tenant_id`
     * di sini wajib RLS & grant yang sama, dan ikut dijaga `ModuleSchemaOwnershipTest`.
     */
    val platformSchemas: Map<String, Set<String>> = mapOf(
        "builder" to setOf("deployments", "conversations", "chat_messages", "build_requests")
    )

    val byModule: Map<ModuleId, Set<String>> = mapOf(
        GarmentModules.ORG_CHART to setOf("departments", "employees"),
        GarmentModules.DYNAMIC_RBAC to setOf("custom_roles", "department_module_assignments"),
        GarmentModules.FACTORY_FLOW to setOf("tenant_pipelines", "tenant_locations", "tenant_location_settings", "tenant_flow_node_locations"),
        GarmentModules.SAMPLING_ORDER to setOf(
            "sampling_orders", "sampling_knit_specs", "sampling_size_charts", "sampling_machine_programs",
            "sampling_yield_timings", "sampling_milestones", "sampling_finishing_deposits", "sampling_qc_inspections",
            "tenant_stage_flows", "tenant_optional_processes", "tenant_stage_phase_tags", "sample_storage_records"
        ),
        GarmentModules.CRM_SALES to setOf("crm_leads", "crm_lead_activities", "crm_contacts", "deals", "deal_purchase_orders"),
        GarmentModules.MASTER_DATA to setOf("material_code_sequences", "material_items", "material_prices", "material_price_policies"),
        GarmentModules.VENDOR_CONTACTS to setOf("vendors", "vendor_assignments"),
        GarmentModules.TECH_PACK_BOM to setOf(
            "tech_pack_style_sequences", "tech_packs", "tech_pack_bom_lines", "tech_pack_labor_operations", "tech_pack_size_yields"
        ),
        GarmentModules.COSTING_HPP to setOf("costing_rate_cards", "costing_sheets", "costing_sheet_buckets", "costing_product_benchmarks"),
        GarmentModules.PRODUCTION_MRP to setOf("bulk_work_orders"),
        GarmentModules.OPERATOR_EXEC to setOf(
            "work_cards", "work_deposits", "rework_tickets", "washing_batches", "washing_batch_items", "washing_batch_sort_outputs",
            "trace_work_orders", "trace_tenant_ordinals", "trace_containers", "trace_container_panel_tallies",
            "trace_container_links", "trace_allocations"
        ),
        GarmentModules.FULFILLMENT to setOf(
            "fulfillment_transfers", "fulfillment_route_settings", "fulfillment_transfer_events", "surat_jalan_manifests", "surat_jalan_items"
        ),
        GarmentModules.INVOICING to setOf(
            "invoice_number_sequences", "invoice_issuer_profiles", "invoice_templates", "invoices", "invoice_lines", "invoice_payments"
        )
    )

    /** Nama schema modul = kode modulnya; tidak ada tabel pemetaan kedua. */
    fun schemaOf(module: ModuleId): String = module.value

    /** `schema.tabel` yang diharapkan untuk setiap tabel modul + schema platform non-modul. */
    val expectedQualified: Set<String> get() =
        byModule.flatMap { (m, tables) -> tables.map { "${schemaOf(m)}.$it" } }.toSet() +
            platformSchemas.flatMap { (schema, tables) -> tables.map { "$schema.$it" } }
}
