package com.eventverse.app.domain.prototype

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Butir B5: kalimat emas bahasa Indonesia lintas template — garment (sampling), bordir, sablon, dan
 * non-konveksi (tiket servis). Kalimat tak dikenal / berbahaya **ditolak**, bukan ditebak.
 */
class DeterministicSpecOpProposerTest {
    private val proposer = DeterministicSpecOpProposer()

    private val sampling = InteractiveScreenFactory.kanban(
        "spk", "SPK Sampling",
        listOf(mapOf("Kolom" to "Baru", "Kartu" to "SP-1")),
        KanbanHints(listOf("Baru", "Digitizing", "Jahit", "Selesai"))
    )!!
    private val bordir = InteractiveScreenFactory.kanban(
        "brd", "SPK Bordir",
        listOf(mapOf("Kolom" to "Digitizing", "Kartu" to "B-1")),
        KanbanHints(listOf("Baru", "Digitizing", "Hooping", "Selesai"))
    )!!
    private val sablon = InteractiveScreenFactory.kanban(
        "sbl", "SPK Sablon",
        listOf(mapOf("Kolom" to "Screen", "Kartu" to "S-1")),
        KanbanHints(listOf("Baru", "Screen", "Curing", "Selesai"))
    )!!
    private val tiket = PrototypeContractSamples.ticketScreen

    @Test
    fun addStatus_recognisedAcrossAllFourTemplates() = runTest {
        assertEquals(
            listOf(SpecOp.AddEnumOption("item", "Kolom", "Pressing", after = "Selesai")),
            proposer.propose("tambah status Pressing setelah Selesai", sampling).getOrThrow()
        )
        assertEquals(
            listOf(SpecOp.AddEnumOption("item", "Kolom", "Boncet", after = "Digitizing")),
            proposer.propose("tambahkan status Boncet setelah Digitizing", bordir).getOrThrow()
        )
        assertEquals(
            listOf(SpecOp.AddEnumOption("item", "Kolom", "Reclaim", after = null)),
            proposer.propose("tambah status Reclaim", sablon).getOrThrow()
        )
        assertEquals(
            listOf(SpecOp.AddEnumOption("tiket", "Status", "Revisi", after = "Diproses")),
            proposer.propose("tambah status Revisi setelah Diproses", tiket).getOrThrow()
        )
    }

    @Test
    fun rename_statusGoesToEnumOption_fieldGoesToLabel() = runTest {
        assertEquals(
            listOf(SpecOp.RenameEnumOption("item", "Kolom", "Selesai", "Arsip")),
            proposer.propose("ganti nama Selesai jadi Arsip", sampling).getOrThrow()
        )
        assertEquals(
            listOf(SpecOp.RenameEnumOption("item", "Kolom", "Hooping", "Penyempitan")),
            proposer.propose("ubah nama status Hooping menjadi Penyempitan", bordir).getOrThrow()
        )
        assertEquals(
            listOf(SpecOp.RenameFieldLabel("tiket", "Peminta", "Dilaporkan oleh")),
            proposer.propose("ganti nama Peminta jadi Dilaporkan oleh", tiket).getOrThrow()
        )
    }

    @Test
    fun addField_timeViaBertipeSuffix_closedVocabulary() = runTest {
        assertEquals(
            listOf(SpecOp.AddField("tiket", FieldSpec("Jam Mulai", "Jam Mulai", FieldType.TIME))),
            proposer.propose("tambah kolom Jam Mulai bertipe TIME", tiket).getOrThrow()
        )
        assertEquals(
            listOf(SpecOp.AddField("tiket", FieldSpec("Jam Selesai", "Jam Selesai", FieldType.TIME))),
            proposer.propose("tambah field Jam Selesai bertipe time", tiket).getOrThrow()
        )
        val err = assertNotNull(
            proposer.propose("tambah kolom Catatan bertipe bintang", tiket).exceptionOrNull(),
            "kalimat bertipe tak dikenal harus ditolak"
        )
        assertTrue(err.message?.contains("belum didukung") == true, err.message)
        // Tanpa akhiran bertipe: tetap TEXT (perilaku lama tidak berubah).
        assertEquals(
            listOf(SpecOp.AddField("tiket", FieldSpec("Prioritas", "Prioritas", FieldType.TEXT))),
            proposer.propose("tambah kolom Prioritas", tiket).getOrThrow()
        )
    }

    @Test
    fun addFieldAndTransition_matchCaseInsensitively() = runTest {
        assertEquals(
            listOf(SpecOp.AddField("tiket", FieldSpec("Prioritas", "Prioritas", FieldType.TEXT))),
            proposer.propose("tambah kolom Prioritas", tiket).getOrThrow()
        )
        assertEquals(
            listOf(SpecOp.AddField("tiket", FieldSpec("No. HP", "No. HP", FieldType.TEXT))),
            proposer.propose("tambah field No. HP", tiket).getOrThrow()
        )
        val expected = listOf(SpecOp.AddTransition("item", "Kolom", "Baru", "Selesai"))
        assertEquals(expected, proposer.propose("izinkan Baru ke Selesai", sampling).getOrThrow())
        assertEquals(expected, proposer.propose("bolehkan baru ke selesai", sampling).getOrThrow())
    }

    @Test
    fun multipleRequests_splitByLaluSemicolonOrNewline() = runTest {
        val result = proposer.propose("tambah status Pressing lalu tambah kolom Catatan", sampling).getOrThrow()
        assertEquals(
            listOf(
                SpecOp.AddEnumOption("item", "Kolom", "Pressing", after = null),
                SpecOp.AddField("item", FieldSpec("Catatan", "Catatan", FieldType.TEXT))
            ),
            result
        )
        assertEquals(3, proposer.propose("tambah status A; tambah status B\ntambah status C", sablon).getOrThrow().size)
    }

    @Test
    fun unknownOrDangerousSentence_isRejectedWithExamples_notGuessed() = runTest {
        listOf(
            "hapus semua data",
            "buatkan dashboard penjualan",
            "tambah status X; hapus semuanya",
            ""
        ).forEach { msg ->
            val result = proposer.propose(msg, sampling)
            assertTrue(result.isFailure, "harus ditolak: '$msg'")
            assertTrue(result.exceptionOrNull()!!.message!!.contains("Contoh"), "pesan memuat contoh kalimat")
        }
    }

    @Test
    fun unknownReferences_areRejectedWithClearMessage() = runTest {
        val transisi = proposer.propose("izinkan Baru ke Negeri Dongeng", sampling)
        assertTrue(transisi.exceptionOrNull()!!.message!!.contains("Negeri Dongeng"))
        assertTrue(proposer.propose("ganti nama Bulan jadi Bintang", sampling).isFailure)
    }

    @Test
    fun moreThanFiveRequests_isRejected() = runTest {
        val msg = (1..6).joinToString(" lalu ") { "tambah kolom Kolom$it" }
        val result = proposer.propose(msg, tiket)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Maksimal"))
    }
}