package com.eventverse.app.domain.prospect

import kotlin.jvm.JvmInline

@JvmInline
value class ProspectLeadId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProspectLeadId cannot be blank" }
        require(value.length <= 64) { "ProspectLeadId must be at most 64 characters" }
    }
}

@JvmInline
value class FlowTranslationId(val value: String) {
    init {
        require(value.isNotBlank()) { "FlowTranslationId cannot be blank" }
        require(value.length <= 64) { "FlowTranslationId must be at most 64 characters" }
    }
}

@JvmInline
value class ProspectPriceEstimateId(val value: String) {
    init {
        require(value.isNotBlank()) { "ProspectPriceEstimateId cannot be blank" }
        require(value.length <= 64) { "ProspectPriceEstimateId must be at most 64 characters" }
    }
}

enum class LeadSource(val code: String) {
    LANDING_PAGE("LANDING_PAGE"),
    SALES_INPUT("SALES_INPUT"),
    REFERRAL("REFERRAL");

    companion object {
        fun fromCode(code: String?): LeadSource? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Where a prospect sits in the funnel.
 *
 * [NEEDS_REVIEW] is a first-class state rather than an error: it is where a lead lands whenever the
 * ledger cannot price every gap, which — until enough builds have logged hours — will be most of
 * them. The feature degrades into a lead-capture form rather than into a wrong number.
 */
enum class LeadStatus(val code: String) {
    SUBMITTED("SUBMITTED"),
    TRANSLATED("TRANSLATED"),
    NEEDS_REVIEW("NEEDS_REVIEW"),
    QUOTED("QUOTED"),
    CONVERTED("CONVERTED"),
    REJECTED("REJECTED");

    val isOpen: Boolean get() = this != CONVERTED && this != REJECTED

    companion object {
        fun fromCode(code: String?): LeadStatus? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Whether a capability the prospect described is already sellable or has to be built.
 */
enum class CoverageKind(val code: String) {
    COVERED("COVERED"),
    GAP("GAP");

    companion object {
        fun fromCode(code: String?): CoverageKind? = entries.firstOrNull { it.code == code }
    }
}
