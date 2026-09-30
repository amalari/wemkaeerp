package com.eventverse.app.presentation.crm.leaddraft

import com.eventverse.app.domain.crm.prefill.DraftIssue
import com.eventverse.app.domain.crm.prefill.LeadDraft

/** State bagian "Isi dengan AI" di dialog lead (TRD-HELP-002 FR-5). */
data class LeadDraftUiState(
    /** `null` = pengaturan belum termuat; bagian AI disembunyikan sampai jelas. */
    val enabled: Boolean? = null,
    val canManage: Boolean = false,
    val text: String = "",
    val isExtracting: Boolean = false,
    val issues: List<DraftIssue> = emptyList(),
    val partial: Boolean = false,
    val error: String? = null,
) {
    val canExtract: Boolean get() = enabled == true && text.isNotBlank() && !isExtracting
}

sealed interface LeadDraftUiEvent {
    data object Load : LeadDraftUiEvent
    data class UpdateText(val text: String) : LeadDraftUiEvent
    data object Extract : LeadDraftUiEvent
    /** Ekstrak begitu pengaturan termuat dan fitur aktif; tidak melakukan apa pun bila fitur mati. */
    data object ExtractWhenReady : LeadDraftUiEvent
    data object Enable : LeadDraftUiEvent
}

sealed interface LeadDraftUiEffect {
    /** Draf siap diterapkan ke form — sekali jalan, supaya koreksi user tidak tertimpa ulang. */
    data class Apply(val draft: LeadDraft) : LeadDraftUiEffect
}
