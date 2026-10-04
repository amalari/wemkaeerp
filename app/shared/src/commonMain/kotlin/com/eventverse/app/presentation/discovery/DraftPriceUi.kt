package com.eventverse.app.presentation.discovery

import com.eventverse.app.shared.json.JsonValue

/** Rincian harga satu modul dari `GET /price`: langganan bulanan (sudah ada) atau rentang bulanan (harus dibangun). */
data class PriceLineUi(
    val moduleId: String,
    val displayName: String,
    val covered: Boolean,
    val monthlyIdr: Long?,
    val gapLowIdr: Long?,
    val gapHighIdr: Long?
)

/** Estimasi draf untuk panel harga; parse longgar (field hilang = null), validasi tetap di server. */
data class DraftPriceUi(
    val subscriptionMonthlyIdr: Long,
    val gapLowMonthlyIdr: Long?,
    val gapHighMonthlyIdr: Long?,
    val withheld: Boolean,
    val lines: List<PriceLineUi>
) {
    val hasBuildCost: Boolean get() = (gapLowMonthlyIdr ?: 0L) > 0L || (gapHighMonthlyIdr ?: 0L) > 0L

    companion object {
        fun fromJson(o: JsonValue.Obj): DraftPriceUi = DraftPriceUi(
            subscriptionMonthlyIdr = o.long("subscriptionMonthlyIdr") ?: 0L,
            gapLowMonthlyIdr = o.long("gapLowMonthlyIdr"),
            gapHighMonthlyIdr = o.long("gapHighMonthlyIdr"),
            withheld = o.boolean("withheld") ?: false,
            lines = o.objectArray("lines").map { l ->
                PriceLineUi(
                    l.string("moduleId").orEmpty(), l.string("displayName").orEmpty(), l.boolean("covered") ?: false,
                    l.long("monthlyIdr"), l.long("gapLowIdr"), l.long("gapHighIdr")
                )
            }
        )
    }
}
