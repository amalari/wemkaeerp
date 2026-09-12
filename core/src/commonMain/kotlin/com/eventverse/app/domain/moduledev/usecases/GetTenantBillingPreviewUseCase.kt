package com.eventverse.app.domain.moduledev.usecases

import com.eventverse.app.domain.moduledev.ModuleCatalogRepository
import com.eventverse.app.domain.moduledev.ModulePricingQuoteRepository
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId

/**
 * One line on a tenant's monthly bill.
 */
data class BillingLine(
    val moduleId: String,
    val displayName: String,
    val monthlyPrice: MoneyIdr,
    val kind: Kind
) {
    enum class Kind {
        /** A catalogue module the tenant runs, at its list price. */
        SUBSCRIPTION,

        /** Bespoke work funded by this tenant, amortised over its contract. */
        CUSTOMIZATION
    }
}

/**
 * What a tenant owes per month, and why.
 */
data class TenantBillingPreview(
    val tenantId: TenantId,
    val lines: List<BillingLine>
) {
    val subscriptionTotal: MoneyIdr
        get() = MoneyIdr.sum(
            lines.filter { it.kind == BillingLine.Kind.SUBSCRIPTION }.map { it.monthlyPrice }
        )

    val customizationTotal: MoneyIdr
        get() = MoneyIdr.sum(
            lines.filter { it.kind == BillingLine.Kind.CUSTOMIZATION }.map { it.monthlyPrice }
        )

    val monthlyTotal: MoneyIdr get() = MoneyIdr.sum(lines.map { it.monthlyPrice })
}

/**
 * Builds the monthly figure from what the tenant actually runs.
 *
 * Two components, from two different places:
 *
 *  - **Subscription** — the catalogue price of every module active in the tenant's pipeline.
 *    Charging per active module rather than per plan tier means a factory using three modules does
 *    not pay what one using nine pays.
 *  - **Customization** — the accepted quotes for work built specifically for them.
 *
 * Which modules are active is read from the tenant's own pipeline (nodes that are not bypassed),
 * so no separate subscription table is needed to answer it.
 *
 * **This is a preview, not an invoice.** It prices against today's catalogue, so a list price
 * raised tomorrow would change this number for existing tenants. Real billing requires the price
 * to be locked per subscription at signup — build that record before building invoicing.
 */
class GetTenantBillingPreviewUseCase(
    private val pipelineRepository: TenantPipelineRepository,
    private val catalogRepository: ModuleCatalogRepository,
    private val quoteRepository: ModulePricingQuoteRepository
) {
    suspend operator fun invoke(tenantId: TenantId): Result<TenantBillingPreview> = runCatching {
        val pipeline = pipelineRepository.findByTenantId(tenantId)
        val activeModuleIds = pipeline?.nodes
            ?.filterNot { it.isBypassed }
            ?.map { it.moduleId }
            ?.distinct()
            .orEmpty()

        val subscriptionLines = activeModuleIds.mapNotNull { moduleId ->
            val entry = catalogRepository.findByModuleId(moduleId) ?: return@mapNotNull null
            // An unreleased or unpriced module is skipped rather than billed at zero: a module
            // silently worth nothing on an invoice is harder to notice than one that is absent.
            val price = entry.baseMonthlyPriceIdr.takeIf { entry.isBillable } ?: return@mapNotNull null
            BillingLine(
                moduleId = entry.moduleId,
                displayName = entry.displayName,
                monthlyPrice = price,
                kind = BillingLine.Kind.SUBSCRIPTION
            )
        }

        val customizationLines = quoteRepository.findAcceptedForTenant(tenantId).map { quote ->
            val entry = catalogRepository.findById(quote.catalogEntryId)
            BillingLine(
                moduleId = entry?.moduleId ?: quote.catalogEntryId.value,
                displayName = entry?.displayName ?: "Kustomisasi",
                monthlyPrice = quote.result.monthlyPrice,
                kind = BillingLine.Kind.CUSTOMIZATION
            )
        }

        TenantBillingPreview(tenantId, subscriptionLines + customizationLines)
    }
}
