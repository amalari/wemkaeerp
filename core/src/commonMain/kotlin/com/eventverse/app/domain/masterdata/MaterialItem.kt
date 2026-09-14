package com.eventverse.app.domain.masterdata

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/**
 * Core material aggregate for WeMade ERP.
 * Defines physical identity, packaging conversions, ownership semantics, and custom tenant attributes.
 */
data class MaterialItem(
    val id: MaterialId,
    val tenantId: TenantId,
    val code: MaterialCode,
    val name: String,
    val category: MaterialCategory,
    val baseUom: UnitOfMeasure,
    val alternateUoms: List<UomConversion> = emptyList(),
    val defaultOwnership: StockOwnershipSemantics = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
    val description: String = "",
    val customAttributes: CustomAttributes = CustomAttributes.EMPTY,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null
) {
    init {
        require(name.isNotBlank()) { "Nama material tidak boleh kosong" }
        require(!baseUom.isPackagingUnit) {
            "Satuan dasar tidak boleh satuan kemasan (${baseUom.displayName}); gunakan konversi alternatif."
        }
        require(alternateUoms.distinctBy { it.from }.size == alternateUoms.size) {
            "Duplikasi satuan konversi kemasan ditemukan pada material '$name'"
        }
    }

    val isArchived: Boolean get() = archivedAt != null

    fun rename(newName: String, now: Instant): MaterialItem {
        require(newName.isNotBlank()) { "Nama material baru tidak boleh kosong" }
        return copy(name = newName.trim(), updatedAt = now)
    }

    fun describe(newDescription: String, now: Instant): MaterialItem =
        copy(description = newDescription.trim(), updatedAt = now)

    fun reclassify(newCategory: MaterialCategory, now: Instant, hasPriceHistory: Boolean): Result<MaterialItem> {
        if (hasPriceHistory && newCategory.defaultUom.dimension != category.defaultUom.dimension) {
            return Result.failure(
                IllegalStateException(
                    "Tidak dapat mengubah kategori '${category.displayName}' ke '${newCategory.displayName}' karena material sudah memiliki riwayat harga acuan dengan dimensi berbeda."
                )
            )
        }
        return Result.success(copy(category = newCategory, updatedAt = now))
    }

    fun defineConversion(conversion: UomConversion, now: Instant): MaterialItem {
        require(conversion.equivalent.uom.canConvertTo(baseUom)) {
            "Satuan ekuivalen konversi (${conversion.equivalent.uom.displayName}) harus kompatibel dengan satuan dasar material (${baseUom.displayName})"
        }
        val filtered = alternateUoms.filterNot { it.from == conversion.from }
        return copy(alternateUoms = filtered + conversion, updatedAt = now)
    }

    fun removeConversion(fromUom: UnitOfMeasure, now: Instant): MaterialItem {
        val filtered = alternateUoms.filterNot { it.from == fromUom }
        return copy(alternateUoms = filtered, updatedAt = now)
    }

    /**
     * Converts [quantity] to [targetUom] evaluating:
     * 1. Identity conversion
     * 2. Global physics (e.g. gram <-> kg)
     * 3. Item-specific packaging conversions (e.g. 1 cone = 1.2 kg)
     */
    fun convert(quantity: Quantity, targetUom: UnitOfMeasure): Quantity {
        if (quantity.uom == targetUom) return quantity

        // 1. Global physical conversion
        if (quantity.uom.canConvertTo(targetUom)) {
            return quantity.convertTo(targetUom)
        }

        // 2. Converting from packaging unit to target
        val fromConv = alternateUoms.firstOrNull { it.from == quantity.uom }
        if (fromConv != null) {
            // quantity is in packaging unit (e.g. 3 cones).
            // 1 cone = 1_000_000 micros. fromConv.equivalent is in base/convertible UoM.
            val equivMicros = (quantity.micros * fromConv.equivalent.micros) / 1_000_000L
            val inEquivQty = Quantity(equivMicros, fromConv.equivalent.uom)
            return inEquivQty.convertTo(targetUom)
        }

        // 3. Converting from target packaging unit
        val toConv = alternateUoms.firstOrNull { it.from == targetUom }
        if (toConv != null) {
            val inEquivQty = quantity.convertTo(toConv.equivalent.uom)
            val packagingMicros = (inEquivQty.micros * 1_000_000L) / toConv.equivalent.micros
            return Quantity(packagingMicros, targetUom)
        }

        throw IllegalArgumentException(
            "Tidak dapat mengonversi ${quantity.uom.displayName} ke ${targetUom.displayName} untuk material '$name'"
        )
    }

    fun toBaseUom(quantity: Quantity): Quantity = convert(quantity, baseUom)

    fun withOwnership(ownership: StockOwnershipSemantics, now: Instant): MaterialItem =
        copy(defaultOwnership = ownership, updatedAt = now)

    fun withCustomAttributes(attributes: CustomAttributes, now: Instant): MaterialItem =
        copy(customAttributes = attributes, updatedAt = now)

    fun archive(now: Instant): MaterialItem =
        copy(archivedAt = now, updatedAt = now)

    fun restore(now: Instant): MaterialItem =
        copy(archivedAt = null, updatedAt = now)

    fun summary(): MaterialSummary =
        MaterialSummary(id, code, name, category, baseUom, defaultOwnership)
}
