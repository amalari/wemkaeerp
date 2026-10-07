package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.bengkelPack
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.bengkelSession
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.klinikPack
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.klinikSession
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterviewSessionCodecTest {

    private fun roundTrip(d: DiscoveryDraft) = DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(d))

    @Test
    fun `round-trip wawancara klinik dan bengkel mengembalikan draf yang sama`() {
        val klinik = draftOf(klinikPack, klinikSession)
        val bengkel = draftOf(bengkelPack, bengkelSession)
        assertEquals(klinik, roundTrip(klinik))
        assertEquals(bengkel, roundTrip(bengkel))
        assertEquals(DiscoveryDraftCodec.encodeToString(klinik), DiscoveryDraftCodec.encodeToString(roundTrip(klinik)))
    }

    @Test
    fun `draf tanpa wawancara tidak menulis kunci interview dan terbaca dengan null`() {
        val garment = DiscoveryDraft(GarmentDomainPack.pack, GarmentBlueprints.FOB_FULL_PACKAGE)
        val json = DiscoveryDraftCodec.encodeToString(garment)
        assertFalse(json.contains("\"interview\""))
        val back = DiscoveryDraftCodec.decode(json)
        assertNull(back.interview)
        assertEquals(json, DiscoveryDraftCodec.encodeToString(back))
    }

    @Test
    fun `menambah wawancara tidak mengubah satu byte pun dari bagian lainnya`() {
        val without = draftOf(klinikPack, null)
        val withInterview = draftOf(klinikPack, klinikSession)
        val stripped = (DiscoveryDraftCodec.encode(withInterview).let { JsonValue.Obj(it.entries - "interview") }).encode()
        assertEquals(DiscoveryDraftCodec.encodeToString(without), stripped)
    }

    private fun mutate(edit: (MutableMap<String, JsonValue>) -> Unit): String {
        val root = DiscoveryDraftCodec.encode(draftOf(klinikPack, klinikSession))
        val interview = (root.entries["interview"] as JsonValue.Obj).entries.toMutableMap().also(edit)
        return JsonValue.Obj(root.entries + ("interview" to JsonValue.Obj(interview))).encode()
    }

    private fun linkWith(key: String, value: JsonValue): (MutableMap<String, JsonValue>) -> Unit = { m ->
        val links = (m["links"] as JsonValue.Arr).items.toMutableList()
        links[0] = JsonValue.Obj((links[0] as JsonValue.Obj).entries + (key to value))
        m["links"] = JsonValue.Arr(links)
    }

    @Test
    fun `kode asal modul tak dikenal ditolak dengan path, bukan fallback`() {
        val ex = assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(mutate(linkWith("origin", JsonValue.Str("REUSE_PLATFORM")))) }
        assertEquals("$.interview.links[0].origin", ex.path)
    }

    @Test
    fun `langkah tak dikenal dan array wajib hilang ditolak`() {
        val step = assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(mutate { it["step"] = JsonValue.Str("g9") }) }
        assertEquals("$.interview.step", step.path)
        val missing = assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(mutate { it.remove("handoffs") }) }
        assertEquals("$.interview.handoffs", missing.path)
    }

    @Test
    fun `kunci slug rusak, tipe salah, dan interview bukan objek ditolak`() {
        val slug = assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(mutate(linkWith("roleKey", JsonValue.Str("Peran Rusak")))) }
        assertEquals("$.interview.links[0].roleKey", slug.path)
        val type = assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(mutate(linkWith("features", JsonValue.Str("bukan array")))) }
        assertEquals("$.interview.links[0].features", type.path)
        val root = DiscoveryDraftCodec.encode(draftOf(klinikPack, null))
        val notObj = JsonValue.Obj(root.entries + ("interview" to JsonValue.Str("x"))).encode()
        assertEquals("$.interview", assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(notObj) }.path)
    }

    @Test
    fun `confidence di luar rentang ditolak dengan path tautannya`() {
        val ex = assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(mutate(linkWith("confidence", JsonValue.Num("150")))) }
        assertTrue(ex.path.startsWith("$.interview.links[0]"), ex.path)
    }
}
