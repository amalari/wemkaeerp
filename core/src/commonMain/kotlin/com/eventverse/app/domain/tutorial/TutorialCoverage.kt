package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleId

/**
 * Kondisi satu modul pack terhadap kontrak cakupan tutorial (PLAN-help-003).
 *
 * @property tutorialIds tutorial `TutorialScope.Module(moduleId)` yang ada di katalog; kosong = belum tercakup.
 * @property exemptReason alasan pengecualian dari daftar exempt frozen; `null` = tidak dikecualikan.
 */
data class ModuleCoverage(
    val moduleId: ModuleId,
    val tutorialIds: List<TutorialId>,
    val exemptReason: String?,
) {
    val isCovered: Boolean get() = tutorialIds.isNotEmpty()
}

/**
 * Kontrak cakupan tutorial per modul: setiap modul pack yang dikenal sumber tutorialnya wajib
 * punya minimal satu tutorial, atau tercatat di [violations] lewat daftar `exempt` dengan alasan.
 *
 * Mirip `MenuInventoryTest` untuk menu (module-integration-rules §5.7), kontrak ini menjaga agar
 * chat helper (`/help/ask`) selalu punya bahan jawaban "cara pakai modul X" untuk setiap modul.
 *
 * Ratchet dua arah:
 * 1. Modul tanpa tutorial dan tanpa exempt = pelanggaran — modul baru tidak boleh lolos begitu saja.
 * 2. Exempt untuk modul yang sudah punya tutorial = pelanggaran — exempt dicabut begitu tutorial ditulis,
 *    sehingga daftar exempt tidak membusuk menjadi salinan permanen.
 * 3. Exempt dengan alasan kosong atau untuk modul yang tidak ada di pack = pelanggaran — tidak ada exempt mati.
 *
 * Pak dari DB yang tidak dikenal `TutorialSource`-nya hanya membawa tutorial platform; kontrak ini
 * ditegakkan pemanggilnya (test) hanya untuk pack bawaan, bukan untuk pack tak dikenal (HELP-001 Non-Goals).
 */
object TutorialCoverage {

    /** Kondisi tiap modul [pack] terhadap [tutorials] (biasanya `TutorialCatalog.forPack(pack)`). */
    fun coverageFor(pack: DomainPack, tutorials: List<ModuleTutorial>): List<ModuleCoverage> {
        val byModule = tutorials.mapNotNull { t -> t.moduleId?.let { it to t.id } }.groupBy({ it.first }, { it.second })
        return pack.modules.map { module -> ModuleCoverage(module.id, byModule[module.id].orEmpty(), exemptReason = null) }
    }

    /**
     * Pelanggaran kontrak terhadap [pack]; kosong = sehat. Sengaja dipisah dari runtime:
     * dipanggil test di CI, bukan saat aplikasi berjalan.
     *
     * @param exempt daftar frozen `ModuleId -> alasan`; hidup di test agar keputusan pengecualian
     *   eksplisit dan dicicil sebagai utang terdaftar, bukan disembunyikan.
     */
    fun violations(pack: DomainPack, tutorials: List<ModuleTutorial>, exempt: Map<ModuleId, String>): List<String> {
        val problems = mutableListOf<String>()
        val byModule = tutorials.mapNotNull { t -> t.moduleId?.let { it to t.id } }.groupBy({ it.first }, { it.second })
        pack.modules.forEach { module ->
            val covered = byModule[module.id].orEmpty().isNotEmpty()
            val reason = exempt[module.id]
            when {
                reason != null && reason.isBlank() ->
                    problems += "Modul ${module.id.value}: alasan Exempt kosong"
                covered && reason != null ->
                    problems += "Modul ${module.id.value}: sudah punya tutorial, cabut Exempt(\"$reason\")"
                !covered && reason == null ->
                    problems += "Modul ${module.id.value}: wajib minimal satu tutorial atau Exempt(alasan)"
            }
        }
        exempt.keys.filter { pack.module(it) == null }.forEach {
            problems += "Exempt ${it.value}: modul tidak ada di pack ${pack.code.value}"
        }
        return problems
    }
}
