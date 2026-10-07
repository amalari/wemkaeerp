package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.interview.InterviewFixtures.draftOf
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.klinikPack
import com.eventverse.app.domain.discovery.interview.InterviewFixtures.klinikSession
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.RoleHint
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.json.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B7 (PLAN induk §4.1, §4.2, §6.1): berdasar cerita + persona konsultan. */
class InterviewBasisTest {

    private val story = "Kami klinik dengan antrean. Perawat memeriksa pasien lalu kasir menagih."
    private val pack = klinikPack.copy(roleHints = listOf(
        RoleHint("perawat", "Perawat", ModuleId("klinik_poli")), RoleHint("kasir", "Kasir", ModuleId("klinik_kasir"))
    ))
    private val proposed = DeterministicInterviewGuesser.propose(pack, story)

    private fun paths(s: InterviewSession) = InterviewValidator.validate(s, pack).map { it.path }
    private fun ref(basis: Basis, quote: String? = null, answerId: String? = null) = BasisRef(basis, quote, answerId)

    @Test
    fun `tebakan deterministik membawa kutipan persis dari cerita dan lolos validator`() {
        assertEquals(InterviewSession.BASED_ON_STORY, proposed.version)
        val quotes = (proposed.divisions.map { it.basisRef } + proposed.roles.map { it.basisRef } + proposed.links.map { it.basisRef }).map { it?.quote }
        assertTrue(quotes.all { it != null && story.contains(it) }, quotes.toString())
        assertEquals("Perawat", proposed.roles.first().basisRef?.quote, "kutipan memakai huruf asli cerita")
        assertEquals(emptyList(), paths(proposed))
        assertEquals(emptyList(), InterviewValidator.validate(proposed.acceptAll(), pack))
    }

    @Test
    fun `modul tanpa dasar ditolak berpath pada sesi berdasar cerita`() {
        val noBasis = proposed.copy(links = proposed.links.map { it.copy(basisRef = null) } , roles = proposed.roles.take(1).map { it.copy(basisRef = null) } + proposed.roles.drop(1))
        val p = paths(noBasis)
        assertTrue("$.interview.links[0].basisRef" in p && "$.interview.links[1].basisRef" in p)
        assertTrue("$.interview.roles[0].basisRef" in p)
    }

    @Test
    fun `SARAN_BELUM_DIJAWAB ditolak, NARASI wajib substring, JAWABAN wajib answerId yang ada`() {
        fun withLink(r: BasisRef) = proposed.copy(links = listOf(proposed.links[0].copy(basisRef = r)) + proposed.links.drop(1))
        assertTrue("$.interview.links[0].basisRef" in paths(withLink(ref(Basis.SARAN_BELUM_DIJAWAB))))
        assertTrue("$.interview.links[0].basisRef.quote" in paths(withLink(ref(Basis.NARASI, quote = "tukang las"))))
        assertTrue("$.interview.links[0].basisRef.quote" in paths(withLink(ref(Basis.NARASI))))
        assertTrue("$.interview.links[0].basisRef.answerId" in paths(withLink(ref(Basis.JAWABAN, answerId = "g3_modul_t9"))))
        assertEquals(emptyList(), paths(withLink(ref(Basis.NARASI, quote = "antrean"))))
        val answered = proposed.copy(links = listOf(proposed.links[0].copy(basisRef = ref(Basis.JAWABAN, answerId = "g1_divisi_t1"))) + proposed.links.drop(1),
            answers = listOf(InterviewAnswer(1, InterviewStep.G1_DIVISI, "g1_divisi_t1", Confirmation.CONFIRMED)))
        assertEquals(emptyList(), paths(answered))
    }

