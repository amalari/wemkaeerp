package com.eventverse.app.shared.help

import com.eventverse.app.domain.help.usecases.HelpResult
import com.eventverse.app.domain.help.usecases.HelpSuggestion
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HelpCodecTest {

    @Test
    fun result_roundTrips() {
        val result = HelpResult(
            answer = "Klik \"+ Tambah Lead\".",
            suggestion = HelpSuggestion(TutorialId("crm_new_lead"), "Mencatat lead baru", GarmentModules.CRM_SALES, 1),
            alternatives = listOf(HelpSuggestion(TutorialId("platform_x"), "X", null, 0)),
            agentRef = "deterministic/help-v1",
        )
        assertEquals(result, HelpCodec.decodeResult(JsonParser.parseObject(HelpCodec.encodeResult(result).encode())))
    }

    @Test
    fun request_withInvalidModule_keepsQuestion_dropsModule() {
        val decoded = HelpCodec.decodeRequest(JsonParser.parseObject("""{"question":"halo","currentModule":"BUKAN MODUL"}"""))
        assertEquals("halo", decoded?.question)
        assertNull(decoded?.currentModule)
        assertNull(HelpCodec.decodeRequest(JsonParser.parseObject("{}")))
    }

    @Test
    fun invalidSuggestionId_isSkipped_notGuessed() {
        val decoded = HelpCodec.decodeResult(JsonParser.parseObject(
            """{"answer":"a","suggestion":{"tutorialId":"BAD ID","stepIndex":0},"alternatives":[],"agentRef":"r"}"""))
        assertNull(decoded.suggestion)
    }
}
