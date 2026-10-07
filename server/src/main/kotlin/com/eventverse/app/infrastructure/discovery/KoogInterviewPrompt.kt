package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.BasisRef
import com.eventverse.app.domain.discovery.interview.BusinessProfile
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.RequirementSpec
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewAnswer
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleHandoff
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser

/**
 * Prompt agent wawancara Koog (plan IV-C1 + C6).
 *
 * Tiga keputusan yang disengaja (mengikuti pola `KoogDiscoveryPrompt`):
 *
 * 1. **Contoh dokumen dirakit dari kode, bukan ditempel sebagai teks.** [exampleSession] dibangun
 *    sebagai `InterviewSession` sungguhan lalu di-encode lewat `DiscoveryDraftCodec` (parser produksi)
 *    via [KoogInterviewBridge] — dan dites wajib lolos `InterviewValidator` penuh (`KoogInterviewPromptTest`).
 *    Contoh memakai pack `contoh` supaya tidak bocor jawaban kasus mana pun.
 * 2. **Persona konsultan digitalisasi usaha** (induk §4.2, C6): alur F0 bisnis → F1 tujuan/titik sakit →
 *    F2 spesifikasi per area, lalu baru diterjemahkan menjadi tebakan G1–G5. Pengetahuan modul lazim hanya
 *    boleh muncul sebagai **pertanyaan**; pengguna bingung diberi 2–3 pilihan berdasar narasi; bahasa awam;
 *    selalu bisa lewati.
 * 3. **Te bak dulu, tanya untuk konfirmasi**: jangan menanyakan yang bisa disimpulkan dari narasi, dan
 *    tidak ada tebakan di luar katalog (`interview_catalog`) — validator yang polisi, prompt hanya guru.
 */
internal object KoogInterviewPrompt {

    private const val PREVIOUS_ANSWER_CHAR_LIMIT = 6_000

    /** Galat koreksi dipotong seperti versi SP: 12 teratas, tiap pesan 240 karakter. */
    internal const val MAX_FEEDBACK_ISSUES = 12
    private const val FEEDBACK_MESSAGE_CHAR_LIMIT = 240

    val system: String = """
        Kamu konsultan digitalisasi usaha yang sedang mewawancarai pemilik usaha untuk merancang sistemnya.
        Bicaralah dengan bahasa awam yang hangat — hindari istilah teknis ERP.

        ALUR WAWANCARA (pegang urutannya):
        - F0 Bisnis: usahanya apa, produknya, pelanggannya, skalanya.
        - F1 Tujuan: sistem seperti apa yang diinginkan, apa yang paling merepotkan sekarang.
        - F2 Spesifikasi per area yang muncul: siapa yang mengisi, apa yang dicatat, siapa yang perlu melihat, kapan dianggap selesai.
        - Terjemahan: jawaban F0-F2 diterjemahkan menjadi tebakan G1 divisi, G2 peran, G3 modul & fitur, G4 sambungan, G5 ringkasan.

        CARA KERJA:
        - Tebak dulu dari cerita, tanya hanya untuk konfirmasi. Jangan menanyakan hal yang sudah jelas dari narasi.
        - Pengguna bingung? Tawarkan 2-3 pilihan yang masuk akal BERDASAR ceritanya, jangan menebak liar.
        - Pengetahuan tentang modul yang lazim hanya boleh keluar sebagai pertanyaan ("apakah ada proses kasir?"),
          tidak pernah langsung mengisi draf tanpa dasar cerita.
        - Setiap tebakan harus bisa ditelusuri ke kalimat narasi atau jawaban pengguna (dasar cerita).
        - Selalu sediakan jalan keluar: pengguna boleh lewati atau "terima semua tebakan".
        - Satu giliran = satu kelompok keputusan; total giliran terjemahan maksimal 8.

        ATURAN KERAS (validator menolak kalau dilanggar):
        - Divisi, peran, dan modul hanya dari cerita atau dari katalog (alat interview_catalog). Jangan mengarang di luar katalog.
        - Tautan hanya menunjuk modul di katalog. Asal jujur: reuse_platform/reuse_pack/extend hanya untuk modul bertanda
          shipped=true, extend wajib menyebut fitur tambahan; modul kustom pakai new.
        - BERDASAR CERITA: setiap divisions/roles/links/handoffs/specs WAJIB membawa basisRef -
          {"basis":"narasi","quote":"kutipan PERSIS dari cerita"} atau {"basis":"jawaban","answerId":"id giliran"} /
          {"basis":"saran_diterima","answerId":"..."}. Tebakan tanpa dasar ditolak validator; profile tidak perlu basisRef.
        - Tidak perlu menulis source atau answers — server yang membubuhkannya. confirmed dan confidence pada tebakan baru juga dibubuhkan server, jadi tidak masalah kalau ditulis atau tidak.
        - Tidak ada yang perlu diubah pada langkah ini? Balas {"interview":{"useCurrent":true}}.

        BALASAN: satu objek JSON berbentuk {"interview":{...}} untuk langkah yang diminta — bentuknya persis
        dokumen dari alat interview_state. Tidak boleh ada teks di luar JSON.
    """.trimIndent()