    @Test
    fun `SARAN_DITERIMA mensyaratkan konfirmasi pengguna`() {
        val s = proposed.copy(
            links = listOf(proposed.links[0].copy(basisRef = ref(Basis.SARAN_DITERIMA, answerId = "g3_modul_t1"), confirmed = Confirmation.GUESSED)) + proposed.links.drop(1),
            answers = listOf(InterviewAnswer(1, InterviewStep.G3_MODUL, "g3_modul_t1", Confirmation.CONFIRMED))
        )
        assertTrue("$.interview.links[0].confirmed" in paths(s))
        assertEquals(emptyList(), paths(s.copy(links = listOf(s.links[0].copy(confirmed = Confirmation.CHANGED)) + s.links.drop(1))))
    }

    @Test
    fun `wawancara lama versi 1 tanpa basisRef tetap sah, tetapi basisRef yang ada tetap diperiksa`() {
        assertEquals(1, klinikSession.version)
        assertEquals(emptyList(), InterviewValidator.validate(klinikSession, klinikPack))
        val bad = klinikSession.copy(links = listOf(klinikSession.links[0].copy(basisRef = ref(Basis.SARAN_BELUM_DIJAWAB))) + klinikSession.links.drop(1))
        assertTrue("$.interview.links[0].basisRef" in InterviewValidator.validate(bad, klinikPack).map { it.path })
    }

    @Test
    fun `fixture bengkel non-garment juga berdasar cerita`() {
        val bengkel = InterviewFixtures.bengkelPack.copy(roleHints = listOf(RoleHint("mekanik", "Mekanik", ModuleId("bengkel_servis"))))
        val s = DeterministicInterviewGuesser.propose(bengkel, "Bengkel motor. Mekanik membongkar lalu memasang.")
        assertEquals(emptyList(), InterviewValidator.validate(s, bengkel))
        assertEquals("Mekanik", s.links.single().basisRef?.quote)
        val liar = s.copy(narrative = "cerita lain sama sekali")
        assertTrue("$.interview.links[0].basisRef.quote" in InterviewValidator.validate(liar, bengkel).map { it.path })
    }

    @Test
    fun `round-trip codec memuat basisRef profil spek versi dan cerita, dan wawancara lama tak berubah byte`() {
        val full = proposed.copy(
            profile = BusinessProfile("Klinik kecil", listOf("antrean rapi"), listOf("pasien menumpuk")),
            specs = listOf(RequirementSpec(RoleKey("antrean"), whoFills = "resepsionis", doneWhen = "pasien dipanggil", basisRef = ref(Basis.NARASI, "antrean")))
        )
        val draft = draftOf(pack, full)
        assertEquals(draft, DiscoveryDraftCodec.decode(DiscoveryDraftCodec.encodeToString(draft)))
        val oldJson = DiscoveryDraftCodec.encodeToString(draftOf(klinikPack, klinikSession))
        listOf("basisRef", "version", "narrative", "profile", "specs").forEach { assertTrue(!oldJson.contains("\"$it\""), it) }
        assertEquals(draftOf(klinikPack, klinikSession), DiscoveryDraftCodec.decode(oldJson))
    }

