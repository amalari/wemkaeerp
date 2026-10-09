package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tes paritas kosakata **CRM** (`sealed interface FieldType`, field-component-rules Kontrak 7).
 *
 * Sealed tidak punya `entries`, jadi pagarnya dua lapis: [coverage] adalah `when` **tanpa `else`** (varian baru
 * membuat berkas ini gagal kompilasi), dan [samples] wajib mencakup seluruh `FieldType.ALL_CODES`.
 * Kosakata ini tidak punya generator SQL: tipe disimpan sebagai JSONB (`field_type` + `config`), jadi paritas
 * "pemetaan SQL" tidak berlaku; yang diuji adalah codec konfigurasi, definisi, validasi nilai, dan konversi.
 * Tenant uji: bordir (non-garment-default), pemilik sumber daya `master_data_material`.
 */
class CrmFieldTypeParityTest {

    private val tenant = TenantId("ten-bordir-uji")
    private val optionId = SelectOptionId("opt_satin")
    private val select = FieldType.SingleSelect(
        listOf(SelectOption(optionId, "Benang satin", "#2563EB"), SelectOption(SelectOptionId("opt_lama"), "Lama", "#16A34A", "2026-01-01T00:00:00Z"))
    )

    /** Satu contoh per varian, termasuk parameter non-default. */
    private val samples: List<FieldType> = listOf(
        FieldType.Text, FieldType.LongText,
        FieldType.Number(NumberFormat.Currency("IDR"), 2), FieldType.Number(NumberFormat.Percent, 1), FieldType.Number(),
        select, FieldType.DateField(withTime = true), FieldType.DateField(), FieldType.Checkbox, FieldType.UserRef(maxCount = 3),
        FieldType.Relation(targetResource = "leads", maxCount = 2)
    )

    /** Pagar kompilator: tanpa `else`, varian baru wajib ditambahkan di sini dan di [samples]. */
    private fun coverage(t: FieldType): String = when (t) {
        is FieldType.Text -> "TEXT"
        is FieldType.LongText -> "LONG_TEXT"
        is FieldType.Number -> "NUMBER"
        is FieldType.SingleSelect -> "SINGLE_SELECT"
        is FieldType.DateField -> "DATE"
        is FieldType.Checkbox -> "CHECKBOX"
        is FieldType.UserRef -> "USER_REF"
        is FieldType.Relation -> "RELATION"
    }

    private fun def(type: FieldType, id: String = "cf-${type.code.lowercase()}") = CustomFieldDefinition(
        id = CustomFieldId(id), tenantId = tenant, ownerResource = OwnerResource.MASTER_DATA_MATERIAL,
        key = FieldKey("kolom_${type.code.lowercase()}"), label = "Kolom ${type.code}", type = type, position = 1000.0
    )

    @Test
    fun samples_allVariants_coverEveryDeclaredCode() {
        assertEquals(FieldType.ALL_CODES, samples.map { it.code }.toSet())
        samples.forEach { assertEquals(it.code, coverage(it)) }
    }

    @Test
    fun configCodec_everySample_roundTripsWithParameters() {
        samples.forEach { t ->
            assertEquals(t, CustomAttributesCodec.decodeFieldType(t.code, CustomAttributesCodec.encodeConfig(t)), "round-trip ${t.code}")
        }
    }

    @Test
    fun definitionCodec_everySample_roundTripsViaJsonTextForNonDefaultTenant() {
        val defs = samples.mapIndexed { i, t -> def(t, "cf-$i") }
        val decoded = CustomAttributesCodec.decodeDefinitions(tenant, CustomAttributesCodec.encodeDefinitions(defs))
        assertEquals(defs.map { it.type }, decoded.map { it.type })
        assertTrue(decoded.all { it.tenantId == tenant && it.ownerResource == OwnerResource.MASTER_DATA_MATERIAL })
    }

    @Test
    fun decodeFieldType_unknownCode_returnsNullAndNeverFallsBackToText() {
        listOf("CURRENCY", "MULTI_SELECT", "FILE", "text", "Text", "", " TEXT").forEach { code ->
            assertNull(CustomAttributesCodec.decodeFieldType(code, JsonValue.Obj(emptyMap())), "kode '$code' harus ditolak")
        }
    }

    /** C7 (TRD-FIELD-001 §4.3): RELATION tanpa `targetResource` = korupsi (null), BUKAN fallback ke tipe lain. */
    @Test
    fun decodeFieldType_relationWithoutTargetResource_returnsNull() {
        assertNull(CustomAttributesCodec.decodeFieldType("RELATION", JsonValue.Obj(emptyMap())))
        assertNull(CustomAttributesCodec.decodeFieldType("RELATION", jsonObjectOf("maxCount" to jsonOf(2))))
        assertEquals(
            FieldType.Relation(targetResource = "leads", maxCount = 2),
            CustomAttributesCodec.decodeFieldType("RELATION", jsonObjectOf("targetResource" to jsonOf("leads"), "maxCount" to jsonOf(2)))
        )
    }

