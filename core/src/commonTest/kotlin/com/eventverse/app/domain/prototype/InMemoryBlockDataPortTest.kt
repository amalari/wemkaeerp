package com.eventverse.app.domain.prototype

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Kerangka [InMemoryBlockDataPort] (kontrak G0, plan induk §3.1) di atas fixture **non-garment**
 * (tiket servis, Kontrak 6). Pengujian penuh — atomisitas multi-field yang lebih dalam, paritas
 * reducer per kasus, dan balapan antar-operasi — menyusul di butir B1 sesuai plan.
 */
class InMemoryBlockDataPortTest {
    private fun port(seed: List<PrototypeRow> = PrototypeContractSamples.ticketSeed.getValue("tiket")) =
        InMemoryBlockDataPort(PrototypeContractSamples.ticketSpec, "tiket", seed)

    @Test
    fun load_returnsSeedRows() = runTest {
        assertEquals(2, port().load().getOrThrow().size)
    }

    @Test
    fun create_assignsStablePrefixedUniqueIds_andEnforcesRequired() = runTest {
        val p = port()
        val first = p.create(mapOf("Judul" to "Laptop lambat", "Peminta" to "Sari", "Status" to "Baru")).getOrThrow()
        val second = p.create(mapOf("Judul" to "Proyektor mati", "Status" to "Baru")).getOrThrow()
        assertTrue(first.id.startsWith("tiket-") && second.id.startsWith("tiket-"), "prefiks stabil dari entityId")
        assertTrue(first.id != second.id, "id unik")
        assertTrue(p.create(mapOf("Peminta" to "tanpa judul")).isFailure, "field wajib ditegakkan")
    }

    @Test
    fun update_appliesSeveralFields_andRejectsUnknownRow() = runTest {
        val p = port()
        val id = p.load().getOrThrow().first().id
        val updated = p.update(id, mapOf("Judul" to "AC ruang server", "Status" to "Diproses")).getOrThrow()
        assertEquals("AC ruang server", updated["Judul"])
        assertEquals("Diproses", updated["Status"])
        assertTrue(p.update("hantu", mapOf("Judul" to "x")).isFailure, "baris tak ada ditolak")
    }

    @Test
    fun update_violatingTransition_leavesRowUnchanged() = runTest {
        val p = port()
        val id = p.load().getOrThrow().first().id // status "Baru"; Baru → Selesai tidak diizinkan mesin
        val before = p.load().getOrThrow().single { it.id == id }
        assertTrue(p.update(id, mapOf("Status" to "Selesai", "Peminta" to "Sari")).isFailure)
        assertEquals(before, p.load().getOrThrow().single { it.id == id }, "atomik: tak ada yang berubah")
    }

    @Test
    fun delete_removesOnlyThatRow_andRejectsRepeat() = runTest {
        val p = port()
        val id = p.load().getOrThrow().first().id
        p.delete(id).getOrThrow()
        assertEquals(1, p.load().getOrThrow().size)
        assertTrue(p.delete(id).isFailure, "hapus dua kali ditolak")
    }

    @Test
    fun failure_isPortExceptionWithValidation_andUserMessage() = runTest {
        val e = port().create(mapOf("Peminta" to "tanpa judul")).exceptionOrNull()
        assertTrue(e is PortException && e.error is PortError.Validation, "dapat ${e?.javaClass?.simpleName}")
        assertTrue(e.message.orEmpty().contains("wajib"), "pesan berbahasa pengguna: ${e.message}")
    }
}
