package com.eventverse.app.domain.pack

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode

/**
 * Invarian blueprint milik pack (TRD-PLAT-008 FR-1): kode unik, menunjuk pack ini, dan hanya menyebut modul pack ini
 * (aturan yang sama dengan `DiscoveryDraft`, supaya blueprint draf yang lolos draf juga lolos sebagai milik pack).
 */
internal fun DomainPack.requireBlueprintsValid() {
    blueprints.groupingBy { it.code }.eachCount().filterValues { it > 1 }.keys.firstOrNull()
        ?.let { error("Pack ${code.value}: blueprint ganda '${it.value}'") }
    val moduleIds = modules.map { it.id.value }.toSet()
    blueprints.forEach { bp ->
        require(bp.pack == code) { "Blueprint ${bp.code.value} menunjuk pack ${bp.pack.value}, bukan ${code.value}" }
        bp.modules.firstOrNull { it.moduleCode !in moduleIds }?.let {
            error("Blueprint ${bp.code.value} menyebut modul '${it.moduleCode}' yang tidak ada di pack ${code.value}")
        }
    }
}

/**
 * Resolusi kode starter tersimpan → [Blueprint] untuk tenant ber-[pack] (TRD-PLAT-008 FR-3). Dua katalog yang
 * dinyatakan, berurutan: (1) [DomainPack.blueprints] milik pack tenant; (2) starter platform ([GarmentBlueprints]) —
 * nilai bawaan kolom `tenants.business_preset` yang dipegang tenant ber-pack data yang tak pernah memilih starter.
 *
 * Kode yang tak ada di keduanya = `null`; pemanggil **menolak** (tenant-variability-rules Kontrak 4), tidak
 * menebak. [pack] `null` (pack tak dikenal) hanya dapat me-resolve starter platform.
 */
fun resolveBlueprint(pack: DomainPack?, code: BlueprintCode): Blueprint? =
    pack?.blueprints?.firstOrNull { it.code == code } ?: GarmentBlueprints.find(code)

/** Kode starter tenant tak dapat di-resolve (TRD-PLAT-008 K5). Pesan memuat tenant, pack, dan kode — tanpa tebakan. */
class UnresolvableBlueprintException(val tenantSlug: String, val packCode: DomainPackCode, val blueprintCode: String) :
    IllegalStateException("Blueprint '$blueprintCode' tenant '$tenantSlug' tidak ada di pack '${packCode.value}' maupun starter platform")
