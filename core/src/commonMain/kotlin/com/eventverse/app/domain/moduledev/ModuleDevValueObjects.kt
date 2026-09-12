package com.eventverse.app.domain.moduledev

import kotlin.jvm.JvmInline
import kotlin.math.roundToLong

// ==============================================================================
// Identities
// ==============================================================================

@JvmInline
value class ModuleCatalogEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "ModuleCatalogEntryId cannot be blank" }
        require(value.length <= 64) { "ModuleCatalogEntryId must be at most 64 characters" }
    }
}

@JvmInline
value class ModuleBuildId(val value: String) {
    init {
        require(value.isNotBlank()) { "ModuleBuildId cannot be blank" }
        require(value.length <= 64) { "ModuleBuildId must be at most 64 characters" }
    }
}

@JvmInline
value class EffortEntryId(val value: String) {
    init {
        require(value.isNotBlank()) { "EffortEntryId cannot be blank" }
        require(value.length <= 64) { "EffortEntryId must be at most 64 characters" }
    }
}

@JvmInline
value class QuoteId(val value: String) {
    init {
        require(value.isNotBlank()) { "QuoteId cannot be blank" }
        require(value.length <= 64) { "QuoteId must be at most 64 characters" }
    }
}

@JvmInline
value class CustomizationRequestId(val value: String) {
    init {
        require(value.isNotBlank()) { "CustomizationRequestId cannot be blank" }
        require(value.length <= 64) { "CustomizationRequestId must be at most 64 characters" }
    }
}

// ==============================================================================
// Money
// ==============================================================================

/**
 * Indonesian rupiah in whole units.
 *
 * [Long] rather than [Double] because these values are multiplied by percentages and then
 * billed monthly for years: floating point drift is not a rounding curiosity here, it is a
 * discrepancy on an invoice. Rupiah has no operational sub-unit, so no minor unit is modelled.
 */
@JvmInline
value class MoneyIdr(val amount: Long) {
    init {
        require(amount >= 0) { "MoneyIdr cannot be negative: $amount" }
    }

    operator fun plus(other: MoneyIdr): MoneyIdr = MoneyIdr(amount + other.amount)

    operator fun times(multiplier: Double): MoneyIdr {
        require(multiplier >= 0) { "Cannot multiply money by a negative factor: $multiplier" }
        return MoneyIdr((amount * multiplier).roundToLong())
    }

    /**
     * Integer division, rounded to the nearest rupiah. Used for spreading a build cost across
     * amortisation months and across the tenants expected to run the module.
     */
    operator fun div(divisor: Int): MoneyIdr {
        require(divisor > 0) { "Cannot divide money by $divisor" }
        return MoneyIdr((amount.toDouble() / divisor).roundToLong())
    }

    companion object {
        val ZERO = MoneyIdr(0)

        fun sum(values: Iterable<MoneyIdr>): MoneyIdr =
            MoneyIdr(values.sumOf { it.amount })
    }
}

// ==============================================================================
// Effort
// ==============================================================================

/**
 * Work effort, always in HOURS.
 *
 * There is deliberately no day-based constructor or accessor anywhere in this package. A
 * working day here is roughly four focused hours, not eight; the moment one layer stores days
 * and another converts them at a nominal eight, every figure doubles — and because price is
 * derived as hours x rate x margin, that error is billed for the length of the contract.
 *
 * Delivery duration is a separate concept, modelled as `leadTimeDays` on the build record, and
 * the two are never converted into one another.
 */
