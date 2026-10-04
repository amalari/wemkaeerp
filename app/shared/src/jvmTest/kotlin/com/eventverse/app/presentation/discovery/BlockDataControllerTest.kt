package com.eventverse.app.presentation.discovery

import com.eventverse.app.domain.prototype.BlockDataPort
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import com.eventverse.app.domain.prototype.KanbanHints
import com.eventverse.app.domain.prototype.PortError
import com.eventverse.app.domain.prototype.PortException
import com.eventverse.app.domain.prototype.PrototypeRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BlockDataControllerTest {

    private class FakeBlockDataPort(
        var rows: MutableList<PrototypeRow> = mutableListOf(),
        var failOnUpdate: PortError? = null,
        var failOnCreate: PortError? = null,
        var failOnDelete: PortError? = null,
        var delayMs: Long = 0
    ) : BlockDataPort {
        var callCount = 0

        override suspend fun load(): Result<List<PrototypeRow>> {
            if (delayMs > 0) delay(delayMs)
            return Result.success(rows.toList())
        }

        override suspend fun create(values: Map<String, String>): Result<PrototypeRow> {
            callCount++
            if (delayMs > 0) delay(delayMs)
            failOnCreate?.let { return Result.failure(PortException(it)) }
            val newRow = PrototypeRow("server-${rows.size + 1}", values)
            rows.add(newRow)
            return Result.success(newRow)
        }

        override suspend fun update(rowId: String, changes: Map<String, String>): Result<PrototypeRow> {
            callCount++
            if (delayMs > 0) delay(delayMs)
            failOnUpdate?.let { return Result.failure(PortException(it)) }
            val idx = rows.indexOfFirst { it.id == rowId }
            if (idx == -1) return Result.failure(PortException(PortError.NotFound("Baris '$rowId' tidak ada")))
            val updated = rows[idx].copy(values = rows[idx].values + changes)
            rows[idx] = updated
            return Result.success(updated)
        }

        override suspend fun delete(rowId: String): Result<Unit> {
            callCount++
            if (delayMs > 0) delay(delayMs)
            failOnDelete?.let { return Result.failure(PortException(it)) }
            rows.removeAll { it.id == rowId }
            return Result.success(Unit)
        }
    }

    private fun sampleKanbanScreen() = requireNotNull(
        InteractiveScreenFactory.kanban(
            screenId = "screen-1",
            title = "Papan Proyek",
            hints = KanbanHints(
                columns = listOf("Draft", "Proses", "Selesai"),
                transitions = mapOf(
                    "Draft" to setOf("Proses"),
                    "Proses" to setOf("Selesai")
                ),
                groupLabel = "Kolom"
            ),
            rows = listOf(
                mapOf("Kolom" to "Draft", "Judul" to "Kartu A")
            )
        )
    )

    @Test
    fun testRollbackOnMoveFailure() = runTest {
        val screen = sampleKanbanScreen()
        val initialRow = PrototypeRow("row-1", mapOf("Kolom" to "Draft", "Judul" to "Kartu A"))
        val fakePort = FakeBlockDataPort(
            rows = mutableListOf(initialRow),
            failOnUpdate = PortError.Unavailable("Koneksi terputus")
        )
        val controller = BlockDataController(fakePort, screen.spec, InteractiveScreenFactory.ENTITY_ID)
        controller.load()

        assertEquals("Draft", controller.rows.first().get("Kolom"))

        // Pindah kartu ke "Proses" tapi port gagal -> wajib rollback ke "Draft"
        val result = controller.move("row-1", "Kolom", "Proses")
        assertTrue(result.isFailure)
        assertEquals("Draft", controller.rows.first().get("Kolom"))
        assertTrue(controller.phase is BlockDataPhase.Error)
        assertEquals("Gagal terhubung ke server: Koneksi terputus", controller.errorMessage)
    }

    @Test
    fun testPessimisticCreateWaitsForServerIdAndKeepsFormOnFailure() = runTest {
        val screen = sampleKanbanScreen()
        val fakePort = FakeBlockDataPort()
        val controller = BlockDataController(fakePort, screen.spec, InteractiveScreenFactory.ENTITY_ID)
        controller.load()

        // Berhasil: baris bertambah dengan ID dari server
        val successRes = controller.create(mapOf("Kolom" to "Draft", "Judul" to "Baru"))
        assertTrue(successRes.isSuccess)
        assertEquals("server-1", successRes.getOrNull()?.id)
        assertEquals(1, controller.rows.size)
        assertEquals(BlockDataPhase.Idle, controller.phase)

        // Gagal server: baris tidak bertambah, phase Error
        fakePort.failOnCreate = PortError.Validation("Judul tidak boleh kosong")
        val failRes = controller.create(mapOf("Kolom" to "Draft", "Judul" to ""))
        assertTrue(failRes.isFailure)
        assertEquals(1, controller.rows.size) // tidak bertambah
        assertEquals("Judul tidak boleh kosong", controller.errorMessage)
        assertTrue(controller.phase is BlockDataPhase.Error)
    }

    @Test
    fun testForbiddenAndUnavailableErrorMapping() = runTest {
        val screen = sampleKanbanScreen()
        val initialRow = PrototypeRow("row-1", mapOf("Kolom" to "Draft", "Judul" to "Kartu A"))
        val fakePort = FakeBlockDataPort(rows = mutableListOf(initialRow))
        val controller = BlockDataController(fakePort, screen.spec, InteractiveScreenFactory.ENTITY_ID)
        controller.load()

        // Forbidden
        fakePort.failOnUpdate = PortError.Forbidden("")
        controller.update("row-1", mapOf("Judul" to "Edit"))
        assertEquals("Anda tidak berwenang melakukan perubahan ini.", controller.errorMessage)

        // Unavailable
        fakePort.failOnDelete = PortError.Unavailable("")
        controller.delete("row-1")
        assertEquals("Gagal terhubung ke server. Coba lagi sebentar.", controller.errorMessage)
    }

    @Test
    fun testClientSidePrevalidationRejectsBeforePort() = runTest {
        val screen = sampleKanbanScreen()
        // Transisi hanya Draft -> Proses -> Selesai. Dari Draft ke Selesai dilarang oleh spec!
        val initialRow = PrototypeRow("row-1", mapOf("Kolom" to "Draft", "Judul" to "Kartu A"))
        val fakePort = FakeBlockDataPort(rows = mutableListOf(initialRow))
        val controller = BlockDataController(fakePort, screen.spec, InteractiveScreenFactory.ENTITY_ID)
        controller.load()

        val result = controller.move("row-1", "Kolom", "Selesai")
        assertTrue(result.isFailure)
        assertEquals(0, fakePort.callCount) // port sama sekali tidak dipanggil!
        assertEquals("Draft", controller.rows.first().get("Kolom"))
        assertTrue(controller.phase is BlockDataPhase.Error)
    }

    @Test
    fun testSequentialActionsSerializedNoDoubleState() = runTest {
        val screen = sampleKanbanScreen()
        val initialRow = PrototypeRow("row-1", mapOf("Kolom" to "Draft", "Judul" to "Kartu A"))
        val fakePort = FakeBlockDataPort(rows = mutableListOf(initialRow), delayMs = 50)
        val controller = BlockDataController(fakePort, screen.spec, InteractiveScreenFactory.ENTITY_ID)
        controller.load()

        // Luncurkan 2 aksi berurutan secara asinkron
        val job1 = launch { controller.move("row-1", "Kolom", "Proses") }
        val job2 = launch { controller.update("row-1", mapOf("Judul" to "Kartu A Updated")) }

        job1.join()
        job2.join()

        assertEquals(1, controller.rows.size)
        val finalRow = controller.rows.first()
        assertEquals("Proses", finalRow.get("Kolom"))
        assertEquals("Kartu A Updated", finalRow.get("Judul"))
        assertEquals(BlockDataPhase.Idle, controller.phase)
    }
}
