package com.eventverse.app.presentation.crm

import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.shared.json.JsonValue

/**
 * Projects a [CrmLead]'s strongly-typed core columns into the same tagged-cell shape custom
 * fields already use, so [LeadFieldDescriptor]-driven UI (the inspector) renders core and
 * custom fields through ONE code path. This is the seam described in [LeadFieldDescriptor]'s
 * KDoc — the domain stays genuinely typed underneath; only this read model is uniform.
 */
object LeadFieldProjection {

    /** Every field of [lead] (core + custom) as a tagged cell, keyed by [LeadFieldDescriptor.fieldId]. */
    fun cellsOf(lead: CrmLead): Map<String, JsonValue.Obj?> {
        val core = mapOf(
            LeadFieldDescriptor.coreFieldId("brand_name") to CustomAttributes.textCell(lead.brandName.value),
            LeadFieldDescriptor.coreFieldId("contact_person") to
                lead.contactPerson.takeIf { it.isNotBlank() }?.let { CustomAttributes.textCell(it) },
            LeadFieldDescriptor.coreFieldId("whatsapp_number") to
                lead.whatsappNumber?.let { CustomAttributes.textCell(it.value) },
            LeadFieldDescriptor.coreFieldId("email") to
                lead.email.takeIf { it.isNotBlank() }?.let { CustomAttributes.textCell(it) },
            LeadFieldDescriptor.coreFieldId("stage") to CustomAttributes.textCell(lead.stage.displayName),
            LeadFieldDescriptor.coreFieldId("source") to
                lead.source.value.takeIf { it.isNotBlank() }?.let { CustomAttributes.textCell(it) },
            LeadFieldDescriptor.coreFieldId("estimated_pcs") to
                lead.estimatedPcs?.let { CustomAttributes.numberCell(it.toString()) },
            LeadFieldDescriptor.coreFieldId("estimated_value_idr") to
                lead.estimatedValue?.let { CustomAttributes.numberCell(it.amount.toString()) },
            LeadFieldDescriptor.coreFieldId("owner_employee_id") to
                lead.ownerEmployeeId?.let { CustomAttributes.textCell(it.value) },
            LeadFieldDescriptor.coreFieldId("expected_close_date") to
                lead.expectedCloseDate?.let { CustomAttributes.textCell(it.toString()) }
        )

        val custom = lead.customAttributes.toJsonValue().entries.mapValues { (_, v) -> v as? JsonValue.Obj }

        return core + custom
    }

    /** True when [fieldId] is a strongly-typed core column rather than a tenant custom field. */
    fun isCoreField(fieldId: String): Boolean = LeadFieldDescriptor.isCoreFieldId(fieldId)

    fun toCustomFieldId(fieldId: String): CustomFieldId? =
        if (isCoreField(fieldId)) null else CustomFieldId(fieldId)
}
