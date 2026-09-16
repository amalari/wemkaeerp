package com.eventverse.app.shared.deal

import com.eventverse.app.domain.crm.BrandName
import com.eventverse.app.domain.crm.Contact
import com.eventverse.app.domain.crm.ContactId
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealId
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.deal.DealTitle
import com.eventverse.app.domain.moduledev.MoneyIdr
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Wire format untuk Contact, Deal, dan Purchase Order — dipakai server Ktor dan client
 * Compose Multiplatform dengan alasan yang sama dengan [com.eventverse.app.shared.crm.CrmLeadCodec]:
 * satu wire format, jadi dua pihak tidak mungkin drift.
 */
object DealCodec {

    // -----------------------------------------------------------------------
    // Contact <-> JSON
    // -----------------------------------------------------------------------

    fun encodeContact(contact: Contact): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(contact.id.value),
        "name" to jsonOf(contact.name),
        "brandName" to jsonOf(contact.brandName.value),
        "phone" to jsonOf(contact.phone?.value),
        "email" to jsonOf(contact.email),
        "address" to jsonOf(contact.address),
        "taxId" to jsonOf(contact.taxId),
        "sourceLeadId" to jsonOf(contact.sourceLeadId?.value),
        "createdAt" to jsonOf(contact.createdAt.toString()),
        "updatedAt" to jsonOf(contact.updatedAt.toString())
    )

    fun decodeContact(obj: JsonValue.Obj, tenantId: TenantId): Contact? {
        val id = obj.string("id") ?: return null
        return Contact(
            id = ContactId(id),
            tenantId = tenantId,
            name = obj.string("name") ?: "",
            brandName = BrandName(obj.string("brandName") ?: ""),
            phone = obj.string("phone")?.takeIf { it.isNotBlank() }?.let { WhatsappNumber(it) },
            email = obj.string("email") ?: "",
            address = obj.string("address") ?: "",
            taxId = obj.string("taxId") ?: "",
            sourceLeadId = obj.string("sourceLeadId")?.let { LeadId(it) },
            createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), Instant.fromEpochMilliseconds(0)),
            updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), Instant.fromEpochMilliseconds(0))
        )
    }

    fun decodeContacts(rawJson: String, tenantId: TenantId): List<Contact> =
        JsonParser.parseArray(rawJson).mapNotNull { item ->
            (item as? JsonValue.Obj)?.let { decodeContact(it, tenantId) }
    }

    fun encodeContacts(contacts: List<Contact>): String =
        jsonArrayOf(contacts.map(::encodeContact)).encode()

    // -----------------------------------------------------------------------
    // Deal <-> JSON
    // -----------------------------------------------------------------------

    fun encodeDeal(deal: Deal): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(deal.id.value),
        "contactId" to jsonOf(deal.contactId.value),
        "sourceLeadId" to jsonOf(deal.sourceLeadId?.value),
        "title" to jsonOf(deal.title.value),
        "stage" to jsonOf(deal.stage.name),
        "estimatedValueIdr" to (deal.estimatedValue?.let { jsonOf(it.amount) } ?: JsonValue.Null),
        "ownerEmployeeId" to jsonOf(deal.ownerEmployeeId?.value),
        "expectedCloseDate" to jsonOf(deal.expectedCloseDate?.toString()),
        "notes" to jsonOf(deal.notes),
        "createdAt" to jsonOf(deal.createdAt.toString()),
        "updatedAt" to jsonOf(deal.updatedAt.toString()),
        "archivedAt" to jsonOf(deal.archivedAt?.toString())
    )

    fun encodeDeals(deals: List<Deal>): String = jsonArrayOf(deals.map(::encodeDeal)).encode()

    fun decodeDeal(obj: JsonValue.Obj, tenantId: TenantId): Deal? {
        val id = obj.string("id") ?: return null
        val contactId = obj.string("contactId") ?: return null
        val title = obj.string("title") ?: return null
        return Deal(
            id = DealId(id),
            tenantId = tenantId,
            contactId = ContactId(contactId),
            sourceLeadId = obj.string("sourceLeadId")?.let { LeadId(it) },
            title = DealTitle(title),
            stage = DealStage.fromCode(obj.string("stage")) ?: DealStage.OPEN,
            estimatedValue = obj.long("estimatedValueIdr")?.let { MoneyIdr(it) },
            ownerEmployeeId = obj.string("ownerEmployeeId")?.let { OrgNodeId(it) },
            expectedCloseDate = DateTimeCodec.parseLocalDateOrNull(obj.string("expectedCloseDate")),
            notes = obj.string("notes") ?: "",
            createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), Instant.fromEpochMilliseconds(0)),
            updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), Instant.fromEpochMilliseconds(0)),
            archivedAt = DateTimeCodec.parseInstantOrNull(obj.string("archivedAt"))
        )
    }

    fun decodeDeals(rawJson: String, tenantId: TenantId): List<Deal> = JsonParser.parseArray(rawJson).mapNotNull { item ->
        (item as? JsonValue.Obj)?.let { decodeDeal(it, tenantId) }
    }

    // -----------------------------------------------------------------------
    // PurchaseOrder <-> JSON
    // -----------------------------------------------------------------------

    private fun encodeLine(line: com.eventverse.app.domain.deal.PurchaseOrderLine): JsonValue.Obj = jsonObjectOf(
        "description" to jsonOf(line.description),
        "quantity" to jsonOf(line.quantity),
        "unitPriceIdr" to jsonOf(line.unitPriceIdr)
    )

    private fun decodeLines(items: List<JsonValue>): List<com.eventverse.app.domain.deal.PurchaseOrderLine> =
        items.mapNotNull { item ->
            val line = item as? JsonValue.Obj ?: return@mapNotNull null
            val description = line.string("description") ?: return@mapNotNull null
            com.eventverse.app.domain.deal.PurchaseOrderLine(
                description = description,
                quantity = line.double("quantity") ?: 1.0,
                unitPriceIdr = line.long("unitPriceIdr") ?: 0L
            )
        }

    fun encodePurchaseOrder(po: com.eventverse.app.domain.deal.PurchaseOrder): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(po.id.value),
        "dealId" to jsonOf(po.dealId.value),
        "poNumber" to jsonOf(po.poNumber.value),
        "poDate" to jsonOf(po.poDate.toString()),
        "origin" to jsonOf(po.origin.name),
        "fileName" to jsonOf(po.fileName),
        "mimeType" to jsonOf(po.mimeType),
        "fileSizeBytes" to (po.fileSizeBytes?.let { jsonOf(it) } ?: JsonValue.Null),
        "lines" to jsonArrayOf(po.lines.map(::encodeLine)),
        "totalValueIdr" to jsonOf(po.totalValueIdr),
        "notes" to jsonOf(po.notes),
        "recordedBy" to jsonOf(po.recordedBy),
        "createdAt" to jsonOf(po.createdAt.toString())
    )

    fun encodePurchaseOrders(pos: List<com.eventverse.app.domain.deal.PurchaseOrder>): String =
        jsonArrayOf(pos.map(::encodePurchaseOrder)).encode()

    fun decodePurchaseOrder(obj: JsonValue.Obj, tenantId: TenantId): com.eventverse.app.domain.deal.PurchaseOrder? {
        val id = obj.string("id") ?: return null
        val dealId = obj.string("dealId") ?: return null
        val poNumber = obj.string("poNumber") ?: return null
        val poDate = DateTimeCodec.parseLocalDateOrNull(obj.string("poDate")) ?: return null
        val origin = obj.string("origin")?.let { com.eventverse.app.domain.deal.PoOrigin.fromCode(it) }
            ?: com.eventverse.app.domain.deal.PoOrigin.MANUAL
        return com.eventverse.app.domain.deal.PurchaseOrder(
            id = com.eventverse.app.domain.deal.PurchaseOrderId(id),
            tenantId = tenantId,
            dealId = DealId(dealId),
            poNumber = com.eventverse.app.domain.deal.PoNumber(poNumber),
            poDate = poDate,
            origin = origin,
            fileName = obj.string("fileName"),
            mimeType = obj.string("mimeType"),
            fileSizeBytes = obj.long("fileSizeBytes"),
            storageKey = obj.string("storageKey"),
            lines = decodeLines(obj.array("lines")),
            notes = obj.string("notes") ?: "",
            recordedBy = obj.string("recordedBy") ?: "system",
            createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), Instant.fromEpochMilliseconds(0))
        )
    }

    // -----------------------------------------------------------------------
    // Attach-PO (manual) request
    // -----------------------------------------------------------------------

    data class AttachManualPoRequest(
        val poNumber: String,
        val poDate: kotlinx.datetime.LocalDate,
        val lines: List<com.eventverse.app.domain.deal.PurchaseOrderLine>,
        val notes: String
    )

    fun encodeAttachManualPoRequest(request: AttachManualPoRequest): String = jsonObjectOf(
        "poNumber" to jsonOf(request.poNumber),
        "poDate" to jsonOf(request.poDate.toString()),
        "lines" to jsonArrayOf(request.lines.map(::encodeLine)),
        "notes" to jsonOf(request.notes)
    ).encode()

    fun decodeAttachManualPoRequest(rawJson: String): AttachManualPoRequest {
        val root = JsonParser.parseObject(rawJson)
        return AttachManualPoRequest(
            poNumber = requireNotNull(root.string("poNumber")) { "poNumber is required" },
            poDate = DateTimeCodec.parseLocalDateOrNull(root.string("poDate"))
                ?: kotlinx.datetime.Clock.System.now()
                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date,
            lines = decodeLines(root.array("lines")),
            notes = root.string("notes") ?: ""
        )
    }

    // -----------------------------------------------------------------------
    // Deal stage transition request
    // -----------------------------------------------------------------------

    fun encodeStageRequest(stage: DealStage): String = jsonObjectOf("stage" to jsonOf(stage.name)).encode()

    fun decodeStageRequest(rawJson: String): DealStage? =
        JsonParser.parseObject(rawJson).string("stage")?.let { DealStage.fromCode(it) }
}
