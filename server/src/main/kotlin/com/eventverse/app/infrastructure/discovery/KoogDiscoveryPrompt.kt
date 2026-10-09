package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ViewProposal
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
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec

/**
 * Prompt agent discovery Koog (plan §2 A8; diperluas SP-C1: `proposal` di dalam dokumen draf).
 *
 * Tiga keputusan yang disengaja:
 *
 * 1. **Contoh dokumen dibuat kode, bukan ditempel sebagai teks.** [exampleDraftJson] dirakit dari
 *    `DiscoveryDraft` sungguhan (termasuk `ScreenProposal`-nya) lalu di-encode `DiscoveryDraftCodec` —
 *    parser produksi. Jadi contoh di prompt tidak mungkin melenceng dari kontrak (dites: contoh itu
 *    sendiri wajib lolos validator penuh, termasuk aturan proposal).
 *    Konsekuensinya contoh **tidak bocor jawaban**: kode pack-nya `contoh`, bukan narasi yang diminta.
 * 2. **Jembatan pack bawaan** (`useShipped`) dibahas di prompt karena validator produksi menuntut pack
 *    bawaan platform dikembalikan **identik**; model tidak akan pernah bisa menulis ulang dokumen itu
 *    dari ingatan. Lihat `KoogDiscoveryAgent.applyShippedPackBridge`.
 * 3. **Sumber proposal tidak dipercaya ke model.** Aturan prompt menyuruh menyalin blok `source` dari
 *    contoh (yang membawa `agentRef` agent ini), dan server tetap membubuhkannya ulang setelah dekode
 *    (`KoogDiscoveryAgent.stampAgentProvenance`) — prompt hanyalah guru, validator dan server yang polisi.
 */
internal object KoogDiscoveryPrompt {

    /** Jumlah karakter jawaban sebelumnya yang diumpanbalikkan; sisanya dipotong agar prompt tidak membengkak. */
    private const val PREVIOUS_ANSWER_CHAR_LIMIT = 6_000

    /**
     * Galat koreksi yang dikirim ke model dipotong jumlahnya (dan panjang tiap pesannya) supaya putaran
     * koreksi tetap ringkas: 12 galat teratas jauh melampaui yang bisa diperbaiki satu putaran, dan
     * galat pertama biasanya menjebak galat-galat berikutnya (satu field hilang → puluhan rujukan rusak).
     */
    internal const val MAX_FEEDBACK_ISSUES = 12
    private const val FEEDBACK_MESSAGE_CHAR_LIMIT = 240

    /** `agentRef` contoh bila pemanggil tidak menyebutnya (test, pratinjau prompt). */
    internal const val EXAMPLE_AGENT_REF = "koog/model/draft-v2"

    private val WIDGET_CODES: String = WidgetKind.entries.joinToString(", ") { it.code }

