package com.eventverse.app.domain.orgchart

import com.eventverse.app.domain.tenant.TenantId
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
    val isCustom: Boolean = false,
    val tenantId: TenantId? = null,
    /** Null = divisi aktif. Non-null = sudah diarsipkan (pola Odoo). Format: ISO-8601 string. */
    val archivedAt: String? = null,
    /** Daftar tingkatan wewenang dinamis di divisi ini. */
    val tiers: List<DepartmentTier> = defaultTiers()
) {
    /** True jika divisi sudah diarsipkan dan tidak aktif. */
    val isArchived: Boolean get() = archivedAt != null

    /** Tambah tingkatan wewenang dinamis baru di divisi ini */
    fun addTier(name: String): Department {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Nama tingkat wewenang tidak boleh kosong" }
        if (tiers.any { it.name.equals(trimmed, ignoreCase = true) }) return this
        val slug = trimmed.lowercase().replace("[^a-z0-9]+".toRegex(), "_").trim('_')
        val newTier = DepartmentTier(id = slug, name = trimmed, rank = tiers.size + 1)
        return copy(tiers = tiers + newTier)
    }

    /** Ubah nama tingkatan wewenang di divisi ini */
    fun updateTier(tierId: String, newName: String): Department {
        val trimmed = newName.trim()
        require(trimmed.isNotBlank()) { "Nama tingkat wewenang tidak boleh kosong" }
        val updatedTiers = tiers.map {
            if (it.id == tierId) it.copy(name = trimmed) else it
        }
        return copy(tiers = updatedTiers)
    }

    companion object {
        fun defaultTiers(): List<DepartmentTier> = listOf(
            DepartmentTier(id = "head", name = "Kepala Divisi", rank = 1),
            DepartmentTier(id = "staff", name = "Staf Pelaksana / Operator", rank = 2)
        )

        fun productionTiers(): List<DepartmentTier> = listOf(
            DepartmentTier(id = "head", name = "Kepala Divisi", rank = 1),
            DepartmentTier(id = "team_lead", name = "Kepala Tim / Mandor", rank = 2),
            DepartmentTier(id = "staff", name = "Operator / Staf", rank = 3)
        )

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
            colorHex = 0xFFEA580C, // Garment Orange
            tiers = productionTiers()
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

        val FINANCE = Department(
            id = DepartmentId("dept-finance"),
            code = "finance",
            displayName = "Keuangan & Akuntansi",
            shortName = "Keuangan",
            colorHex = 0xFF7C3AED // Purple
        )

        /** Aliased for backwards compatibility */
        val FINANCE_EXECUTIVE = FINANCE

        /**
         * Master palette of curated, accessible factory department colors.
         */
        val AVAILABLE_COLORS = listOf(
            DepartmentColor(0xFF2563EB, "Biru Sales"),
            DepartmentColor(0xFFEA580C, "Oranye Produksi"),
            DepartmentColor(0xFF0D9488, "Teal Gudang"),
            DepartmentColor(0xFF16A34A, "Hijau QC"),
            DepartmentColor(0xFF7C3AED, "Ungu Keuangan"),
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
         * Scoped by tenantId to prevent primary key collision across multi-tenant database.
         */
        fun defaultPresets(tenantId: TenantId? = null): List<Department> {
            val prefix = if (tenantId == null || tenantId.value == "ten-demo-001") "" else "${tenantId.value}-"
            return listOf(
                Department(
                    id = DepartmentId("dept-${prefix}sales"),
                    code = "sales",
                    displayName = "Penjualan & CRM",
                    shortName = "Sales",
                    colorHex = 0xFF2563EB,
                    tenantId = tenantId
                ),
                Department(
                    id = DepartmentId("dept-${prefix}ppic"),
                    code = "production_ppic",
                    displayName = "Produksi & PPIC",
                    shortName = "Produksi",
                    colorHex = 0xFFEA580C,
                    tenantId = tenantId,
                    tiers = productionTiers()
                ),
                Department(
                    id = DepartmentId("dept-${prefix}warehouse"),
                    code = "warehouse",
                    displayName = "Gudang & Logistik",
                    shortName = "Gudang",
                    colorHex = 0xFF0D9488,
                    tenantId = tenantId
                ),
                Department(
                    id = DepartmentId("dept-${prefix}qc"),
                    code = "qc",
                    displayName = "Quality Control (QC)",
                    shortName = "QC",
                    colorHex = 0xFF16A34A,
                    tenantId = tenantId
                ),
                Department(
                    id = DepartmentId("dept-${prefix}finance"),
                    code = "finance",
                    displayName = "Keuangan & Akuntansi",
                    shortName = "Keuangan",
                    colorHex = 0xFF7C3AED,
                    tenantId = tenantId
                )
            )
        }

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
            colorHex: Long,
            tenantId: TenantId? = null
        ): Department {
            val trimmedName = name.trim()
            val trimmedShortName = shortName.trim().ifBlank { trimmedName.take(8) }
            val slug = trimmedShortName.lowercase()
                .replace("[^a-z0-9]+".toRegex(), "-")
                .trim('-')
                .ifBlank { "custom" }

            val prefix = if (tenantId == null || tenantId.value == "ten-demo-001") "" else "${tenantId.value}-"

            return Department(
                id = DepartmentId("dept-${prefix}$slug-${(100..999).random()}"),
                code = slug,
                displayName = trimmedName,
                shortName = trimmedShortName,
                colorHex = colorHex,
                isCustom = true,
                tenantId = tenantId
            )
        }
    }
}
