package com.eventverse.app.domain.crm.prefill

import com.eventverse.app.domain.tenant.TenantId

/**
 * Opt-in per tenant untuk draf lead AI (TRD-HELP-002 K1): mati sampai admin pabrik (CRM MANAGE) menyalakannya,
 * karena fitur ini mengirim nama pelanggan ke penyedia LLM. Tidak ada baris = mati.
 */
interface CrmAiSettingsRepository {
    suspend fun isLeadDraftEnabled(tenantId: TenantId): Boolean
    suspend fun setLeadDraftEnabled(tenantId: TenantId, enabled: Boolean, updatedByUserId: String?)
}