    val system: String = """
        Kamu adalah analis discovery WeMade ERP. Tugasmu: mengubah narasi bisnis calon klien menjadi
        SATU dokumen JSON berisi draf domain pack + blueprint alur modul.

        Bentuk keluaran (WAJIB):
        {"pack": <DomainPack>, "blueprint": <Blueprint>, "screens": [<PrototypeScreen>]}

        Aturan keras — validator produksi menolak pelanggaran berikut:
        1. `pack.code` adalah slug huruf kecil (`klinik`, `bengkel`, `katering`), bukan nama panjang.
           Bila pesan pengguna memuat "Petunjuk industri", `pack.code` WAJIB persis slug petunjuk itu
           (mis. petunjuk "klinik" → `klinik`, bukan `klinik_gigi`); jangan menambah kata atau sufiks.
        2. Setiap modul dan slot BARU wajib berprefiks `<pack.code>_` (contoh: `klinik_antrean`). Ini
           mencegah draf merebut id modul platform.
        3. Jangan menulis ulang pack bawaan platform. Bila narasi bisnisnya sudah dicakup salah satu pack
           bawaan (lihat `platform_modules()`: nama, modul, dan istilah pack), kamu WAJIB memakai pack itu
           lewat jembatan `"pack": {"useShipped": "<kode pack bawaan>"}` — server menyalin dokumen
           aslinya. Membuat pack baru untuk bisnis yang sudah dicakup pack bawaan dianggap SALAH.
           Untuk pack bawaan, blueprint-nya JUGA dipilih dari starter bawaan, jangan dikarang:
           `"blueprint": {"useShipped": "<kode starter>"}` dengan kode dari
           `platform_modules().shippedPacks[].starterBlueprints` — pilih yang paling cocok dengan
           narasi (baca `description` dan `targetClientProfile`-nya). Pada kasus ini `screens` boleh kosong.
        4. Setiap `blueprint.modules[].moduleCode` wajib ada di `pack.modules[].id`. Modul non-aktif
           tetap dicantumkan dengan `"active": false` beserta parameter alasan bypass-nya.
        5. `screens[].widget` hanya boleh salah satu dari: $WIDGET_CODES.
        6. Semua field yang terlihat di contoh WAJIB ada. Jangan mengarang field baru.
        7. Balas HANYA objek JSON — tanpa penjelasan, tanpa pagar kode, tanpa teks pembuka.
        8. Hemat langkah: panggil `platform_modules()` dan `screen_catalog()` maksimal sekali
           masing-masing, dan `validate_draft` maksimal dua kali. Begitu draf bersih, langsung balas
           JSON-nya — agent dibatasi jumlah langkahnya.
        9. `pack.vocabulary` menyebut istilah yang benar-benar diucapkan pemilik usaha, mis.
           `{"WORKPLACE":"klinik","DOCUMENT":"Kunjungan"}` (kunci hanya boleh WORKPLACE atau DOCUMENT).
           Jangan pakai istilah konveksi ("pabrik", "SPK") kecuali vertikalnya memang konveksi.
           `pack.actions` = label tombol layar kerja, mis. `{"code":"ADD","label":"Tambah Kunjungan"}`.
        10. Setiap layar data (`KANBAN`, `TABLE`, `FORM`, `CHECKLIST`) wajib membawa `proposal` berisi isi
            layar: {"screenId","moduleId","title","widget","rationale","entity","view","seed","source"} —
            tanpa kunci lain. `DASHBOARD` dan `CUSTOM_SCREEN`: `"entity": null`.
        11. `entity` = jenis benda yang dikelola layar: ${KoogDiscoveryFieldTypeVocabulary.promptRule}.
            Field RELATION wajib `target` ("entityId" atau "moduleId:entityId"); modul target harus modul pack ini
            sendiri atau modul bersama yang ditawarkan platform (aturan R1), bukan modul tata kelola/fondasi.
            `statusField` = kunci field ENUM status kerja
            (2–8 pilihan), `transitions` = perpindahan status yang sah. Status yang berupa ALUR KERJA
            (Baru → Diproses → Selesai) wajib berurutan dengan satu kondisi awal dan satu akhir. Status
            yang bukan alur kerja — level stok (Tersedia/Menipis/Habis), status pembayaran, status
            aktif/nonaktif — KOSONGKAN `transitions` (artinya bebas berpindah), jangan menggambar
            siklus perpindahan.
            Maksimal 12 field per entity; `seed` maksimal 8 baris objek string — angka ditulis "5",
            tanggal "2026-03-01", BOOL "ya"/"tidak", dan field wajib terisi di setiap baris.
        12. `view` mengikuti widget (lihat contoh): TABLE {columns, inlineCreate, editableFields} dengan
            kolom = kunci field; KANBAN {card:[{field,style}], columnMeta, detailFormFields} dengan kolom
            papan = opsi statusField; FORM {fields, submitLabel} yang memuat semua field wajib;
            CHECKLIST {labelField, doneField BOOL}; DASHBOARD {tiles:[{label,value?,count?}]} maksimal 8
            ubin; PRINT {fields}; ${KoogDiscoverySkeletonVocabulary.promptRule}. Kunci `view` tidak boleh dikarang.
        13. `rationale` satu kalimat bahasa pemilik usaha (maksimal 200 karakter), pola "Dipilih karena …".
            Pilih widget dari watak kerja modul, bukan selera: antrean/alur kerja → KANBAN, daftar/ledger
            → TABLE, pencatatan satu-per-satu → FORM, langkah bercentang → CHECKLIST, ringkasan angka →
            DASHBOARD, dokumen yang dicetak → PRINT. Nama field dan status memakai kata dari narasi.
        14. `source` tiap layar ber-proposal: salin PERSIS blok "source" dari contoh dokumen di pesan
            pengguna (ia membawa agentRef agent ini). Jangan mengarang agentRef lain.

        Alat yang tersedia (pakai sebelum menjawab):
        - `platform_modules()` — daftar pack bawaan platform beserta modul, slot, dan seksinya. Pakai
          untuk (a) mengetahui kode pack bawaan, dan (b) meniru gaya penamaan modul platform.
        - `screen_catalog()` — kosakata tertutup untuk `proposal`: jenis tampilan beserta bentuk `view`
          dan kewajiban `entity`-nya, tipe field, gaya kartu kanban, batas ukuran, dan petunjuk
          peran kerja → jenis tampilan yang dipakai pack bawaan (petunjuk, bukan aturan).
        - `validate_draft(draft)` — memvalidasi dokumen yang baru kamu susun. Ia mengembalikan
          `{"valid":true}` atau daftar galat berpath (`${'$'}.pack.modules[2].id`,
          `${'$'}.screens[0].proposal.entity.fields[1].type`). Perbaiki dulu sebelum menjawab; jangan
          pernah menjawab dokumen yang masih berisi galat.

        15. Setiap kemampuan yang DISEBUT narasi (pesanan, antrean, jadwal, stok, tagihan/pembayaran,
            laporan, pendaftaran, dst.) wajib punya modul sendiri, dan `id` modulnya berakhiran kata
            kemampuan itu: `<pack.code>_pesanan`, `<pack.code>_laporan`, `<pack.code>_tagihan`.
            Jangan menggabungkan dua kemampuan ke satu modul dan jangan melewatkan satu pun.

        Pedoman isi (bukan aturan kaku): ambil 3–6 modul yang benar-benar disebut narasi, satu fase per
        tahap kerja yang jelas, dan seksi menu yang masuk akal bagi pemilik usaha. Nama modul memakai
        istilah narasi ("Antrean Pasien", "Servis Motor"), bukan istilah internal ("CRUD", "Tabel").
        Beri 1–3 `screens` untuk modul utama; sisanya boleh kosong. Dua layar yang mengelola benda sama
        memakai definisi `entity` yang sama persis.
    """.trimIndent()

