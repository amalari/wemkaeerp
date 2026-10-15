package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.prototype.EntitySpec

/**
 * Pembawa nilai simulasi (TRD-BLDR-002 FR-6). Murni. Per [FieldCarry]:
 * - field asal/tujuan tidak ada di entitasnya → `rejected` (`carries[i].fromField|toField`), tidak dibawa, tidak dibuang diam-diam;
 * - tipe tak kompatibel menurut [ScenarioCarryTypes] → `rejected`;
 * - nilai sumber kosong (tak ada / string kosong) → tidak dibawa, BUKAN galat;
 * - nilai yang tak sah bagi field tujuan (`FieldSpec.accepts`, mis. label ENUM yang tak ada di opsi tujuan, tanggal tanpa jam
 *   ke field `withTime`) → `rejected`, tidak dikoersi tebakan;
 * - dua carries ke field tujuan sama → yang pertama menang, berikutnya `rejected`.
 * Nilai teks dari tipe lain (angka, tanggal, label, "ya"/"tidak") dibawa apa adanya ke TEXT/LONG_TEXT: representasi tersimpannya
 * sudah string, jadi tidak ada konversi nilai yang bisa salah.
 */
object DefaultScenarioCarryRule : ScenarioCarryRule {

    override fun apply(sourceValues: Map<String, String>, carries: List<FieldCarry>, source: EntitySpec, target: EntitySpec): CarryOutcome {
        val values = linkedMapOf<String, String>()
        val rejected = mutableListOf<ScenarioIssue>()
        carries.forEachIndexed { i, carry ->
            val from = source.field(carry.fromField)
            val to = target.field(carry.toField)
            if (from == null) rejected += ScenarioIssue("carries[$i].fromField", "field '${carry.fromField}' tidak ada di entitas asal '${source.id}'")
            if (to == null) rejected += ScenarioIssue("carries[$i].toField", "field '${carry.toField}' tidak ada di entitas tujuan '${target.id}'")
            if (from == null || to == null) return@forEachIndexed
            val problem = ScenarioCarryTypes.problem(from.type, to.type)
            val value = sourceValues[carry.fromField].orEmpty()
            when {
                problem != null -> rejected += ScenarioIssue("carries[$i].toField", problem)
                value.isEmpty() -> Unit
                carry.toField in values -> rejected += ScenarioIssue("carries[$i].toField", "field '${carry.toField}' sudah diisi pembawa lain")
                !to.accepts(value) -> rejected += ScenarioIssue("carries[$i].toField", "nilai '$value' tidak sah untuk field '${to.label}'")
                else -> values[carry.toField] = value
            }
        }
        return CarryOutcome(values, rejected)
    }
}
