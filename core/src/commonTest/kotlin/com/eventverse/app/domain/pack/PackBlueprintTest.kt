package com.eventverse.app.domain.pack

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackDecodeException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TRD-PLAT-008: Blueprint non-garment sebagai data milik pack — invarian, codec, resolusi, paritas garment. */
class PackBlueprintTest {

    /** Fixture non-default: pack klinik hasil agent deterministik, dengan starter-nya sebagai milik pack. */
    private suspend fun klinik(): Pair<DomainPack, Blueprint> {
        val draft = DeterministicDiscoveryAgent().draft(
            DiscoveryRequest("Kami klinik dengan antrean pasien dan tagihan kasir.", industryHint = "klinik")
        ).getOrThrow()
        return draft.pack.copy(blueprints = listOf(draft.blueprint)) to draft.blueprint
    }

    @Test
    fun `pack klinik membawa blueprint dan round-trip codec sama persis`() = runTest {
        val (pack, bp) = klinik()
        val json = DomainPackCodec.encodeToString(pack)
        assertTrue(json.contains("\"blueprints\""))
        assertEquals(pack, DomainPackCodec.decode(json))
        assertEquals(listOf(bp), DomainPackCodec.decode(json).blueprints)
    }

    @Test
    fun `kunci blueprints aditif - pack tanpa blueprint ter-encode byte-identik dan garment tidak menulisnya`() = runTest {
        val (pack, _) = klinik()
        val without = pack.copy(blueprints = emptyList())
        val withKey = DomainPackCodec.encode(pack)
        assertEquals(DomainPackCodec.encode(without).encode(), JsonValue.Obj(withKey.entries - "blueprints").encode())
        assertFalse(DomainPackCodec.encodeToString(without).contains("blueprints"))
        assertFalse(DomainPackCodec.encodeToString(GarmentDomainPack.pack).contains("blueprints"))
        assertTrue(GarmentDomainPack.pack.blueprints.isEmpty(), "starter garment tetap di GarmentBlueprints (K2)")
    }

    @Test
    fun `invarian pack menolak blueprint ganda, pack lain, dan modul yang tidak ada`() = runTest {
        val (pack, bp) = klinik()
        assertTrue(assertFailsWith<IllegalStateException> { pack.copy(blueprints = listOf(bp, bp)) }.message!!.contains("ganda"))
        assertFailsWith<IllegalArgumentException> { pack.copy(blueprints = listOf(bp.copy(pack = GarmentDomainPack.CODE))) }
        val ghost = bp.copy(modules = bp.modules + BlueprintModule("modul_hantu", true))
        assertTrue(assertFailsWith<IllegalStateException> { pack.copy(blueprints = listOf(ghost)) }.message!!.contains("modul_hantu"))
    }

    @Test
    fun `decode menolak blueprints rusak dengan path - bukan array, bukan objek, field wajib hilang`() = runTest {
        val (pack, _) = klinik()
        val root = DomainPackCodec.encode(pack)
        fun decodeWith(value: JsonValue) = DomainPackCodec.decode(JsonValue.Obj(root.entries + ("blueprints" to value)))

        assertEquals("$.blueprints", assertFailsWith<DomainPackDecodeException> { decodeWith(JsonValue.Str("x")) }.path)
        assertEquals("$.blueprints[0]", assertFailsWith<DomainPackDecodeException> { decodeWith(JsonValue.Arr(listOf(JsonValue.Str("x")))) }.path)
        val noName = (JsonParser.parseObject(DomainPackCodec.encodeToString(pack))["blueprints"] as JsonValue.Arr).items.first() as JsonValue.Obj
        val broken = JsonValue.Obj(noName.entries - "displayName")
        assertEquals("$.blueprints[0].displayName", assertFailsWith<DomainPackDecodeException> { decodeWith(JsonValue.Arr(listOf(broken))) }.path)
    }

    @Test
    fun `resolveBlueprint - milik pack dulu, lalu starter platform, tak dikenal null`() = runTest {
        val (pack, bp) = klinik()
        assertEquals(bp, resolveBlueprint(pack, bp.code))
        // Tenant ber-pack data yang memegang kode bawaan kolom (fob_full_package) tetap terbaca (K3).
        assertEquals(GarmentBlueprints.FOB_FULL_PACKAGE, resolveBlueprint(pack, BlueprintCode("fob_full_package")))
        assertNull(resolveBlueprint(pack, BlueprintCode("tidak_ada")))
        assertNull(resolveBlueprint(null, bp.code), "pack tak dikenal tidak mengarang blueprint")
        assertEquals(GarmentBlueprints.CMT_MAKLOON, resolveBlueprint(GarmentDomainPack.pack, GarmentBlueprints.CMT_MAKLOON.code))
    }

    @Test
    fun `draf garment yang menambah blueprint ke pack bawaan ditolak sebagai penulisan ulang`() = runTest {
        val extra = GarmentBlueprints.CMT_MAKLOON
        val rewritten = GarmentDomainPack.pack.copy(blueprints = listOf(extra))
        val issues = DiscoveryDraftValidator.validate(DiscoveryDraft(rewritten, GarmentBlueprints.FOB_FULL_PACKAGE))
        assertTrue(issues.any { it.path == "$.pack" })
        assertNotNull(GarmentBlueprints.find(extra.code))
    }
}
