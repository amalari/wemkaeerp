package com.eventverse.app.shared.builder

import com.eventverse.app.domain.builder.BriefFeature
import com.eventverse.app.domain.builder.BriefKeys
import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.BriefStatus
import com.eventverse.app.domain.builder.ModuleBriefOutcome
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.evidence.EvidenceSource
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** Fixture bentuk `GET /api/builder/chat` untuk pesan BRIEF — satu garment, satu non-garment (Kontrak 6). */
object BriefContractFixtures {
    /** Bentuk v1 legacy (fitur string) — dibaca apa adanya tanpa bukti (TRD-BLDR-003 FR-6.1). */
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
    fun `stages with evidence should round trip and legacy stage without evidence stays readable`() {
        val bukti = EvidenceRef(EvidenceSource.NARRATIVE, quote = "intake pesanan")
        val stages = listOf(StageSketch("Intake", evidence = bukti), StageSketch("Revisi", reworkTo = "Intake"))
        assertEquals(stages, ModuleBriefCodec.decodeStages(ModuleBriefCodec.encodeStages(stages).encode()))
        assertEquals(listOf(StageSketch("Intake")), ModuleBriefCodec.decodeStages("""[{"name":"Intake"}]"""))
    }

    @Test
    fun `features v2 should round trip evidence and v1 legacy strings read as text without evidence`() {
        val bukti = EvidenceRef(EvidenceSource.NARRATIVE, quote = "lampirkan foto")
        val v2 = listOf(BriefFeature("Lampirkan foto", bukti), BriefFeature("Cetak label"))
        assertEquals(v2, ModuleBriefCodec.decodeFeatures(ModuleBriefCodec.encodeFeatures(v2).encode()))
        assertEquals(listOf(BriefFeature("Lampirkan foto desain")), ModuleBriefCodec.decodeFeatures("""["Lampirkan foto desain"]"""))
    }

    @Test
    fun `mixed v1 and v2 features should be rejected as corrupt`() {
        assertFailsWith<IllegalArgumentException> { ModuleBriefCodec.decodeFeatures("""["a",{"text":"b"}]""") }
    }

    @Test
    fun `unknown evidence source should be rejected not defaulted`() {
        assertFailsWith<IllegalStateException> {
            ModuleBriefCodec.decodeFeatures("""[{"text":"a","evidence":{"source":"TEBAKAN"}}]""")
        }
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
        assertFailsWith<IllegalArgumentException> { ModuleBriefCodec.decodeFeatures("""["a",1]""") }
        assertFailsWith<IllegalStateException> { ModuleBriefCodec.decodeFeatures("""{"a":1}""") }
        assertFailsWith<IllegalArgumentException> { ModuleBriefCodec.decodeFeatures("""[{"text":"a","extra":1}]""") }
        assertFailsWith<IllegalStateException> { ModuleBriefCodec.decodeFeatures("""[{"text":""}]""") }
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
    fun `input fingerprint should change when narrative features or agent change and keep legacy formula`() {
        val base = BriefKeys.inputFingerprint("cerita", listOf("a", "b"), "briefer/v1")
        assertEquals(base, BriefKeys.inputFingerprint("cerita", listOf("a", "b"), "briefer/v1"))
        assertNotEquals(base, BriefKeys.inputFingerprint("cerita lain", listOf("a", "b"), "briefer/v1"))
        assertNotEquals(base, BriefKeys.inputFingerprint("cerita", listOf("a"), "briefer/v1"))
        assertNotEquals(base, BriefKeys.inputFingerprint("cerita", listOf("a", "b"), "briefer/v2"))
        // Paritas rumus legacy `b1:` + FNV-1a (FR-2.4): idempotensi brief/pesan lama tidak boleh pecah.
        assertEquals("b1:" + BriefKeys.fingerprint("cerita\u001Ea\u001Fb\u001Ebriefer/v1"), base)
    }

    @Test
    fun `outcome should carry source only when created`() {
        ModuleBriefOutcome("m", BriefStatus.CREATED, BriefSource.AI)
        ModuleBriefOutcome("m", BriefStatus.FRESH)
        assertFailsWith<IllegalArgumentException> { ModuleBriefOutcome("m", BriefStatus.CREATED) }
        assertFailsWith<IllegalArgumentException> { ModuleBriefOutcome("m", BriefStatus.SKIPPED, BriefSource.AI) }
    }
}
