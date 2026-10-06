package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.json.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Perbaikan setelah eval live 2026-10-06 (0/3 untuk semua kasus garment): model mengarang blueprint baru
 * (`garment_starter`) untuk pack bawaan. Jembatan blueprint memilih starter bawaan lewat kode, dan katalog alat
 * memberi tahu model kode apa yang ada.
 */
class KoogShippedBridgeTest {

    private fun answer(blueprintNode: String) = """{"pack":{"useShipped":"garment"},"blueprint":$blueprintNode,"screens":[]}"""

    @Test
    fun `useShipped pada blueprint menghasilkan starter bawaan persis`() {
        GarmentBlueprints.all.forEach { starter ->
            val draft = decodeAnswer(answer("""{"useShipped":"${starter.code.value}"}"""))
            assertEquals(starter, draft.blueprint, starter.code.value)
            assertEquals(GarmentDomainPack.pack, draft.pack)
        }
    }

    @Test
    fun `kode starter tak dikenal ditolak berpath dengan daftar pilihan`() {
        val e = assertFailsWith<DiscoveryDraftDecodeException> { decodeAnswer(answer("""{"useShipped":"garment_starter"}""")) }
        assertEquals("$.blueprint.useShipped", e.path)
        GarmentBlueprints.all.forEach { assertTrue(e.message.orEmpty().contains(it.code.value), e.message) }
    }

    @Test
    fun `blueprint tulisan sendiri tetap lewat apa adanya tanpa disentuh jembatan`() {
        val own = DiscoveryDraftCodec.encodeToString(com.eventverse.app.domain.discovery.DiscoveryDraft(GarmentDomainPack.pack, GarmentBlueprints.CMT_MAKLOON))
        assertEquals(own, applyShippedBlueprintBridge(own))
    }

    @Test
    fun `katalog alat memuat starter blueprint bawaan beserta kode jembatannya`() {
        val catalog = JsonParser.parseObject(platformCatalogJson(listOf(GarmentDomainPack.pack)))
        val starters = catalog.objectArray("shippedPacks").single().objectArray("starterBlueprints")
        assertEquals(GarmentBlueprints.all.map { it.code.value }, starters.map { it.string("code") })
        starters.forEach { assertTrue(it.string("reuseWith").orEmpty().contains("useShipped"), it.string("code")) }
    }

    @Test
    fun `prompt mewajibkan kode pack dari petunjuk, pack bawaan, starter, status non-alur, dan kemampuan`() {
        val s = KoogDiscoveryPrompt.system
        listOf("Petunjuk industri", "WAJIB memakai pack itu", "starterBlueprints", "KOSONGKAN `transitions`", "wajib punya modul sendiri")
            .forEach { assertTrue(s.contains(it), "prompt tidak memuat '$it'") }
    }
}
