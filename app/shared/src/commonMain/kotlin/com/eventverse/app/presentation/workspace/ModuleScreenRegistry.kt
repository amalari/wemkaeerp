package com.eventverse.app.presentation.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.crm.CrmWorkspaceScreen
import com.eventverse.app.presentation.masterdata.MasterDataWorkspaceScreen
import com.eventverse.app.presentation.sampling.SamplingWorkspaceScreen
import com.eventverse.app.presentation.techpack.TechPackWorkspaceScreen
import com.eventverse.app.presentation.vendor.VendorContactsWorkspaceScreen

/** Yang dibutuhkan layar kerja modul: tenant, keputusan wewenang, persona, dan modifier ukuran penuh. */
internal data class ModuleScreenContext(
    val tenantSlug: String,
    val decision: AccessDecision,
    val persona: TestingPersona?,
    val modifier: Modifier
)

/**
 * Layar khusus per modul (B6e, TRD-PLAT-001 FR-5). Menambah layar = satu entri di sini; modul tanpa entri memakai
 * layar kerja generik. Kunci = id modul pack, jadi modul pack lain tidak butuh perubahan kode untuk bisa dibuka.
 */
internal object ModuleScreenRegistry {
    val screens: Map<BusinessModule, @Composable (ModuleScreenContext) -> Unit> = mapOf(
        GarmentModules.CRM_SALES to { c -> CrmWorkspaceScreen(tenantSlug = c.tenantSlug, access = c.decision.config, modifier = c.modifier) },
        GarmentModules.SAMPLING_ORDER to { c -> SamplingWorkspaceScreen(tenantSlug = c.tenantSlug, decision = c.decision, persona = c.persona, modifier = c.modifier) },
        GarmentModules.MASTER_DATA to { c -> MasterDataWorkspaceScreen(tenantSlug = c.tenantSlug, decision = c.decision, persona = c.persona, modifier = c.modifier) },
        GarmentModules.VENDOR_CONTACTS to { c -> VendorContactsWorkspaceScreen(tenantSlug = c.tenantSlug, decision = c.decision, modifier = c.modifier) },
        GarmentModules.TECH_PACK_BOM to { c -> TechPackWorkspaceScreen(tenantSlug = c.tenantSlug, decision = c.decision, persona = c.persona, modifier = c.modifier) },
        GarmentModules.INVOICING to { c -> com.eventverse.app.presentation.invoicing.InvoiceWorkspaceScreen(tenantSlug = c.tenantSlug, access = c.decision.config, modifier = c.modifier) },
        GarmentModules.COSTING_HPP to { c -> com.eventverse.app.presentation.costing.CostingWorkspaceScreen(tenantSlug = c.tenantSlug, decision = c.decision, persona = c.persona, modifier = c.modifier) },
        GarmentModules.OPERATOR_EXEC to { c -> com.eventverse.app.presentation.operator.OperatorFloorWorkspaceScreen(tenantSlug = c.tenantSlug, decision = c.decision, persona = c.persona, modifier = c.modifier) },
        GarmentModules.QUALITY_CONTROL to { c -> com.eventverse.app.presentation.qc.QcInspectorWorkspaceScreen(tenantSlug = c.tenantSlug, decision = c.decision, persona = c.persona, modifier = c.modifier) },
        GarmentModules.PRODUCTION_MRP to { c -> com.eventverse.app.presentation.production.ProductionWorkspaceScreen(tenantSlug = c.tenantSlug, decision = c.decision, persona = c.persona, modifier = c.modifier) }
    )
}