    /** Instruksi per langkah — satu giliran = satu kelompok keputusan (plan induk §1). */
    internal fun stepInstruction(step: InterviewStep): String = when (step) {
        InterviewStep.F0_BISNIS ->
            "Fase konsultan F0 (bisnis): baca narasinya, lalu isi profile saja - summary ringkas usaha pengguna " +
                "(apa dijual/dikerjakan, siapa pelanggannya, sebesar apa skalanya). Divisi/peran/tautan belum; balas dokumen dengan profile terisi."
        InterviewStep.F1_TUJUAN ->
            "Fase konsultan F1 (tujuan & titik sakit): lengkapi profile.goals (maks 6) dan profile.painPoints dari narasi, " +
                "pertahankan summary yang sudah ada. Belum saatnya menebak divisi/peran/tautan."
        InterviewStep.F2_SPEK ->
            "Fase konsultan F2 (spesifikasi per area): untuk area yang merepotkan, isi specs - areaKey = roleKey peran " +
                "terkait, lalu whoFills/whatRecorded/whoSees/doneWhen sesuai cerita (yang tak disebut biarkan kosong) " +
                "dengan basisRef NARASI berikut kutipannya. Bidang kosong tidak wajib diisi."
        InterviewStep.G1_DIVISI ->
            "Tebak G1 (divisi): kelompok kerja yang terdengar dari cerita (\"potong, jahit, QC\" berarti divisi Potong, Jahit, QC). Isi divisions saja."
        InterviewStep.G2_PERAN ->
            "Tebak G2 (peran): jabatan per divisi dari kata kerja & pelaku (\"operator rajut\", \"admin gudang\"). Isi roles saja; divisi yang dirujuk wajib sudah ada."
        InterviewStep.G3_MODUL ->
            "Tebak G3 (modul & fitur): tautkan tiap peran ke modul di katalog + asal yang jujur + fitur bila extend. Isi links saja."
        InterviewStep.G4_SAMBUNGAN ->
            "Tebak G4 (sambungan): serah-terima antar modul mengikuti urutan cerita (\"kain datang, lalu dipotong\"), pakai port wiredPorts di katalog. Isi handoffs saja."
        InterviewStep.G5_RINGKASAN, InterviewStep.DONE ->
            "G5: tidak ada tebakan lagi. Balas {\"interview\":{\"useCurrent\":true}}."
    }

    /**
     * Rencana alur penuh (satu panggilan): seluruh G1–G4 dari narasi. Alur dan modul ditentukan DULU; pertanyaan
     * per modul menyusul dan hanya untuk yang belum jelas dari cerita.
     */
    internal const val PLAN_INSTRUCTION: String =
        "RENCANA ALUR PENUH: dari narasi, susun SEKALIGUS divisions, roles, links, dan handoffs - seluruh alur kerja " +
            "dan modul yang dipakai, urut sesuai cerita. Setiap butir wajib punya basisRef dengan kutipan PERSIS dari narasi. " +
            "Pakai hanya modul di katalog (alat interview_catalog); yang tak disebut cerita jangan diisi. Peran wajib menunjuk " +
            "divisi yang kamu buat, tautan wajib menunjuk peran yang kamu buat."

    /**
     * Tambahan bila perencana BOLEH bertanya (belum pernah): hal pokok yang tak bisa disimpulkan dari cerita
     * ditanyakan dulu, bukan ditebak. Hanya satu putaran tanya; setelah itu rencana wajib disusun.
     */
    internal const val PLAN_MAY_ASK: String =
        " JIKA hal pokok tak bisa disimpulkan dari narasi (mis. jenis usaha atau alur utamanya tidak jelas), JANGAN menebak: " +
            "balas HANYA pertanyaan, maksimal 3, singkat, bahasa awam, tanpa divisions/roles/links, dengan bentuk persis: " +
            "{\"interview\":{\"step\":\"g1_divisi\",\"divisions\":[],\"roles\":[],\"links\":[],\"handoffs\":[],\"answers\":[]," +
            "\"clarifications\":[{\"id\":\"c1\",\"question\":\"...\"}]}}. Bertanya hanya bila sungguh perlu; bila narasi cukup, langsung susun rencana."

