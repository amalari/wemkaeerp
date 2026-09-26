package com.eventverse.app.domain.crm

import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.FieldType

/**
 * One field of a lead's form — core or custom — projected to the SAME shape so the UI
 * layer (and any future generic renderer) never needs to know which storage a field lives
 * in. This is the seam that keeps the hybrid model from turning into two UI code paths:
 * a domain that is genuinely typed underneath, with one uniform read model on top.
 *
 * [fieldId] starts with `"core:"` for a strongly-typed column (e.g. `"core:brand_name"`) or
 * is a bare [com.eventverse.app.domain.customfield.CustomFieldId] value for a custom field.
 * A PATCH dispatches on that prefix: `core:` -> a typed setter on [CrmLead] (through domain
 * validation), `cf-...` -> [com.eventverse.app.domain.customfield.CustomAttributes] coercion.
 */
data class LeadFieldDescriptor(
    val fieldId: String,
    val label: String,
    val type: FieldType,
    val isRequired: Boolean,
    /** Core fields with business logic hanging off them (owner, stage, ...) cannot be deleted by a tenant admin. */
    val isEditable: Boolean,
    val isDeletable: Boolean,
    val isCore: Boolean
) {
    companion object {
        private const val CORE_PREFIX = "core:"

        fun isCoreFieldId(fieldId: String): Boolean = fieldId.startsWith(CORE_PREFIX)

        fun coreFieldId(name: String): String = "$CORE_PREFIX$name"

        /** The fixed, strongly-typed core fields of a CRM lead, in display order. */
        fun coreFields(): List<LeadFieldDescriptor> = listOf(
            LeadFieldDescriptor(coreFieldId("brand_name"), "Nama Brand/Perusahaan", FieldType.Text, isRequired = false, isEditable = true, isDeletable = false, isCore = true),
            LeadFieldDescriptor(coreFieldId("contact_person"), "Nama Kontak", FieldType.Text, isRequired = false, isEditable = true, isDeletable = false, isCore = true),
            LeadFieldDescriptor(coreFieldId("whatsapp_number"), "Nomor Handphone", FieldType.Text, isRequired = false, isEditable = true, isDeletable = false, isCore = true),
            LeadFieldDescriptor(coreFieldId("email"), "Email", FieldType.Text, isRequired = false, isEditable = true, isDeletable = false, isCore = true),
            LeadFieldDescriptor(coreFieldId("stage"), "Tahap", FieldType.Text, isRequired = true, isEditable = true, isDeletable = false, isCore = true),
            LeadFieldDescriptor(coreFieldId("source"), "Sumber Lead", FieldType.Text, isRequired = false, isEditable = true, isDeletable = false, isCore = true),
            LeadFieldDescriptor(coreFieldId("estimated_pcs"), "Estimasi Jumlah (pcs)", FieldType.Number(), isRequired = false, isEditable = true, isDeletable = false, isCore = true),
            LeadFieldDescriptor(coreFieldId("estimated_value_idr"), "Estimasi Nilai (Rp)", FieldType.Number(format = com.eventverse.app.domain.customfield.NumberFormat.Currency("IDR")), isRequired = false, isEditable = true, isDeletable = false, isCore = true),
            LeadFieldDescriptor(coreFieldId("owner_employee_id"), "PIC Sales", FieldType.UserRef(), isRequired = false, isEditable = true, isDeletable = false, isCore = true),
            LeadFieldDescriptor(coreFieldId("expected_close_date"), "Target Closing", FieldType.DateField(), isRequired = false, isEditable = true, isDeletable = false, isCore = true)
        )

        fun fromCustomField(def: CustomFieldDefinition): LeadFieldDescriptor = LeadFieldDescriptor(
            fieldId = def.id.value,
            label = def.label,
            type = def.type,
            isRequired = def.isRequired,
            isEditable = true,
            isDeletable = true,
            isCore = false
        )
    }
}
