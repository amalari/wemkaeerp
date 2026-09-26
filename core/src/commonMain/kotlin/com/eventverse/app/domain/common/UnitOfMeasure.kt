package com.eventverse.app.domain.common

/**
 * Physical dimension of measurement.
 */
enum class MeasureDimension(val displayName: String) {
    MASS("Massa / Berat"),
    LENGTH("Panjang / Dimensi"),
    COUNT("Hitungan / Satuan"),
    TIME("Waktu");
}

/**
 * Unit of Measure across all production, sampling, master data, and costing bounded contexts.
 *
 * Packaging units (CONE, ROLL, BALE, BOX) have [microsInBase] = 0L because their physical equivalent
 * (e.g. 1 cone = 1.2 kg) is material-specific data, not a universal physical constant.
 */
enum class UnitOfMeasure(
    val code: String,
    val displayName: String,
    val dimension: MeasureDimension,
    val microsInBase: Long
) {
    // ── MASS (Base: GRAM) ────────────────────────────────────────────────────
    GRAM("g", "Gram", MeasureDimension.MASS, 1_000_000L),
    KILOGRAM("kg", "Kilogram", MeasureDimension.MASS, 1_000_000_000L),

    // ── LENGTH (Base: MILLIMETER) ────────────────────────────────────────────
    MILLIMETER("mm", "Milimeter", MeasureDimension.LENGTH, 1_000_000L),
    CENTIMETER("cm", "Sentimeter", MeasureDimension.LENGTH, 10_000_000L),
    METER("m", "Meter", MeasureDimension.LENGTH, 1_000_000_000L),
    YARD("yd", "Yard", MeasureDimension.LENGTH, 914_400_000L),
    INCH("in", "Inci", MeasureDimension.LENGTH, 25_400_000L),

    // ── COUNT (Base: PIECE) ──────────────────────────────────────────────────
    PIECE("pcs", "Pieces / Helai", MeasureDimension.COUNT, 1_000_000L),
    LUSIN("dz", "Lusin (12 pcs)", MeasureDimension.COUNT, 12_000_000L),
    GROSS("grs", "Gross (144 pcs)", MeasureDimension.COUNT, 144_000_000L),
    SET("set", "Set", MeasureDimension.COUNT, 1_000_000L),

    // ── TIME (Base: MINUTE) ──────────────────────────────────────────────────
    MINUTE("min", "Menit", MeasureDimension.TIME, 1_000_000L),
    HOUR("hr", "Jam", MeasureDimension.TIME, 60_000_000L),

    // ── PACKAGING UNITS (Faktor konversi spesifik per-material, microsInBase = 0)
    CONE("cone", "Cone Benang", MeasureDimension.COUNT, 0L),
    ROLL("roll", "Roll Kain", MeasureDimension.COUNT, 0L),
    BALE("bale", "Bale Benang", MeasureDimension.COUNT, 0L),
    BOX("box", "Box / Dus", MeasureDimension.COUNT, 0L);

    val isPackagingUnit: Boolean get() = microsInBase == 0L

    fun canConvertTo(other: UnitOfMeasure): Boolean {
        if (this == other) return true
        if (isPackagingUnit || other.isPackagingUnit) return false
        return dimension == other.dimension
    }

    fun toBaseMicros(amountMicros: Long): Long {
        require(!isPackagingUnit) { "Satuan kemasan '$name' tidak memiliki konversi global ke satuan dasar" }
        return (amountMicros * microsInBase) / 1_000_000L
    }

    fun fromBaseMicros(baseMicros: Long): Long {
        require(!isPackagingUnit) { "Satuan kemasan '$name' tidak memiliki konversi global dari satuan dasar" }
        return (baseMicros * 1_000_000L) / microsInBase
    }

    companion object {
        fun fromCode(code: String?): UnitOfMeasure? {
            if (code == null) return null
            val normalized = code.trim().lowercase()
            return entries.firstOrNull {
                it.code.equals(normalized, ignoreCase = true) || it.name.equals(normalized, ignoreCase = true)
            }
        }
    }
}
