package com.eventverse.app.presentation.crm

import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.shared.crm.CrmLeadCodec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regresi review C6: pembaca sel jalur core wajib **agnostik-tipe** — sel `Num` (hasil builder cabang
 * Number, syarat validasi tulis `CustomFieldValidation`) dan `Str` (cabang teks) sama-sama terbaca
 * menjadi patch core. Sebelum perbaikan, `value?.string("v")` membaca sel `Num` sebagai null sehingga
 * edit `estimated_pcs` dari inspektur diam-diam no-op.
 */
class CoreFieldPatchTest {

    @Test
    fun coreFieldPatch_numberCell_estimatedPcsCarried() {
        assertEquals(
            CrmLeadCodec.PatchLeadRequest(estimatedPcs = 12),
            coreFieldPatchOf("core:estimated_pcs", CustomAttributes.numberCell("12"))
        )
    }

    @Test
    fun coreFieldPatch_numberCell_estimatedValueCarried() {
        val patch = coreFieldPatchOf("core:estimated_value_idr", CustomAttributes.numberCell("150000"))
        assertEquals(150000L, requireNotNull(patch.estimatedValue) { "sel Num wajib menghasilkan estimatedValue" }.amount)
    }

    @Test
    fun coreFieldPatch_textCell_estimatedPcsCarried() {
        assertEquals(
            CrmLeadCodec.PatchLeadRequest(estimatedPcs = 7),
            coreFieldPatchOf("core:estimated_pcs", CustomAttributes.textCell("7"))
        )
    }

    @Test
    fun coreFieldPatch_nullValue_emptyPatch() {
        assertEquals(CrmLeadCodec.PatchLeadRequest(), coreFieldPatchOf("core:estimated_pcs", null))
    }

    @Test
    fun coreFieldPatch_unknownFieldId_emptyPatch() {
        assertEquals(CrmLeadCodec.PatchLeadRequest(), coreFieldPatchOf("core:hantu", CustomAttributes.numberCell("1")))
    }
}
