package com.eventverse.app.domain.discovery.brief

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.prototype.EntitySpec

/**
 * Merakit [RequirementsBrief] dari draf (sisi server, plan C4): modul terpilih, layar, entitas (dari spec
 * yang sama dengan prototype lewat [WidgetRegistry.interactiveFor]), log perubahan klien, dan cakupan katalog.
 * Murni dan deterministik — tidak ada akses jaringan/DB; harga datang dari pemanggil ([coverage]).
 *
 * Modul yang **belum ada di katalog** (bukan [BriefCoverage.covered]) otomatis masuk [RequirementsBrief.customNeeds]
 * supaya developer tahu itu pekerjaan bangun, bukan konfigurasi.
 */
object RequirementsBriefAssembler {

    fun assemble(
        draft: DiscoveryDraft, included: Set<String>, changes: List<CaptureEntry>, coverage: List<BriefCoverage>,
        context: BriefContext? = null,
        revision: BriefRevision? = null
    ): RequirementsBrief {
        val modules = draft.pack.modules.filter { it.id.value in included }.map { m ->
            // Pasangan (layar, spec prototype-nya bila bisa dimainkan) — spec sama dengan yang dilihat klien.
            val perScreen = draft.screens.filter { it.moduleId == m.id }.map { s ->
                s to WidgetRegistry.interactiveFor(WidgetRegistry.screenFor(s, draft.pack), draft.pack)?.spec
            }
            BriefModule(
                moduleId = m.id.value,
                displayName = m.displayName,
                screens = perScreen.map { (s, spec) -> BriefScreen(s.title, s.widget, spec?.screens?.firstOrNull()?.entityId) },
                entities = perScreen.mapNotNull { it.second }.flatMap { it.entities }.distinctBy { it.id }.map(::entityOf)
            )
        }
        val gaps = coverage.filter { !it.covered && it.moduleId in included }
            .map { "Modul '${it.displayName}' belum ada di katalog — perlu dibangun (CUSTOM_EXTENSION)." }
        return RequirementsBrief(draft.pack.code.value, modules, changes, coverage.filter { it.moduleId in included }, gaps, context?.takeUnless { it.isEmpty }, revision)
    }

    private fun entityOf(e: EntitySpec) = BriefEntity(
        id = e.id,
        label = e.label,
        fields = e.fields.map { BriefField(it.label, it.type.name, it.required, it.options) },
        statusField = e.stateMachine?.field,
        transitions = e.stateMachine?.transitions?.mapValues { (_, v) -> v.toList() }.orEmpty()
    )
}
