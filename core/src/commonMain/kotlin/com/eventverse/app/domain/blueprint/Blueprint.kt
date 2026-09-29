package com.eventverse.app.domain.blueprint

import com.eventverse.app.domain.pack.DomainPackCode
import kotlin.jvm.JvmInline

/** Kode Blueprint tersimpan (`fob_full_package`). Untuk garment = kode preset lama persis (TRD-PLAT-001 Q5). */
@JvmInline
value class BlueprintCode(val value: String) {
    init {
        require(Regex("^[a-z][a-z0-9_]{0,63}$").matches(value)) { "BlueprintCode '$value' harus huruf kecil/angka/underscore" }
    }
}

/**
 * Satu modul dalam Blueprint. Modul **non-aktif tetap dicantumkan** beserta parameternya, karena tenant atau
 * skenario bisa mengaktifkannya (TRD-PLAT-001 FR-2): Gudang di-bypass pada CMT, tapi stoknya tetap titipan.
 *
 * @param parameters kunci & nilai string; artinya ditafsirkan modul yang bersangkutan dengan parser ketat.
 */
data class BlueprintModule(
    val moduleCode: String,
    val active: Boolean,
    val parameters: Map<String, String> = emptyMap()
) {
    init { require(moduleCode.isNotBlank()) { "BlueprintModule.moduleCode kosong" } }
}

/**
 * Starter alur pabrik sebagai data (TRD-PLAT-001). Mesin kanvas membaca modul aktif & parameter dari sini —
 * spec modul tidak lagi menyebut nama preset. Format yang sama kelak ditulis AI agent (plan A6/A7).
 */
data class Blueprint(
    val code: BlueprintCode,
    val pack: DomainPackCode,
    val displayName: String,
    val shortBadge: String,
    val description: String,
    val targetClientProfile: String,
    val modules: List<BlueprintModule>
) {
    init {
        require(modules.isNotEmpty()) { "Blueprint ${code.value} tanpa modul" }
        modules.groupingBy { it.moduleCode }.eachCount().filterValues { it > 1 }.keys.firstOrNull()
            ?.let { error("Blueprint ${code.value}: modul ganda '$it'") }
    }

    fun module(moduleCode: String): BlueprintModule? = modules.firstOrNull { it.moduleCode == moduleCode }

    val activeModuleCodes: Set<String> get() = modules.filter { it.active }.map { it.moduleCode }.toSet()

    fun isActive(moduleCode: String): Boolean = module(moduleCode)?.active == true

    fun parametersOf(moduleCode: String): Map<String, String> = module(moduleCode)?.parameters.orEmpty()
}
