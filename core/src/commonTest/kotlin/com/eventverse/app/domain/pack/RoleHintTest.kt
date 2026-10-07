package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.interview.InterviewFixtures.bengkelPack
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.klinikPack
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.pack.DomainPackDecodeException
import com.eventverse.app.shared.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RoleHintTest {

    private val klinikDenganKamus = klinikPack.copy(roleHints = listOf(
        RoleHint("perawat", "Perawat", ModuleId("klinik_poli")),
        RoleHint("kasir", "Kasir", ModuleId("klinik_kasir")),
        RoleHint("perawat gigi", "Perawat Gigi", ModuleId("klinik_pendaftaran"))
    ))

    @Test
    fun `pack klinik dengan kamus menebak modul dari peran, frasa terpanjang menang`() {
        assertEquals(ModuleId("klinik_poli"), klinikDenganKamus.guessRoleHint("Perawat senior")?.moduleId)
        assertEquals(ModuleId("klinik_pendaftaran"), klinikDenganKamus.guessRoleHint("perawat gigi")?.moduleId)
        assertEquals(ModuleId("klinik_kasir"), klinikDenganKamus.guessRoleHint("Kasir")?.moduleId)
    }

    @Test
    fun `pencocokan per kata utuh dan peran asing tidak ditebak`() {
        assertNull(klinikDenganKamus.guessRoleHint("perawatan"))
        assertNull(klinikDenganKamus.guessRoleHint("satpam"))
    }

    @Test
    fun `pack tanpa kamus tidak menebak apa pun, bukan jatuh ke kamus garment`() {
        assertNull(bengkelPack.guessRoleHint("operator jahit"))
        assertNull(klinikPack.guessRoleHint("penjahit"))
        assertNull(klinikPack.guessRoleHint("kasir"))
    }

    @Test
    fun `kamus garment mencakup setiap modul operasional dan hanya menunjuk modul pack`() {
        val pack = GarmentDomainPack.pack
        val hinted = pack.roleHints.map { it.moduleId }.toSet()
        val operational = pack.modules.filter { it.slot != null }.map { it.id }
        assertEquals(emptyList(), operational.filter { it !in hinted }, "Modul operasional garment tanpa kamus peran")
        assertEquals(ModuleId("operator_exec"), pack.guessRoleHint("Operator Rajut")?.moduleId)
    }

    @Test
    fun `kamus kembar atau menunjuk modul asing ditolak`() {
        val dup = RoleHint("kasir", "Kasir", ModuleId("klinik_kasir"))
        assertFailsWith<IllegalStateException> { klinikPack.copy(roleHints = listOf(dup, dup)) }
        assertFailsWith<IllegalArgumentException> { klinikPack.copy(roleHints = listOf(RoleHint("x", "X", ModuleId("klinik_hantu")))) }
        assertFailsWith<IllegalArgumentException> { RoleHint("Kasir Besar", "K", ModuleId("klinik_kasir")) }
    }

    @Test
    fun `round-trip kamus dan pack tanpa kamus tidak menulis kunci roleHints`() {
        assertEquals(klinikDenganKamus, DomainPackCodec.decode(DomainPackCodec.encodeToString(klinikDenganKamus)))
        val plain = DomainPackCodec.encodeToString(klinikPack)
        assertEquals(false, plain.contains("roleHints"))
        assertEquals(klinikPack, DomainPackCodec.decode(plain))
        val garment = GarmentDomainPack.pack
        assertEquals(garment, DomainPackCodec.decode(DomainPackCodec.encodeToString(garment)))
    }

    @Test
    fun `kamus rusak di dokumen ditolak berpath`() {
        val root = DomainPackCodec.encode(klinikDenganKamus)
        val bad = JsonValue.Obj(root.entries + ("roleHints" to JsonValue.Arr(listOf(JsonValue.Obj(mapOf("word" to JsonValue.Str("a")))))))
        assertEquals("$.roleHints[0].label", assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(bad) }.path)
        val notArr = JsonValue.Obj(root.entries + ("roleHints" to JsonValue.Str("x")))
        assertEquals("$.roleHints", assertFailsWith<DomainPackDecodeException> { DomainPackCodec.decode(notArr) }.path)
    }
}
