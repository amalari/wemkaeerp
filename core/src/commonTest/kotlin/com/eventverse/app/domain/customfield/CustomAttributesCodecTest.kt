package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.prototype.FieldType

import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomAttributesCodecTest {

    private val tenantId = TenantId("ten-demo-001")

    @Test
    fun encodeDecode_singleSelectField_roundTrips() {
        val type = CrmFieldType(FieldType.ENUM, 
            options = listOf(
                SelectOption(SelectOptionId("opt_a"), "Opsi A", "#2563EB"),
                SelectOption(SelectOptionId("opt_b"), "Opsi B", "#16A34A", archivedAt = "2026-01-01T00:00:00Z")
            )
        )
        val def = CustomFieldDefinition(
            id = CustomFieldId("cf-1"),
            tenantId = tenantId,
            ownerResource = OwnerResource.CRM_SALES,
            key = FieldKey("jenis_sablon"),
            label = "Jenis Sablon",
            type = type,
            position = 1000.0
        )

        val encoded = CustomAttributesCodec.encodeDefinition(def)
        val decoded = CustomAttributesCodec.decodeDefinition(tenantId, encoded)

        assertEquals(def.id, decoded?.id)
        assertEquals(def.label, decoded?.label)
        assertEquals(def.type, decoded?.type)
    }

    @Test
    fun encodeDecode_numberFieldWithCurrency_roundTrips() {
        val type = CrmFieldType(FieldType.NUMBER, format = NumberFormat.Currency("IDR"), decimals = 0)
        val def = CustomFieldDefinition(
            id = CustomFieldId("cf-2"),
            tenantId = tenantId,
            ownerResource = OwnerResource.CRM_SALES,
            key = FieldKey("nilai_estimasi"),
            label = "Nilai Estimasi",
            type = type,
            position = 2000.0
        )

        val encoded = CustomAttributesCodec.encodeDefinition(def)
        val decoded = CustomAttributesCodec.decodeDefinition(tenantId, encoded)

        assertEquals(type, decoded?.type)
    }

    @Test
    fun encodeDecode_listOfDefinitions_roundTripsViaJsonText() {
        val defs = listOf(
            CustomFieldDefinition(
                id = CustomFieldId("cf-3"), tenantId = tenantId, ownerResource = OwnerResource.CRM_SALES,
                key = FieldKey("detail_kain"), label = "Detail Kain", type = CrmFieldType(FieldType.TEXT), position = 3000.0
            ),
            CustomFieldDefinition(
                id = CustomFieldId("cf-4"), tenantId = tenantId, ownerResource = OwnerResource.CRM_SALES,
                key = FieldKey("sample_approved"), label = "Sample Approved", type = CrmFieldType(FieldType.BOOL), position = 4000.0
            )
        )

        val json = CustomAttributesCodec.encodeDefinitions(defs)
        val decoded = CustomAttributesCodec.decodeDefinitions(tenantId, json)

        assertEquals(2, decoded.size)
        assertEquals(CrmFieldType(FieldType.TEXT), decoded[0].type)
        assertEquals(CrmFieldType(FieldType.BOOL), decoded[1].type)
    }

    @Test
    fun customAttributes_cellRoundTrips() {
        var attrs = CustomAttributes.EMPTY
        val fieldId = CustomFieldId("cf-5")
        attrs = attrs.with(fieldId, CustomAttributes.textCell("@sinarjaya.id"))

        assertEquals("@sinarjaya.id", attrs.text(fieldId))
        assertEquals(true, attrs.hasValue(fieldId))

        attrs = attrs.with(fieldId, null)
        assertEquals(false, attrs.hasValue(fieldId))
    }
}
