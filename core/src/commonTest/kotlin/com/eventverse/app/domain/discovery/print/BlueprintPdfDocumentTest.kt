package com.eventverse.app.domain.discovery.print

import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.DiscoveryRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Isi dokumen blueprint (plan §5 Fase D).
 *
 * Dua hal yang dijaga di sini dan tidak bisa dijaga test lain:
 * 1. **Vertikalitas** — PDF tenant klinik tidak boleh memuat kata konveksi (pelajaran A4: kosakata
 *    netral yang tidak dikunci test akan kembali menjadi kosakata garment).
 * 2. **Kejujuran modul** — modul bypass ikut tercetak; PDF yang hanya menampilkan modul aktif
 *    membuat prospek membandingkan penawaran dengan sistem yang berbeda.
 */
class BlueprintPdfDocumentTest {

    private val agent = DeterministicDiscoveryAgent()

    private suspend fun clinic(): DiscoveryDraft = agent.draft(
        DiscoveryRequest(
            "Kami klinik gigi: pasien mendaftar antrean per poli, ada stok obat, dan tagihan pembayaran kasir.",
            industryHint = "klinik"
        )
    ).getOrThrow()

    private suspend fun document(draft: DiscoveryDraft, status: DiscoveryDraftStatus = DiscoveryDraftStatus.DRAFT) =
        BlueprintPdfDocument.of(draft, generatedAtLabel = "30 Sep 2026 14:05 WIB", watermark = BlueprintPdfDocument.watermarkFor(status))

    @Test
    fun `dokumen klinik berbicara istilah klinik bukan kosakata pabrik`() = runTest {
        val doc = document(clinic())

        assertEquals("klinik", doc.terms.firstOrNull { it.first == "perusahaan" }?.second)
        assertEquals("Kunjungan", doc.terms.firstOrNull { it.first == "dokumen" }?.second)

        val moduleNames = doc.modules.map { it.displayName }.joinToString(" ")
        assertFalse(moduleNames.contains("SPK"), "Nama modul klinik tidak boleh menyebut SPK: $moduleNames")
        assertFalse(moduleNames.contains("pabrik", ignoreCase = true))
    }

    @Test
    fun `setiap modul pack tercetak sekali dan parameter mengikuti blueprint`() = runTest {
        val draft = clinic()
        val doc = document(draft)

        assertEquals(draft.pack.modules.size, doc.modules.size)
        assertEquals(draft.pack.modules.map { it.id.value }, doc.modules.map { it.moduleCode })
        assertEquals(draft.pack.modules.size, doc.modules.map { it.moduleCode }.distinct().size)
        assertEquals(draft.blueprint.activeModuleCodes.size, doc.activeModuleCount)

        doc.modules.forEach { module ->
            val expected = draft.blueprint.parametersOf(module.moduleCode).entries.sortedBy { it.key }.map { it.key to it.value }
            assertEquals(expected, module.parameters, "Parameter ${module.moduleCode} harus sama dengan blueprint")
        }
    }

    @Test
    fun `modul yang dihapus dari alur tetap tercetak bertanda bypass`() = runTest {
        val draft = clinic()
        val doc = document(draft)
        val bypassed = doc.modules.filter { !it.active }

        // Bukan asumsi tentang jumlahnya: kalau agent memang mengaktifkan semuanya, tak ada yang diuji
        // di sini dan test di bawah tetap bermakna lewat modifikasi paksa blueprint.
        val forced = draft.copy(blueprint = draft.blueprint.copy(modules = draft.blueprint.modules.map { it.copy(active = false) }))
        val forcedDoc = document(forced)

        assertEquals(0, forcedDoc.activeModuleCount)
        assertTrue(forcedDoc.modules.all { !it.active })
        assertEquals(bypassed.size, doc.modules.size - doc.activeModuleCount)
    }

    @Test
    fun `watermark mengikuti status draf`() = runTest {
        val draft = clinic()
        assertEquals(BlueprintPdfDocument.WATERMARK_DRAFT, document(draft, DiscoveryDraftStatus.DRAFT).watermark)
        assertEquals(BlueprintPdfDocument.WATERMARK_LOCKED, document(draft, DiscoveryDraftStatus.LOCKED).watermark)
    }

    @Test
    fun `fase dan layar mengikuti urutan pack`() = runTest {
        val draft = clinic()
        val doc = document(draft)

        // Baris fase memakai `displayName` pack apa adanya: pack sudah menomori namanya sendiri
        // ("1. Operasi"), dan menambahkan `order` lagi menghasilkan "1. 1. Operasi" — ketahuan saat
        // mencetak PDF klinik dan melihatnya, bukan oleh test mana pun.
        assertEquals(draft.pack.orderedPhases.map { "${it.displayName} — ${it.subtitle}" }, doc.phases)
        assertEquals(draft.screens.size, doc.screens.size)
    }
}