@JvmInline
value class WorkHours(val hours: Double) {
    init {
        require(!hours.isNaN()) { "WorkHours cannot be NaN" }
        require(hours.isFinite()) { "WorkHours must be finite" }
        require(hours >= 0.0) { "WorkHours cannot be negative: $hours" }
    }

    operator fun plus(other: WorkHours): WorkHours = WorkHours(hours + other.hours)

    operator fun times(factor: Double): WorkHours {
        require(factor >= 0) { "Cannot scale hours by a negative factor: $factor" }
        return WorkHours(hours * factor)
    }

    fun costAt(hourlyRate: MoneyIdr): MoneyIdr = hourlyRate * hours

    companion object {
        val ZERO = WorkHours(0.0)

        fun sum(values: Iterable<WorkHours>): WorkHours =
            WorkHours(values.sumOf { it.hours })
    }
}

/**
 * A percentage as written by a human: `35.0` means 35%, not 0.35.
 *
 * Kept explicit because margin, maintenance and discount are all entered by people and stored
 * verbatim in quotes; silently mixing the two conventions would be invisible until a price came
 * out a hundred times too small.
 */
@JvmInline
value class Percentage(val value: Double) {
    init {
        require(!value.isNaN() && value.isFinite()) { "Percentage must be a finite number" }
        require(value >= 0.0) { "Percentage cannot be negative: $value" }
    }

    /** `35%` -> `0.35`. */
    val asFraction: Double get() = value / 100.0

    /** `35%` -> `1.35`, for marking an amount up. */
    val asMultiplier: Double get() = 1.0 + asFraction

    companion object {
        val ZERO = Percentage(0.0)
    }
}

/**
 * The size of a piece of work in weighted feature points.
 *
 * Its purpose is to make two builds comparable: a neighbour's hours are meaningless on their
 * own, but its hours *per point* transfer to work of a different size.
 */
@JvmInline
value class SizePoints(val value: Int) {
    init {
        require(value >= 0) { "SizePoints cannot be negative: $value" }
    }

    val isZero: Boolean get() = value == 0
}

// ==============================================================================
// Enumerations
// ==============================================================================

enum class BuildType(val code: String, val displayName: String) {
    NEW_MODULE("NEW_MODULE", "Modul Baru"),
    CUSTOMIZATION("CUSTOMIZATION", "Kustomisasi Tenant"),
    ENHANCEMENT("ENHANCEMENT", "Pengembangan Lanjutan"),
    REFACTOR("REFACTOR", "Refaktor Internal");

    companion object {
        fun fromCode(code: String): BuildType? = entries.firstOrNull { it.code == code }
    }
}

enum class BuildStatus(val code: String) {
    ESTIMATING("ESTIMATING"),
    APPROVED("APPROVED"),
    IN_PROGRESS("IN_PROGRESS"),
    DELIVERED("DELIVERED"),
    CANCELLED("CANCELLED");

    val isClosed: Boolean get() = this == DELIVERED || this == CANCELLED

    companion object {
        fun fromCode(code: String): BuildStatus? = entries.firstOrNull { it.code == code }
    }
}

enum class ModuleLifecycleStatus(val code: String) {
    PLANNED("PLANNED"),
    IN_DEVELOPMENT("IN_DEVELOPMENT"),
    BETA("BETA"),
    RELEASED("RELEASED"),
    DEPRECATED("DEPRECATED");

    /** Only a released module may be billed for. */
    val isBillable: Boolean get() = this == RELEASED || this == BETA

    companion object {
        fun fromCode(code: String): ModuleLifecycleStatus? = entries.firstOrNull { it.code == code }
    }
}

enum class ComplexityTier(val code: String) {
    S("S"), M("M"), L("L"), XL("XL");

    companion object {
        fun fromCode(code: String): ComplexityTier? = entries.firstOrNull { it.code == code }
    }
}

enum class EffortRole(val code: String, val displayName: String) {
    BACKEND("BACKEND", "Backend"),
    FRONTEND("FRONTEND", "Frontend"),
    DESIGN("DESIGN", "Desain"),
    QA("QA", "QA"),
    PM("PM", "Manajemen"),
    DEVOPS("DEVOPS", "DevOps");

    companion object {
        fun fromCode(code: String): EffortRole? = entries.firstOrNull { it.code == code }
    }
}

