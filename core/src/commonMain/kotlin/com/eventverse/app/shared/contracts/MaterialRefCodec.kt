package com.eventverse.app.shared.contracts

import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.masterdata.MaterialCode
import com.eventverse.app.domain.masterdata.MaterialId
import com.eventverse.app.shared.json.*

object MaterialRefCodec {

    fun encode(ref: MaterialRef): JsonValue.Obj = jsonObjectOf(
        "freeText" to jsonOf(ref.freeText),
        "materialId" to jsonOf(ref.materialId?.value),
        "resolvedCode" to jsonOf(ref.resolvedCode?.value),
        "resolvedName" to jsonOf(ref.resolvedName)
    )

    fun decode(obj: JsonValue.Obj): MaterialRef = MaterialRef(
        freeText = obj.string("freeText") ?: "",
        materialId = obj.string("materialId")?.let { MaterialId(it) },
        resolvedCode = obj.string("resolvedCode")?.let { MaterialCode(it) },
        resolvedName = obj.string("resolvedName")
    )
}
