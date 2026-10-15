package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.scenario.ScenarioLimits as L

/**
 * Validator skenario (TRD-BLDR-002 FR-2); angka dari [ScenarioLimits]. Murni, tanpa I/O. Temuan berpath
 * (`steps[1].carries[0].toField`). Daftar kosong = sah.
 *
 * Keputusan yang tidak tersurat di FR-2 (ditulis, bukan diam-diam):
 * - skenario tanpa langkah ditolak (FR-2 "langkah 1..7");
 * - "modul aktif" = ada di `blueprint.activeModuleCodes` (yang selalu bagian dari `pack.modules`);
 * - carries butuh KEDUA langkah berlayar berproposal berentitas; bila tidak, carries harus kosong (ditolak berpath);
 * - dua carries ke `toField` yang sama pada satu langkah ditolak (nilai saling menimpa = data hilang senyap);
 * - tipe: lihat [ScenarioCarryTypes] (alasan tidak memakai `FieldTypeConversion`);
 * - [validateAll] menambah batas [ScenarioLimits.SCENARIOS_MAX], id unik, dan skenario ganda (urutan `moduleId` sama persis).
 */
object ScenarioRules : ScenarioValidator {

    override fun validate(draft: DiscoveryDraft, scenario: ScenarioSketch): List<ScenarioIssue> = buildList {
        if (scenario.title.length > L.TITLE_MAX_CHARS) add(ScenarioIssue("title", "maksimal ${L.TITLE_MAX_CHARS} karakter"))
        if (scenario.steps.isEmpty()) add(ScenarioIssue("steps", "minimal 1 langkah"))
        if (scenario.steps.size > L.STEPS_MAX) add(ScenarioIssue("steps", "maksimal ${L.STEPS_MAX} langkah"))
        scenario.steps.indices.forEach { i -> step(draft, scenario, i, this) }
        scenario.steps.withIndex().filter { it.value.reworkTo != null }.drop(1)
            .forEach { add(ScenarioIssue("steps[${it.index}].reworkTo", "hanya satu loop rework per skenario")) }
    }

    /** Daftar skenario tenant: tiap skenario (path berawalan `scenarios[i].`) + batas jumlah, id unik, tanpa duplikat urutan. */
    fun validateAll(draft: DiscoveryDraft, scenarios: List<ScenarioSketch>): List<ScenarioIssue> = buildList {
        if (scenarios.size > L.SCENARIOS_MAX) add(ScenarioIssue("scenarios", "maksimal ${L.SCENARIOS_MAX} skenario"))
        val ids = mutableSetOf<String>()
        val sequences = mutableSetOf<List<String>>()
        scenarios.forEachIndexed { i, s ->
            validate(draft, s).forEach { add(ScenarioIssue("scenarios[$i].${it.path}", it.message)) }
            if (!ids.add(s.id.value)) add(ScenarioIssue("scenarios[$i].id", "id sudah dipakai skenario lain"))
            if (!sequences.add(sequenceKey(s))) add(ScenarioIssue("scenarios[$i].steps", "skenario ganda: urutan modul sama dengan skenario lain"))
        }
    }

    /** Identitas isi skenario untuk deteksi ganda: urutan `moduleId`. */
    fun sequenceKey(scenario: ScenarioSketch): List<String> = scenario.steps.map { it.moduleId.value }

    private fun step(draft: DiscoveryDraft, scenario: ScenarioSketch, i: Int, out: MutableList<ScenarioIssue>) {
        val step = scenario.steps[i]
        val p = "steps[$i]"
        if (step.label.length > L.LABEL_MAX_CHARS) out += ScenarioIssue("$p.label", "maksimal ${L.LABEL_MAX_CHARS} karakter")
        if (step.moduleId.value !in draft.blueprint.activeModuleCodes) {
            out += ScenarioIssue("$p.moduleId", "'${step.moduleId.value}' bukan modul aktif di draf")
        }
        val screen = step.screenId?.let { id -> draft.screens.firstOrNull { it.screenId == id } }
        if (step.screenId != null && (screen == null || screen.moduleId != step.moduleId)) {
            out += ScenarioIssue("$p.screenId", "'${step.screenId}' bukan layar milik modul '${step.moduleId.value}'")
        }
        val target = step.reworkTo
        if (target != null && target !in 0 until i) out += ScenarioIssue("$p.reworkTo", "harus indeks langkah sebelumnya (0 sampai ${i - 1})")
        carries(draft, scenario, i, out)
    }

    private fun carries(draft: DiscoveryDraft, scenario: ScenarioSketch, i: Int, out: MutableList<ScenarioIssue>) {
        val step = scenario.steps[i]
        if (step.carries.isEmpty()) return
        val p = "steps[$i].carries"
        if (step.carries.size > L.CARRIES_MAX_PER_STEP) out += ScenarioIssue(p, "maksimal ${L.CARRIES_MAX_PER_STEP} pembawa per langkah")
        val next = scenario.steps.getOrNull(i + 1)
        if (next == null) {
            out += ScenarioIssue(p, "langkah terakhir tidak boleh membawa data")
            return
        }
        val from = entityFields(draft, step)
        val to = entityFields(draft, next)
        if (from == null || to == null) {
            out += ScenarioIssue(p, "kedua langkah wajib punya layar berproposal; langkah kerangka tidak dapat membawa data")
            return
        }
        val seenTargets = mutableSetOf<String>()
        step.carries.forEachIndexed { c, carry ->
            val src = from.firstOrNull { it.key == carry.fromField }
            val dst = to.firstOrNull { it.key == carry.toField }
            if (src == null) out += ScenarioIssue("$p[$c].fromField", "field '${carry.fromField}' tidak ada di layar langkah ini")
            if (dst == null) out += ScenarioIssue("$p[$c].toField", "field '${carry.toField}' tidak ada di layar langkah berikutnya")
            if (src != null && dst != null) {
                ScenarioCarryTypes.problem(src.type, dst.type)?.let { out += ScenarioIssue("$p[$c].toField", it) }
            }
            if (dst != null && !seenTargets.add(carry.toField)) out += ScenarioIssue("$p[$c].toField", "field tujuan sudah diisi pembawa lain")
        }
    }

    /** Field entitas layar langkah; `null` = langkah kerangka / layar tanpa entitas. */
    internal fun entityFields(draft: DiscoveryDraft, step: ScenarioStep): List<FieldProposal>? {
        val id = step.screenId ?: return null
        return draft.screens.firstOrNull { it.screenId == id && it.moduleId == step.moduleId }?.proposal?.entity?.fields
    }
}
