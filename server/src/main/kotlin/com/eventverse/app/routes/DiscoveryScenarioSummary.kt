package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.shared.discovery.ScenarioSketchCodec
import com.eventverse.app.shared.json.JsonValue

/**
 * Menambahkan `scenarios` (TRD-BLDR-002) ke ringkasan draf. Draf tanpa skenario dikembalikan apa adanya (tidak ada kunci
 * baru) — draf lama tidak berubah satu byte pun.
 */
internal fun withScenarios(base: JsonValue.Obj, stored: StoredDiscoveryDraft): JsonValue.Obj =
    if (stored.draft.scenarios.isEmpty()) base
    else JsonValue.Obj(base.entries + ("scenarios" to ScenarioSketchCodec.encode(stored.draft.scenarios)))
