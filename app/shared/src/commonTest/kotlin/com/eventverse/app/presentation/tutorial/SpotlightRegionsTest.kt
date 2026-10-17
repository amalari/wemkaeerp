package com.eventverse.app.presentation.designsystem

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TRD-TUT-002 FR-1: geometri region mode tembus — fungsi murni [spotlightRegions], diuji tanpa Compose.
 * Menutup: target di tengah, target menyentuh tepi (region ≤ 0 dibuang), target lebih besar dari kontainer,
 * `target == null` (0 region), dan invariant area (region menutup kontainer kecuali lubang).
 */
class SpotlightRegionsTest {

    private val container = IntSize(1000, 800)
    private val pad = 6f
    private val radius = 14f

    @Test
    fun targetNull_returnsNoRegions() {
        assertEquals(emptyList(), spotlightRegions(container, null, pad, radius))
    }

    @Test
    fun targetInCenter_returnsFourRegionsAroundHole() {
        val target = Rect(400f, 300f, 600f, 500f)
        val regions = spotlightRegions(container, target, pad, radius)
        assertEquals(4, regions.size)
        val hole = target.inflate(pad)
        // Atas: penuh lebar, 0..hole.top
        assertTrue(Rect(0f, 0f, 1000f, hole.top) in regions)
        // Bawah: penuh lebar, hole.bottom..h
        assertTrue(Rect(0f, hole.bottom, 1000f, 800f) in regions)
        // Start: hanya strip di sisi kiri lubang
        assertTrue(Rect(0f, hole.top, hole.left, hole.bottom) in regions)
        // End: strip di sisi kanan lubang
        assertTrue(Rect(hole.right, hole.top, 1000f, hole.bottom) in regions)
    }

    @Test
    fun targetTouchingTopEdge_dropsEmptyTopRegion() {
        val target = Rect(400f, 0f, 600f, 500f) // menyentuh tepi atas
        val regions = spotlightRegions(container, target, pad, radius)
        assertEquals(3, regions.size, "region atas ≤ 0 dibuang")
        assertTrue(regions.none { it.bottom <= 0f })
    }

    @Test
    fun targetTouchingLeftAndRightEdges_dropsStartAndEndRegions() {
        val target = Rect(0f, 200f, 1000f, 600f) // selebar kontainer
        val regions = spotlightRegions(container, target, pad, radius)
        assertEquals(2, regions.size, "hanya atas & bawah; start/end ≤ 0 dibuang")
        assertTrue(regions.all { it.left == 0f && it.right == 1000f })
    }

    @Test
    fun targetBiggerThanContainer_yieldsOnlyPositiveRegions() {
        val target = Rect(-50f, -50f, 1200f, 900f) // lebih besar dari kontainer
        val regions = spotlightRegions(container, target, pad, radius)
        assertTrue(regions.all { it.width > 0f && it.height > 0f }, "region tak valid dibuang")
    }

    @Test
    fun regions_coverContainerExceptHole() {
        val target = Rect(300f, 200f, 700f, 600f)
        val regions = spotlightRegions(container, target, pad, radius)
        val hole = target.inflate(pad)
        val covered = regions.sumOf { (it.width * it.height).toDouble() }
        val total = container.width.toDouble() * container.height
        val holeArea = hole.width.toDouble() * hole.height
        assertEquals(total - holeArea, covered, 0.001, "region menutup kontainer kecuali lubang")
    }
}
