package com.eventverse.app.presentation.builder

import com.eventverse.app.presentation.builder.chat.templateSeedPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Navigasi route internal Builder: kunci menu harus terpisah dari query, dan parameter query
 * (mis. `template`) dibaca dari path internal — bukan URL browser — supaya alur wizard
 * (`/builder/newpack` → `/builder/chat?template=…`) bekerja di kelima target KMP.
 */
class BuilderNavTest {

    @Test
    fun `section memotong query route supaya kunci menu tetap cocok`() {
        assertEquals("chat", builderSection("/builder/chat?template=bordir"))
        assertEquals("chat", builderSection("/builder/chat"))
        assertEquals("newpack", builderSection("/builder/newpack"))
        assertEquals("overview", builderSection("/builder"))
        assertEquals("overview", builderSection(""))
    }

    @Test
    fun `query param dibaca dari route internal, kosong berarti null`() {
        assertEquals("bordir", builderQueryParam("/builder/chat?template=bordir", "template"))
        assertEquals("fob", builderQueryParam("/builder/chat?template=fob&x=1", "template"))
        assertEquals("1", builderQueryParam("/builder/chat?template=fob&x=1", "x"))
        assertNull(builderQueryParam("/builder/chat", "template"), "tanpa query = null")
        assertNull(builderQueryParam("/builder/chat?template=", "template"), "nilai kosong = null, bukan \"\"")
        assertNull(builderQueryParam("/builder/chat?lain=1", "template"), "nama lain tidak ikut terbaca")
    }

    @Test
    fun `pesan pembuka template hanya untuk kode yang dikenal`() {
        listOf("wizard", "fob", "cmt", "sablon", "bordir", "rajut", "custom").forEach { code ->
            assertTrue(templateSeedPrompt(code)?.isNotBlank() == true, "kode $code harus punya pesan pembuka")
        }
        assertNull(templateSeedPrompt("entah"), "kode tak dikenal tidak dipalsukan jadi template")
        assertNull(templateSeedPrompt(""))
    }
}
