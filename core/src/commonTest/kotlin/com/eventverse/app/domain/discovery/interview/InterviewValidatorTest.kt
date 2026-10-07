package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.bengkelPack
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.bengkelSession
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.klinikPack
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.klinikSession
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.rbac.ModuleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InterviewValidatorTest {

    private fun issues(s: InterviewSession, pack: com.eventverse.app.domain.pack.DomainPack = klinikPack): List<DiscoveryValidationIssue> =
        InterviewValidator.validate(s, pack)

    private fun pathsOf(s: InterviewSession, pack: com.eventverse.app.domain.pack.DomainPack = klinikPack) = issues(s, pack).map { it.path }

    @Test
    fun `wawancara klinik dan bengkel sah menghasilkan nol temuan`() {
        assertEquals(emptyList(), issues(klinikSession))
        assertEquals(emptyList(), issues(bengkelSession, bengkelPack))
    }

    @Test
    fun `validator draf memanggil validator wawancara dengan path lengkap`() {
        val bad = klinikSession.copy(links = klinikSession.links + RoleModuleLink(RoleKey("perawat"), ModuleId("klinik_hantu"), ModuleOrigin.NEW, confirmed = Confirmation.CONFIRMED))
        val found = DiscoveryDraftValidator.validate(draftOf(klinikPack, bad))
        assertTrue(found.any { it.path == "$.interview.links[4].moduleId" }, found.toString())
    }

    @Test
    fun `draf tanpa wawancara tidak menambah temuan`() {
        assertEquals(emptyList(), DiscoveryDraftValidator.validate(draftOf(klinikPack, null)))
    }

    @Test
    fun `kode divisi kembar ditolak di path kode kedua`() {
        val s = klinikSession.copy(divisions = klinikSession.divisions + DivisionDraft(DivisionCode("poli"), "Poli 2", ItemSource.ANSWER))
        assertTrue("$.interview.divisions[3].code" in pathsOf(s))
    }

    @Test
    fun `peran menunjuk divisi yang tidak ada ditolak`() {
        val s = klinikSession.copy(roles = klinikSession.roles + RoleDraft(RoleKey("apoteker"), "Apoteker", DivisionCode("farmasi"), ItemSource.GUESS))
        val i = issues(s).single { it.path == "$.interview.roles[3].divisionCode" }
        assertTrue(i.message.contains("farmasi"))
    }

    @Test
    fun `kunci peran kembar dan dua kepala satu divisi ditolak`() {
        val s = klinikSession.copy(roles = klinikSession.roles + RoleDraft(RoleKey("perawat"), "Perawat 2", DivisionCode("pendaftaran"), ItemSource.GUESS, isHead = true))
        val p = pathsOf(s)
        assertTrue("$.interview.roles[3].roleKey" in p)
        assertTrue("$.interview.roles[3].isHead" in p)
    }

    @Test
    fun `tautan ke peran atau modul tak dikenal ditolak berpath`() {
        val s = klinikSession.copy(links = listOf(RoleModuleLink(RoleKey("tukang"), ModuleId("klinik_hantu"), ModuleOrigin.NEW)))
        val p = pathsOf(s)
        assertTrue("$.interview.links[0].roleKey" in p)
        assertTrue("$.interview.links[0].moduleId" in p)
    }

    @Test
    fun `tautan peran ke modul yang sama dua kali ditolak`() {
        val dup = klinikSession.links.first()
        assertTrue("$.interview.links[4]" in pathsOf(klinikSession.copy(links = klinikSession.links + dup)))
    }

    @Test
    fun `NEW untuk modul yang sudah ada di pack bawaan ditolak`() {
        val s = klinikSession.copy(links = listOf(RoleModuleLink(RoleKey("resepsionis"), ModuleId("org_chart"), ModuleOrigin.NEW, confirmed = Confirmation.CONFIRMED)))
        assertTrue("$.interview.links[0].origin" in pathsOf(s))
    }

    @Test
    fun `REUSE_PLATFORM untuk modul operasional atau modul pack baru ditolak`() {
        val s = klinikSession.copy(links = listOf(RoleModuleLink(RoleKey("perawat"), ModuleId("klinik_poli"), ModuleOrigin.REUSE_PLATFORM, confirmed = Confirmation.CONFIRMED)))
        assertTrue("$.interview.links[0].origin" in pathsOf(s))
    }

    @Test
    fun `REUSE_PACK dan EXTEND pada modul bawaan sah, tetapi EXTEND tanpa fitur ditolak`() {
        val garment = GarmentDomainPack.pack
        val op = garment.modules.first { it.kind == ModuleKind.OPERATIONAL }.id
        fun session(origin: ModuleOrigin, features: List<String>) = InterviewSession(
            step = InterviewStep.G3_MODUL,
            divisions = listOf(DivisionDraft(DivisionCode("d"), "Divisi", ItemSource.GUESS)),
            roles = listOf(RoleDraft(RoleKey("r"), "Peran", DivisionCode("d"), ItemSource.GUESS)),
            links = listOf(RoleModuleLink(RoleKey("r"), op, origin, features))
        )
        assertEquals(emptyList(), issues(session(ModuleOrigin.REUSE_PACK, emptyList()), garment))
        assertEquals(emptyList(), issues(session(ModuleOrigin.EXTEND, listOf("Fitur tambahan")), garment))
        assertEquals(listOf("$.interview.links[0].origin"), pathsOf(session(ModuleOrigin.EXTEND, emptyList()), garment))
    }

    @Test
    fun `REUSE_PACK untuk modul yang tidak ada di pack bawaan ditolak`() {
        val s = klinikSession.copy(links = listOf(RoleModuleLink(RoleKey("perawat"), ModuleId("klinik_poli"), ModuleOrigin.REUSE_PACK, confirmed = Confirmation.CONFIRMED)))
        assertTrue("$.interview.links[0].origin" in pathsOf(s))
    }

    @Test
    fun `sambungan ke modul asing, port tak tersambung, dan ke diri sendiri ditolak`() {
        val s = klinikSession.copy(handoffs = listOf(
            ModuleHandoff(ModuleId("klinik_hantu"), ModuleId("klinik_poli"), PortType("Permintaan")),
            ModuleHandoff(ModuleId("klinik_poli"), ModuleId("klinik_poli"), PortType("PortAsing"))
        ))
        val p = pathsOf(s)
        assertTrue("$.interview.handoffs[0].from" in p)
        assertTrue("$.interview.handoffs[1].to" in p)
        assertTrue("$.interview.handoffs[1].portType" in p)
    }

    @Test
    fun `batas ukuran ditegakkan di path koleksinya`() {
        val many = (1..13).map { DivisionDraft(DivisionCode("d$it"), "D$it", ItemSource.GUESS) }
        assertTrue("$.interview.divisions" in pathsOf(InterviewSession(InterviewStep.G1_DIVISI, divisions = many)))
        val answers = (1..9).map { InterviewAnswer(it, InterviewStep.G1_DIVISI, "q", Confirmation.CONFIRMED) }
        assertTrue("$.interview.answers" in pathsOf(InterviewSession(InterviewStep.G1_DIVISI, answers = answers)))
    }

    @Test
    fun `giliran kembar ditolak`() {
        val a = InterviewAnswer(1, InterviewStep.G1_DIVISI, "q", Confirmation.CONFIRMED)
        assertTrue("$.interview.answers[1].turn" in pathsOf(InterviewSession(InterviewStep.G1_DIVISI, answers = listOf(a, a))))
    }

    @Test
    fun `wawancara selesai dengan tebakan belum dikonfirmasi ditolak`() {
        val s = klinikSession.copy(links = listOf(klinikSession.links[1].copy(confirmed = Confirmation.GUESSED)))
        assertTrue("$.interview.links[0].confirmed" in pathsOf(s))
        // sebelum selesai, GUESSED wajar
        assertEquals(emptyList(), issues(s.copy(step = InterviewStep.G3_MODUL, handoffs = emptyList())))
    }

    @Test
    fun `kosakata konveksi ditolak untuk pack non-garment tetapi tidak untuk garment`() {
        val leak = klinikSession.copy(roles = listOf(klinikSession.roles[0].copy(label = "Penjahit")))
        assertTrue("$.interview.roles[0].label" in pathsOf(leak))
        val garment = InterviewSession(
            step = InterviewStep.G2_PERAN,
            divisions = listOf(DivisionDraft(DivisionCode("jahit"), "Jahit", ItemSource.GUESS)),
            roles = listOf(RoleDraft(RoleKey("penjahit"), "Penjahit", DivisionCode("jahit"), ItemSource.GUESS))
        )
        assertEquals(emptyList(), issues(garment, GarmentDomainPack.pack))
    }

    @Test
    fun `kunci slug rusak ditolak saat konstruksi, tidak dinormalkan`() {
        assertFailsWith<IllegalArgumentException> { DivisionCode("Poli Umum") }
        assertFailsWith<IllegalArgumentException> { RoleKey("") }
        assertFailsWith<IllegalArgumentException> { RoleModuleLink(RoleKey("a"), ModuleId("m"), ModuleOrigin.NEW, confidence = 101) }
    }

    @Test
    fun `kode enum dikenal dua arah dan kode asing tidak jatuh ke bawaan`() {
        InterviewStep.entries.forEach { assertEquals(it, InterviewStep.fromCode(it.code)) }
        ModuleOrigin.entries.forEach { assertEquals(it, ModuleOrigin.fromCode(it.code)) }
        Confirmation.entries.forEach { assertEquals(it, Confirmation.fromCode(it.code)) }
        assertEquals(null, ModuleOrigin.fromCode("REUSE_PLATFORM"))
        assertEquals(null, InterviewStep.fromCode(""))
    }
}
