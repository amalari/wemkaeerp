package com.eventverse.app.presentation.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tes komponen dasar pemilih rujukan (C7, TRD-FIELD-001 FR-7). Murni — tanpa rendering Compose;
 * bagian penyaring diangkat ke [filterRelationOptions] persis supaya bisa dites begini.
 */
class ClayRelationPickerTest {

    private val options = listOf(
        RelationOption("p-1", "PO-2026-001"),
        RelationOption("p-2", "PO-2026-002"),
        RelationOption("k-9", "Kain Katun")
    )

    @Test
    fun blankQuery_returnsAllOptionsUnfiltered() {
        assertEquals(options, filterRelationOptions(options, ""))
        assertEquals(options, filterRelationOptions(options, "   "))
    }

    @Test
    fun query_matchesLabelCaseInsensitive() {
        assertEquals(listOf(options[2]), filterRelationOptions(options, "katun"))
        assertEquals(listOf(options[2]), filterRelationOptions(options, "KATUN"))
    }

    @Test
    fun query_matchesIdWhenLabelDiffers() {
        assertEquals(listOf(options[0]), filterRelationOptions(options, "p-1"))
    }

    @Test
    fun query_noMatch_returnsEmpty() {
        assertTrue(filterRelationOptions(options, "tidak-ada").isEmpty())
    }

    @Test
    fun relationOption_rejectsBlankId() {
        assertFailsWith<IllegalArgumentException> { RelationOption("", "Tanpa Id") }
    }

    @Test
    fun notFoundLabel_isStable() {
        assertEquals("Tidak ditemukan", RELATION_NOT_FOUND_LABEL)
    }
}
