package com.eventverse.app.domain.invoicing.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Perpindahan elemen di kanvas template.
 *
 * Aturan ini dipakai bersama oleh tarikan mouse dan tombol panah, jadi salah di sini berarti
 * elemen terasa "tidak bisa digeser" atau bisa lolos keluar dari lembar kertas A4.
 */
class TemplateRectMovementTest {

    private val a4 = PaperSize.A4
    private val element = TemplateRect(Mm10(200), Mm10(1000), Mm10(600), Mm10(80)) // 20mm, 100mm

    @Test
    fun `moved by without snap should translate the element exactly`() {
        val moved = element.movedBy(
            dx = Mm10(235), // 23.5 mm
            dy = Mm10(-150), // -15 mm
            snapMm10 = 0,
            paperWidth = a4.width,
            paperHeight = a4.height
        )

        assertEquals(Mm10(435), moved.x)
        assertEquals(Mm10(850), moved.y)
        assertEquals(element.width, moved.width)
    }

    @Test
    fun `moved by with snap grid should land on the nearest lower grid line`() {
        val moved = element.movedBy(
            dx = Mm10(235),
            dy = Mm10(0),
            snapMm10 = 50, // grid 5 mm
            paperWidth = a4.width,
            paperHeight = a4.height
        )

        assertEquals(0, moved.x.value % 50, "Posisi X harus kelipatan grid 5 mm")
        assertEquals(Mm10(400), moved.x) // 43.5 mm dibulatkan ke bawah ke 40 mm
    }

    @Test
    fun `moved by should keep the element inside the paper`() {
        val movedToFarRight = element.movedBy(
            dx = Mm10(99_999),
            dy = Mm10(99_999),
            snapMm10 = 50,
            paperWidth = a4.width,
            paperHeight = a4.height
        )

        assertEquals(
            a4.width.value - element.width.value,
            movedToFarRight.x.value,
            "Elemen tidak boleh melewati tepi kanan kertas"
        )
        // Sumbu Y berhenti di 285 mm, bukan 289 mm: snap grid membulatkan ke bawah, jadi elemen
        // berhenti di garis grid terakhir yang masih di dalam kertas.
        assertEquals(2_850, movedToFarRight.y.value)
        assertTrue(
            movedToFarRight.y + element.height <= a4.height,
            "Elemen tetap harus utuh di dalam kertas setelah snap"
        )
    }

    @Test
    fun `moved by negative directions should clamp at the top left corner`() {
        val moved = element.movedBy(
            dx = Mm10(-5_000),
            dy = Mm10(-5_000),
            snapMm10 = 50,
            paperWidth = a4.width,
            paperHeight = a4.height
        )

        assertEquals(Mm10.ZERO, moved.x)
        assertEquals(Mm10.ZERO, moved.y)
    }

    @Test
    fun `moved beyond the edge without snap should not exceed the printable area`() {
        val moved = element.movedBy(
            dx = Mm10(99_999),
            dy = Mm10.ZERO,
            snapMm10 = 0,
            paperWidth = a4.width,
            paperHeight = a4.height
        )

        assertEquals(a4.width.value - element.width.value, moved.x.value)
    }
}
