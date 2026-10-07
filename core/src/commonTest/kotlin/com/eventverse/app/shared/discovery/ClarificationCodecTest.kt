package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ClarificationCodecTest {
    @Test
    fun `klarifikasi bulak-balik dan kunci tidak ditulis bila kosong`() {
        val s = InterviewSession(InterviewStep.F0_BISNIS, clarifications = listOf(Clarification("c1", "Tanya?", "Jawab"), Clarification("c2", "Lagi?")))
        assertEquals(s, InterviewSessionCodec.decode(InterviewSessionCodec.encode(s), "$.interview"))
        assertFalse(InterviewSessionCodec.encode(InterviewSession(InterviewStep.G1_DIVISI)).encode().contains("clarifications"))
    }
}