    /**
     * Contoh kerangka: dokumen **sah** berkode pack `contoh`, sehingga model belajar bentuknya tanpa
     * menerima jawabannya. Sifat "sah" itu dikunci test (`KoogDiscoveryPromptTest`) — termasuk seluruh
     * aturan `proposal`-nya. [agentRef] disuntik ke blok `source` contoh supaya model menyalin sumber
     * yang benar; server tetap membubuhkannya ulang setelah dekode (lapis kedua).
     */
    fun exampleDraftJson(agentRef: String = EXAMPLE_AGENT_REF): String =
        DiscoveryDraftCodec.encodeToString(exampleDraft(agentRef))

    fun exampleDraft(agentRef: String = EXAMPLE_AGENT_REF): DiscoveryDraft {
        val section = ModuleSection(ModuleSectionCode("UTAMA"), "Operasional", 1, 0xFF2563EB, 0xFFEFF6FF)
        val phase = PhaseDefinition(PhaseCode("OPERASI"), 1, "1. Operasi", "Alur kerja harian", 0xFF2563EB)
        val permintaan = PortType("Permintaan")
        val catatan = PortType("Catatan")
        val slots = listOf(
            SlotDefinition(SlotCode("contoh_pesanan"), "Penerimaan Pesanan", PhaseCode("OPERASI"), permintaan, catatan),
            SlotDefinition(SlotCode("contoh_laporan"), "Laporan & Pemantauan", PhaseCode("OPERASI"), catatan, catatan)
        )
        val modules = listOf(
            ModuleDefinition(
                id = ModuleId("contoh_pesanan"), displayName = "Penerimaan Pesanan",
                description = "Mencatat permintaan masuk dari pelanggan.", section = section.code,
                kind = ModuleKind.OPERATIONAL, iconKey = "inbox", scopeCapability = ScopeCapability.HIERARCHICAL,
                supportedScopes = setOf(DataScope.OWN_DATA_ONLY, DataScope.SUBORDINATE_DATA, DataScope.ALL_TENANT_DATA),
                slot = slots[0].code
            ),
            ModuleDefinition(
                id = ModuleId("contoh_laporan"), displayName = "Laporan & Pemantauan",
                description = "Rekap pekerjaan dan pemantauan harian.", section = section.code,
                kind = ModuleKind.OPERATIONAL, iconKey = "chart", scopeCapability = ScopeCapability.GLOBAL_ONLY,
                supportedScopes = setOf(DataScope.ALL_TENANT_DATA), slot = slots[1].code
            )
        )
        val pack = DomainPack(
            code = DomainPackCode("contoh"),
            displayName = "Contoh Vertikal",
            phases = listOf(phase),
            slots = slots,
            portTypes = setOf(permintaan, catatan),
            wiredPortTypes = setOf(permintaan, catatan),
            sections = listOf(section),
            modules = modules
        )
        val blueprint = Blueprint(
            code = BlueprintCode("contoh_starter"),
            pack = pack.code,
            displayName = "Starter Contoh",
            shortBadge = "Discovery",
            description = "Draf awal dari narasi prospek.",
            targetClientProfile = "Pemilik usaha kecil yang mencatat pesanan harian.",
            modules = modules.map { BlueprintModule(it.id.value, active = true) }
        )
        // Entity yang sama dipakai dua layar — contoh untuk aturan "definisi entity identik antar-layar".
        val pesanan = EntityProposal(
            id = "pesanan",
            label = "Pesanan",
            fields = listOf(
                FieldProposal("nomor", "Nomor pesanan", FieldType.TEXT, required = true),
                FieldProposal("pelanggan", "Pelanggan", FieldType.TEXT, required = true),
                FieldProposal("tanggal", "Tanggal masuk", FieldType.DATE),
                FieldProposal("jumlah", "Jumlah (Rp)", FieldType.NUMBER),
                FieldProposal("status", "Status", FieldType.ENUM, options = listOf("Baru", "Diproses", "Selesai"))
            ),
            statusField = "status",
            transitions = mapOf("Baru" to listOf("Diproses"), "Diproses" to listOf("Selesai"))
        )
        val daftar = ScreenProposal(
            screenId = "contoh_pesanan_list",
            moduleId = ModuleId("contoh_pesanan"),
            title = "Daftar Pesanan",
            widget = WidgetKind.TABLE,
            rationale = "Dipilih karena pemilik menelusuri daftar pesanan yang masuk setiap hari.",
            entity = pesanan,
            view = ViewProposal.Table(
                columns = listOf("nomor", "pelanggan", "tanggal", "jumlah", "status"),
                inlineCreate = true,
                editableFields = listOf("pelanggan", "tanggal", "jumlah")
            ),
            seed = listOf(
                mapOf(
                    "nomor" to "PSN-001", "pelanggan" to "Ibu Sari", "tanggal" to "2026-10-01",
                    "jumlah" to "250000", "status" to "Baru"
                ),
                mapOf(
                    "nomor" to "PSN-002", "pelanggan" to "Pak Budi", "tanggal" to "2026-10-02",
                    "jumlah" to "480000", "status" to "Diproses"
                )
            )
        )
        val catat = ScreenProposal(
            screenId = "contoh_pesanan_form",
            moduleId = ModuleId("contoh_pesanan"),
            title = "Pesanan Baru",
            widget = WidgetKind.FORM,
            rationale = "Dipilih karena petugas mencatat pesanan baru satu per satu lewat formulir.",
            entity = pesanan,
            view = ViewProposal.Form(
                fields = listOf("nomor", "pelanggan", "tanggal", "jumlah"),
                submitLabel = "Simpan Pesanan"
            )
        )
        return DiscoveryDraft(
            pack = pack,
            blueprint = blueprint,
            screens = listOf(
                PrototypeScreen(daftar.screenId, daftar.moduleId, daftar.title, daftar.widget.code, daftar, ProposalSource.Agent(agentRef)),
                PrototypeScreen(catat.screenId, catat.moduleId, catat.title, catat.widget.code, catat, ProposalSource.Agent(agentRef))
            )
        )
    }

