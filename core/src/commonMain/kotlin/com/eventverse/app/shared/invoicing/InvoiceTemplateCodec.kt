package com.eventverse.app.shared.invoicing

import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceTemplateId
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Instant

object InvoiceTemplateCodec {

    fun encode(template: InvoiceTemplate): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(template.id.value),
        "tenantId" to jsonOf(template.tenantId.value),
        "name" to jsonOf(template.name),
        "paperSize" to jsonOf(template.paperSize.name),
        "marginMm10" to jsonOf(template.marginMm10),
        "applicableKinds" to jsonArrayOf(template.applicableKinds.map { jsonOf(it.name) }),
        "elements" to jsonArrayOf(template.elements.map(::encodeElement)),
        "isDefault" to jsonOf(template.isDefault),
        "archivedAt" to jsonOf(template.archivedAt?.toString()),
        "createdAt" to jsonOf(template.createdAt.toString()),
        "updatedAt" to jsonOf(template.updatedAt.toString())
    )

    fun decode(obj: JsonValue.Obj): InvoiceTemplate {
        val id = InvoiceTemplateId(obj.string("id") ?: "")
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val name = obj.string("name") ?: ""
        val paperSize = PaperSize.fromCode(obj.string("paperSize"))
        val marginMm10 = obj.int("marginMm10") ?: 150
        val applicableKinds = obj.stringArray("applicableKinds").mapNotNull {
            runCatching { InvoiceKind.valueOf(it) }.getOrNull()
        }.toSet().ifEmpty { InvoiceKind.entries.toSet() }

        val elements = obj.objectArray("elements").mapNotNull(::decodeElement)
        val isDefault = obj.boolean("isDefault") ?: false
        val archivedAt = DateTimeCodec.parseInstantOrNull(obj.string("archivedAt"))
        val createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), Instant.fromEpochMilliseconds(0))
        val updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), createdAt)

        return InvoiceTemplate(
            id = id,
            tenantId = tenantId,
            name = name,
            paperSize = paperSize,
            marginMm10 = marginMm10,
            applicableKinds = applicableKinds,
            elements = elements,
            isDefault = isDefault,
            archivedAt = archivedAt,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // ── Element Encoding & Decoding ───────────────────────────────────────────────────────────

    fun encodeElement(el: TemplateElement): JsonValue.Obj {
        val map = linkedMapOf<String, JsonValue>(
            "id" to jsonOf(el.elementId),
            "rect" to encodeRect(el.rect),
            "zOrder" to jsonOf(el.zOrder),
            "anchorBelowTable" to jsonOf(el.anchorBelowTable)
        )

        when (el) {
            is TemplateElement.StaticText -> {
                map["type"] = jsonOf("static_text")
                map["text"] = jsonOf(el.text)
                map["style"] = encodeTextStyle(el.style)
            }
            is TemplateElement.BoundField -> {
                map["type"] = jsonOf("bound_field")
                map["binding"] = jsonOf(el.binding.value)
                map["prefix"] = jsonOf(el.prefix)
                map["suffix"] = jsonOf(el.suffix)
                map["style"] = encodeTextStyle(el.style)
            }
            is TemplateElement.ImageBox -> {
                map["type"] = jsonOf("image_box")
                map["binding"] = jsonOf(el.binding?.value)
                map["assetUrl"] = jsonOf(el.assetUrl)
            }
            is TemplateElement.RectShape -> {
                map["type"] = jsonOf("rect_shape")
                map["fillHex"] = el.fillHex?.let { jsonOf(it) } ?: JsonValue.Null
                map["strokeHex"] = el.strokeHex?.let { jsonOf(it) } ?: JsonValue.Null
                map["strokeMm10"] = jsonOf(el.strokeMm10)
                map["cornerMm10"] = jsonOf(el.cornerMm10)
            }
            is TemplateElement.LineShape -> {
                map["type"] = jsonOf("line_shape")
                map["strokeHex"] = jsonOf(el.strokeHex)
                map["strokeMm10"] = jsonOf(el.strokeMm10)
            }
            is TemplateElement.ItemTable -> {
                map["type"] = jsonOf("item_table")
                map["columns"] = jsonArrayOf(el.columns.map(::encodeTableColumn))
                map["rowHeightMm10"] = jsonOf(el.rowHeight.value)
                map["headerStyle"] = encodeTextStyle(el.headerStyle)
                map["bodyStyle"] = encodeTextStyle(el.bodyStyle)
                map["showHeader"] = jsonOf(el.showHeader)
                map["zebraFillHex"] = el.zebraFillHex?.let { jsonOf(it) } ?: JsonValue.Null
            }
        }

        return JsonValue.Obj(map)
    }

    fun decodeElement(obj: JsonValue.Obj): TemplateElement? {
        // `id` adalah nama kanonik yang ditulis [encodeElement]; `elementId` adalah nama yang
        // dipakai seed SQL `V32__register_invoicing_module.sql`. Membaca keduanya wajib: tanpa itu
        // seluruh 23 elemen template standar dibuang diam-diam dan kanvas A4 tampil kosong.
        val id = obj.string("id") ?: obj.string("elementId") ?: return null
        val rect = obj.obj("rect")?.let(::decodeRect) ?: return null
        val zOrder = obj.double("zOrder") ?: 0.0
        val anchorBelowTable = obj.boolean("anchorBelowTable") ?: false

        return when (obj.string("type")) {
            "static_text" -> TemplateElement.StaticText(
                elementId = id,
                rect = rect,
                zOrder = zOrder,
                anchorBelowTable = anchorBelowTable,
                text = obj.string("text") ?: "",
                style = obj.obj("style")?.let(::decodeTextStyle) ?: TextStyleSpec()
            )
            "bound_field" -> TemplateElement.BoundField(
                elementId = id,
                rect = rect,
                zOrder = zOrder,
                anchorBelowTable = anchorBelowTable,
                binding = BindingToken(obj.string("binding") ?: ""),
                prefix = obj.string("prefix") ?: "",
                suffix = obj.string("suffix") ?: "",
                style = obj.obj("style")?.let(::decodeTextStyle) ?: TextStyleSpec()
            )
            "image_box" -> TemplateElement.ImageBox(
                elementId = id,
                rect = rect,
                zOrder = zOrder,
                anchorBelowTable = anchorBelowTable,
                binding = obj.string("binding")?.let { BindingToken(it) },
                assetUrl = obj.string("assetUrl")
            )
            "rect_shape" -> TemplateElement.RectShape(
                elementId = id,
                rect = rect,
                zOrder = zOrder,
                anchorBelowTable = anchorBelowTable,
                fillHex = obj.long("fillHex"),
                strokeHex = obj.long("strokeHex"),
                strokeMm10 = obj.int("strokeMm10") ?: 2,
                cornerMm10 = obj.int("cornerMm10") ?: 0
            )
            "line_shape" -> TemplateElement.LineShape(
                elementId = id,
                rect = rect,
                zOrder = zOrder,
                anchorBelowTable = anchorBelowTable,
                strokeHex = obj.long("strokeHex") ?: 0xFF1E293BL,
                strokeMm10 = obj.int("strokeMm10") ?: 2
            )
            "item_table" -> {
                val columns = obj.objectArray("columns").mapNotNull(::decodeTableColumn)
                if (columns.isEmpty()) return null
                TemplateElement.ItemTable(
                    elementId = id,
                    rect = rect,
                    zOrder = zOrder,
                    anchorBelowTable = anchorBelowTable,
                    columns = columns,
                    rowHeight = Mm10(obj.int("rowHeightMm10") ?: 80),
                    headerStyle = obj.obj("headerStyle")?.let(::decodeTextStyle) ?: TextStyleSpec(isBold = true),
                    bodyStyle = obj.obj("bodyStyle")?.let(::decodeTextStyle) ?: TextStyleSpec(),
                    showHeader = obj.boolean("showHeader") ?: true,
                    zebraFillHex = obj.long("zebraFillHex")
                )
            }
            else -> null
        }
    }

    // ── Supporting Codecs ─────────────────────────────────────────────────────────────────────

    private fun encodeRect(rect: TemplateRect): JsonValue.Obj = jsonObjectOf(
        "x" to jsonOf(rect.x.value),
        "y" to jsonOf(rect.y.value),
        "width" to jsonOf(rect.width.value),
        "height" to jsonOf(rect.height.value)
    )

    private fun decodeRect(obj: JsonValue.Obj): TemplateRect = TemplateRect(
        x = Mm10(obj.int("x") ?: 0),
        y = Mm10(obj.int("y") ?: 0),
        width = Mm10(obj.int("width") ?: 0),
        height = Mm10(obj.int("height") ?: 0)
    )

    private fun encodeTextStyle(style: TextStyleSpec): JsonValue.Obj = jsonObjectOf(
        "fontSizePt" to jsonOf(style.fontSizePt),
        "isBold" to jsonOf(style.isBold),
        "isItalic" to jsonOf(style.isItalic),
        "align" to jsonOf(style.align.name),
        "colorHex" to jsonOf(style.colorHex)
    )

    private fun decodeTextStyle(obj: JsonValue.Obj): TextStyleSpec = TextStyleSpec(
        fontSizePt = obj.int("fontSizePt") ?: 10,
        isBold = obj.boolean("isBold") ?: false,
        isItalic = obj.boolean("isItalic") ?: false,
        align = TextAlign.fromCode(obj.string("align")),
        colorHex = obj.long("colorHex") ?: 0xFF1E293BL
    )

    private fun encodeTableColumn(col: TableColumn): JsonValue.Obj = jsonObjectOf(
        "binding" to jsonOf(col.binding.value),
        "header" to jsonOf(col.header),
        "widthRatio" to MeasureCodec.encodeRatio(col.widthRatio),
        "align" to jsonOf(col.align.name)
    )

    private fun decodeTableColumn(obj: JsonValue.Obj): TableColumn? {
        val bindingStr = obj.string("binding") ?: return null
        // `header` = bentuk kanonik, `headerText` = bentuk seed SQL V32.
        val header = obj.string("header") ?: obj.string("headerText") ?: return null
        // Rasio kanonik berbentuk objek; seed SQL menulisnya sebagai teks ("5/12"). Keduanya
        // diterima, dan kolom tidak boleh dibuang hanya karena bentuk rasionya berbeda.
        val widthRatio = obj.obj("widthRatio")?.let(MeasureCodec::decodeRatio)
            ?: MeasureCodec.parseRatioText(obj.string("widthRatio"))
            ?: Ratio.ONE
        val align = TextAlign.fromCode(obj.string("align"))
        return TableColumn(
            binding = BindingToken(bindingStr),
            header = header,
            widthRatio = widthRatio,
            align = align
        )
    }
}
