package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.builder.BriefKeys
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.discovery.scenario.ScenarioLimits as L

/**
 * Fallback skenario tanpa model (TRD-BLDR-002 FR-4). Murni; aman bila `interview` null atau tak ada handoff (daftar kosong).
 *
 * Sambungan = `interview.handoffs` antar modul **OPERATIONAL aktif** di blueprint (REUSE_PLATFORM/governance/foundation dan
 * modul tak aktif diabaikan). Jalur = yang TERPANJANG dari modul masuk (tanpa sambungan masuk) tanpa mengulang modul
 * (siklus dipotong); seri diputus menurut urutan kemunculan handoff, jadi deterministik. Jalur dipotong [ScenarioLimits.STEPS_MAX].
 * Jalur < 2 modul bukan skenario lintas modul → kosong. Langkah ber-label nama modul; layar langkah = layar pertama modul yang
 * berproposal berentitas (tanpa → `screenId = null`, carries kosong, kerangka). Carries = field `key` identik atau label
 * ternormalisasi identik, tipe kompatibel ([ScenarioCarryTypes]), tiap field tujuan sekali. Hasil `DETERMINISTIC`, `confirmed = false`,
 * dan hanya dikembalikan bila lolos [ScenarioRules]; tak lolos = kosong (tidak mengarang).
 */
object DeterministicScenarioBuilder {

    fun build(draft: DiscoveryDraft): List<ScenarioSketch> {
        val modules = draft.pack.modules
            .filter { it.kind == ModuleKind.OPERATIONAL && it.id.value in draft.blueprint.activeModuleCodes }
            .associateBy { it.id.value }
        val edges = draft.interview?.handoffs.orEmpty()
            .map { it.from.value to it.to.value }
            .filter { (a, b) -> a != b && a in modules && b in modules }
            .distinct()
        if (edges.isEmpty()) return emptyList()
        val entries = edges.map { it.first }.distinct().filter { m -> edges.none { it.second == m } }
        var best = emptyList<String>()
        fun walk(path: List<String>) {
            if (path.size > best.size) best = path
            edges.filter { it.first == path.last() && it.second !in path }.forEach { walk(path + it.second) }
        }
        entries.forEach { walk(listOf(it)) }
        val chain = best.take(L.STEPS_MAX).mapNotNull { modules[it] }
        if (chain.size < 2) return emptyList()
        val screens = chain.map { screenFor(draft, it) }
        val steps = chain.mapIndexed { i, m ->
            ScenarioStep(
                moduleId = m.id, screenId = screens[i]?.first,
                label = m.displayName.take(L.LABEL_MAX_CHARS).ifBlank { m.id.value },
                carries = screens.getOrNull(i + 1)?.let { next -> screens[i]?.let { pairs(it.second, next.second) } }.orEmpty()
            )
        }
        val sketch = ScenarioSketch(
            id = ScenarioId("scn-" + BriefKeys.fingerprint(chain.joinToString(">") { it.id.value })),
            title = "${steps.first().label} sampai ${steps.last().label}".take(L.TITLE_MAX_CHARS),
            steps = steps, source = ScenarioSource.DETERMINISTIC, confirmed = false
        )
        return if (ScenarioRules.validate(draft, sketch).isEmpty()) listOf(sketch) else emptyList()
    }

    private fun screenFor(draft: DiscoveryDraft, module: ModuleDefinition): Pair<String, List<FieldProposal>>? =
        draft.screens.firstNotNullOfOrNull { s ->
            val fields = s.proposal?.entity?.fields
            if (s.moduleId == module.id && fields != null) s.screenId to fields else null
        }

    private fun pairs(from: List<FieldProposal>, to: List<FieldProposal>): List<FieldCarry> {
        val used = mutableSetOf<String>()
        val out = mutableListOf<FieldCarry>()
        for (f in from) {
            val match = to.firstOrNull { it.key == f.key && it.key !in used }
                ?: to.firstOrNull { BriefKeys.normalize(it.label) == BriefKeys.normalize(f.label) && it.key !in used }
            if (match != null && ScenarioCarryTypes.compatible(f.type, match.type) && out.size < L.CARRIES_MAX_PER_STEP) {
                used += match.key
                out += FieldCarry(f.key, match.key)
            }
        }
        return out
    }
}
