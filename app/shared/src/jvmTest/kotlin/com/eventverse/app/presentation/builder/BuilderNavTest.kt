package com.eventverse.app.presentation.builder

import com.eventverse.app.domain.builder.BuilderRouteMap
import com.eventverse.app.domain.i18n.LanguageCode
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
        // TRD-PLAT-014 FR-1-1: `/builder` tanpa segmen membuka Chat (default lama: overview).
        assertEquals("chat", builderSection("/builder"))
        assertEquals("chat", builderSection(""))
        // Sub-segmen v2 diteruskan utuh ke shell (tab Live / tab Setting).
        assertEquals("live/production", builderSection("/builder/live/production"))
        assertEquals("live/preview", builderSection("/builder/live/preview"))
        assertEquals("setting/billing", builderSection("/builder/setting/billing"))
    }

    @Test
    fun `menu v2 berisi tepat lima key kontrak dan label ter-resolve`() {
        val menu = builderMenu(LanguageCode.ID)
        assertEquals(
            listOf("chat", "live", "handoff", "audit", "setting"),
            menu.map { it.key },
            "menu v2 harus 5 entri kontrak BuilderConsoleMenu, berurutan"
        )
        assertEquals("Pengaturan", builderMenu(LanguageCode.ID).last().label)
        assertEquals("Settings", builderMenu(LanguageCode.EN).last().label)
    }

    @Test
    fun `key lama resolve ke tujuan baru dan identitas tidak di-push ulang`() {
        // Redirect hanya untuk pemetaan lintas lokasi; identitas (chat, newpack) tidak memicu push.
        assertEquals("live/production", BuilderRouteMap.resolve("deployments"))
        assertEquals("handoff", BuilderRouteMap.resolve("buildqueue"))
        assertEquals(null, BuilderRouteMap.resolve("bukan-menu"))
        listOf("chat", "newpack").forEach { key ->
            assertEquals(key, BuilderRouteMap.resolve(key), "identitas $key tidak boleh berubah")
        }
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