    /** Setelah pertanyaan dijawab (jawaban sudah ada di narasi): tidak boleh bertanya lagi, rencana wajib disusun. */
    internal const val PLAN_MUST_ANSWER: String = " Pertanyaan sudah dijawab dan jawabannya ada di narasi: jangan bertanya lagi, susun rencananya."

    /**
     * Pesan pengguna satu giliran. [feedback] kosong = giliran pertama; kalau ada, jawaban sebelumnya
     * dikirim ulang bersama galat berpath supaya model mengoreksi **bagian yang salah** saja.
     */
    fun userMessage(
        step: InterviewStep,
        draft: DiscoveryDraft,
        narrative: String,
        feedback: List<DiscoveryValidationIssue>,
        previousAnswer: String?,
        round: Int,
        instruction: String = stepInstruction(step)
    ): String = buildString {
        appendLine("Narasi pemilik usaha:")
        appendLine("\"\"\"")
        appendLine(narrative.trim())
        appendLine("\"\"\"")
        appendLine()
        appendLine("Keadaan wawancara sekarang (sama dengan alat interview_state):")
        appendLine(KoogInterviewBridge.interviewStateJson(draft))
        appendLine()
        appendLine(instruction)
        appendLine("Daftar modul & aturan asal lihat alat interview_catalog.")
        appendLine()
        appendLine("Contoh dokumen interview yang sah (pack 'contoh' — jangan dipakai apa adanya):")
        appendLine(exampleInterviewJson())
        if (feedback.isEmpty()) {
            appendLine()
            appendLine("Susun tebakan untuk langkah di atas dari narasi, lalu balas JSON-nya.")
            return@buildString
        }
        appendLine()
        appendLine("Putaran koreksi ke-$round. Usulan sebelumnya ditolak validator:")
        feedback.take(MAX_FEEDBACK_ISSUES).forEach {
            appendLine("- ${it.path}: ${it.message.take(FEEDBACK_MESSAGE_CHAR_LIMIT)}")
        }
        val hidden = feedback.size - MAX_FEEDBACK_ISSUES
        if (hidden > 0) appendLine("(+$hidden galat lain tidak dicantumkan; perbaiki dulu yang tercantum)")
        previousAnswer?.let {
            appendLine()
            appendLine("Jawaban sebelumnya (dipotong bila terlalu panjang):")
            appendLine(it.take(PREVIOUS_ANSWER_CHAR_LIMIT))
        }
        appendLine()
        appendLine("Perbaiki HANYA bagian yang dilaporkan, pertahankan sisanya, lalu balas JSON utuh.")
    }

    // --- Contoh dokumen: dirakit dari kode, bukan teks tempel ----------------------------------

