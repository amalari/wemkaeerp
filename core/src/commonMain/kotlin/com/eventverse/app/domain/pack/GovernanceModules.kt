package com.eventverse.app.domain.pack

/**
 * Modul tata kelola wajib sebuah pack **runtime** (TRD-PLAT-009): `org_chart` dan `dynamic_rbac`.
 *
 * Tanpa keduanya, entitlement tenant (bawaan = modul pack) tidak mencakup `dynamic_rbac`, sehingga Owner pun
 * mendapat 403 `NOT_ENTITLED` di `/api/tenant/roles` dan `/api/tenant/module-assignments`. Modul tata kelola milik
 * platform (bukan hasil wawancara), jadi disalin **identik** dari [GarmentModules] — aturan R1 rujukan modul bersama.
 */
object GovernanceModules {
    val required: List<ModuleId> = listOf(GarmentModules.ORG_CHART, GarmentModules.DYNAMIC_RBAC)
}

/**
 * Pack ini ditambah seksi `GOVERNANCE` dan modul tata kelola wajib bila belum ada. **Idempoten**: modul yang sudah
 * ada tidak diganti dan tidak digandakan (definisi berbeda ditolak registry, bukan ditimpa di sini). Pack bawaan
 * tidak dilewatkan ke sini — tak ada yang berubah untuk garment.
 */
fun DomainPack.withGovernanceModules(): DomainPack {
    val missing = GovernanceModules.required.filter { id -> module(id) == null }
    if (missing.isEmpty()) return this
    val section = GarmentModules.sections.first { it.code.value == "GOVERNANCE" }
    val definitions = missing.map { id ->
        requireNotNull(GarmentModules.modules.firstOrNull { it.id == id }) { "Modul tata kelola ${id.value} hilang dari GarmentModules" }
    }
    return copy(
        sections = if (sections.any { it.code == section.code }) sections else listOf(section) + sections,
        modules = definitions + modules
    )
}