    /**
     * Pesan pengguna satu putaran. [feedback] kosong = putaran pertama; kalau ada, jawaban sebelumnya
     * dikirim ulang bersama galat berpath supaya model mengoreksi **bagian yang salah** saja. Galatnya
     * dipotong ([MAX_FEEDBACK_ISSUES] teratas, tiap pesan dibatasi) — galat ke-13 ke bawah biasanya
     * akibat berantai dari yang pertama, dan umpan balik yang membengkak justru membingungkan model.
     */
    fun userMessage(
        request: DiscoveryRequest,
        feedback: List<DiscoveryValidationIssue>,
        previousAnswer: String?,
        round: Int,
        agentRef: String = EXAMPLE_AGENT_REF
    ): String = buildString {
        appendLine("Narasi prospek:")
        appendLine("\"\"\"")
        appendLine(request.narrative.trim())
        appendLine("\"\"\"")
        request.industryHint?.takeIf { it.isNotBlank() }?.let { appendLine("Petunjuk industri: $it") }
        request.displayName?.takeIf { it.isNotBlank() }?.let { appendLine("Nama tampilan yang diminta: $it") }
        appendLine()
        appendLine("Contoh kerangka dokumen yang sah (kode pack 'contoh' — jangan dipakai apa adanya):")
        appendLine(exampleDraftJson(agentRef))
        appendLine()
        if (feedback.isEmpty()) {
            appendLine("Susun dokumen untuk narasi di atas, panggil validate_draft, lalu balas JSON-nya.")
            return@buildString
        }
        appendLine("Putaran koreksi ke-$round. Draf sebelumnya ditolak validator:")
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
}
