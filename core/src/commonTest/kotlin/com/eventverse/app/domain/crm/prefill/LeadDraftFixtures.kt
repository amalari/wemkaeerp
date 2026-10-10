package com.eventverse.app.domain.crm.prefill

import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.FieldKey
import com.eventverse.app.domain.customfield.CrmFieldType
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.customfield.SelectOption
import com.eventverse.app.domain.customfield.SelectOptionId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/** Skema field kustom tenant **bordir** (non-default, Kontrak 6): tidak ada satu pun kolom konveksi rajut. */
internal object BordirLeadFields {
    private val tenant = TenantId("ten-bordir-uji")
    private fun def(id: String, label: String, type: CrmFieldType, archived: Boolean = false) = CustomFieldDefinition(
        id = CustomFieldId(id), tenantId = tenant, ownerResource = OwnerResource.CRM_SALES, key = FieldKey(id.removePrefix("cf-").replace('-', '_')),
        label = label, type = type, position = 1000.0, archivedAt = if (archived) Instant.fromEpochMilliseconds(0) else null,
    )

    val jenisBordir = def("cf-jenis-bordir", "Jenis Bordir", CrmFieldType(FieldType.ENUM, listOf(
        SelectOption(SelectOptionId("opt_komputer"), "Bordir Komputer", "#2563EB"),
        SelectOption(SelectOptionId("opt_manual"), "Bordir Manual", "#EA580C"),
    )))
    val jumlahWarna = def("cf-jumlah-warna", "Jumlah Warna Benang", CrmFieldType(FieldType.NUMBER))
    val catatan = def("cf-catatan", "Catatan Desain", CrmFieldType(FieldType.TEXT))
    val tanggal = def("cf-deadline", "Deadline", CrmFieldType(FieldType.DATE))
    val lama = def("cf-lama", "Kolom Lama", CrmFieldType(FieldType.TEXT), archived = true)

    val all = listOf(jenisBordir, jumlahWarna, catatan, tanggal, lama)
}
