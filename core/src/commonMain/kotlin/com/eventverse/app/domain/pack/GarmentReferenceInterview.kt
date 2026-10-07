package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleHandoff
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink

/**
 * Pack garment dinyatakan sebagai **satu wawancara acuan** (PLAN-iv-B B1): bukti bahwa kontrak
 * `InterviewSession` cukup ekspresif untuk vertikal yang sudah jadi, dan jangkar paritas (Strangler Fig,
 * tenant-variability Kontrak 8) — modul garment baru tanpa padanan di sini menggagalkan
 * `GarmentReferenceInterviewParityTest`.
 *
 * Ini **data khas garment**, sengaja di paket pack dan bukan di mesin wawancara. Semua tautan `CONFIRMED`:
 * acuan adalah keadaan akhir yang sah, bukan tebakan. Asal: modul operasional bawaan = `REUSE_PACK`; modul
 * tata kelola/fondasi = `REUSE_PLATFORM` (aturan `InterviewValidator`).
 */
object GarmentReferenceInterview {

    private fun div(code: String, name: String) = DivisionDraft(DivisionCode(code), name, ItemSource.GUESS)
    private fun role(key: String, label: String, division: String, head: Boolean = false) =
        RoleDraft(RoleKey(key), label, DivisionCode(division), ItemSource.GUESS, head)

    private fun link(role: String, module: ModuleId, origin: ModuleOrigin) =
        RoleModuleLink(RoleKey(role), module, origin, emptyList(), Confirmation.CONFIRMED)

    private fun handoff(from: ModuleId, to: ModuleId, port: String) = ModuleHandoff(from, to, PortType(port), Confirmation.CONFIRMED)

    val session: InterviewSession by lazy {
        val m = GarmentModules
        val pack = ModuleOrigin.REUSE_PACK
        val platform = ModuleOrigin.REUSE_PLATFORM
        InterviewSession(
            step = InterviewStep.DONE,
            divisions = listOf(
                div("manajemen", "Manajemen"), div("penjualan", "Penjualan"), div("gudang", "Gudang"),
                div("teknik", "Teknik & Biaya"), div("potong", "Potong"), div("jahit", "Jahit"),
                div("qc", "QC"), div("pengiriman", "Pengiriman"), div("keuangan", "Keuangan")
            ),
            roles = listOf(
                role("pemilik", "Pemilik", "manajemen", head = true),
                role("admin_penjualan", "Admin Penjualan", "penjualan", head = true),
                role("admin_gudang", "Admin Gudang", "gudang", head = true),
                role("staf_teknik", "Staf Teknik", "teknik", head = true),
                role("kepala_potong", "Kepala Potong", "potong", head = true),
                role("operator_jahit", "Operator Jahit", "jahit", head = true),
                role("petugas_qc", "Petugas QC", "qc", head = true),
                role("admin_pengiriman", "Admin Pengiriman", "pengiriman", head = true),
                role("staf_keuangan", "Staf Keuangan", "keuangan", head = true)
            ),
            links = listOf(
                link("pemilik", m.ORG_CHART, platform), link("pemilik", m.DYNAMIC_RBAC, platform),
                link("pemilik", m.FACTORY_FLOW, platform), link("pemilik", m.MASTER_DATA, platform),
                link("admin_penjualan", m.CRM_SALES, pack), link("admin_penjualan", m.SAMPLING_ORDER, pack),
                link("admin_penjualan", m.VENDOR_CONTACTS, platform),
                link("admin_gudang", m.INVENTORY, pack),
                link("staf_teknik", m.TECH_PACK_BOM, pack), link("staf_teknik", m.COSTING_HPP, pack),
                link("kepala_potong", m.PRODUCTION_MRP, pack),
                link("operator_jahit", m.OPERATOR_EXEC, pack),
                link("petugas_qc", m.QUALITY_CONTROL, pack),
                link("admin_pengiriman", m.FULFILLMENT, pack),
                link("staf_keuangan", m.INVOICING, platform)
            ),
            handoffs = listOf(
                handoff(m.CRM_SALES, m.PRODUCTION_MRP, "ProductionOrderDraft"),
                handoff(m.TECH_PACK_BOM, m.COSTING_HPP, "TechPackAndYieldData"),
                handoff(m.INVENTORY, m.PRODUCTION_MRP, "VerifiedMaterialStock"),
                handoff(m.PRODUCTION_MRP, m.OPERATOR_EXEC, "CutPiecesBundle"),
                handoff(m.OPERATOR_EXEC, m.QUALITY_CONTROL, "AssembledGarmentBundle"),
                handoff(m.QUALITY_CONTROL, m.FULFILLMENT, "InspectedAndGradedUnit"),
                handoff(m.FULFILLMENT, m.INVOICING, "DispatchedShipmentManifest")
            )
        )
    }
}

