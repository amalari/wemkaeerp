package com.eventverse.app.domain.crm

/**
 * Jalur pembuatan lead (TRD-HELP-002 K2). Disimpan di `crm_leads.created_via`.
 *
 * Uji Variabilitas: konsep sistem — sama untuk semua tenant dan industri, tidak diubah admin → enum.
 * [AI_DRAFT] berarti field awalnya diisi draf AI lalu **disimpan oleh manusia** ([CrmLead.createdByUserId]);
 * AI tidak pernah menyimpan lead. Nilai tersimpan yang tidak dikenal ditolak ([fromCode] → null), bukan
 * dianggap MANUAL diam-diam.
 */
enum class LeadCreationChannel {
    MANUAL,
    AI_DRAFT;

    companion object {
        fun fromCode(code: String): LeadCreationChannel? = entries.firstOrNull { it.name == code }
    }
}
