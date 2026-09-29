package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.blueprint.Blueprint

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
 * 1. Modul aktif = `blueprint.isActive(modul)`; port per modul dari parameternya di Blueprint.
 * 2. A → B bila `outputsFor(A) ∩ (inputsFor(B) ∪ referenceInputs(B))` tidak kosong.
 * 3. **Terusan bypass**: modul yang di-bypass meneruskan masukannya. Bila A → X (X bypass) dan
 *    X → B, maka A → B — sampel CMT langsung ke HPP karena tech pack dibawa buyer. Hanya satu
 *    lompatan; rantai bypass lebih panjang harus diputuskan eksplisit, bukan ditebak.
 *
 * Modul operasional baru yang didaftarkan dengan port yang benar otomatis tersambung di kanvas.
 */
object CatalogPortWiring {

    fun activeModules(
        blueprint: Blueprint,
        specs: List<OperationalModuleSpecification> = OperationalModuleCatalog.all
    ): List<OperationalModuleSpecification> = specs.filter { blueprint.isActive(it.module.code) }

    fun edges(
        blueprint: Blueprint,
        specs: List<OperationalModuleSpecification> = OperationalModuleCatalog.all
    ): List<CatalogPortEdge> {
        val all = specs
        val active = activeModules(blueprint, specs).toSet()
        fun flows(a: OperationalModuleSpecification, b: OperationalModuleSpecification) =
            (a.outputsFor(blueprint.parametersOf(a.module.code)).toSet() intersect b.inputsFor(blueprint.parametersOf(b.module.code)).toSet())
        fun reads(a: OperationalModuleSpecification, b: OperationalModuleSpecification) =
            flows(a, b) + (a.outputsFor(blueprint.parametersOf(a.module.code)).toSet() intersect b.referenceInputs.toSet())

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
