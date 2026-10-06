package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ScreenProposalFixtures.kanbanAntrean
import com.eventverse.app.domain.prototype.DataBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScreenProposalConversionTest {

    @Test
    fun `kanban sah menjadi layar interaktif dengan kolom dari opsi status`() {
        val screen = kanbanAntrean().toInteractiveScreen().getOrThrow()
        val spec = screen.spec.screens.single()
        assertEquals(WidgetKind.KANBAN, spec.widget)
        val kanban = assertNotNull(spec.kanban)
        assertEquals("status", kanban.groupField)
        assertEquals(listOf("Menunggu", "Diperiksa", "Selesai"), kanban.columns)
        assertEquals("nama", kanban.titleField)
        assertEquals(listOf("nama", "keluhan", "tanggal_kunjungan", "prioritas"), kanban.card.map { it.field })
        assertEquals(listOf("nama", "keluhan", "status"), kanban.detailForm?.fields)
        assertEquals(2, screen.newStore().rows.getValue("pasien").size)
        assertEquals(DataBinding.Memory, screen.binding)
    }

    @Test
    fun `transisi proposal menjadi mesin status dan tanpa transisi tidak ada mesin`() {
        val withMachine = kanbanAntrean().toInteractiveScreen().getOrThrow().spec.entity("pasien")
        assertEquals(setOf("Diperiksa"), withMachine?.stateMachine?.transitions?.get("Menunggu"))
        val free = ScreenProposalFixtures.tabelTagihan().toInteractiveScreen().getOrThrow().spec.entity("tagihan")
        assertEquals(null, free?.stateMachine)
    }

    @Test
    fun `tabel form checklist dan dasbor semuanya terbentuk`() {
        listOf(
            ScreenProposalFixtures.tabelTagihan(), ScreenProposalFixtures.formPendaftaran(),
            ScreenProposalFixtures.checklistPersiapan(), ScreenProposalFixtures.dasborHarian()
        ).forEach { p ->
            val screen = p.toInteractiveScreen()
            assertTrue(screen.isSuccess, "${p.screenId}: ${screen.exceptionOrNull()?.message}")
            assertEquals(p.widget, screen.getOrThrow().spec.screens.single().widget)
        }
        val table = ScreenProposalFixtures.tabelTagihan().toInteractiveScreen().getOrThrow().spec.screens.single().table
        assertEquals("status", table?.statusField)
        assertEquals(true, table?.inlineCreate)
    }

    @Test
    fun `usulan tak sah gagal dengan pesan berpath dan tidak melempar mentah`() {
        val result = kanbanAntrean().copy(rationale = "").toInteractiveScreen()
        val error = assertIs<ProposalConversionException>(result.exceptionOrNull())
        assertEquals("$.rationale", error.issues.first().path)
        assertTrue(error.message.orEmpty().contains("$.rationale"))
    }

    @Test
    fun `cetak dan layar kustom sah tapi tidak punya bentuk interaktif`() {
        listOf(ScreenProposalFixtures.cetakKuitansi(), ScreenProposalFixtures.layarKustom()).forEach { p ->
            assertEquals(emptyList(), ScreenProposalValidator.validate(p))
            val error = assertIs<ProposalConversionException>(p.toInteractiveScreen().exceptionOrNull())
            assertTrue(error.message.orEmpty().contains("tidak punya bentuk interaktif"))
        }
    }

    @Test
    fun `binding proposal diteruskan ke layar`() {
        val api = ScreenProposalFixtures.tabelTagihan().copy(binding = DataBinding.Api("/api/tagihan"))
        assertEquals(DataBinding.Api("/api/tagihan"), api.toInteractiveScreen().getOrThrow().binding)
    }
}