enum class BuildPhase(val code: String, val displayName: String) {
    ANALYSIS("ANALYSIS", "Analisis"),
    DESIGN("DESIGN", "Desain"),
    IMPLEMENTATION("IMPLEMENTATION", "Implementasi"),
    REVIEW("REVIEW", "Review"),
    QA("QA", "Pengujian"),
    DEPLOY("DEPLOY", "Rilis");

    companion object {
        fun fromCode(code: String): BuildPhase? = entries.firstOrNull { it.code == code }
    }
}

enum class EstimatorKind(val code: String) {
    HUMAN("HUMAN"),
    AI("AI"),
    HYBRID("HYBRID");

    companion object {
        fun fromCode(code: String): EstimatorKind? = entries.firstOrNull { it.code == code }
    }
}

/**
 * How much the retrieved neighbours justify trusting an estimate.
 *
 * [LOW] is not advisory: a use case that produces it must refuse to emit a price at all. An
 * estimate extrapolated from distant neighbours is wrong *and* confident, and the resulting
 * monthly figure is locked in for the length of the contract.
 */
enum class EstimateConfidence(val code: String) {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH");

    val permitsPricing: Boolean get() = this != LOW

    companion object {
        fun fromCode(code: String): EstimateConfidence? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Where a build's recorded hours came from.
 *
 * Only [LOGGED] rows are evidence. [RECONSTRUCTED] hours were recalled from memory, and
 * [IMPORTED] rows carry features with no hours at all. Measuring a model's accuracy against
 * reconstructed rows measures our own guesses, so the distinction has to survive in the data
 * rather than living in someone's head.
 */
enum class EffortSource(val code: String) {
    LOGGED("LOGGED"),
    RECONSTRUCTED("RECONSTRUCTED"),
    IMPORTED("IMPORTED");

    /** Whether this row may contribute a productivity ratio to future estimates. */
    val isTrainingGrade: Boolean get() = this == LOGGED

    companion object {
        fun fromCode(code: String): EffortSource? = entries.firstOrNull { it.code == code }
    }
}

enum class RequirementSource(val code: String) {
    TENANT_REQUEST("TENANT_REQUEST"),
    INTERNAL_ROADMAP("INTERNAL_ROADMAP"),
    BUGFIX("BUGFIX");

    companion object {
        fun fromCode(code: String): RequirementSource? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Experience of whoever did the work.
 *
 * Without it, recorded hours blend how big the work was with who did it, and a model has no way
 * to separate the two — it will attribute to module complexity what was really unfamiliarity.
 */
enum class BuilderExperienceLevel(val code: String) {
    JUNIOR("JUNIOR"),
    MID("MID"),
    SENIOR("SENIOR");

    companion object {
        fun fromCode(code: String): BuilderExperienceLevel? = entries.firstOrNull { it.code == code }
    }
}

enum class QuoteStatus(val code: String) {
    DRAFT("DRAFT"),
    SENT("SENT"),
    ACCEPTED("ACCEPTED"),
    REJECTED("REJECTED"),
    SUPERSEDED("SUPERSEDED");

    /** Only an accepted quote contributes to what a tenant is billed. */
    val isBillable: Boolean get() = this == ACCEPTED

    companion object {
        fun fromCode(code: String): QuoteStatus? = entries.firstOrNull { it.code == code }
    }
}

enum class CustomizationRequestStatus(val code: String) {
    SUBMITTED("SUBMITTED"),
    ESTIMATING("ESTIMATING"),
    QUOTED("QUOTED"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    IN_PROGRESS("IN_PROGRESS"),
    DELIVERED("DELIVERED");

    val isOpen: Boolean get() = this != REJECTED && this != DELIVERED

    companion object {
        fun fromCode(code: String): CustomizationRequestStatus? =
            entries.firstOrNull { it.code == code }
    }
}
