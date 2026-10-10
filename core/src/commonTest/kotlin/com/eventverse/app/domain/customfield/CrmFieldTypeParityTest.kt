package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tes paritas kosakata **CRM** (`CrmFieldType` — registry tunggal = enum prototype `FieldType`,
 * diputuskan 2026-10-10, `PLAN-unify-field-vocabulary.md`; field-component-rules Kontrak 7).
 *
 * Pagarnya dua lapis: [coverage] adalah `when (kind)` **tanpa `else`** atas SELURUH entri enum
 * (tipe baru membuat berkas ini gagal kompilasi), dan [samples] wajib memuat satu contoh per kind.
 * Tipe disimpan sebagai JSONB (`field_type` + `config`); yang diuji adalah codec, validasi nilai,
 * konversi, dan kompatibilitas kode legacy. Tenant uji: bordir (non-garment-default).
 */
class CrmFieldTypeParityTest {

    private val tenant = TenantId("ten-bordir-uji")
    private val optionId = SelectOptionId("opt_satin")
    private val selectOptions = listOf(
        SelectOption(optionId, "Benang satin", "#2563EB"),
        SelectOption(SelectOptionId("opt_lama"), "Lama", "#16A34A", "2026-01-01T00:00:00Z")
    )

    /** Satu contoh per kind (11 tipe), termasuk parameter non-default. */
    private val samples: List<CrmFieldType> = listOf(
        CrmFieldType(FieldType.TEXT), CrmFieldType(FieldType.LONG_TEXT),
        CrmFieldType(FieldType.NUMBER, format = NumberFormat.Currency("IDR"), decimals = 2),
        CrmFieldType(FieldType.NUMBER, format = NumberFormat.Percent, decimals = 1), CrmFieldType(FieldType.NUMBER),
        CrmFieldType(FieldType.ENUM, options = selectOptions),
        CrmFieldType(FieldType.DATE, withTime = true), CrmFieldType(FieldType.DATE),
        CrmFieldType(FieldType.TIME),
        CrmFieldType(FieldType.MULTI_SELECT, options = selectOptions, maxSelections = 2),
        CrmFieldType(FieldType.BOOL),
        CrmFieldType(FieldType.USER_REF, maxCount = 3),
        CrmFieldType(FieldType.RELATION, targetResource = "leads", maxCount = 2),
        CrmFieldType(FieldType.FILE)
    )

    /** Pagar kompilator: tanpa `else`, kind baru wajib ditambahkan di sini dan di [samples]. */
    private fun coverage(t: FieldType): String = when (t) {
        FieldType.TEXT -> "TEXT"
        FieldType.LONG_TEXT -> "LONG_TEXT"
        FieldType.NUMBER -> "NUMBER"
        FieldType.DATE -> "DATE"
        FieldType.TIME -> "TIME"
        FieldType.ENUM -> "ENUM"
        FieldType.MULTI_SELECT -> "MULTI_SELECT"
        FieldType.BOOL -> "BOOL"
        FieldType.USER_REF -> "USER_REF"
        FieldType.RELATION -> "RELATION"
        FieldType.FILE -> "FILE"
    }

    private fun def(type: CrmFieldType, id: String = "cf-${type.code.lowercase()}") = CustomFieldDefinition(
        id = CustomFieldId(id), tenantId = tenant, ownerResource = OwnerResource.MASTER_DATA_MATERIAL,
        key = FieldKey("kolom_${type.code.lowercase()}"), label = "Kolom ${type.code}", type = type, position = 1000.0
    )