    /** Pack `contoh`: dua modul operasional kustom + modul bersama `org_chart` dari pack bawaan. */
    internal fun examplePack(): DomainPack {
        val orgChart = requireNotNull(GarmentDomainPack.pack.module(GarmentModules.ORG_CHART)) {
            "org_chart hilang dari pack bawaan"
        }
        val govSection = requireNotNull(GarmentDomainPack.pack.sections.firstOrNull { it.code == orgChart.section })
        val utama = ModuleSectionCode("UTAMA")
        fun operational(id: String, name: String) = ModuleDefinition(
            id = ModuleId(id), displayName = name, description = name, section = utama, kind = ModuleKind.OPERATIONAL,
            iconKey = "clipboard", scopeCapability = ScopeCapability.HIERARCHICAL,
            supportedScopes = setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA), slot = SlotCode(id)
        )
        val pesanan = operational("contoh_pesanan", "Pesanan")
        val produksi = operational("contoh_produksi", "Produksi")
        return DomainPack(
            code = DomainPackCode("contoh"), displayName = "Contoh",
            phases = listOf(PhaseDefinition(PhaseCode("OPERASI"), 1, "1. Operasi", "Alur harian", 0xFF2563EB)),
            slots = listOf(
                SlotDefinition(SlotCode("contoh_pesanan"), "Pesanan", PhaseCode("OPERASI"), PortType("Permintaan"), PortType("Catatan")),
                SlotDefinition(SlotCode("contoh_produksi"), "Produksi", PhaseCode("OPERASI"), PortType("Permintaan"), PortType("Catatan"))
            ),
            portTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
            wiredPortTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
            sections = listOf(govSection, ModuleSection(utama, "Operasional", 2, 0xFF2563EB, 0xFFEFF6FF)),
            modules = listOf(orgChart, pesanan, produksi)
        )
    }

    /**
     * Sesi contoh pada G3: paling kaya (profil, divisi+peran+tautan+sambungan+spesifikasi, semuanya berdasar) —
     * mengajarkan bentuk dokumen berdasar-cerita, bukan jawaban. Wajib lolos validator penuh (dites).
     */
    internal fun exampleSession(): InterviewSession {
        val narrative = "Kami studio contoh: admin CS mencatat pesanan pelanggan tiap hari, lalu operator mengerjakannya di ruang produksi."
        val dasarPesanan = BasisRef(Basis.NARASI, quote = "admin CS mencatat pesanan pelanggan")
        val dasarProduksi = BasisRef(Basis.NARASI, quote = "operator mengerjakannya di ruang produksi")
        return InterviewSession(
            step = InterviewStep.G3_MODUL,
            divisions = listOf(
                DivisionDraft(DivisionCode("pesanan"), "Pesanan", ItemSource.GUESS, dasarPesanan),
                DivisionDraft(DivisionCode("produksi"), "Produksi", ItemSource.ANSWER, dasarProduksi)
            ),
            roles = listOf(
                RoleDraft(RoleKey("admin_cs"), "Admin CS", DivisionCode("pesanan"), ItemSource.GUESS, isHead = true, basisRef = dasarPesanan),
                RoleDraft(RoleKey("operator"), "Operator", DivisionCode("produksi"), ItemSource.GUESS, basisRef = dasarProduksi)
            ),
            links = listOf(
                RoleModuleLink(RoleKey("admin_cs"), ModuleId("contoh_pesanan"), ModuleOrigin.NEW, listOf("Catatan pesanan harian"), Confirmation.CONFIRMED, 80, dasarPesanan),
                RoleModuleLink(RoleKey("admin_cs"), ModuleId("org_chart"), ModuleOrigin.REUSE_PLATFORM, emptyList(), Confirmation.CONFIRMED, null, dasarPesanan),
                RoleModuleLink(RoleKey("operator"), ModuleId("contoh_produksi"), ModuleOrigin.NEW, emptyList(), Confirmation.CONFIRMED, 70, dasarProduksi)
            ),
            handoffs = listOf(
                ModuleHandoff(ModuleId("contoh_pesanan"), ModuleId("contoh_produksi"), PortType("Permintaan"), Confirmation.CONFIRMED, basisRef = dasarProduksi)
            ),
            answers = listOf(InterviewAnswer(1, InterviewStep.G3_MODUL, "g3_modul_t1", Confirmation.CONFIRMED)),
            version = InterviewSession.BASED_ON_STORY,
            narrative = narrative,
            profile = BusinessProfile(
                "Studio contoh yang mengerjakan pesanan pelanggan tiap hari.",
                goals = listOf("Pencatatan pesanan yang rapi")
            ),
            specs = listOf(
                RequirementSpec(
                    RoleKey("admin_cs"),
                    whoFills = "Admin CS",
                    whatRecorded = "Pesanan pelanggan harian",
                    whoSees = "Pemilik studio",
                    doneWhen = "Pesanan tercatat di sistem",
                    basisRef = dasarPesanan
                )
            )
        )
    }

    /** Dokumen interview contoh — dirakit dari kode lalu dipotong dari dokumen draf penuh (parser produksi). */
    internal fun exampleInterviewJson(): String {
        val draft = DiscoveryDraft(
            pack = examplePack(),
            blueprint = Blueprint(
                code = BlueprintCode("contoh_starter"), pack = DomainPackCode("contoh"), displayName = "Contoh",
                shortBadge = "c", description = "Contoh", targetClientProfile = "Contoh",
                modules = listOf(BlueprintModule("contoh_pesanan", true))
            ),
            interview = exampleSession()
        )
        val document = JsonParser.parseObject(DiscoveryDraftCodec.encodeToString(draft))
        val interview = requireNotNull(document.obj("interview"))
        return com.eventverse.app.shared.json.JsonValue.Obj(mapOf("interview" to interview)).encode()
    }

    /** Draf contoh utuh — dipakai test membuktikan contoh lolos validator penuh (AC C1). */
    internal fun exampleDraft(): DiscoveryDraft = DiscoveryDraft(
        pack = examplePack(),
        blueprint = Blueprint(
            code = BlueprintCode("contoh_starter"), pack = DomainPackCode("contoh"), displayName = "Contoh",
            shortBadge = "c", description = "Contoh", targetClientProfile = "Contoh",
            modules = listOf(BlueprintModule("contoh_pesanan", true))
        ),
        interview = exampleSession()
    )
}