package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode

/**
 * Sumber tutorial satu pack. Implementasi bawaan dikirim per rilis; antarmuka ini ada agar isi bisa pindah ke DB
 * kelak tanpa mengubah pemakainya (TRD-HELP-001 §4.5).
 */
interface TutorialSource {
    fun tutorialsFor(pack: DomainPackCode): List<ModuleTutorial>

    /** Anchor UI yang dideklarasikan pack; langkah tutorial hanya boleh merujuk anchor di sini. */
    fun anchorsFor(pack: DomainPackCode): Set<TutorialAnchorId>
}

/**
 * Katalog tutorial satu pack = tutorial platform yang modulnya dipakai pack itu + tutorial milik pack.
 *
 * Pack dari DB yang tidak dikenal [source] hanya mendapat tutorial platform — batasan yang disengaja
 * (TRD-HELP-001 Non-Goals), bukan fallback ke tutorial pack lain.
 */
class TutorialCatalog(private val source: TutorialSource) {

    fun forPack(pack: DomainPack): List<ModuleTutorial> =
        PlatformTutorials.all.filter { it.appliesTo(pack) } + source.tutorialsFor(pack.code)

    fun find(pack: DomainPack, id: TutorialId): ModuleTutorial? = forPack(pack).firstOrNull { it.id == id }

    /** Pelanggaran invarian FR-1 untuk [pack]; kosong = katalog sehat. Dipakai test, bukan saat runtime. */
    fun violations(pack: DomainPack): List<String> {
        val tutorials = forPack(pack)
        val anchors = PlatformTutorials.anchors + source.anchorsFor(pack.code)
        val problems = mutableListOf<String>()
        tutorials.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { problems += "Tutorial ganda ${it.value}" }
        tutorials.forEach { t ->
            t.moduleId?.let { if (pack.module(it) == null) problems += "${t.id.value}: modul ${it.value} tidak ada di pack ${pack.code.value}" }
            t.steps.forEachIndexed { i, step ->
                step.screen?.let { if (pack.module(it) == null) problems += "${t.id.value}#$i: layar ${it.value} tidak ada di pack" }
                step.anchor?.let { if (it !in anchors) problems += "${t.id.value}#$i: anchor ${it.value} tidak terdaftar" }
            }
        }
        return problems
    }

    private fun ModuleTutorial.appliesTo(pack: DomainPack): Boolean = when (val s = scope) {
        is TutorialScope.Module -> pack.module(s.moduleId) != null
        is TutorialScope.Surface -> true
    }
}
