package com.eventverse.app.domain.prototype

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [InMemoryBlockDataPort] penuh (butir B1) di atas fixture **non-garment** (tiket servis, Kontrak 6):
 * atomisitas multi-field, id unik monoton, field wajib, transisi status, hapus baris tak ada,
 * serialisasi antar-operasi, dan **paritas kasus-per-kasus dengan [PrototypeReducer]** (AC B1) —
 * satu sumber aturan, kebijakan plan induk §2.1.
 */
class InMemoryBlockDataPortTest {
    private fun port(seed: List<PrototypeRow> = PrototypeContractSamples.ticketSeed.getValue("tiket")) =
        InMemoryBlockDataPort(PrototypeContractSamples.ticketSpec, "tiket", seed)

    // ---- load & create ----------------------------------------------------------------------

    @Test
    fun load_returnsSeedRows() = runTest {
        assertEquals(2, port().load().getOrThrow().size)
    }

    @Test
    fun create_assignsStablePrefixedMonotonicIds_neverReusedAfterDelete() = runTest {
        val p = port()
        val first = p.create(mapOf("Judul" to "Laptop lambat", "Status" to "Baru")).getOrThrow()
        val second = p.create(mapOf("Judul" to "Proyektor mati", "Status" to "Baru")).getOrThrow()
        assertTrue(first.id.startsWith("tiket-") && second.id.startsWith("tiket-"), "prefiks stabil dari entityId")
        assertTrue(first.id != second.id, "id unik")
        p.delete(first.id).getOrThrow()
        val third = p.create(mapOf("Judul" to "Printer macet", "Status" to "Baru")).getOrThrow()
        assertTrue(third.id != first.id, "id yang terhapus tidak dipakai ulang selama umur port")
    }

    @Test
    fun create_rejectsBlankRequired_andUnknownField() = runTest {
        val p = port()
        assertTrue(p.create(mapOf("Judul" to "", "Status" to "Baru")).isFailure, "field wajib kosong ditolak")
        assertTrue(p.create(mapOf("Judul" to "x", "Hantu" to "y")).isFailure, "field tak dikenal ditolak")
    }

    // ---- update: atomik & klasifikasi galat ---------------------------------------------------

    @Test
    fun update_appliesSeveralFields_inOrder() = runTest {
        val p = port()
        val id = p.load().getOrThrow().first().id
        val updated = p.update(id, mapOf("Judul" to "AC ruang server", "Status" to "Diproses")).getOrThrow()
        assertEquals("AC ruang server", updated["Judul"])
        assertEquals("Diproses", updated["Status"])
    }

    @Test
    fun update_atomic_whenLastChangeViolates_earlierChangesRolledBack() = runTest {
        val p = port()
        val before = p.load().getOrThrow().first() // status "Baru"; Baru → Selesai terlarang
        val result = p.update(before.id, mapOf("Judul" to "sah", "Peminta" to "sari", "Status" to "Selesai"))
        assertTrue(result.isFailure)
        assertEquals(before, p.load().getOrThrow().single { it.id == before.id }, "atomik: satu pelanggaran = tak ada yang berubah")
    }

    @Test
    fun update_atomic_whenFieldUnknown_nothingChanges() = runTest {
        val p = port()
        val before = p.load().getOrThrow().first()
        assertTrue(p.update(before.id, mapOf("Judul" to "sah", "Hantu" to "x")).isFailure)
        assertEquals(before, p.load().getOrThrow().single { it.id == before.id }, "atomik: field tak dikenal = tak ada yang berubah")
    }