    @Test
    fun `kode basis asing, versi asing, dan tipe salah ditolak dengan path`() {
        val root = DiscoveryDraftCodec.encode(draftOf(pack, proposed))
        fun mutate(edit: (MutableMap<String, JsonValue>) -> Unit): String {
            val iv = (root.entries["interview"] as JsonValue.Obj).entries.toMutableMap().also(edit)
            return JsonValue.Obj(root.entries + ("interview" to JsonValue.Obj(iv))).encode()
        }
        fun firstLinkWith(key: String, v: JsonValue): (MutableMap<String, JsonValue>) -> Unit = { m ->
            val items = (m["links"] as JsonValue.Arr).items.toMutableList()
            items[0] = JsonValue.Obj((items[0] as JsonValue.Obj).entries + (key to v)); m["links"] = JsonValue.Arr(items)
        }
        val badBasis = JsonValue.Obj(mapOf("basis" to JsonValue.Str("karangan")))
        assertEquals("$.interview.links[0].basisRef.basis", assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(mutate(firstLinkWith("basisRef", badBasis))) }.path)
        assertEquals("$.interview.version", assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(mutate { it["version"] = JsonValue.Num("3") }) }.path)
        assertEquals("$.interview.profile", assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(mutate { it["profile"] = JsonValue.Str("x") }) }.path)
        assertEquals("$.interview.links[0].basisRef", assertFailsWith<DiscoveryDraftDecodeException> { DiscoveryDraftCodec.decode(mutate(firstLinkWith("basisRef", JsonValue.Str("x")))) }.path)
    }

    @Test
    fun `profil dan spek dibatasi, giliran konsultan dan terjemahan dihitung terpisah`() {
        val bad = proposed.copy(
            profile = BusinessProfile("", List(7) { "g$it" }),
            specs = listOf(RequirementSpec(RoleKey("a"), whoFills = " ", basisRef = ref(Basis.NARASI, "antrean")), RequirementSpec(RoleKey("a"), basisRef = ref(Basis.NARASI, "antrean")))
        )
        val p = paths(bad)
        assertTrue("$.interview.profile.summary" in p && "$.interview.profile.goals" in p)
        assertTrue("$.interview.specs[0].whoFills" in p && "$.interview.specs[1].areaKey" in p)
        val fAnswers = List(3) { InterviewAnswer(it + 1, InterviewStep.F0_BISNIS, "f$it", Confirmation.CONFIRMED) }
        val gAnswers = List(8) { InterviewAnswer(it + 4, InterviewStep.G1_DIVISI, "g$it", Confirmation.CONFIRMED) }
        assertEquals(emptyList(), paths(proposed.copy(answers = fAnswers + gAnswers)))
        assertTrue("$.interview.answers" in paths(proposed.copy(answers = fAnswers + gAnswers + InterviewAnswer(12, InterviewStep.G2_PERAN, "g9", Confirmation.CONFIRMED))))
    }

    @Test
    fun `fase konsultan F0 sampai F2 ditanyakan berurutan lalu masuk G1, dengan lewati yang tak perlu`() {
        val d = { s: InterviewSession -> draftOf(pack, s) }
        var s = proposed.copy(step = InterviewStep.F0_BISNIS)
        assertEquals(InterviewStep.F0_BISNIS, s.nextQuestion(d(s))?.step)
        s = s.answer(d(s), "f0_bisnis_t1", Confirmation.CONFIRMED, text = "Klinik umum, 40 pasien per hari").getOrThrow()
        assertEquals("Klinik umum, 40 pasien per hari", s.profile?.summary)
        assertEquals(InterviewStep.F1_TUJUAN, s.nextQuestion(d(s))?.step)
        s = s.answer(d(s), "f1_tujuan_t2", Confirmation.CONFIRMED, text = "antrean rapi",
            revised = s.copy(profile = s.profile?.copy(painPoints = listOf("pasien menumpuk")))).getOrThrow()
        assertEquals(listOf("antrean rapi"), s.profile?.goals?.takeLast(1))
        assertEquals(InterviewStep.F2_SPEK, s.nextQuestion(d(s))?.step)
        s = s.answer(d(s), "f2_spek_t3", Confirmation.CONFIRMED, revised = s.copy(specs = listOf(RequirementSpec(RoleKey("antrean"), whoFills = "resepsionis")))).getOrThrow()
        assertEquals("g1_divisi_t4", s.nextQuestion(d(s))?.id)
        assertEquals(JAWABAN_FOR_NEW, s.specs.single().basisRef, "butir baru dari jawaban diberi dasar JAWABAN")
        // Tanpa titik sakit, F2 dilewati; profil sudah terisi, F0/F1 dilewati.
        val lean = proposed.copy(step = InterviewStep.F2_SPEK, profile = BusinessProfile("x", listOf("g")))
        assertEquals(InterviewStep.G1_DIVISI, lean.nextQuestion(d(lean))?.step)
        assertNull(proposed.copy(step = InterviewStep.DONE).nextQuestion(d(proposed)))
    }

    private val JAWABAN_FOR_NEW = BasisRef(Basis.JAWABAN, answerId = "f2_spek_t3")
}
