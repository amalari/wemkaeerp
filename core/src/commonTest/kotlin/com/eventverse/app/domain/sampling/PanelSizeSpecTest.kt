package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonWriter
import com.eventverse.app.shared.sampling.SamplingProgramCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class PanelSizeSpecTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun samplingOrderFixture(yields: YieldAndTiming) = SamplingOrder(
        id = SamplingOrderId("smp-1"),
        tenantId = TenantId("tnt-1"),
        spkNumber = SpkNumber("SPK-SMP-0009"),
        clientName = "PT Buyer",
        styleName = "Cardigan Rajut",
        yieldAndTiming = yields,
        createdAt = now,
        updatedAt = now
    )

    private val baseline = YieldAndTiming(
        panelWeights = PanelWeightGrams(front = 120.0, back = 118.0, sleeve = 70.0),
        panelMinutes = PanelKnittingMinutes(front = 40, back = 38, sleeve = 25)
    )

    @Test
    fun `a size without its own spec falls back to the reference numbers`() {
        val spec = baseline.specFor("XL")
        assertEquals(120.0, spec.weights.front)
        assertEquals(40, spec.minutes.front)
        assertNull(spec.derivedFromSize)
    }

    @Test
    fun `a size with its own spec overrides the reference numbers`() {
        val updated = baseline.withSpec(
            PanelSizeSpec("XL", weights = PanelWeightGrams(front = 138.0))
        )
        assertEquals(138.0, updated.specFor("XL").weights.front)
        // Angka acuan tidak ikut berubah — costing membacanya dan tidak boleh terganggu.
        assertEquals(120.0, updated.panelWeights.front)
    }

    @Test
    fun `size lookup ignores letter case`() {
        val updated = baseline.withSpec(PanelSizeSpec("xl", weights = PanelWeightGrams(front = 99.0)))
        assertEquals(99.0, updated.specFor("XL").weights.front)
    }

    @Test
    fun `writing the same size twice replaces it instead of duplicating`() {
        val updated = baseline
            .withSpec(PanelSizeSpec("L", weights = PanelWeightGrams(front = 100.0)))
            .withSpec(PanelSizeSpec("L", weights = PanelWeightGrams(front = 110.0)))
        assertEquals(1, updated.perSize.size)
        assertEquals(110.0, updated.specFor("L").weights.front)
    }

    @Test
    fun `copying a size leaves a trace of where the numbers came from`() {
        val source = PanelSizeSpec("L", weights = PanelWeightGrams(front = 130.0))
        val copy = source.copyTo("XL")

        assertEquals(130.0, copy.weights.front)
        assertEquals("L", copy.derivedFromSize)
        assertTrue(copy.isInherited)
        assertFalse(copy.isMeasured, "Angka warisan tidak boleh mengaku sudah ditimbang")
    }

    @Test
    fun `a spec cannot be derived from itself`() {
        assertFailsWith<IllegalArgumentException> { PanelSizeSpec("L", derivedFromSize = "L") }
    }

    @Test
    fun `order copy helper records provenance on the order`() {
        val order = samplingOrderFixture(baseline)
        val updated = order.copyPanelSpecFrom("L", "XL", order.updatedAt)
        assertEquals("L", updated.yieldAndTiming.specFor("XL").derivedFromSize)
    }

    @Test
    fun `per size specs survive a json round trip`() {
        val original = baseline
            .withSpec(PanelSizeSpec("XL", weights = PanelWeightGrams(front = 138.0), derivedFromSize = "L"))
            .withSpec(PanelSizeSpec("S", minutes = PanelKnittingMinutes(front = 31)))

        val json = JsonWriter.write(SamplingProgramCodec.encodeYieldAndTiming(original))
        val decoded = SamplingProgramCodec.decodeYieldAndTiming(JsonParser.parseObject(json))

        assertEquals(2, decoded.perSize.size)
        assertEquals(138.0, decoded.specFor("XL").weights.front)
        assertEquals("L", decoded.specFor("XL").derivedFromSize)
        assertEquals(31, decoded.specFor("S").minutes.front)
        assertEquals(120.0, decoded.panelWeights.front)
    }

    @Test
    fun `legacy rows without per size specs still decode`() {
        val legacy = """{"panelWeights":{"front":120.0},"panelMinutes":{"front":40}}"""
        val decoded = SamplingProgramCodec.decodeYieldAndTiming(JsonParser.parseObject(legacy))
        assertTrue(decoded.perSize.isEmpty())
        assertEquals(120.0, decoded.specFor("ALL SIZE").weights.front)
    }
}
