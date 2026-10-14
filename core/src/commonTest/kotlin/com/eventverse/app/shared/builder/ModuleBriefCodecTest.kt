package com.eventverse.app.shared.builder

import com.eventverse.app.domain.builder.BriefKeys
import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.BriefStatus
import com.eventverse.app.domain.builder.ModuleBriefOutcome
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** Fixture bentuk `GET /api/builder/chat` untuk pesan BRIEF — satu garment, satu non-garment (Kontrak 6). */
object BriefContractFixtures {
    const val GARMENT_BRIEF_MESSAGE = """{"id":"m1","role":"AGENT","text":"Sampling memproses permintaan sampel dari buyer.",
        "moduleId":"sampling_order","kind":"BRIEF","briefSource":"AI","features":["Lampirkan foto desain"],
        "stages":[{"name":"Intake"},{"name":"Bordir","optional":true},{"name":"Revisi","reworkTo":"Intake"}]}"""
    const val NON_GARMENT_BRIEF_MESSAGE = """{"id":"m2","role":"AGENT","text":"Poli Umum menerima pasien dan mencatat pemeriksaan.",
        "moduleId":"klinik_poli","kind":"BRIEF","briefSource":"DETERMINISTIC","features":[],"stages":[]}"""
}

class ModuleBriefCodecTest {

    @Test
    fun `stages should round trip including optional and rework`() {
        val stages = listOf(StageSketch("Intake"), StageSketch("Bordir", optional = true), StageSketch("Revisi", reworkTo = "Intake"))
        assertEquals(stages, ModuleBriefCodec.decodeStages(ModuleBriefCodec.encodeStages(stages).encode()))
    }

    @Test
    fun `features should round trip`() {
        val f = listOf("Lampirkan foto", "Cetak label")
        assertEquals(f, ModuleBriefCodec.decodeFeatures(ModuleBriefCodec.encodeFeatures(f).encode()))
    }

    @Test
    fun `garment and non-garment fixtures should decode`() {
        val g = JsonParser.parse(BriefContractFixtures.GARMENT_BRIEF_MESSAGE) as JsonValue.Obj
        assertEquals(3, ModuleBriefCodec.decodeStages(g["stages"]!!).size)
        assertEquals(BriefSource.AI, ModuleBriefCodec.parseSource((g["briefSource"] as JsonValue.Str).value))
        val n = JsonParser.parse(BriefContractFixtures.NON_GARMENT_BRIEF_MESSAGE) as JsonValue.Obj
        assertEquals(emptyList(), ModuleBriefCodec.decodeStages(n["stages"]!!))
        assertEquals(BriefSource.DETERMINISTIC, ModuleBriefCodec.parseSource((n["briefSource"] as JsonValue.Str).value))
    }

    @Test
    fun `unknown source should be rejected not defaulted`() {
        assertFailsWith<IllegalStateException> { ModuleBriefCodec.parseSource("HUMAN") }
    }

    @Test
    fun `malformed stages should be rejected`() {
        assertFailsWith<IllegalStateException> { ModuleBriefCodec.decodeStages("""{"name":"x"}""") }
        assertFailsWith<IllegalStateException> { ModuleBriefCodec.decodeStages("""[{"optional":true}]""") }
        assertFailsWith<IllegalStateException> { ModuleBriefCodec.decodeStages("""[{"name":"x","optional":"ya"}]""") }
        assertFailsWith<IllegalArgumentException> { ModuleBriefCodec.decodeStages("""[{"name":"x","extra":1}]""") }
    }

    @Test
    fun `malformed features should be rejected`() {
        assertFailsWith<IllegalStateException> { ModuleBriefCodec.decodeFeatures("""["a",1]""") }
        assertFailsWith<IllegalStateException> { ModuleBriefCodec.decodeFeatures("""{"a":1}""") }
    }

    @Test
    fun `fingerprint should match FNV-1a 64 reference value`() {
        assertEquals("e71fa2190541574b", BriefKeys.fingerprint("abc"))
    }

    @Test
    fun `follow-up id should be stable across case and whitespace and differ per module`() {
        val a = BriefKeys.followUpId("klinik_poli", "Siapa yang mengisi?")
        assertEquals(a, BriefKeys.followUpId("klinik_poli", "  siapa   yang MENGISI? "))
        assertNotEquals(a, BriefKeys.followUpId("klinik_lab", "Siapa yang mengisi?"))
        assertEquals(true, a.startsWith("ai:klinik_poli:"))
    }

    @Test
    fun `basis ref should change when narrative features or agent change`() {
        val base = BriefKeys.basisRef("cerita", listOf("a", "b"), "briefer/v1")
        assertEquals(base, BriefKeys.basisRef("cerita", listOf("a", "b"), "briefer/v1"))
        assertNotEquals(base, BriefKeys.basisRef("cerita lain", listOf("a", "b"), "briefer/v1"))
        assertNotEquals(base, BriefKeys.basisRef("cerita", listOf("a"), "briefer/v1"))
        assertNotEquals(base, BriefKeys.basisRef("cerita", listOf("a", "b"), "briefer/v2"))
    }

    @Test
    fun `outcome should carry source only when created`() {
        ModuleBriefOutcome("m", BriefStatus.CREATED, BriefSource.AI)
        ModuleBriefOutcome("m", BriefStatus.FRESH)
        assertFailsWith<IllegalArgumentException> { ModuleBriefOutcome("m", BriefStatus.CREATED) }
        assertFailsWith<IllegalArgumentException> { ModuleBriefOutcome("m", BriefStatus.SKIPPED, BriefSource.AI) }
    }
}
