package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioKeys
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.domain.discovery.scenario.ScenarioStep
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Kontrak SC3 / FR-9 Track 0: `ScenarioSketch.questions` opsional, kompatibel dengan draf lama, ketat pada nilai tak sah. */
class ScenarioQuestionsCodecTest {
    private fun decode(raw: String) = ScenarioSketchCodec.decode(JsonParser.parse(raw), "$.scenarios")
    private fun sketch(id: String = "scn-1", questions: List<String> = emptyList()) = ScenarioSketch(
        ScenarioId(id), "Sampel bordir", listOf(ScenarioStep(ModuleId("klinik_kasir"), null, "Bayar")), ScenarioSource.AI,
        questions = questions
    )

    @Test
    fun `scenario with questions should round trip`() {
        val s = sketch(questions = listOf("DP harus lunas dulu?", "Siapa yang menerbitkan invoice?"))
        assertEquals(listOf(s), decode(ScenarioSketchCodec.encode(listOf(s)).encode()))
    }

    @Test
    fun `scenario without questions should encode byte identical to the legacy json`() {
        val json = ScenarioSketchCodec.encode(listOf(sketch())).encode()
        assertTrue("questions" !in json, json)
        assertEquals(emptyList(), decode(json).single().questions)
    }

    @Test
    fun `legacy json without questions key should decode to empty questions`() {
        val raw = """[{"id":"s","title":"T","source":"AI","confirmed":false,"steps":[{"moduleId":"m","screenId":null,"label":"L","carries":[]}]}]"""
        assertEquals(emptyList(), decode(raw).single().questions)
    }

    @Test
    fun `non text or non array questions should be rejected with path`() {
        val head = """[{"id":"s","title":"T","source":"AI","confirmed":false,"steps":[{"moduleId":"m","screenId":null,"label":"L","carries":[]}],"questions":"""
        assertEquals("\$.scenarios[0].questions", assertFailsWith<DiscoveryDraftDecodeException> { decode(head + "\"x\"}]") }.path)
        assertEquals("\$.scenarios[0].questions[1]", assertFailsWith<DiscoveryDraftDecodeException> { decode(head + "[\"a\",3]}]") }.path)
        assertFailsWith<DiscoveryDraftDecodeException> { decode(head + "[\"a\",\" \"]}]") }
    }

    @Test
    fun `blank question should be rejected by the constructor`() {
        assertFailsWith<IllegalArgumentException> { sketch(questions = listOf(" ")) }
    }

    @Test
    fun `question id should be stable per scenario and normalized text but differ across scenarios`() {
        val a = ScenarioKeys.questionId(ScenarioId("scn-1"), "DP harus lunas dulu?")
        assertEquals(a, ScenarioKeys.questionId(ScenarioId("scn-1"), "  dp HARUS   lunas dulu? "))
        assertNotEquals(a, ScenarioKeys.questionId(ScenarioId("scn-2"), "DP harus lunas dulu?"))
        assertNotEquals(a, ScenarioKeys.questionId(ScenarioId("scn-1"), "Siapa yang menerbitkan invoice?"))
        assertTrue(a.startsWith("scn:scn-1:") && ScenarioKeys.isQuestionId(a))
    }
}
