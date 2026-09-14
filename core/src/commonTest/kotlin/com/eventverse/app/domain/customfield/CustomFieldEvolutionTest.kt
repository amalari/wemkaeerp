package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomFieldEvolutionTest {

    private val tenantId = TenantId("ten-demo-001")
    private val fieldCreatedEarly = Instant.parse("2026-01-01T00:00:00Z")
    private val requiredSince = Instant.parse("2026-02-01T00:00:00Z")

    private fun requiredTextField() = CustomFieldDefinition(
        id = CustomFieldId("cf-required"),
        tenantId = tenantId,
        ownerResource = OwnerResource.CRM_SALES,
        key = FieldKey("wajib"),
        label = "Wajib",
        type = FieldType.Text,
        position = 1000.0,
        isRequired = true,
        requiredSince = requiredSince
    )

    @Test
    fun validateForCreate_missingRequiredField_producesError() {
        val errors = CustomFieldValidation.validateForCreate(listOf(requiredTextField()), emptyMap())
        assertEquals(1, errors.size)
        assertTrue(errors.first() is CustomFieldValidationError.Required)
    }

    @Test
    fun validateForCreate_presentRequiredField_noError() {
        val cell = CustomAttributes.textCell("isi")
        val errors = CustomFieldValidation.validateForCreate(
            listOf(requiredTextField()), mapOf(CustomFieldId("cf-required") to cell)
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateForPatch_recordPredatesRequiredField_editingOtherFieldsIsNotBlocked() {
        // A record created BEFORE requiredSince, patched on a completely different field,
        // must not be rejected just because the required field was never filled in.
        val otherField = CustomFieldDefinition(
            id = CustomFieldId("cf-other"), tenantId = tenantId, ownerResource = OwnerResource.CRM_SALES,
            key = FieldKey("lainnya"), label = "Lainnya", type = FieldType.Text, position = 2000.0
        )
        val errors = CustomFieldValidation.validateForPatch(
            definitions = listOf(requiredTextField(), otherField),
            recordCreatedAt = fieldCreatedEarly,
            patch = mapOf(CustomFieldId("cf-other") to CustomAttributes.textCell("baru"))
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun validateForPatch_touchingRequiredFieldWithBlankValue_isRejected() {
        val errors = CustomFieldValidation.validateForPatch(
            definitions = listOf(requiredTextField()),
            recordCreatedAt = fieldCreatedEarly,
            patch = mapOf(CustomFieldId("cf-required") to CustomAttributes.textCell(""))
        )
        assertEquals(1, errors.size)
        assertTrue(errors.first() is CustomFieldValidationError.Required)
    }

    @Test
    fun validateForPatch_archivedField_isRejected() {
        val archived = requiredTextField().copy(isRequired = false, archivedAt = Instant.parse("2026-03-01T00:00:00Z"))
        val errors = CustomFieldValidation.validateForPatch(
            definitions = listOf(archived),
            recordCreatedAt = fieldCreatedEarly,
            patch = mapOf(CustomFieldId("cf-required") to CustomAttributes.textCell("x"))
        )
        assertEquals(1, errors.size)
        assertTrue(errors.first() is CustomFieldValidationError.ArchivedField)
    }

    @Test
    fun archive_systemField_isRefused() {
        val systemField = requiredTextField().copy(isSystem = true, isRequired = false, requiredSince = null)
        val result = runCatching { systemField.archive(Instant.parse("2026-03-01T00:00:00Z")) }
        assertTrue(result.isFailure)
    }

    @Test
    fun rename_touchesOnlyLabel_keyStaysStable() {
        val field = requiredTextField()
        val renamed = field.rename("Nama Baru")
        assertEquals("Nama Baru", renamed.label)
        assertEquals(field.key, renamed.key)
        assertEquals(field.id, renamed.id)
    }

    @Test
    fun singleSelect_unknownOptionId_producesError() {
        val select = CustomFieldDefinition(
            id = CustomFieldId("cf-select"), tenantId = tenantId, ownerResource = OwnerResource.CRM_SALES,
            key = FieldKey("kategori"), label = "Kategori", position = 1000.0,
            type = FieldType.SingleSelect(listOf(SelectOption(SelectOptionId("opt_a"), "A", "#2563EB")))
        )
        val errors = CustomFieldValidation.validateForCreate(
            listOf(select), mapOf(select.id to CustomAttributes.selectCell(SelectOptionId("opt_ghost")))
        )
        assertEquals(1, errors.size)
        assertTrue(errors.first() is CustomFieldValidationError.UnknownOption)
    }
}
