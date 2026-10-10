package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.prototype.FieldType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Parser kompatibilitas kode legacy CRM (PLAN-unify-field-vocabulary §3, Kontrak 4 variability):
 * semua kode legacy terpetakan, tak dikenal/kosong **ditolak** (bukan jatuh ke TEXT), dan round-trip
 * enum berlaku untuk semua anggota kosakata (termasuk `TIME` yang tak punya kode legacy).
 */
class CrmLegacyTypeCodeTest {

    /** Pasangan kode legacy -> enum: identitas dan alias lama yang jadi alasan parser ini ada. */
    private val expectedLegacy = mapOf(
        "TEXT" to FieldType.TEXT,
        "LONG_TEXT" to FieldType.LONG_TEXT,
        "NUMBER" to FieldType.NUMBER,
        "DATE" to FieldType.DATE,
        "SINGLE_SELECT" to FieldType.ENUM,
        "MULTI_SELECT" to FieldType.MULTI_SELECT,
        "CHECKBOX" to FieldType.BOOL,
        "RELATION" to FieldType.RELATION,
        "FILE" to FieldType.FILE,
        "USER_REF" to FieldType.USER_REF,
    )

    @Test
    fun everyLegacyCode_mapsToItsEnumMember() {
        expectedLegacy.forEach { (code, expected) ->
            assertEquals(expected, CrmLegacyTypeCode.toFieldType(code), "kode legacy '$code'")
        }
    }

    @Test
    fun legacyAliases_singleSelectAndCheckbox_readAsEnumAndBool() {
        // Kode tersimpan di `custom_field_definitions` hari ini (18 baris demo): SINGLE_SELECT/CHECKBOX/TEXT.
        assertEquals(FieldType.ENUM, CrmLegacyTypeCode.toFieldType("SINGLE_SELECT"))
        assertEquals(FieldType.BOOL, CrmLegacyTypeCode.toFieldType("CHECKBOX"))
        assertEquals(FieldType.TEXT, CrmLegacyTypeCode.toFieldType("TEXT"))
    }

    @Test
    fun toCode_returnsEnumName_forEveryMember_includingTimeWithoutLegacyCode() {
        FieldType.entries.forEach { type ->
            assertEquals(type.name, CrmLegacyTypeCode.toCode(type), "toCode($type)")
            assertEquals(type, CrmLegacyTypeCode.toFieldType(CrmLegacyTypeCode.toCode(type)), "round-trip $type")
        }
        // TIME lahir di kosakata prototype: tak punya kode legacy, tapi tetap punya tulisan baru.
        assertEquals("TIME", CrmLegacyTypeCode.toCode(FieldType.TIME))
    }

    @Test
    fun unknownOrBlank_isRejected_notDefaultedToText() {
        listOf("SELECT", "select", "CHECK BOX", " Text", "TEXT ", "", "USERREF").forEach { bad ->
            val error = assertFailsWith<IllegalArgumentException>("'$bad' harus ditolak") {
                CrmLegacyTypeCode.toFieldType(bad)
            }
            assertTrue("tidak dikenal" in error.message.orEmpty() || "kosong" in error.message.orEmpty(), error.message)
        }
    }
}
