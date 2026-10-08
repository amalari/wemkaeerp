package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.discovery.proposal.SkeletonHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SkeletonSketchPlanTest {
    private fun row(label: String, lebar: String, petunjuk: String? = null): Map<String, String> =
        buildMap {
            put("Blok", label)
            put("Lebar", lebar)
            if (petunjuk != null) put("Petunjuk", petunjuk)
        }

    @Test
    fun skeletonHintOf_unknownOrBlank_returnsNull() {
        assertNull(skeletonHintOf(null))
        assertNull(skeletonHintOf(""))
        assertNull(skeletonHintOf("grafik"))
    }

    @Test
    fun skeletonHintOf_everyHint_isMapped() {
        assertEquals(SkeletonHint.TABLE, skeletonHintOf("tabel"))
        assertEquals(SkeletonHint.FORM, skeletonHintOf("formulir"))
        assertEquals(SkeletonHint.METRIC_CARDS, skeletonHintOf("kartu angka"))
        assertEquals(SkeletonHint.ACTIONS, skeletonHintOf("aksi"))
    }

    @Test
    fun planSketchRows_genericLegacySample_keepsOldLookAndPairing() {
        val plan = planSketchRows(
            listOf(row("Ringkasan X", "penuh"), row("Daftar X", "separuh"), row("Panel aksi", "separuh"))
        )
        assertEquals(listOf(1, 2), plan.map { it.size })
        assertEquals(SketchShape.NEUTRAL, plan[0][0].shape)
        assertEquals(SketchShape.NEUTRAL, plan[1][1].shape)
    }

    @Test
    fun planSketchRows_oddHalfBlock_standsAlone() {
        val plan = planSketchRows(listOf(row("A", "separuh"), row("B", "penuh"), row("C", "separuh")))
        assertEquals(listOf(1, 1, 1), plan.map { it.size })
    }

    @Test
    fun planSketchRows_namedBlocks_getShapePerHintAndUnknownIsNeutral() {
        val plan = planSketchRows(
            listOf(row("Keranjang", "penuh", "tabel"), row("Bayar", "separuh", "aksi"), row("X", "separuh", "???"))
        )
        assertEquals(SketchShape.TABLE, plan[0][0].shape)
        assertEquals(SketchShape.ACTIONS, plan[1][0].shape)
        assertEquals(SketchShape.NEUTRAL, plan[1][1].shape)
    }
}
