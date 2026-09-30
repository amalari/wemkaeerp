package com.eventverse.app.domain.crm.prefill

import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.FieldKey
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.customfield.OwnerResource
import com.eventverse.app.domain.customfield.SelectOption
import com.eventverse.app.domain.customfield.SelectOptionId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

/** Skema field kustom tenant **bordir** (non-default, Kontrak 6): tidak ada satu pun kolom konveksi rajut. */
internal object BordirLeadFields {
    private val tenant = TenantId("ten-bordir-uji")
    private fun def(id: String, label: String, type: FieldType, archived: Boolean = false) = CustomFieldDefinition(
        id = CustomFieldId(id), tenantId = tenant, ownerResource = OwnerResource.CRM_SALES, key = FieldKey(id.removePrefix("cf-").replace('-', '_')),
        label = label, type = type, position = 1000.0, archivedAt = if (archived) Instant.fromEpochMilliseconds(0) else null,
    )

    val jenisBordir = def("cf-jenis-bordir", "Jenis Bordir", FieldType.SingleSelect(listOf(
        SelectOption(SelectOptionId("opt_komputer"), "Bordir Komputer", "#2563EB"),
        SelectOption(SelectOptionId("opt_manual"), "Bordir Manual", "#EA580C"),
    )))
    val jumlahWarna = def("cf-jumlah-warna", "Jumlah Warna Benang", FieldType.Number())
    val catatan = def("cf-catatan", "Catatan Desain", FieldType.Text)
    val tanggal = def("cf-deadline", "Deadline", FieldType.DateField())
    val lama = def("cf-lama", "Kolom Lama", FieldType.Text, archived = true)

    val all = listOf(jenisBordir, jumlahWarna, catatan, tanggal, lama)
}
