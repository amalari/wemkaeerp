package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DiscoveryValidationIssue
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.WidgetKind
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

/**
 * Prompt agent discovery Koog (plan §2 A8).
 *
 * Dua keputusan yang disengaja:
 *
 * 1. **Contoh dokumen dibuat kode, bukan ditempel sebagai teks.** [exampleDraftJson] dirakit dari
 *    `DiscoveryDraft` sungguhan lalu di-encode `DiscoveryDraftCodec` — parser produksi. Jadi contoh di
 *    prompt tidak mungkin melenceng dari kontrak (dites: contoh itu sendiri wajib lolos validator).
 *    Konsekuensinya contoh **tidak bocor jawaban**: kode pack-nya `contoh`, bukan narasi yang diminta.
 * 2. **Jembatan pack bawaan** (`useShipped`) dibahas di prompt karena validator produksi menuntut pack
 *    bawaan platform dikembalikan **identik**; model tidak akan pernah bisa menulis ulang dokumen itu
 *    dari ingatan. Lihat `KoogDiscoveryAgent.applyShippedPackBridge`.
 */
internal object KoogDiscoveryPrompt {

    /** Jumlah karakter jawaban sebelumnya yang diumpan balikkan; sisanya dipotong agar prompt tidak membengkak. */
    private const val PREVIOUS_ANSWER_CHAR_LIMIT = 6_000

    private val WIDGET_CODES: String = WidgetKind.entries.joinToString(", ") { it.code }

    val system: String = """
        Kamu adalah analis discovery WeMade ERP. Tugasmu: mengubah narasi bisnis calon klien menjadi
        SATU dokumen JSON berisi draf domain pack + blueprint alur modul.

        Bentuk keluaran (WAJIB):
        {"pack": <DomainPack>, "blueprint": <Blueprint>, "screens": [<PrototypeScreen>]}

        Aturan keras — validator produksi menolak pelanggaran berikut:
        1. `pack.code` adalah slug huruf kecil (`klinik`, `bengkel`, `katering`), bukan nama panjang.
        2. Setiap modul dan slot BARU wajib berprefiks `<pack.code>_` (contoh: `klinik_antrean`). Ini
           mencegah draf merebut id modul platform.
        3. Jangan menulis ulang pack bawaan platform. Bila salah satu pack bawaan sudah cocok, pakai
           jembatan: tulis `"pack": {"useShipped": "<kode pack bawaan>"}` — server menyalin dokumen
           aslinya. `blueprint.pack` tetap memakai kode pack itu.
        4. Setiap `blueprint.modules[].moduleCode` wajib ada di `pack.modules[].id`. Modul non-aktif
           tetap dicantumkan dengan `"active": false` beserta parameter alasan bypass-nya.
        5. `screens[].widget` hanya boleh salah satu dari: $WIDGET_CODES.
        6. Semua field yang terlihat di contoh WAJIB ada. Jangan mengarang field baru.
        7. Balas HANYA objek JSON — tanpa penjelasan, tanpa pagar kode, tanpa teks pembuka.
        8. Hemat langkah: panggil `platform_modules()` maksimal sekali dan `validate_draft` maksimal dua
           kali. Begitu draf bersih, langsung balas JSON-nya — agent dibatasi jumlah langkahnya.
        9. `pack.vocabulary` menyebut istilah yang benar-benar diucapkan pemilik usaha, mis.
           `{"WORKPLACE":"klinik","DOCUMENT":"Kunjungan"}` (kunci hanya boleh WORKPLACE atau DOCUMENT).
           Jangan pakai istilah konveksi ("pabrik", "SPK") kecuali vertikalnya memang konveksi.
           `pack.actions` = label tombol layar kerja, mis. `{"code":"ADD","label":"Tambah Kunjungan"}`.

        Alat yang tersedia (pakai sebelum menjawab):
        - `platform_modules()` — daftar pack bawaan platform beserta modul, slot, dan seksinya. Pakai
          untuk (a) mengetahui kode pack bawaan, dan (b) meniru gaya penamaan modul platform.
        - `validate_draft(draft)` — memvalidasi dokumen yang baru kamu susun. Ia mengembalikan
          `{"valid":true}` atau daftar galat berpath (`${'$'}.pack.modules[2].id`). Perbaiki dulu sebelum
          menjawab; jangan pernah menjawab dokumen yang masih berisi galat.

        Pedoman isi (bukan aturan kaku): ambil 3–6 modul yang benar-benar disebut narasi, satu fase per
        tahap kerja yang jelas, dan seksi menu yang masuk akal bagi pemilik usaha. Nama modul memakai
        istilah narasi ("Antrean Pasien", "Servis Motor"), bukan istilah internal ("CRUD", "Tabel").
        Beri 1–3 `screens` untuk modul utama; sisanya boleh kosong.
    """.trimIndent()

    /**
     * Contoh kerangka: dokumen **sah** berkode pack `contoh`, sehingga model belajar bentuknya tanpa
     * menerima jawabannya. Sifat "sah" itu dikunci test (`KoogDiscoveryPromptTest`).
     */
    fun exampleDraftJson(): String = DiscoveryDraftCodec.encodeToString(exampleDraft())

    fun exampleDraft(): DiscoveryDraft {
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
        return DiscoveryDraft(
            pack = pack,
            blueprint = blueprint,
            screens = listOf(
                PrototypeScreen("contoh_pesanan_list", ModuleId("contoh_pesanan"), "Daftar Pesanan", WidgetKind.TABLE.code),
                PrototypeScreen("contoh_pesanan_form", ModuleId("contoh_pesanan"), "Pesanan Baru", WidgetKind.FORM.code)
            )
        )
    }

    /**
     * Pesan pengguna satu putaran. [feedback] kosong = putaran pertama; kalau ada, jawaban sebelumnya
     * dikirim ulang bersama galat berpath supaya model mengoreksi **bagian yang salah** saja.
     */
    fun userMessage(
        request: DiscoveryRequest,
        feedback: List<DiscoveryValidationIssue>,
        previousAnswer: String?,
        round: Int
    ): String = buildString {
        appendLine("Narasi prospek:")
        appendLine("\"\"\"")
        appendLine(request.narrative.trim())
        appendLine("\"\"\"")
        request.industryHint?.takeIf { it.isNotBlank() }?.let { appendLine("Petunjuk industri: $it") }
        request.displayName?.takeIf { it.isNotBlank() }?.let { appendLine("Nama tampilan yang diminta: $it") }
        appendLine()
        appendLine("Contoh kerangka dokumen yang sah (kode pack 'contoh' — jangan dipakai apa adanya):")
        appendLine(exampleDraftJson())
        appendLine()
        if (feedback.isEmpty()) {
            appendLine("Susun dokumen untuk narasi di atas, panggil validate_draft, lalu balas JSON-nya.")
            return@buildString
        }
        appendLine("Putaran koreksi ke-$round. Draf sebelumnya ditolak validator:")
        feedback.forEach { appendLine("- ${it.path}: ${it.message}") }
        previousAnswer?.let {
            appendLine()
            appendLine("Jawaban sebelumnya (dipotong bila terlalu panjang):")
            appendLine(it.take(PREVIOUS_ANSWER_CHAR_LIMIT))
        }
        appendLine()
        appendLine("Perbaiki HANYA bagian yang dilaporkan, pertahankan sisanya, lalu balas JSON utuh.")
    }
}
