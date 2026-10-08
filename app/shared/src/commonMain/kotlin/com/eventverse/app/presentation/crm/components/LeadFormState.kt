package com.eventverse.app.presentation.crm.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eventverse.app.domain.crm.LeadCreationChannel
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.crm.prefill.LeadDraft
import com.eventverse.app.domain.crm.prefill.LeadDraftFields
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.domain.customfield.SelectOptionId
import com.eventverse.app.shared.json.JsonValue

/**
 * Isi form lead baru. Diangkat dari `CreateLeadDialog` supaya draf AI bisa diterapkan tanpa dialog tahu asal nilainya.
 *
 * [aiFilled] = kunci field yang nilainya masih dari draf AI; mengetik di field itu menghapus tandanya (FR-5).
 * [usedAiDraft] menentukan `createdVia`: lead yang field-nya pernah diisi draf AI tercatat `AI_DRAFT` walau
 * semuanya dikoreksi — jujur tentang asal, dan tetap disimpan oleh user.
 */
@Stable
class LeadFormState(initialStage: LeadStage) {
    var stage by mutableStateOf(if (initialStage == LeadStage.FOLLOW_UP) LeadStage.FOLLOW_UP else LeadStage.NEW_LEAD)
    var brandName by mutableStateOf(""); private set
    var contactPerson by mutableStateOf(""); private set
    var phone by mutableStateOf(""); private set
    var email by mutableStateOf(""); private set
    var productCategory by mutableStateOf(""); private set
    var pcs by mutableStateOf(""); private set
    /** Nilai mentah field kustom: teks / angka / id opsi. */
    val custom = mutableStateMapOf<CustomFieldId, String>()
    val aiFilled = mutableStateListOf<String>()
    var usedAiDraft by mutableStateOf(false); private set

    fun update(key: String, value: String) {
        when (key) {
            LeadDraftFields.BRAND_NAME -> brandName = value
            LeadDraftFields.CONTACT_PERSON -> contactPerson = value
            LeadDraftFields.WHATSAPP -> phone = value
            LeadDraftFields.EMAIL -> email = value
            LeadDraftFields.PRODUCT_CATEGORY -> productCategory = value
            LeadDraftFields.ESTIMATED_PCS -> pcs = value.filter { it.isDigit() }
            else -> custom[CustomFieldId(key)] = value
        }
        aiFilled.remove(key)
    }

    /** Hanya field yang diisi draf yang ditimpa; field lain (mungkin sudah diketik user) dibiarkan. */
    fun applyDraft(draft: LeadDraft) {
        val values = buildMap {
            draft.brandName?.let { put(LeadDraftFields.BRAND_NAME, it) }
            draft.contactPerson?.let { put(LeadDraftFields.CONTACT_PERSON, it) }
            draft.whatsappNumber?.let { put(LeadDraftFields.WHATSAPP, it.localDisplay) }
            draft.email?.let { put(LeadDraftFields.EMAIL, it) }
            draft.productCategory?.let { put(LeadDraftFields.PRODUCT_CATEGORY, it) }
            draft.estimatedPcs?.let { put(LeadDraftFields.ESTIMATED_PCS, it.toString()) }
            draft.customValues.forEach { (id, cell) -> cellText(cell)?.let { put(id.value, it) } }
        }
        values.forEach { (k, v) -> update(k, v) }
        aiFilled.addAll(values.keys)
        if (values.isNotEmpty()) usedAiDraft = true
    }

    fun isAi(key: String): Boolean = key in aiFilled

    val isPhoneValid: Boolean get() = phone.isBlank() || WhatsappNumber.isValidIndonesianPhone(phone.trim())
    val isEmailValid: Boolean get() = email.isBlank() || (email.contains("@") && email.contains(".") && email.trim().length >= 5)

    fun canSubmit(schema: List<LeadFieldDescriptor>): Boolean {
        val hasIdentifier = brandName.isNotBlank() || contactPerson.isNotBlank() || phone.isNotBlank()
        val customOk = schema.filter { supportsInput(it.type) }.all { f ->
            val raw = custom[CustomFieldId(f.fieldId)].orEmpty().trim()
            (!f.isRequired || raw.isNotEmpty()) && when (f.type) {
                is FieldType.Number -> raw.isEmpty() || raw.replace(",", ".").toDoubleOrNull() != null
                is FieldType.DateField -> isBlankOrIsoDate(raw)
                is FieldType.Text, is FieldType.LongText, is FieldType.SingleSelect -> true
                // Tidak dirender di dialog lead baru (supportsInput) — tidak ada nilai untuk divalidasi.
                is FieldType.Checkbox, is FieldType.UserRef -> true
            }
        }
        return hasIdentifier && isPhoneValid && isEmailValid && customOk
    }

    /** Sel field kustom untuk `CreateLeadRequest`; kosong = tidak dikirim. Validasi akhir tetap di server. */
    fun customValues(schema: List<LeadFieldDescriptor>): Map<CustomFieldId, JsonValue.Obj?> = schema.mapNotNull { f ->
        val id = CustomFieldId(f.fieldId)
        val raw = custom[id]?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val cell = when (f.type) {
            is FieldType.Number -> raw.replace(",", ".").toDoubleOrNull()?.let { CustomAttributes.numberCell(raw.replace(",", ".")) }
            is FieldType.SingleSelect -> CustomAttributes.selectCell(SelectOptionId(raw))
            else -> CustomAttributes.textCell(raw)
        } ?: return@mapNotNull null
        id to cell
    }.toMap()

    val createdVia: LeadCreationChannel get() = if (usedAiDraft) LeadCreationChannel.AI_DRAFT else LeadCreationChannel.MANUAL

    private fun cellText(cell: JsonValue.Obj): String? = when (val v = cell.entries["v"]) {
        is JsonValue.Str -> v.value
        is JsonValue.Num -> v.raw
        else -> null
    }

    companion object {
        /** Kontrol yang dirender di dialog lead baru. UserRef butuh daftar karyawan; Checkbox & tanggal berwaktu menyusul. */
        private val CREATE_FORM_CONTROLS = setOf(
            LeadFieldControl.TEXT,
            LeadFieldControl.NUMBER,
            LeadFieldControl.SINGLE_SELECT,
            LeadFieldControl.DATE_PICKER
        )

        /**
         * Field kustom yang punya input di dialog (TRD-HELP-002 K4). Rute lewat [leadFieldControl]
         * supaya paritas tipe→kontrol satu sumber kebenaran; tanggal tanpa waktu masuk lewat
         * `ClayDatePicker` (Irisan 1 Track C), tanggal berwaktu (`DATE_TIME_TEXT`) belum.
         */
        fun supportsInput(type: FieldType): Boolean = leadFieldControl(type) in CREATE_FORM_CONTROLS
    }
}
