package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.WidgetRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentScreenSuggestions
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.InteractiveScreenFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test paritas Strangler Fig (tenant-variability Kontrak 8) untuk acuan emas B1: setiap suggestion garment
 * diekspresikan sebagai [ScreenProposal] dan harus **setara** dengan `WidgetRegistry.interactiveFor` dari
 * suggestion yang sama. Test mengiterasi daftar suggestion — entri baru tanpa padanan membuat test gagal.
 */
class GarmentProposalParityTest {
    private val pack = GarmentDomainPack.pack

    private val all: List<ScreenProposal> = PackScreenProposer.proposalsForAll(pack).getOrThrow()

    /** Entitas lama selalu `item`; proposal memberi id modulnya supaya layar satu draf tak bertabrakan. Hanya id yang dinormalkan. */
    private fun legacyOf(p: ScreenProposal): InteractiveScreen? {
        val legacy = WidgetRegistry.interactiveFor(PrototypeScreen(p.screenId, p.moduleId, p.title, p.widget.code), pack) ?: return null
        val id = p.entity?.id ?: return legacy
        return legacy.copy(
            spec = legacy.spec.copy(
                entities = legacy.spec.entities.map { it.copy(id = id) },
                screens = legacy.spec.screens.map { s -> s.copy(entityId = s.entityId?.let { id }) }
            ),
            seed = legacy.seed.mapKeys { id }
        )
    }

    @Test
    fun `setiap suggestion garment punya padanan proposal`() {
        GarmentScreenSuggestions.all.forEach { s ->
            assertTrue(all.any { it.moduleId == s.moduleId && it.widget == s.widget }, "Suggestion ${s.moduleId.value} tanpa proposal")
        }
        // 9 suggestion + 1 form pelengkap (stok kain)
        assertEquals(GarmentScreenSuggestions.all.size + GarmentScreenSuggestions.all.count { it.formHints != null }, all.size)
    }

    @Test
    fun `semua proposal garment lolos validator sebagai pack`() {
        all.forEach { p ->
            assertEquals(emptyList(), ScreenProposalValidator.validate(p, source = ProposalSource.Pack, packModuleIds = pack.modules.map { it.id.value }.toSet()), p.screenId)
            assertTrue(p.rationale.isNotBlank(), p.screenId)
        }
    }

    @Test
    fun `toInteractiveScreen setara dengan interactiveFor untuk setiap modul`() {
        all.forEach { p ->
            val legacy = legacyOf(p)
            val converted = p.toInteractiveScreen(ProposalSource.Pack)
            if (legacy == null) {
                assertTrue(converted.isFailure, "${p.screenId}: interactiveFor null, proposal harus gagal konversi (digambar statis)")
            } else {
                assertEquals(legacy, converted.getOrThrow(), p.screenId)
            }
        }
    }

    @Test
    fun `hanya cetak yang tidak interaktif di garment`() {
        val statics = all.filter { legacyOf(it) == null }
        assertEquals(listOf(WidgetKind.PRINT), statics.map { it.widget })
    }

    @Test
    fun `layar form berbagi entitas dengan layar sumbernya dan tidak memakai awalan default`() {
        val stok = all.filter { it.moduleId == com.eventverse.app.domain.pack.GarmentModules.INVENTORY }
        assertEquals(listOf(WidgetKind.TABLE, WidgetKind.FORM), stok.map { it.widget })
        assertEquals(stok[0].entity?.id, stok[1].entity?.id)
        assertTrue(!stok[1].screenId.startsWith("default-"), "awalan default- milik layar sumber (WidgetRegistry.screenFor)")
    }

    @Test
    fun `kunci dan petunjuk lama tidak hilang di proposal`() {
        val papan = all.first { it.screenId == "default-${com.eventverse.app.domain.pack.GarmentModules.SAMPLING_ORDER.value}" }
        val view = assertIs<ViewProposal.Kanban>(papan.view)
        assertEquals("Simpan SPK", view.detailFormSubmitLabel)
        assertEquals(7, view.card.size)
        assertEquals(InteractiveScreenFactory.GROUP_FIELD, papan.entity?.statusField)
    }

    @Test
    fun `kunci longgar hanya berlaku untuk pack`() {
        val crm = all.first { it.entity?.fields?.any { f -> f.key == "No. PO" } == true }
        assertTrue(ScreenProposalValidator.validate(crm, source = ProposalSource.Deterministic).any { it.message.contains("No. PO") })
        assertTrue(crm.toInteractiveScreen().isFailure)
    }

    @Test
    fun `suggestion tanpa rationale ditolak dan modul tanpa suggestion kosong`() {
        val noReason = pack.copy(screenSuggestions = pack.screenSuggestions.map { it.copy(rationale = null) })
        val failure = PackScreenProposer.proposalsForAll(noReason)
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("rationale"))
        val module = pack.modules.first { m -> pack.screenSuggestions.none { it.moduleId == m.id } }
        assertEquals(emptyList(), PackScreenProposer.proposalsFor(pack, module).getOrThrow())
    }
}
