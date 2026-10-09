package com.eventverse.app.presentation.relation

import com.eventverse.app.presentation.designsystem.RelationOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tes dasar hubungan rujukan (C7, TRD-FIELD-001 Track C): bentuk tampil nilai, parser target,
 * dan alur kontroler pemilih (memuat opsi + cache label + status galat). Murni — tanpa Compose.
 */
class RelationFieldSupportTest {

    // ── relationDisplay ─────────────────────────────────────────────────────────────────────

    @Test
    fun blankValue_showsDash() {
        val display = relationDisplay("") { null }
        assertEquals("—", display.text)
        assertFalse(display.missing)
    }

    @Test
    fun resolvedLabel_showsLabel() {
        val display = relationDisplay("p-1") { id -> if (id == "p-1") "PO-2026-001" else null }
        assertEquals("PO-2026-001", display.text)
        assertFalse(display.missing)
    }

    @Test
    fun noResolver_fallsBackToId_notMissing() {
        val display = relationDisplay("p-1", labelFor = null)
        assertEquals("p-1", display.text)
        assertFalse(display.missing)
    }

    @Test
    fun resolverAvailableButUnknown_marksMissing() {
        val display = relationDisplay("p-9") { null }
        assertEquals("Tidak ditemukan (p-9)", display.text)
        assertTrue(display.missing)
    }

    @Test
    fun cachedLabel_unknownIdFallsBackToId_notMissing() {
        // Cache kosong = belum diresolusi, bukan bukti target hilang: id ditampilkan apa adanya.
        val empty = relationDisplay("p-9", cachedRelationLabel { null })
        assertEquals("p-9", empty.text)
        assertFalse(empty.missing)

        val cached = relationDisplay("p-9", cachedRelationLabel { if (it == "p-9") "PO-9" else null })
        assertEquals("PO-9", cached.text)
        assertFalse(cached.missing)
    }

    // ── resolveRelationTarget ───────────────────────────────────────────────────────────────

    @Test
    fun target_withoutColon_usesDefaultModule() {
        assertEquals("sales" to "pesanan", resolveRelationTarget("pesanan", defaultModule = "sales"))
    }

    @Test
    fun target_withModulePrefix_splits() {
        assertEquals("gudang" to "kain", resolveRelationTarget("gudang:kain", defaultModule = "sales"))
    }

    @Test
    fun target_invalidShape_failsClosedWithBlankEntity() {
        // Bentuk tak sah ditolak oleh aturan core yang sama (bukan di-parse asal) → entitas kosong,
        // supaya pemanggil fail-closed alih-alih menebak modul/entitas.
        assertEquals("sales" to "", resolveRelationTarget("", defaultModule = "sales"))
        assertEquals("sales" to "", resolveRelationTarget(":kain", defaultModule = "sales"))
        assertEquals("sales" to "", resolveRelationTarget("a:b:c", defaultModule = "sales"))
        assertEquals("sales" to "", resolveRelationTarget("dua kata", defaultModule = "sales"))
    }

    // ── RelationFieldController ─────────────────────────────────────────────────────────────

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun controller_primeLoadsOptionsAndCachesLabels() = runTest {
        val source = FakeRelationSource(
            result = Result.success(listOf(RelationOption("p-1", "PO-2026-001")))
        )
        val controller = RelationFieldController(source, "ten", "sales", "pesanan", this)

        controller.prime()
        advanceUntilIdle()

        assertEquals(listOf(RelationOption("p-1", "PO-2026-001")), controller.options)
        assertEquals("PO-2026-001", controller.labelFor("p-1"))
        assertFalse(controller.isLoading)
        assertNull(controller.error)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun controller_blankQueryClearsOptionsWithoutCallingSource() = runTest {
        val source = FakeRelationSource(result = Result.success(emptyList()))
        val controller = RelationFieldController(source, "ten", "sales", "pesanan", this)

        controller.onQueryChange("")
        advanceUntilIdle()

        assertTrue(controller.options.isEmpty())
        assertEquals(0, source.calls)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun controller_searchFailureSurfacesError() = runTest {
        val source = FakeRelationSource(result = Result.failure(IllegalStateException("403")))
        val controller = RelationFieldController(source, "ten", "sales", "pesanan", this)

        controller.prime()
        advanceUntilIdle()

        assertEquals("403", controller.error)
        assertTrue(controller.options.isEmpty())
    }

    private class FakeRelationSource(private val result: Result<List<RelationOption>>) :
        RelationOptionsRemoteDataSource {
        var calls: Int = 0
            private set

        override suspend fun search(
            tenantSlug: String,
            module: String,
            entity: String,
            query: String
        ): Result<List<RelationOption>> {
            calls++
            return result
        }
    }
}