    @Test
    fun samples_allKinds_coverEveryEnumEntry() {
        assertEquals(FieldType.entries.map { it.name }.toSet(), samples.map { it.code }.toSet())
        samples.forEach { assertEquals(it.code, coverage(it.kind)) }
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

    /** Kode legacy tersimpan tetap terbaca (tanpa migrasi data): tulisan baru = nama enum. */
    @Test
    fun configCodec_legacyCodes_stillRead() {
        val legacySelect = CustomAttributesCodec.decodeFieldType(
            "SINGLE_SELECT",
            CustomAttributesCodec.encodeConfig(CrmFieldType(FieldType.ENUM, options = selectOptions))
        )
        assertEquals(CrmFieldType(FieldType.ENUM, options = selectOptions), legacySelect)
        assertEquals(CrmFieldType(FieldType.BOOL), CustomAttributesCodec.decodeFieldType("CHECKBOX", JsonValue.Obj(emptyMap())))
        // Tulisan baru memakai nama enum — baris CHECKBOX lama tidak ditulis ulang.
        assertEquals("BOOL", CrmFieldType(FieldType.BOOL).code)
        assertEquals("ENUM", CrmFieldType(FieldType.ENUM).code)
    }

    /** C7: sel RELATION = id record target string; dibaca kembali lewat [CustomAttributes.relation], tanpa karangan. */
    @Test
    fun customAttributes_relationCell_roundTripsAndReadsBack() {
        val id = CustomFieldId("cf-relation")
        val attrs = CustomAttributes.EMPTY.with(id, CustomAttributes.relationCell("lead-9"))
        assertEquals("lead-9", attrs.relation(id))
        assertEquals("relation", attrs.rawCell(id)?.string("t"))
    }

    @Test
    fun decodeFieldType_unknownCode_returnsNullAndNeverFallsBackToText() {
        listOf("CURRENCY", "FORMULA", "text", "Text", "", " TEXT").forEach { code ->
            assertNull(CustomAttributesCodec.decodeFieldType(code, JsonValue.Obj(emptyMap())), "kode '$code' harus ditolak")
        }
    }

    /** C7 (TRD-FIELD-001 §4.3): RELATION tanpa `targetResource` = korupsi (null), BUKAN fallback ke tipe lain. */
    @Test
    fun decodeFieldType_relationWithoutTargetResource_returnsNull() {
        assertNull(CustomAttributesCodec.decodeFieldType("RELATION", JsonValue.Obj(emptyMap())))
        assertNull(CustomAttributesCodec.decodeFieldType("RELATION", jsonObjectOf("maxCount" to jsonOf(2))))
        assertEquals(
            CrmFieldType(FieldType.RELATION, targetResource = "leads", maxCount = 2),
            CustomAttributesCodec.decodeFieldType("RELATION", jsonObjectOf("targetResource" to jsonOf("leads"), "maxCount" to jsonOf(2)))
        )
    }

    @Test
    fun decodeDefinition_unknownTypeCode_returnsNull() {
        val encoded = CustomAttributesCodec.encodeDefinition(def(CrmFieldType(FieldType.TEXT)))
        val tampered = JsonValue.Obj(encoded.entries + ("type" to jsonOf("FORMULA")))
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
        assertEquals(
            CrmFieldType(FieldType.NUMBER, format = NumberFormat.Plain, decimals = 2),
            CustomAttributesCodec.decodeFieldType("NUMBER", legacy)
        )
    }

    // ---- validasi nilai per tipe ----------------------------------------------------------------

    private fun validCell(t: CrmFieldType): JsonValue.Obj = when (t.kind) {
        FieldType.TEXT, FieldType.LONG_TEXT -> CustomAttributes.textCell("catatan")
        FieldType.NUMBER -> CustomAttributes.numberCell("12.5")
        FieldType.ENUM -> CustomAttributes.selectCell(optionId)
        // C6 (Irisan 2): tanggal berwaktu wajib TTTT-BB-HH'T'JJ:MM; tanggal-saja untuk varian polos.
        FieldType.DATE ->
            if (t.withTime) CustomAttributes.dateTimeCell(LocalDateTime(2026, 10, 8, 9, 30))
            else CustomAttributes.dateCell(LocalDate(2026, 10, 8))
        FieldType.TIME -> CustomAttributes.textCell("09:30")
        FieldType.MULTI_SELECT -> CustomAttributes.textCell("[\"opt_satin\"]")
        FieldType.BOOL -> CustomAttributes.checkboxCell(true)
        FieldType.USER_REF -> CustomAttributes.textCell("user-1")
        // C7: sel RELATION = id record target bertag `relation`; keberadaan diverifikasi server (Track B).
        FieldType.RELATION -> CustomAttributes.relationCell("lead-1")
        FieldType.FILE -> CustomAttributes.textCell("fields/ten-bordir-uji/crm_sales/l-1/lampiran-a1b2c3-scan.pdf")
    }

    private fun invalidCell(t: CrmFieldType): JsonValue.Obj = when (t.kind) {
        FieldType.TEXT, FieldType.LONG_TEXT -> CustomAttributes.numberCell("1")
        FieldType.NUMBER -> CustomAttributes.textCell("bukan angka")
        FieldType.ENUM -> CustomAttributes.selectCell(SelectOptionId("opt_hantu"))
        // C6: penolakan silang — tanggal-saja pada varian berwaktu ditolak, bukan dikoersi.
        FieldType.DATE ->
            if (t.withTime) CustomAttributes.dateCell(LocalDate(2026, 10, 8))
            else CustomAttributes.textCell("bukan-tanggal")
        // D7: hanya JJ:MM tepat menit; bentuk lain ditolak tanpa koersi.
        FieldType.TIME -> CustomAttributes.textCell("9:30")
        // MULTI_SELECT: id opsi di luar pilihan aktif / bukan larik JSON.
        FieldType.MULTI_SELECT -> CustomAttributes.textCell("opt_hantu")
        FieldType.BOOL -> CustomAttributes.textCell("ya")
        FieldType.USER_REF -> CustomAttributes.numberCell("1")
        FieldType.RELATION -> CustomAttributes.numberCell("1")
        // C8: referensi tanpa namespace `fields/` = bukan FileRef sah → TypeMismatch, bukan fallback.
        FieldType.FILE -> CustomAttributes.textCell("scan.pdf")
    }

    @Test
    fun validation_everySample_acceptsValidCell() {
        samples.forEach { t ->
            val d = def(t)
            val cells = mapOf(d.id to validCell(t))
            // FILE terikat ke record yang SUDAH ada (TRD-FIELD-004 FR-1.3): sampel sah hanya lewat patch record `l-1`;
            // pada create ditolak (lihat validation_file_*). Tipe lain: create.
            val errors = if (t.kind == FieldType.FILE) {
                CustomFieldValidation.validateForPatch(tenant, "l-1", listOf(d), Instant.fromEpochMilliseconds(0), cells)
            } else {
                CustomFieldValidation.validateForCreate(tenant, listOf(d), cells)
            }
            assertEquals(emptyList(), errors, t.code)
        }
    }

    @Test
    fun validation_file_onCreate_isRejected_andRefOfAnotherRecord_isRejectedOnPatch() {
        val d = def(CrmFieldType(FieldType.FILE), "cf-file")
        val ref = mapOf(d.id to validCell(CrmFieldType(FieldType.FILE)))
        assertEquals(1, CustomFieldValidation.validateForCreate(tenant, listOf(d), ref).size, "record baru belum punya berkas")
        assertEquals(1, CustomFieldValidation.validateForPatch(tenant, "l-2", listOf(d), Instant.fromEpochMilliseconds(0), ref).size, "ref record l-1 di record l-2")
    }

    @Test
    fun validation_everySample_rejectsMismatchedCell() {
        samples.forEach { t ->
            val d = def(t)
            val errors = CustomFieldValidation.validateForCreate(tenant, listOf(d), mapOf(d.id to invalidCell(t)))
            assertEquals(1, errors.size, "${t.code} harus menolak nilai tak cocok")
        }
    }

    /** C6 (Irisan 2): tanggal berwaktu hanya menerima TTTT-BB-HH'T'JJ:MM tepat menit — sisanya ditolak. */
    @Test
    fun validation_dateFieldWithTime_acceptsOnlyMinuteDateTime() {
        val d = def(CrmFieldType(FieldType.DATE, withTime = true), "cf-date-time")
        val ok = CustomAttributes.dateTimeCell(LocalDateTime(2026, 10, 8, 14, 30))
        assertEquals(emptyList(), CustomFieldValidation.validateForCreate(tenant, listOf(d), mapOf(d.id to ok)))
        listOf(
            CustomAttributes.dateCell(LocalDate(2026, 10, 8)),
            CustomAttributes.textCell("2026-10-08T14:30:00"),
            CustomAttributes.textCell("2026-10-08 14:30"),
            CustomAttributes.textCell("2026-10-08T25:00"),
            CustomAttributes.textCell("bukan-tanggal"),
        ).forEach { cell ->
            assertEquals(1, CustomFieldValidation.validateForCreate(tenant, listOf(d), mapOf(d.id to cell)).size, "sel '$cell' harus ditolak")
        }
    }

    /** C6: field tanggal-saja menolak nilai berwaktu — tidak ada koersi diam-diam ke salah satu arah. */
    @Test
    fun validation_dateFieldWithoutTime_rejectsDateTimeValue() {
        val d = def(CrmFieldType(FieldType.DATE), "cf-date-only")
        val errors = CustomFieldValidation.validateForCreate(tenant,
            listOf(d), mapOf(d.id to CustomAttributes.dateTimeCell(LocalDateTime(2026, 10, 8, 9, 30)))
        )
        assertEquals(1, errors.size)
    }

    /** C6: konversi teks ke tanggal berwaktu menerima TTTT-BB-HH'T'JJ:MM; tanggal-saja = Cleared (teks asli dipulihkan). */
    @Test
    fun conversion_toDateFieldWithTime_convertsMinuteDateTime_clearsDateOnly() {
        val from = CrmFieldType(FieldType.TEXT)
        val to = CrmFieldType(FieldType.DATE, withTime = true)
        val converted = FieldTypeConversion.coerce(CustomAttributes.textCell("2026-10-08T14:30"), from, to)
        assertTrue(converted is CoercionResult.Converted, "tanggal-jam menit harus terkonversi")
        assertEquals(
            CoercionResult.Cleared("2026-10-08"),
            FieldTypeConversion.coerce(CustomAttributes.textCell("2026-10-08"), from, to)
        )
    }

    /** C6: ganti `withTime` mengubah bentuk nilai sah = LOSSY (dry run + konfirmasi); varian sama = IDENTITY. */
    @Test
    fun conversion_dateFieldChangingWithTime_isLossy() {
        assertEquals(
            ConversionSafety.LOSSY,
            FieldTypeConversion.classify(CrmFieldType(FieldType.DATE), CrmFieldType(FieldType.DATE, withTime = true))
        )
        assertEquals(
            ConversionSafety.LOSSY,
            FieldTypeConversion.classify(CrmFieldType(FieldType.DATE, withTime = true), CrmFieldType(FieldType.DATE))
        )
        assertEquals(
            ConversionSafety.IDENTITY,
            FieldTypeConversion.classify(CrmFieldType(FieldType.DATE), CrmFieldType(FieldType.DATE))
        )
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
        val relation = CrmFieldType(FieldType.RELATION, targetResource = "leads")
        samples.filter { it.kind != FieldType.RELATION }.forEach { other ->
            assertEquals(ConversionSafety.FORBIDDEN, FieldTypeConversion.classify(other, relation), "dari ${other.code}")
            assertEquals(ConversionSafety.FORBIDDEN, FieldTypeConversion.classify(relation, other), "ke ${other.code}")
        }
    }
}
