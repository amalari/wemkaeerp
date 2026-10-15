package com.eventverse.app.presentation.builder

import com.eventverse.app.domain.builder.BuildRequestStatus
import com.eventverse.app.domain.builder.BuilderConsoleBadges
import com.eventverse.app.presentation.builder.handoff.BuilderHandoffRow
import com.eventverse.app.presentation.builder.handoff.detailText
import com.eventverse.app.presentation.builder.handoff.parseOwnedBuildRequests
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pane Handoff (TRD-PLAT-014 FR-4): parser permintaan milik tenant dari field `buildRequests`
 * `GET /api/builder/deployments`, toleran field lama yang belum ada, dan badge "menunggu" memakai
 * aturan beku PLAN §7.1 (`BuilderConsoleBadges.handoffActiveCount`).
 */
class BuilderHandoffStateTest {

    @Test
    fun parse_shouldReadAllFieldsFromBuildRequestsArray() {
        val raw = JsonParser.parse(
            """{"deployments":[],"buildRequests":""" +
                """[{"id":"br-1","moduleId":"sewing_kanban","status":"QUOTED","reason":"Modul belum diimplementasi platform","quoteId":"q-9","hasBrief":true,"briefVersion":2}]""" +
                """}"""
        )
        val rows = parseOwnedBuildRequests(raw)
        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("br-1", row.id)
        assertEquals("sewing_kanban", row.moduleId)
        assertEquals(BuildRequestStatus.QUOTED, row.status)
        assertEquals("Modul belum diimplementasi platform", row.reason)
        assertEquals("q-9", row.quoteId)
        assertTrue(row.hasBrief)
        assertEquals(2, row.briefVersion)
        assertTrue(row.isPending)
    }

    @Test
    fun parse_shouldTolerateLegacyRowsWithoutReasonOrQuote() {
        // Bentuk payload lama: id/moduleId/status saja — tidak boleh meledak, alasan tampil jujur kosong.
        val raw = JsonParser.parse(
            """{"deployments":[],"buildRequests":[{"id":"br-2","moduleId":"qc_inspection","status":"QUEUED"}]}"""
        )
        val row = parseOwnedBuildRequests(raw).single()
        assertEquals("", row.reason)
        assertNull(row.quoteId)
        assertFalse(row.hasBrief)
        assertEquals(1, row.briefVersion, "versi brief default 1 untuk permintaan lama")
    }

    @Test
    fun parse_nonObjectRoot_shouldYieldEmptyList() {
        assertTrue(parseOwnedBuildRequests(null).isEmpty())
        assertTrue(parseOwnedBuildRequests(JsonParser.parse("[]")).isEmpty())
    }

    @Test
    fun pendingBadge_shouldFollowFrozenHandoffRule() {
        val rows = parseOwnedBuildRequests(
            JsonParser.parse(
                """{"buildRequests":[""" +
                    """{"id":"1","moduleId":"m","status":"QUEUED"},""" +
                    """{"id":"2","moduleId":"m","status":"SHIPPED"},""" +
                    """{"id":"3","moduleId":"m","status":"REJECTED"},""" +
                    """{"id":"4","moduleId":"m","status":"SUPERSEDED"},""" +
                    """{"id":"5","moduleId":"m","status":"IN_PROGRESS"}]}"""
            )
        )
        // Aturan beku PLAN §7.1: hanya QUEUED/QUOTED/APPROVED/IN_PROGRESS yang dihitung.
        assertEquals(2, BuilderConsoleBadges.handoffActiveCount(rows.mapNotNull { it.status }))
        assertTrue(rows.first { it.id == "5" }.isPending)
        assertFalse(rows.first { it.id == "4" }.isPending)
    }

    @Test
    fun unknownStatus_shouldStayRawWithoutBeingFaked() {
        val rows = parseOwnedBuildRequests(
            JsonParser.parse("""{"buildRequests":[{"id":"9","moduleId":"m","status":"ENTAH"}]}""")
        )
        val row = rows.single()
        assertNull(row.status, "status tak dikenal tidak dipalsukan jadi status domain")
        assertFalse(row.isPending)
    }

    @Test
    fun supersededDetail_shouldNameReplacementOrGugurHonestly() {
        val replaced = BuilderHandoffRow(
            id = "1", moduleId = "m", statusRaw = "SUPERSEDED", reason = "revisi",
            quoteId = null, hasBrief = false, briefVersion = 1, supersededBy = "br-7",
        )
        assertEquals("revisi · digantikan oleh br-7", replaced.detailText())
        val dropped = replaced.copy(supersededBy = null)
        assertEquals("revisi · digugurkan (modul tak lagi aktif)", dropped.detailText())
        // Permintaan lama tanpa alasan: jujur "tanpa catatan", bukan alasan rekayasa.
        val legacy = BuilderHandoffRow("2", "m", "QUEUED", "", null, false, 1, null)
        assertEquals("Tanpa catatan alasan (permintaan lama).", legacy.detailText())
    }
}
