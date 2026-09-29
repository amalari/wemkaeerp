package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.BusinessModule

/** Satu sambungan maju antarmodul yang diturunkan dari port, beserta tipe data yang mengalir. */
data class CatalogPortEdge(
    val from: BusinessModule,
    val to: BusinessModule,
    val dataTypes: Set<String>,
    /** Hanya lewat masukan rujukan (dibaca, bukan aliran barang) — digambar sebagai port masuk penerima. */
    val isReference: Boolean = false
)

/**
 * Menurunkan sambungan kanvas Factory Flow dari **port katalog**, bukan dari daftar tulis tangan
 * (TRD-FLOW-002). Aturannya:
 *
 * 1. Modul aktif pada preset = spec yang `supportedPresets`-nya memuat preset itu.
 * 2. A → B bila `outputsFor(A) ∩ (inputsFor(B) ∪ referenceInputs(B))` tidak kosong.
 * 3. **Terusan bypass**: modul yang di-bypass meneruskan masukannya. Bila A → X (X bypass) dan
 *    X → B, maka A → B — sampel CMT langsung ke HPP karena tech pack dibawa buyer. Hanya satu
 *    lompatan; rantai bypass lebih panjang harus diputuskan eksplisit, bukan ditebak.
 *
 * Modul operasional baru yang didaftarkan dengan port yang benar otomatis tersambung di kanvas.
 */
object CatalogPortWiring {

    fun activeModules(
        preset: GarmentBusinessPreset,
        specs: List<OperationalModuleSpecification> = OperationalModuleCatalog.all
    ): List<OperationalModuleSpecification> = specs.filter { preset in it.supportedPresets }

    fun edges(
        preset: GarmentBusinessPreset,
        specs: List<OperationalModuleSpecification> = OperationalModuleCatalog.all
    ): List<CatalogPortEdge> {
        val all = specs
        val active = activeModules(preset, specs).toSet()
        fun flows(a: OperationalModuleSpecification, b: OperationalModuleSpecification) =
            (a.outputsFor(preset).toSet() intersect b.inputsFor(preset).toSet())
        fun reads(a: OperationalModuleSpecification, b: OperationalModuleSpecification) =
            flows(a, b) + (a.outputsFor(preset).toSet() intersect b.referenceInputs.toSet())

        val direct = active.flatMap { a ->
            active.filter { b -> b != a }.mapNotNull { b ->
                reads(a, b).takeIf { it.isNotEmpty() }?.let { CatalogPortEdge(a.module, b.module, it, isReference = flows(a, b).isEmpty()) }
            }
        }
        val throughBypass = (all - active).flatMap { bypassed ->
            val feeders = active.filter { a -> flows(a, bypassed).isNotEmpty() }
            val consumers = active.filter { b -> flows(bypassed, b).isNotEmpty() }
            feeders.flatMap { a -> consumers.filter { it != a }.map { b -> CatalogPortEdge(a.module, b.module, flows(bypassed, b)) } }
        }
        val directPairs = direct.map { it.from to it.to }.toSet()
        return direct + throughBypass.filter { (it.from to it.to) !in directPairs }.distinctBy { it.from to it.to }
    }
}
