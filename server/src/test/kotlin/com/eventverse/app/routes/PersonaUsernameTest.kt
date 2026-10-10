package com.eventverse.app.routes

import com.eventverse.app.domain.auth.Username
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PersonaUsernameTest {

    @Test
    fun personaUsername_shortSlug_keepsLegacyFormat() {
        assertEquals("persona_budi_santoso", personaUsername("budi-santoso"))
    }

    @Test
    fun personaUsername_exactlyThirtyChars_isNotShortened() {
        val slug = "a".repeat(22)
        assertEquals("persona_$slug", personaUsername(slug))
        assertEquals(30, personaUsername(slug).length)
    }

    @Test
    fun personaUsername_longSlug_isValidUsernameAndStable() {
        val slug = "bapak-haji-muhammad-abdurrahman-wahid-suryadiningrat-pertama"
        val name = personaUsername(slug)
        assertEquals(30, name.length)
        Username(name)
        assertEquals(name, personaUsername(slug))
    }

    @Test
    fun personaUserId_longSlugsWithSamePrefix_stayDistinctAndFitLimit() {
        val tenant = com.eventverse.app.domain.tenant.TenantId("ten-a")
        val prefix = "bapak-haji-muhammad-abdurrahman-wahid-suryadiningrat-"
        val a = personaUserId(tenant, prefix + "pertama")
        val b = personaUserId(tenant, prefix + "kedua")
        assertNotEquals(a, b)
        assertTrue(a.length <= 64)
    }

    @Test
    fun personaUsername_longSlugsWithSamePrefix_stayDistinct() {
        val prefix = "bapak-haji-muhammad-abdurrahman-wahid-"
        assertNotEquals(personaUsername(prefix + "satu"), personaUsername(prefix + "dua"))
        assertTrue(personaUsername(prefix + "satu").startsWith("persona_bapak_haji"))
    }
}
