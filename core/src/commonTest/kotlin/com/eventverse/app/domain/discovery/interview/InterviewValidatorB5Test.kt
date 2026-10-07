package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.interview.InterviewFixtures.klinikPack
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.klinikSession
import com.eventverse.app.domain.discovery.proposal.VerticalPurity
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentReservedTerms
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.shared.pack.DomainPackCodec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InterviewValidatorB5Test {

    private fun paths(s: InterviewSession, pack: com.eventverse.app.domain.pack.DomainPack = klinikPack) =
        InterviewValidator.validate(s, pack).map { it.path }

    @AfterTest fun cleanup() = DomainPackRegistry.unregister(com.eventverse.app.domain.pack.DomainPackCode("katering"))

    @Test
    fun `kosakata cadangan garment adalah data pack dan paritas dengan VerticalPurity lama`() {
        assertEquals(GarmentReservedTerms.terms, GarmentDomainPack.pack.reservedTerms)
        listOf("Penjahit senior", "rajut", "polimer", "kain tenun", "sablon", "PO masuk", "qc akhir", "pesanan").forEach {
            assertEquals(VerticalPurity.leak(it), VerticalPurity.leak(it, GarmentDomainPack.pack.reservedTerms), it)
        }
    }

    @Test
    fun `pack lain menjaga kosakatanya sendiri dan kosakata garment tidak lagi hardcode`() {
        val katering = InterviewFixtures.pack("katering", "Katering", InterviewFixtures.operational("katering_dapur", "Dapur"))
            .copy(reservedTerms = listOf("koki", "resep"))
        DomainPackRegistry.register(katering)
        // Peran "Koki" bocor ke klinik: ditolak karena kosakata cadangan pack katering (data), bukan daftar kode.
        val leak = klinikSession.copy(roles = listOf(klinikSession.roles[0].copy(label = "Koki")))
        assertTrue("$.interview.roles[0].label" in paths(leak))
        // Katering sendiri boleh memakainya; garment tetap menjaga miliknya untuk pack lain.
        assertEquals(emptyList(), InterviewValidator.validate(
            InterviewSession(InterviewStep.G2_PERAN, divisions = listOf(DivisionDraft(DivisionCode("dapur"), "Dapur", ItemSource.GUESS)),
                roles = listOf(RoleDraft(RoleKey("koki"), "Koki", DivisionCode("dapur"), ItemSource.GUESS))), katering))
        assertTrue("$.interview.roles[0].label" in paths(klinikSession.copy(roles = listOf(klinikSession.roles[0].copy(label = "Penjahit")))))
    }

    @Test
    fun `pack tanpa kosakata cadangan lain tidak menolak apa pun dan kamus kosong tak mematikan penjagaan garment`() {
        assertNull(VerticalPurity.leak("penjahit", emptyList()))
        assertTrue("$.interview.divisions[0].name" in paths(klinikSession.copy(divisions = listOf(klinikSession.divisions[0].copy(name = "Bagian Jahit")) + klinikSession.divisions.drop(1))))
    }

    @Test
    fun `kosakata cadangan round-trip codec, ditolak bila rusak, dan tak ditulis bila kosong`() {
        val pack = klinikPack.copy(reservedTerms = listOf("pasien"))
        assertEquals(pack, DomainPackCodec.decode(DomainPackCodec.encodeToString(pack)))
        assertEquals(false, DomainPackCodec.encodeToString(klinikPack).contains("reservedTerms"))
        assertFailsWith<IllegalArgumentException> { klinikPack.copy(reservedTerms = listOf("Pasien")) }
        assertFailsWith<IllegalStateException> { klinikPack.copy(reservedTerms = listOf("a", "a")) }
        assertEquals(GarmentDomainPack.pack, DomainPackCodec.decode(DomainPackCodec.encodeToString(GarmentDomainPack.pack)))
    }

    @Test
    fun `port sambungan harus cocok dengan keluaran modul asal atau masukan modul tujuan`() {
        val wrong = klinikSession.copy(handoffs = listOf(ModuleHandoff(ModuleId("klinik_pendaftaran"), ModuleId("klinik_poli"), PortType("Catatan"), Confirmation.CONFIRMED)))
        // Catatan = keluaran pendaftaran → sah
        assertEquals(emptyList(), InterviewValidator.validate(wrong, klinikPack))
        val pack = klinikPack.copy(
            portTypes = klinikPack.portTypes + PortType("Lain"), wiredPortTypes = klinikPack.wiredPortTypes + PortType("Lain")
        )
        val bad = klinikSession.copy(handoffs = listOf(ModuleHandoff(ModuleId("klinik_pendaftaran"), ModuleId("klinik_poli"), PortType("Lain"), Confirmation.CONFIRMED)))
        val issue = InterviewValidator.validate(bad, pack).single { it.path == "$.interview.handoffs[0].portType" }
        assertTrue(issue.message.contains("Catatan") && issue.message.contains("Permintaan"), issue.message)
    }

    @Test
    fun `sambungan ke modul tata kelola tanpa slot ditolak`() {
        val s = klinikSession.copy(handoffs = listOf(ModuleHandoff(ModuleId("klinik_poli"), ModuleId("org_chart"), PortType("Catatan"), Confirmation.CONFIRMED)))
        val issue = InterviewValidator.validate(s, klinikPack).single { it.path == "$.interview.handoffs[0]" }
        assertTrue(issue.message.contains("org_chart"))
    }

    @Test
    fun `wawancara selesai dengan divisi tanpa peran atau peran tanpa modul ditolak`() {
        val noRole = klinikSession.copy(divisions = klinikSession.divisions + DivisionDraft(DivisionCode("farmasi"), "Farmasi", ItemSource.ANSWER))
        assertTrue("$.interview.divisions[3]" in paths(noRole))
        val noLink = klinikSession.copy(roles = klinikSession.roles + RoleDraft(RoleKey("satpam"), "Satpam", DivisionCode("kasir"), ItemSource.ANSWER))
        assertTrue("$.interview.roles[3]" in paths(noLink))
        // Belum selesai: yatim wajar.
        assertEquals(emptyList(), InterviewValidator.validate(noRole.copy(step = InterviewStep.G2_PERAN, links = emptyList(), handoffs = emptyList()), klinikPack))
    }
}
