package com.eventverse.app.domain.crm.prefill

import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.CustomFieldDefinition
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.domain.customfield.FieldType
import com.eventverse.app.shared.json.JsonValue

/**
 * Nilai mentah ekstraktor → [LeadDraft] (TRD-HELP-002 FR-3). Batasnya **sama** dengan form manual dan kolom DB
 * (`CreateLeadDialog`, `CrmLeadsTable`); nilai yang gagal dikosongkan + [DraftIssue], tidak pernah dipaksa masuk.
 * Opsi pilihan dicocokkan tepat dengan label (abaikan huruf besar/kecil) — tidak ada pencocokan "paling mirip".
 */
object LeadDraftSanitizer {
    const val MAX_NAME = 150
    const val MAX_CATEGORY = 100
    const val MAX_CUSTOM_TEXT = 1000

    /**
     * Field kustom yang didukung pilot (K4): Teks, Teks panjang, Angka, Pilihan tunggal — yang aktif saja.
     * C7 (TRD-FIELD-001 FR-6): `Relation` (dan `UserRef`/`File`) **tidak** didukung prefill AI — model tak boleh
     * mengarang id rujukan; field itu dikecualikan di sini, jadi nilai mentahnya tak pernah dikonsumsi.
     */
    fun supportedCustomFields(definitions: List<CustomFieldDefinition>): List<CustomFieldDefinition> =
        definitions.filter { !it.isArchived && (it.type is FieldType.Text || it.type is FieldType.LongText || it.type is FieldType.Number || it.type is FieldType.SingleSelect) }

    /** `when` tanpa `else` (Kontrak 6): tipe yang tak didukung tak pernah sampai ke sini (disaring [supportedCustomFields]). */
    fun specsFor(definitions: List<CustomFieldDefinition>): List<DraftFieldSpec> = CORE_SPECS + supportedCustomFields(definitions).map { d ->
        when (val t = d.type) {
            is FieldType.Text, is FieldType.LongText -> DraftFieldSpec(d.id.value, d.label, DraftFieldKind.TEXT)
            is FieldType.Number -> DraftFieldSpec(d.id.value, d.label, DraftFieldKind.NUMBER)
            is FieldType.SingleSelect -> DraftFieldSpec(d.id.value, d.label, DraftFieldKind.SELECT, t.activeOptions.map { it.label })
            // Tak didukung prefill AI; tak pernah tercapai. Jaga fail-closed bila daftar dukungan berubah.
            is FieldType.DateField, is FieldType.Checkbox, is FieldType.UserRef, is FieldType.Relation, is FieldType.File ->
                error("Tipe ${t.code} tidak didukung prefill AI")
        }
    }

    fun sanitize(raw: Map<String, String>, definitions: List<CustomFieldDefinition>, agentRef: String, partial: Boolean): LeadDraft {
        val issues = mutableListOf<DraftIssue>()
        fun text(key: String, max: Int): String? = raw[key]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.length <= max) it else null.also { _ -> issues += DraftIssue(key, "Lebih dari $max karakter - tidak diisi") }
        }

        val whatsapp = raw[LeadDraftFields.WHATSAPP]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            WhatsappNumber.parse(it) ?: null.also { _ -> issues += DraftIssue(LeadDraftFields.WHATSAPP, "Nomor \"$it\" bukan nomor HP Indonesia yang sah") }
        }
        val email = text(LeadDraftFields.EMAIL, MAX_NAME)?.let {
            if (it.contains("@") && it.contains(".") && it.length >= 5) it
            else null.also { _ -> issues += DraftIssue(LeadDraftFields.EMAIL, "\"$it\" bukan alamat email yang sah") }
        }
        val pcs = raw[LeadDraftFields.ESTIMATED_PCS]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            it.replace(".", "").replace(",", "").toIntOrNull()?.takeIf { n -> n >= 0 }
                ?: null.also { _ -> issues += DraftIssue(LeadDraftFields.ESTIMATED_PCS, "\"$it\" bukan jumlah pcs yang sah") }
        }

        val custom = mutableMapOf<CustomFieldId, JsonValue.Obj>()
        supportedCustomFields(definitions).forEach { d ->
            val value = raw[d.id.value]?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            customCell(d, value)?.let { custom[d.id] = it }
                ?: run { issues += DraftIssue(d.id.value, "\"$value\" tidak cocok untuk kolom ${d.label}") }
        }

        return LeadDraft(
            brandName = text(LeadDraftFields.BRAND_NAME, MAX_NAME),
            contactPerson = text(LeadDraftFields.CONTACT_PERSON, MAX_NAME),
            whatsappNumber = whatsapp,
            email = email,
            productCategory = text(LeadDraftFields.PRODUCT_CATEGORY, MAX_CATEGORY),
            estimatedPcs = pcs,
            customValues = custom,
            issues = issues,
            agentRef = agentRef,
            partial = partial,
        )
    }

    private fun customCell(d: CustomFieldDefinition, value: String): JsonValue.Obj? = when (val t = d.type) {
        is FieldType.Text, is FieldType.LongText -> value.takeIf { it.length <= MAX_CUSTOM_TEXT }?.let(CustomAttributes::textCell)
        is FieldType.Number -> value.replace(",", ".").toDoubleOrNull()?.let { n ->
            val raw = if (t.decimals == 0) n.toLong().toString() else n.toString()
            if (t.decimals == 0 && n % 1.0 != 0.0) null else CustomAttributes.numberCell(raw)
        }
        is FieldType.SingleSelect -> t.activeOptions.firstOrNull { it.label.equals(value, ignoreCase = true) }?.let { CustomAttributes.selectCell(it.id) }
        // C7: RELATION (dan tipe non-teks lain) tidak didukung prefill AI — **jangan mengarang rujukan**.
        // Juga tak pernah sampai sini (disaring supportedCustomFields); cabang eksplisit menggantikan `else`.
        is FieldType.DateField, is FieldType.Checkbox, is FieldType.UserRef, is FieldType.Relation, is FieldType.File -> null
    }

    private val CORE_SPECS = listOf(
        DraftFieldSpec(LeadDraftFields.BRAND_NAME, "Nama brand/perusahaan", DraftFieldKind.TEXT),
        DraftFieldSpec(LeadDraftFields.CONTACT_PERSON, "Nama kontak (orang)", DraftFieldKind.TEXT),
        DraftFieldSpec(LeadDraftFields.WHATSAPP, "Nomor HP/WhatsApp", DraftFieldKind.TEXT),
        DraftFieldSpec(LeadDraftFields.EMAIL, "Email", DraftFieldKind.TEXT),
        DraftFieldSpec(LeadDraftFields.PRODUCT_CATEGORY, "Kategori produk yang diminta", DraftFieldKind.TEXT),
        DraftFieldSpec(LeadDraftFields.ESTIMATED_PCS, "Perkiraan jumlah (pcs)", DraftFieldKind.NUMBER),
    )
}
