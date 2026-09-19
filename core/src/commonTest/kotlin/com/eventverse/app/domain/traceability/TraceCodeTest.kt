package com.eventverse.app.domain.traceability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TraceCodeTest {

    private fun sample(
        kind: TraceWorkOrderKind = TraceWorkOrderKind.SAMPLING,
        tier: TraceTier = TraceTier.BUNDLE,
        tenant: Int = 7,
        order: Int = 1234,
        size: Int = 3,
        seq: Int = 41
    ) = TraceCodec.encode(kind, tier, tenant, order, size, seq)

    @Test
    fun `encode then parse returns identical parts`() {
        val code = sample()
        val parts = assertNotNull(TraceCodec.parse(code.value))

        assertEquals(TraceCodec.CURRENT_VERSION, parts.version)
        assertEquals(TraceWorkOrderKind.SAMPLING, parts.workOrderKind)
        assertEquals(TraceTier.BUNDLE, parts.tier)
        assertEquals(7, parts.tenantOrdinal)
        assertEquals(1234, parts.workOrderOrdinal)
        assertEquals(3, parts.sizeIndex)
        assertEquals(41, parts.sequence)
    }

    @Test
    fun `encoded code has fixed length and starts with magic`() {
        val code = sample()
        assertEquals(TraceCodec.LENGTH, code.value.length)
        assertEquals(TraceCodec.MAGIC, code.value.first())
    }

    @Test
    fun `checksum rejects every single character substitution`() {
        val code = sample().value
        var checked = 0
        for (index in 0 until code.length) {
            for (replacement in TraceCodec.ALPHABET) {
                if (replacement == code[index]) continue
                val mutated = code.substring(0, index) + replacement + code.substring(index + 1)
                if (index == 0 && replacement != TraceCodec.MAGIC) {
                    // posisi magic ditolak lebih awal, bukan oleh checksum
                    assertNull(TraceCodec.parse(mutated), "Kode tanpa magic harus ditolak: $mutated")
                    continue
                }
                assertNull(TraceCodec.parse(mutated), "Substitusi di posisi $index harus tertolak: $mutated")
                checked++
            }
        }
        assertTrue(checked > 0)
    }

    @Test
    fun `checksum rejects transposition of adjacent characters`() {
        val code = sample().value
        var transpositionsChecked = 0
        for (index in 0 until code.length - 2) {
            if (code[index] == code[index + 1]) continue
            val mutated = buildString {
                append(code.substring(0, index))
                append(code[index + 1])
                append(code[index])
                append(code.substring(index + 2))
            }
            assertNull(TraceCodec.parse(mutated), "Transposisi di posisi $index harus tertolak: $mutated")
            transpositionsChecked++
        }
        assertTrue(transpositionsChecked >= 5, "Uji transposisi terlalu sedikit: $transpositionsChecked")
    }

    @Test
    fun `normalize corrects crockford lookalikes instead of dropping them`() {
        val code = TraceCodec.encode(TraceWorkOrderKind.SAMPLING, TraceTier.BUNDLE, 0, 1, 0, 1)
        // '1' dan '0' di dalam kode diketik sebagai I/L/O oleh operator yang terburu-buru.
        val mistyped = code.value.replace('1', 'I').replace('0', 'O')
        assertEquals(code.value, TraceCodec.normalize(mistyped))
        assertNotNull(TraceCodec.parse(mistyped))
    }

    @Test
    fun `grouped form and lowercase input still parse`() {
        val code = sample()
        val typed = TraceCodec.grouped(code).lowercase()
        assertEquals(code, TraceCodec.parseCode(typed))
    }

    @Test
    fun `scan payload accepts full url and bare code`() {
        val code = sample()
        val url = TraceCodec.toScanUrl(code, "trace.wemade.id")
        assertTrue(url.startsWith("HTTPS://TRACE.WEMADE.ID/T/"))
        assertEquals(code, TraceCodec.fromScanPayload(url))
        assertEquals(code, TraceCodec.fromScanPayload(code.value))
    }

    @Test
    fun `tiers and kinds round trip through their symbols`() {
        for (kind in TraceWorkOrderKind.entries) {
            for (tier in TraceTier.entries) {
                val parts = assertNotNull(TraceCodec.parse(sample(kind = kind, tier = tier).value))
                assertEquals(kind, parts.workOrderKind)
                assertEquals(tier, parts.tier)
            }
        }
    }

    @Test
    fun `boundary ordinals encode and decode`() {
        val code = TraceCodec.encode(
            TraceWorkOrderKind.BULK,
            TraceTier.SACK,
            TraceCodec.MAX_TENANT_ORDINAL - 1,
            TraceCodec.MAX_WORK_ORDER_ORDINAL - 1,
            TraceCodec.MAX_SIZE_INDEX - 1,
            TraceCodec.MAX_SEQUENCE - 1
        )
        val parts = assertNotNull(TraceCodec.parse(code.value))
        assertEquals(TraceCodec.MAX_TENANT_ORDINAL - 1, parts.tenantOrdinal)
        assertEquals(TraceCodec.MAX_WORK_ORDER_ORDINAL - 1, parts.workOrderOrdinal)
        assertEquals(TraceCodec.MAX_SIZE_INDEX - 1, parts.sizeIndex)
        assertEquals(TraceCodec.MAX_SEQUENCE - 1, parts.sequence)
    }

    @Test
    fun `garbage and truncated input return null rather than throwing`() {
        assertNull(TraceCodec.parse(""))
        assertNull(TraceCodec.parse("HALO"))
        assertNull(TraceCodec.parse(sample().value.dropLast(1)))
        assertNull(TraceCodec.fromScanPayload("https://example.com/"))
    }
}
