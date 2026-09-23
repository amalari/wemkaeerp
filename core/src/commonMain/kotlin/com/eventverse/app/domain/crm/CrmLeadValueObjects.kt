package com.eventverse.app.domain.crm

import kotlin.jvm.JvmInline

@JvmInline
value class LeadId(val value: String) {
    init {
        require(value.isNotBlank()) { "LeadId cannot be blank" }
        require(value.length <= 64) { "LeadId must be at most 64 characters" }
    }
}

@JvmInline
value class BrandName(val value: String = "") {
    init {
        require(value.length <= 150) { "BrandName must be at most 150 characters" }
    }

    val isBlank: Boolean get() = value.isBlank()

    fun display(fallback: String = "Tanpa Nama Brand"): String =
        if (value.isBlank()) fallback else value
}

/**
 * A Phone/WhatsApp number, always normalised to E.164 without a leading `+`
 * (e.g. `"6281234567890"`), so the server and every client build the exact same
 * `https://wa.me/{value}` deep link with no ad-hoc normalisation anywhere else.
 *
 * Accepts Indonesian mobile formats (`08...`, `+628...`, `628...`) with standard length (10-13 digits for 08...).
 */
@JvmInline
value class WhatsappNumber(val value: String) {
    init {
        require(DIGITS_ONLY_REGEX.matches(value)) {
            "WhatsappNumber must be normalised digits only (E.164 without '+'): $value"
        }
        require(value.length in 8..15) { "WhatsappNumber must be 8-15 digits: $value" }
    }

    val waLink: String get() = "https://wa.me/$value"
    val normalizedNumber: String get() = value
    val localDisplay: String get() = if (value.startsWith("62")) "0" + value.removePrefix("62") else value

    companion object {
        private val DIGITS_ONLY_REGEX = Regex("^[0-9]+$")

        /**
         * Parses a locally-formatted Indonesian phone number into E.164. Returns null for input
         * that cannot be confidently normalised, rather than storing something malformed.
         * Valid Indonesian mobile numbers have 10-13 digits starting with 08 (or 11-14 digits starting with 628).
         */
        fun parse(raw: String): WhatsappNumber? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null
            val digits = trimmed.filter { it.isDigit() || it == '+' }
            val normalised = when {
                digits.startsWith("+62") -> digits.removePrefix("+")
                digits.startsWith("62") -> digits
                digits.startsWith("0") -> "62" + digits.removePrefix("0")
                else -> return null
            }
            // Must start with Indonesian mobile prefix 628 and have valid length (628 + 8..11 digits = 11..14 total)
            if (!normalised.startsWith("628")) return null
            if (normalised.length !in 11..14) return null
            return runCatching { WhatsappNumber(normalised) }.getOrNull()
        }

        fun isValidIndonesianPhone(raw: String): Boolean = parse(raw) != null
    }
}

/**
 * Business-defined pipeline stage for a convection factory's sales process.
 *
 * A fixed enum, not a tenant-configurable stage table: configurable stages need transition
 * rules, value migration and a reporting impact that lands with kanban in a later phase.
 * These six values were chosen to match the actual convection sales flow (inquiry -> tech
 * pack/spec discussion -> quotation -> sample -> DP confirmed/SPK issued -> lost).
 */
enum class LeadStage(val displayName: String) {
    NEW_LEAD("New Lead"),
    /** Sudah dihubungi sales, sedang digali kebutuhannya — belum cukup jelas untuk dikualifikasi. */
    FOLLOW_UP("Follow Up"),
    QUALIFIED("Qualified Lead"),
    UNQUALIFIED("Unqualified");

    /**
     * Whether this stage may transition to [target].
     * In a Kanban board, leads can be moved freely between stages (e.g. qualify, unqualify, or reopen).
     */
    fun canTransitionTo(target: LeadStage): Boolean = this != target

    companion object {
        fun fromCode(code: String?): LeadStage? = when (code) {
            "NEW_LEAD", "INQUIRY" -> NEW_LEAD
            "FOLLOW_UP", "CONTACTED", "IN_PROGRESS" -> FOLLOW_UP
            "QUALIFIED", "TECHPACK_SPEC", "QUOTATION_SENT", "SAMPLE_APPROVAL", "DEAL_DP_CONFIRMED" -> QUALIFIED
            "UNQUALIFIED", "LOST" -> UNQUALIFIED
            else -> entries.firstOrNull { it.name == code }
        }
    }
}

/** Free-text-ish but bounded source dimension, used for pipeline-by-source reporting. */
@JvmInline
value class LeadSource(val value: String) {
    init {
        require(value.length <= 50) { "LeadSource must be at most 50 characters" }
    }

    companion object {
        val UNSPECIFIED = LeadSource("")
    }
}

/** Kategori pakaian / arketipe produk garmen (misal: Kaos / Polo, Kemeja Drill, Jaket Fleece, Seragam). */
@JvmInline
value class ProductCategory(val value: String) {
    init {
        require(value.length <= 100) { "ProductCategory must be at most 100 characters" }
    }

    companion object {
        val EMPTY = ProductCategory("")
    }
}

/** Ringkasan metrik performa eksekutif KPI Sales CRM. */
data class CrmLeadKpiMetrics(
    val totalPipelineValue: Long = 0L,
    val activeLeadsCount: Int = 0,
    val qualifiedConversionRate: Double = 0.0,
    val followUpNeededCount: Int = 0
)

