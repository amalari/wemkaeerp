package com.eventverse.app.domain.orgchart

import kotlin.jvm.JvmInline

@JvmInline
value class DepartmentId(val value: String) {
    init {
        require(value.isNotBlank()) { "DepartmentId cannot be blank" }
    }
}

/**
 * Color metadata for garment factory departments.
 */
data class DepartmentColor(
    val hex: Long,
    val name: String
)

/**
 * Garment factory departments for WeMade ERP.
 * Designed as a dynamic domain entity so users/tenants can define their own
 * organizational divisions or start with standard garment presets during onboarding.
 */
data class Department(
    val id: DepartmentId,
    val code: String,
    val displayName: String,
    val shortName: String,
    val colorHex: Long,
    val isCustom: Boolean = false
) {
    companion object {
        val SALES = Department(
            id = DepartmentId("dept-sales"),
            code = "sales",
            displayName = "Penjualan & CRM",
            shortName = "Sales",
            colorHex = 0xFF2563EB // Trust Blue
        )

        val PRODUCTION_PPIC = Department(
            id = DepartmentId("dept-ppic"),
            code = "production_ppic",
            displayName = "Produksi & PPIC",
            shortName = "Produksi",
            colorHex = 0xFFEA580C // Garment Orange
        )

        val WAREHOUSE = Department(
            id = DepartmentId("dept-warehouse"),
            code = "warehouse",
            displayName = "Gudang & Logistik",
            shortName = "Gudang",
            colorHex = 0xFF0D9488 // Teal
        )

        val QUALITY_CONTROL = Department(
            id = DepartmentId("dept-qc"),
            code = "qc",
            displayName = "Quality Control (QC)",
            shortName = "QC",
            colorHex = 0xFF16A34A // Emerald
        )

        val FINANCE_EXECUTIVE = Department(
            id = DepartmentId("dept-exec"),
            code = "finance_executive",
            displayName = "Keuangan & Direksi",
            shortName = "Direksi",
            colorHex = 0xFF7C3AED // Purple
        )

        /**
         * Master palette of curated, accessible factory department colors.
         */
        val AVAILABLE_COLORS = listOf(
            DepartmentColor(0xFF2563EB, "Biru Sales"),
            DepartmentColor(0xFFEA580C, "Oranye Produksi"),
            DepartmentColor(0xFF0D9488, "Teal Gudang"),
            DepartmentColor(0xFF16A34A, "Hijau QC"),
            DepartmentColor(0xFF7C3AED, "Ungu Direksi"),
            DepartmentColor(0xFFE11D48, "Rose Bordir"),
            DepartmentColor(0xFFD97706, "Amber Sablon"),
            DepartmentColor(0xFF4F46E5, "Indigo Desain"),
            DepartmentColor(0xFF0284C7, "Sky Printing"),
            DepartmentColor(0xFF059669, "Emerald Finishing"),
            DepartmentColor(0xFF9333EA, "Violet Packaging"),
            DepartmentColor(0xFFC026D3, "Fuchsia Pola"),
            DepartmentColor(0xFFDB2777, "Pink Garment"),
            DepartmentColor(0xFF475569, "Slate Maintenance"),
            DepartmentColor(0xFFB45309, "Ochre Jahit"),
            DepartmentColor(0xFF0891B2, "Cyan Cutting")
        )

        /**
         * Starter presets loaded during initial registration/onboarding demo.
         */
        fun defaultPresets(): List<Department> = listOf(
            SALES,
            PRODUCTION_PPIC,
            WAREHOUSE,
            QUALITY_CONTROL,
            FINANCE_EXECUTIVE
        )

        /**
         * Returns only the colors that have not been assigned to any existing department yet.
         * If all colors are exhausted, returns the complete palette to prevent empty selection.
         */
        fun availableColors(existingDepartments: List<Department>): List<DepartmentColor> {
            val usedHexes = existingDepartments.map { it.colorHex }.toSet()
            val unused = AVAILABLE_COLORS.filter { it.hex !in usedHexes }
            return if (unused.isNotEmpty()) unused else AVAILABLE_COLORS
        }

        /**
         * Creates a dynamic custom department.
         */
        fun createCustom(
            name: String,
            shortName: String,
            colorHex: Long
        ): Department {
            val trimmedName = name.trim()
            val trimmedShortName = shortName.trim().ifBlank { trimmedName.take(8) }
            val slug = trimmedShortName.lowercase()
                .replace("[^a-z0-9]+".toRegex(), "-")
                .trim('-')
                .ifBlank { "custom" }

            return Department(
                id = DepartmentId("dept-$slug-${(100..999).random()}"),
                code = slug,
                displayName = trimmedName,
                shortName = trimmedShortName,
                colorHex = colorHex,
                isCustom = true
            )
        }
    }
}
