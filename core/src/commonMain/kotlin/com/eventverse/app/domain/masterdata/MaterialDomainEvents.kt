package com.eventverse.app.domain.masterdata

import com.eventverse.app.domain.common.DomainEvent
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

data class MaterialItemCreated(
    val tenantId: TenantId,
    val materialId: MaterialId,
    val code: MaterialCode,
    val name: String,
    val category: MaterialCategory,
    val occurredAt: Instant
) : DomainEvent

data class MaterialItemRenamed(
    val tenantId: TenantId,
    val materialId: MaterialId,
    val oldName: String,
    val newName: String,
    val occurredAt: Instant
) : DomainEvent

data class MaterialStandardPriceSet(
    val tenantId: TenantId,
    val materialId: MaterialId,
    val unitPrice: UnitPrice,
    val effectiveFrom: Instant,
    val occurredAt: Instant
) : DomainEvent

data class MaterialItemArchived(
    val tenantId: TenantId,
    val materialId: MaterialId,
    val occurredAt: Instant
) : DomainEvent
