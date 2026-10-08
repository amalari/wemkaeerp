package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ScreenSuggestion
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.TableHints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * C3 Irisan 2 (Kontrak 7, pack non-default): petunjuk field `LONG_TEXT` dari usulan pack bordir
 * diteruskan utuh ke usulan layar dan **lolos validator** — termasuk baris contoh multibaris.
 */
class PackSuggestionMappingLongTextTest {

    @Test
    fun longTextHint_becomesProposalField_andMultilineSeedPassesValidation() {
        val multiline = "Cek lapis kain.\nJahit manual, benang polyester 120.\nFinishing uap."
        val suggestion = ScreenSuggestion(
            ModuleId("bordir_antrean"), "Antrean Bordir", WidgetKind.TABLE,
            sampleRows = listOf(mapOf("nomor" to "Antri", "catatan" to multiline)),
            tableHints = TableHints(
                statusColumn = "nomor", options = listOf("Antri", "Jahit", "Selesai"),
                fields = listOf(
                    FieldHint("nomor", FieldType.ENUM, options = listOf("Antri", "Jahit", "Selesai")),
                    FieldHint("catatan", FieldType.LONG_TEXT)
                )
            ),
            rationale = "Antrean bordir butuh catatan panjang per pesanan."
        )

        val proposals = PackSuggestionMapping.map(suggestion)
        val table = proposals.single { it.widget == WidgetKind.TABLE }

        val catatan = table.entity!!.fields.single { it.key == "catatan" }
        assertEquals(FieldType.LONG_TEXT, catatan.type, "petunjuk tipe diteruskan, tidak di-tebak jadi TEXT")
        assertEquals(multiline, table.seed.single()["catatan"], "isi multibaris tidak dipangkas")
        assertTrue(ScreenProposalValidator.validate(table, "$.proposal", null, null).isEmpty(), "usulan hasil pemetaan harus sah")
    }
}