    @Test
    fun update_rejectsBlankingRequiredField_withUserMessage() = runTest {
        val p = port()
        val id = p.load().getOrThrow().first().id
        val result = p.update(id, mapOf("Judul" to ""))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("wajib"), "pesan berbahasa pengguna")
    }

    @Test
    fun missingRow_updateAndDelete_areNotFound_notValidation() = runTest {
        val p = port()
        val updateError = p.update("hantu", mapOf("Judul" to "x")).exceptionOrNull()
        val deleteError = p.delete("hantu").exceptionOrNull()
        assertTrue(updateError is PortException && updateError.error is PortError.NotFound, "dapat ${updateError?.javaClass?.simpleName}")
        assertTrue(deleteError is PortException && deleteError.error is PortError.NotFound)
        assertTrue(updateError.message.orEmpty().contains("tidak ada"), "pesan berbahasa pengguna")
    }

    // ---- delete -------------------------------------------------------------------------------

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

    // ---- paritas dengan PrototypeReducer (AC B1: iterasi kasus yang sama) ----------------------

    /** Perintah mini yang punya padanan 1:1 di kedua jalur (port dan reducer). */
    private sealed interface CaseCmd {
        data class CreateRow(val values: Map<String, String>) : CaseCmd
        data class SetRow(val rowId: String, val field: String, val value: String) : CaseCmd
        data class RemoveRow(val rowId: String) : CaseCmd
    }

    @Test
    fun port_parityWithReducer_sameOutcomeEveryCase() = runTest {
        val spec = PrototypeContractSamples.ticketSpec
        val seed = PrototypeContractSamples.ticketSeed.getValue("tiket")
        val cases = listOf(
            "create sah" to CaseCmd.CreateRow(mapOf("Judul" to "kasus", "Status" to "Baru")),
            "create field tak dikenal" to CaseCmd.CreateRow(mapOf("Judul" to "x", "Hantu" to "y")),
            "create enum tak sah" to CaseCmd.CreateRow(mapOf("Judul" to "x", "Status" to "Hantu")),
            "create wajib kosong" to CaseCmd.CreateRow(mapOf("Judul" to "", "Status" to "Baru")),
            "create wajib tidak diisi" to CaseCmd.CreateRow(mapOf("Status" to "Baru")),
            "set sah" to CaseCmd.SetRow("t-1", "Judul", "baru"),
            "set transisi terlarang" to CaseCmd.SetRow("t-1", "Status", "Selesai"),
            "set enum tak sah" to CaseCmd.SetRow("t-1", "Status", "Hantu"),
            "set wajib dikosongkan" to CaseCmd.SetRow("t-1", "Judul", ""),
            "set baris tak ada" to CaseCmd.SetRow("hantu", "Judul", "x"),
            "set field tak dikenal" to CaseCmd.SetRow("t-1", "Hantu", "x"),
            "hapus ada" to CaseCmd.RemoveRow("t-2"),
            "hapus tak ada" to CaseCmd.RemoveRow("hantu"),
        )
        cases.forEach { (name, cmd) ->
            // Jalur reducer: toko segar dari seed yang sama, aksi padanannya.
            val fresh = PrototypeStore.seeded(spec, mapOf("tiket" to seed))
            val viaReducer = runCatching {
                when (val k = cmd) {
                    is CaseCmd.CreateRow -> PrototypeReducer.reduce(spec, fresh, PrototypeAction.Create("tiket", PrototypeRow("kasus-1", k.values))).getOrThrow()
                    is CaseCmd.SetRow -> PrototypeReducer.reduce(spec, fresh, PrototypeAction.SetField("tiket", k.rowId, k.field, k.value)).getOrThrow()
                    is CaseCmd.RemoveRow -> PrototypeReducer.reduce(spec, fresh, PrototypeAction.Delete("tiket", k.rowId)).getOrThrow()
                }
            }
            // Jalur port: port segar dari seed yang sama.
            val p = InMemoryBlockDataPort(spec, "tiket", seed)
            val viaPort = runCatching {
                when (val k = cmd) {
                    is CaseCmd.CreateRow -> p.create(k.values).getOrThrow()
                    is CaseCmd.SetRow -> p.update(k.rowId, mapOf(k.field to k.value)).getOrThrow()
                    is CaseCmd.RemoveRow -> p.delete(k.rowId).getOrThrow()
                }
            }
            assertEquals(
                viaReducer.isSuccess, viaPort.isSuccess,
                "kasus '$name': keberhasilan beda — reducer=${viaReducer.exceptionOrNull()?.message} vs port=${viaPort.exceptionOrNull()?.message}"
            )
            // Isi baris dibandingkan berurutan tanpa id — id hasil `create` memang beda kontrak
            // (id diberikan implementasi); aturan validasinya yang wajib identik.
            val expected = (viaReducer.getOrNull() ?: PrototypeStore.seeded(spec, mapOf("tiket" to seed))).rowsOf("tiket").map { it.values }
            assertEquals(expected, p.load().getOrThrow().map { it.values }, "kasus '$name': isi baris beda")
        }
    }

    // ---- serialisasi antar-operasi (tak ada balapan) -------------------------------------------

    @Test
    fun concurrent_creates_areSerialized_idsUnique_noLostRow() = runTest {
        val p = port(seed = emptyList())
        val ids = coroutineScope {
            (1..40).map { n -> async(Dispatchers.Default) { p.create(mapOf("Judul" to "tiket $n")).getOrThrow().id } }.awaitAll()
        }
        assertEquals(40, ids.distinct().size, "id unik di bawah konkurensi")
        assertEquals(40, p.load().getOrThrow().size, "tak ada baris hilang")
    }

    @Test
    fun concurrent_mixedOperations_finalStateConsistent() = runTest {
        val p = port(seed = emptyList())
        val created: List<PrototypeRow> = coroutineScope {
            (1..30).map { n -> async(Dispatchers.Default) { p.create(mapOf("Judul" to "n$n")).getOrThrow() } }.awaitAll()
        }
        val toDelete = created.take(10).map { it.id }.toSet()
        coroutineScope {
            created.map { row ->
                async(Dispatchers.Default) {
                    if (row.id in toDelete) p.delete(row.id).getOrThrow() else p.update(row.id, mapOf("Peminta" to "sari")).getOrThrow()
                }
            }.awaitAll()
        }
        val rows = p.load().getOrThrow()
        assertEquals(20, rows.size, "30 dibuat − 10 dihapus")
        assertTrue(rows.none { it.id in toDelete }, "baris terhapus benar-benar pergi")
        rows.forEach { assertEquals("sari", it["Peminta"], "baris yang tersisa ter-update utuh") }
    }

    @Test
    fun concurrent_updatesOnSameRow_convergeToOneWrittenValue() = runTest {
        val p = port()
        val row = p.load().getOrThrow().first()
        val written = (1..20).map { "penulis $it" }.toSet()
        coroutineScope {
            written.map { v -> async(Dispatchers.Default) { p.update(row.id, mapOf("Judul" to v)).getOrThrow() } }.awaitAll()
        }
        val final = p.load().getOrThrow().single { it.id == row.id }["Judul"]
        assertTrue(final in written, "nilai akhir salah satu tulisan, tanpa korupsi: $final")
    }
}

