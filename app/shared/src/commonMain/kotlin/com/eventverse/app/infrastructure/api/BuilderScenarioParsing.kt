package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.shared.discovery.ScenarioSketchCodec
import com.eventverse.app.shared.json.JsonValue

/** Hasil `POST /api/builder/draft/scenarios/propose`: [featureEnabled] false = server membalas 404 (fitur skenario belum aktif). */
data class ScenarioProposeOutcome(val featureEnabled: Boolean, val scenarios: List<ScenarioSketch>)

/**
 * Parser tunggal balasan propose: `{"scenarios":[...]}`. Kunci `scenarios` wajib dan harus array; isi dibaca
 * [ScenarioSketchCodec] yang ketat. Bentuk rusak atau tak dikenal **gagal dengan pesan jelas** - tidak jatuh senyap ke
 * daftar kosong (daftar kosong yang sah tetap `[]`).
 */
internal fun parseProposedScenarios(json: JsonValue): List<ScenarioSketch> {
    val arr = (json as? JsonValue.Obj)?.get("scenarios") as? JsonValue.Arr
        ?: error("Respons skenario tidak memuat daftar scenarios")
    return ScenarioSketchCodec.decode(arr, "scenarios")
}

/** Parser tunggal balasan confirm: `{"scenario":{...}}` (tepat satu skenario). */
internal fun parseConfirmedScenario(json: JsonValue): ScenarioSketch {
    val obj = (json as? JsonValue.Obj)?.get("scenario") as? JsonValue.Obj
        ?: error("Respons konfirmasi tidak memuat scenario")
    return ScenarioSketchCodec.decode(JsonValue.Arr(listOf(obj)), "scenario").single()
}