    @Test
    fun decodeDefinition_unknownTypeCode_returnsNull() {
        val encoded = CustomAttributesCodec.encodeDefinition(def(FieldType.Text))
        val tampered = JsonValue.Obj(encoded.entries + ("type" to jsonOf("MULTI_SELECT")))
        assertNull(CustomAttributesCodec.decodeDefinition(tenant, tampered))
    }

    @Test
    fun decodeDefinition_knownTypeCode_isNotNull() {
        samples.forEach { assertNotNull(CustomAttributesCodec.decodeDefinition(tenant, CustomAttributesCodec.encodeDefinition(def(it)))) }
    }

    /** Format tak dikenal, atau currency tanpa kode, ditolak — bukan dibaca diam-diam sebagai Plain (Kontrak 8, D4). */
    @Test
    fun decodeFieldType_numberWithUnknownFormat_isRejectedNotPlain() {
        val unknown = jsonObjectOf("format" to jsonOf("euro"), "decimals" to jsonOf(0))
        assertNull(CustomAttributesCodec.decodeFieldType("NUMBER", unknown))
        val currencyWithoutCode = jsonObjectOf("format" to jsonOf("currency"), "decimals" to jsonOf(0))
        assertNull(CustomAttributesCodec.decodeFieldType("NUMBER", currencyWithoutCode))
    }

    /** Baris lama tanpa kunci `format` tetap terbaca sebagai angka polos: kunci hilang bukan nilai tak dikenal. */
    @Test
    fun decodeFieldType_numberWithoutFormatKey_isPlain() {
        val legacy = jsonObjectOf("decimals" to jsonOf(2))
        assertEquals(FieldType.Number(NumberFormat.Plain, 2), CustomAttributesCodec.decodeFieldType("NUMBER", legacy))
    }

    // ---- validasi nilai per tipe ----------------------------------------------------------------

    private fun validCell(t: FieldType): JsonValue.Obj = when (t) {
        is FieldType.Text, is FieldType.LongText -> CustomAttributes.textCell("catatan")
        is FieldType.Number -> CustomAttributes.numberCell("12.5")
        is FieldType.SingleSelect -> CustomAttributes.selectCell(optionId)
        is FieldType.DateField -> CustomAttributes.dateCell(LocalDate(2026, 10, 8))
        is FieldType.Checkbox -> CustomAttributes.checkboxCell(true)
        is FieldType.UserRef -> CustomAttributes.textCell("user-1")
        is FieldType.Relation -> CustomAttributes.textCell("lead-1")
    }

    private fun invalidCell(t: FieldType): JsonValue.Obj = when (t) {
        is FieldType.Text, is FieldType.LongText -> CustomAttributes.numberCell("1")
        is FieldType.Number -> CustomAttributes.textCell("bukan angka")
        is FieldType.SingleSelect -> CustomAttributes.selectCell(SelectOptionId("opt_hantu"))
        is FieldType.DateField -> CustomAttributes.textCell("bukan-tanggal")
        is FieldType.Checkbox -> CustomAttributes.textCell("ya")
        is FieldType.UserRef -> CustomAttributes.numberCell("1")
        is FieldType.Relation -> CustomAttributes.numberCell("1")
    }

    @Test
    fun validation_everySample_acceptsValidCell() {
        samples.forEach { t ->
            val d = def(t)
            assertEquals(emptyList(), CustomFieldValidation.validateForCreate(listOf(d), mapOf(d.id to validCell(t))), t.code)
        }
    }

    @Test
    fun validation_everySample_rejectsMismatchedCell() {
        samples.forEach { t ->
            val d = def(t)
            val errors = CustomFieldValidation.validateForCreate(listOf(d), mapOf(d.id to invalidCell(t)))
            assertEquals(1, errors.size, "${t.code} harus menolak nilai tak cocok")
        }
    }

    // ---- konversi tipe --------------------------------------------------------------------------

    @Test
    fun conversion_everyPairOfSamples_classifiesWithoutThrowing() {
        samples.forEach { from -> samples.forEach { to -> FieldTypeConversion.classify(from, to) } }
    }

    @Test
    fun conversion_everySample_toItself_isIdentity() {
        samples.forEach { assertEquals(ConversionSafety.IDENTITY, FieldTypeConversion.classify(it, it), it.code) }
    }

    /** C7 (TRD-FIELD-001 FR-5): konversi dari/ke Relation = FORBIDDEN (padanan UserRef) — baris link
     *  `custom_field_relation_links` tidak boleh hilang senyap lewat ganti tipe. */
    @Test
    fun conversion_toOrFromRelation_isForbidden() {
        samples.filter { it !is FieldType.Relation }.forEach { other ->
            assertEquals(ConversionSafety.FORBIDDEN, FieldTypeConversion.classify(other, FieldType.Relation(targetResource = "leads")), "dari ${other.code}")
            assertEquals(ConversionSafety.FORBIDDEN, FieldTypeConversion.classify(FieldType.Relation(targetResource = "leads"), other), "ke ${other.code}")
        }
    }
}
