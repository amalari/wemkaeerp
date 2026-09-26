package com.eventverse.app.shared.masterdata

import com.eventverse.app.domain.masterdata.MaterialCatalogPage
import com.eventverse.app.shared.json.*

object MaterialCatalogPageCodec {

    fun encode(page: MaterialCatalogPage): JsonValue.Obj = jsonObjectOf(
        "items" to jsonArrayOf(page.items.map(MaterialItemCodec::encodeMaterial)),
        "totalCount" to jsonOf(page.totalCount),
        "page" to jsonOf(page.page),
        "pageSize" to jsonOf(page.pageSize),
        "totalPages" to jsonOf(page.totalPages)
    )

    fun decode(obj: JsonValue.Obj): MaterialCatalogPage {
        val items = obj.objectArray("items").map(MaterialItemCodec::decodeMaterial)
        val totalCount = obj.long("totalCount") ?: items.size.toLong()
        val page = obj.int("page") ?: 1
        val pageSize = obj.int("pageSize") ?: 20
        return MaterialCatalogPage(items, totalCount, page, pageSize)
    }
}
