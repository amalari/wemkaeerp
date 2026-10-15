package com.eventverse.app.presentation.discovery.scenario

import com.eventverse.app.domain.discovery.scenario.CarryOutcome
import com.eventverse.app.domain.discovery.scenario.FieldCarry
import com.eventverse.app.domain.discovery.scenario.ScenarioCarryRule
import com.eventverse.app.domain.discovery.scenario.ScenarioIssue
import com.eventverse.app.domain.prototype.EntitySpec
import com.eventverse.app.domain.prototype.FieldType

/**
 * Pengganti SEMENTARA [ScenarioCarryRule] sampai implementasi Track A (core) digabung: menyalin nilai apa adanya hanya bila
 * kedua field ada dan **tipenya sama** (ENUM: nilai harus salah satu opsi tujuan). Selain itu pasangan **ditolak** dengan
 * path - tidak dikoersi diam-diam. Nilai sumber kosong dilewati (tidak ada yang dibawa, bukan penolakan).
 * Pemanggil produksi menyuntikkan aturan Track A lewat [ScenarioActions.carryRule]; kelas ini hanya bawaan.
 */
object StraightCarryRule : ScenarioCarryRule {
    override fun apply(sourceValues: Map<String, String>, carries: List<FieldCarry>, source: EntitySpec, target: EntitySpec): CarryOutcome {
        val values = linkedMapOf<String, String>()
        val rejected = mutableListOf<ScenarioIssue>()
        carries.forEachIndexed { i, c ->
            val path = "carries[$i]"
            val from = source.fields.firstOrNull { it.key == c.fromField }
            val to = target.fields.firstOrNull { it.key == c.toField }
            when {
                from == null -> rejected += ScenarioIssue("$path.fromField", "Field '${c.fromField}' tidak ada di '${source.label}'.")
                to == null -> rejected += ScenarioIssue("$path.toField", "Field '${c.toField}' tidak ada di '${target.label}'.")
                from.type != to.type -> rejected += ScenarioIssue(path, "'${from.label}' dan '${to.label}' beda jenis isian, tidak dibawa.")
                else -> {
                    val v = sourceValues[c.fromField].orEmpty()
                    if (v.isBlank()) return@forEachIndexed
                    if (to.type == FieldType.ENUM && to.options.none { it.label == v }) {
                        rejected += ScenarioIssue(path, "Nilai '$v' bukan pilihan '${to.label}'.")
                    } else values[c.toField] = v
                }
            }
        }
        return CarryOutcome(values, rejected)
    }
}
