package com.eventverse.app.domain.masterdata

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import kotlin.jvm.JvmInline

@JvmInline
value class MaterialId(val value: String) {
    init {
        require(value.isNotBlank()) { "MaterialId cannot be blank" }
        require(value.length <= 64) { "MaterialId must be at most 64 characters" }
    }
}

@JvmInline
value class MaterialCode(val value: String) {
    init {
        require(value.isNotBlank()) { "MaterialCode cannot be blank" }
        require(value.length <= 32) { "MaterialCode must be at most 32 characters" }
        require(CODE_REGEX.matches(value)) {
            "MaterialCode '$value' tidak valid. Harus huruf besar/angka, diawali alfanumerik, hanya boleh '-', '/': e.g. YRN-001"
        }
    }

    companion object {
        private val CODE_REGEX = Regex("^[A-Z0-9][A-Z0-9\\-\\/]*$")

        fun normalize(raw: String): MaterialCode =
            MaterialCode(raw.trim().uppercase().replace(Regex("[^A-Z0-9\\-\\/]"), "-"))
    }
}

@JvmInline
value class MaterialPriceId(val value: String) {
    init {
        require(value.isNotBlank()) { "MaterialPriceId cannot be blank" }
        require(value.length <= 64) { "MaterialPriceId must be at most 64 characters" }
    }
}

enum class MaterialCategory(
    val code: String,
    val displayName: String,
    val defaultUom: UnitOfMeasure,
    val codePrefix: String
) {
    YARN("yarn", "Benang Rajut / Jahit", UnitOfMeasure.KILOGRAM, "YRN"),
    FABRIC("fabric", "Kain / Bahan Utama", UnitOfMeasure.KILOGRAM, "FAB"),
    TRIM("trim", "Trim & Kancing / Zipper", UnitOfMeasure.PIECE, "TRM"),
    ACCESSORY("accessory", "Aksesoris / Label / Hangtag", UnitOfMeasure.PIECE, "ACC"),
    PACKAGING("packaging", "Kemasan & Polybag / Dus", UnitOfMeasure.PIECE, "PKG"),
    CHEMICAL("chemical", "Bahan Kimia / Pewarna", UnitOfMeasure.KILOGRAM, "CHM"),
    SUBCON_SERVICE("subcon_service", "Jasa Subkon Eksternal", UnitOfMeasure.PIECE, "SVC");

    companion object {
        fun fromCode(code: String?): MaterialCategory? {
            if (code == null) return null
            val normalized = code.trim().lowercase()
            return entries.firstOrNull {
                it.code == normalized || it.name.equals(normalized, ignoreCase = true)
            }
        }
    }
}

/**
 * Material-specific conversion factor for packaging units (e.g. 1 cone = 1.2 kg).
 */
data class UomConversion(
    val from: UnitOfMeasure,
    val equivalent: Quantity
) {
    init {
        require(from != equivalent.uom) { "Satuan asal dan tujuan konversi tidak boleh sama: $from" }
        require(equivalent.isPositive) { "Nilai konversi ekuivalen harus positif: ${equivalent.formatted()}" }
    }
}

data class MaterialSummary(
    val id: MaterialId,
    val code: MaterialCode,
    val name: String,
    val category: MaterialCategory,
    val baseUom: UnitOfMeasure,
    val defaultOwnership: StockOwnershipSemantics
)
