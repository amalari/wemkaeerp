package com.eventverse.app.presentation.builder

import com.eventverse.app.presentation.builder.chat.BuilderChatEntry
import com.eventverse.app.presentation.builder.chat.shownEntries
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pembuka lokal modul hanya untuk keadaan KOSONG (TRD-BLDR-001 FR-7). */
class ChatBriefOpeningTest {
    private val brief = BuilderChatEntry("b", false, "r", emptyList(), false, true, moduleId = "sewing")

    @Test
    fun `utas dengan BRIEF tidak menampilkan pembuka lokal`() {
        assertEquals(listOf(brief), shownEntries(listOf(brief), null, loaded = true, thread = "sewing"))
    }

    @Test
    fun `belum termuat atau tanpa draf tidak mengarang pembuka`() {
        assertEquals(emptyList(), shownEntries(emptyList(), null, loaded = true, thread = "sewing"))
        assertEquals(emptyList(), shownEntries(emptyList(), null, loaded = false, thread = null))
    }
}
