package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ProposalLimits
import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.domain.prototype.DashboardHints
import com.eventverse.app.domain.prototype.FormHints
import com.eventverse.app.domain.prototype.KanbanHints
import com.eventverse.app.domain.prototype.TableHints

/** Satu kolom kanvas. [colorHex] adalah data vertikal, bukan keputusan design system. */
data class PhaseDefinition(
    val code: PhaseCode,
    val order: Int,
    val displayName: String,
    val subtitle: String,
    val colorHex: Long
) {
    init {
        require(order > 0) { "Urutan fase ${code.value} harus positif" }
        require(displayName.isNotBlank()) { "Nama fase ${code.value} kosong" }
    }
}

/**
 * Satu slot kemampuan: modul yang mengisinya digambar di [phase] dan lazimnya menerima/mengeluarkan port default.
 *
 * **Pemetaan peran → tampilan** (plan §2.3, data pack — Uji Variabilitas: tiap industri memberi watak kerja yang
 * berbeda pada slot yang sama):
 *  - [defaultWidget] — jenis tampilan lazim modul pengisi slot ini (antrean → KANBAN, stok → TABLE, laporan →
 *    DASHBOARD). **Null = pack tidak berpendapat**: pembuat deterministik tidak membuat layar untuk slot itu
 *    dan tidak pernah meminjam tebakan dari pack/slot lain.
 *  - [defaultStatuses] — urutan status lazim (awal → akhir) bila tampilannya KANBAN/TABLE; kosong = tidak ada
 *    status baku. Kolom kanban/pilihan status layar diturunkan darinya.
 *
 * Keduanya opsional dan kompatibel mundur: pack lama tanpa kunci ini terbaca dengan `null`/kosong.
 */
data class SlotDefinition(
    val code: SlotCode,
    val displayName: String,
    val phase: PhaseCode,
    val defaultInput: PortType,
    val defaultOutput: PortType,
    val defaultWidget: WidgetKind? = null,
    val defaultStatuses: List<String> = emptyList()
) {
    init {
        require(displayName.isNotBlank()) { "Nama slot ${code.value} kosong" }
        if (defaultStatuses.isNotEmpty()) {
            require(defaultWidget == WidgetKind.KANBAN || defaultWidget == WidgetKind.TABLE) {
                "Slot ${code.value}: defaultStatuses hanya bermakna untuk defaultWidget KANBAN atau TABLE"
            }
            require(defaultStatuses.size in 2..ProposalLimits.STATUSES) {
                "Slot ${code.value}: defaultStatuses wajib 2–${ProposalLimits.STATUSES} butir, dapat ${defaultStatuses.size}"
            }
            require(defaultStatuses.distinct().size == defaultStatuses.size) { "Slot ${code.value}: defaultStatuses ada yang kembar" }
            require(defaultStatuses.all { it.isNotBlank() && it.length <= ProposalLimits.TEXT }) {
                "Slot ${code.value}: butir defaultStatuses wajib terisi dan ≤ ${ProposalLimits.TEXT} karakter"
            }
        }
    }
}

/**
 * Usulan satu layar prototype bawaan pack (mock `/builder/prototype`). **Isi layar = data pack**:
 * judul layar dan watak widget bawaan adalah kosakata vertikal (tenant-variability-rules), sementara
 * bentuk widgetnya sendiri tetap kosakata sistem [WidgetKind] yang bisa digambar renderer mana pun.
 * Draf kerja tenant yang baru di-bootstrap memakai usulan ini sebelum agent LLM mengusulkan layar
 * miliknya sendiri; draf yang sudah punya layar tidak pernah ditimpa.
 */
data class ScreenSuggestion(
    val moduleId: ModuleId,
    val title: String,
    val widget: WidgetKind,
    /**
     * Baris contoh isi layar (v2 mock `/builder/prototype`): **data vertikal** — kalimat, angka, dan
     * nama yang akan dilihat user garment, bukan karangan mesin renderer. Kosong = pack tidak
     * mengusulkan isi; `WidgetRegistry` lalu memakai penanda strukturnya sendiri (tidak pernah
     * fallback ke kosakata pack lain). Bentuk baris dikonsumsi per widget:
     *  - KANBAN: kunci `Kolom` menamaikan kolom papan; entri lain = kartu (nilai pertama judul, sisanya detail).
     *  - FORM: satu baris berisi label field → contoh isian; kunci `Simpan` digambar sebagai tombol.
     *  - CHECKLIST: kunci `Butir` → teks inspeksi, `Selesai` = "ya"/"tidak".
     *  - TABLE: baris-baris berkunci sama = header + isi tabel.
     *  - DASHBOARD: satu pasang label → angka per baris (satu tile).
     *  - PRINT: pasangan label → isi dokumen cetak.
     */
    val sampleRows: List<Map<String, String>> = emptyList(),
    /**
     * Perilaku papan (hanya bermakna untuk [WidgetKind.KANBAN]): urutan kolom termasuk yang kosong dan
     * transisi yang boleh. Null = kolom diturunkan dari [sampleRows], kartu bebas pindah.
     */
    val kanbanHints: KanbanHints? = null,
    /** Perilaku tabel (hanya bermakna untuk [WidgetKind.TABLE]); null = tabel hanya bisa disortir/difilter. */
    val tableHints: TableHints? = null,
    /** Ubin dasbor yang dihitung dari layar lain (hanya bermakna untuk [WidgetKind.DASHBOARD]). */
    val dashboardHints: DashboardHints? = null,
    /**
     * Petunjuk formulir tambah data (butir B2): pelengkap layar sumber — boleh terpasang pada
     * usulan ber-widget apa pun (lazimnya TABLE/KANBAN), karena form berbagi entitas dengan layar
     * sumbernya, bukan berdiri sendiri. Null = modul tidak mengusulkan form.
     */
    val formHints: FormHints? = null,
    /**
     * Asal data layar (kontrak v2, plan induk §3.4): bawaan [DataBinding.Memory] (demo memori);
     * modul pilot memakai [DataBinding.Api] agar blok memuat/menyimpan ke server. Jenis binding
     * adalah kosakata **sistem** (kode); memilih nilainya adalah keputusan pack.
     */
    val dataBinding: DataBinding = DataBinding.Memory,
    /**
     * Alasan pilihan layar ini dalam bahasa pemilik usaha ("Dipilih karena …"), ditulis manusia — data pack.
     * Dibaca `PackScreenProposer` menjadi `ScreenProposal.rationale`. Null = pack belum menuliskannya; proposer
     * lalu **menolak** (tidak mengarang alasan). Opsional: pack lama tanpa kunci ini tetap terbaca.
     */
    val rationale: String? = null
) {
    init {
        require(title.isNotBlank()) { "Usulan layar ${moduleId.value} tanpa judul" }
        require(sampleRows.all { row -> row.isNotEmpty() && row.keys.all { it.isNotBlank() } && row.values.all { it.isNotBlank() } }) {
            "Usulan layar ${moduleId.value} punya baris contoh kosong"
        }
    }
}

/**
 * Kosakata satu vertikal platform (Jalur B, B0): fase kanvas, slot modul, dan tipe port.
 *
 * Pack adalah milik **platform** dan dikirim per rilis (Discovery B0 Q1) — tenant memilih pack,
 * tidak menyuntingnya. Variasi di dalam vertikal (template industri, kerangka tahap tenant, preset)
 * tetap data di bawahnya.
 *
 * @param portTypes seluruh kosakata port vertikal, termasuk port batas/deskriptif yang tidak
 *   disambungkan antarmodul (mis. `CommercialInquiry` masuk dari luar sistem).
 * @param wiredPortTypes subset yang dipakai kanvas untuk menyambung modul.
 */
data class DomainPack(
    val code: DomainPackCode,
    val displayName: String,
    val phases: List<PhaseDefinition>,
    val slots: List<SlotDefinition>,
    val portTypes: Set<PortType>,
    val wiredPortTypes: Set<PortType>,
    /** Seksi menu & modul yang dikirim pack (B6). Urutan [modules] = urutan menu di dalam seksinya. */
    val sections: List<ModuleSection> = emptyList(),
    val modules: List<ModuleDefinition> = emptyList(),
    /**
     * Tombol aksi layar kerja generik (A4): urutan = urutan tombol, label = kalimat pack.
     *
     * **Kenapa di pack, bukan di [ModuleDefinition]** (rencana menulisnya di sana):
     * [DomainPackRegistry.violations] mewajibkan definisi modul bersama platform (`org_chart`,
     * `dynamic_rbac`) **identik** lintas pack — sementara label aksi justru yang paling wajar berbeda
     * antar vertikal. Menaruhnya di `ModuleDefinition` membuat pack klinik yang memakai `org_chart`
     * ditolak saat didaftarkan (`DomainPackApiTest` memakai susunan itu).
     */
    val actions: List<ModuleAction> = ModuleActionCode.neutral,
    /**
     * Istilah yang diucapkan pack (A4). **Tidak ada** = chrome memakai kata netral
     * ([VocabularyKey.neutral]) — bukan jatuh ke kosakata vertikal mana pun.
     */
    val vocabulary: Map<VocabularyKey, String> = emptyMap(),
    /**
     * Label tampilan manusiawi tipe port (`"ProductionOrderDraft"` → `"Draf Pesanan Produksi (PO)"`).
     * Kosakata pack, sejajar [actions] dan [vocabulary]: **kode port tetap identitas tersimpan**
     * (kunci kontrak `upstreamPrerequisites`/`downstreamHandoffs`), label hanya untuk layar.
     * Kunci yang tidak terdaftar di [portTypes] ditolak — label untuk port yang tidak ada = pack rusak.
     */
    val portLabels: Map<String, String> = emptyMap(),
    /**
     * Usulan layar prototype bawaan pack (lihat [ScreenSuggestion]). Kosong = pack belum mengusulkan
     * layar apa pun — pane prototype tetap jujur menampilkan keadaan kosong, tanpa fallback ke
     * kosakata pack lain.
     */
    val screenSuggestions: List<ScreenSuggestion> = emptyList(),
    /** Kamus peran → modul untuk wawancara (lihat [RoleHint]). Kosong = tidak ada tebakan, bukan kamus pack lain. */
    val roleHints: List<RoleHint> = emptyList(),
    /** Kosakata cadangan pack ini: istilah yang tidak boleh muncul di usulan pack **lain** (kemurnian vertikal). Kosong = tak ada. */
    val reservedTerms: List<String> = emptyList()
) {
    init {
        require(phases.isNotEmpty()) { "Pack ${code.value} tanpa fase" }
        requireUnique("fase", phases.map { it.code.value })
        requireUnique("urutan fase", phases.map { it.order.toString() })
        requireUnique("slot", slots.map { it.code.value })
        val phaseCodes = phases.map { it.code }.toSet()
        slots.forEach { slot ->
            require(slot.phase in phaseCodes) { "Slot ${slot.code.value} menunjuk fase tak dikenal ${slot.phase.value}" }
            listOf(slot.defaultInput, slot.defaultOutput).forEach { port ->
                require(port in portTypes) { "Slot ${slot.code.value} memakai port tak terdaftar ${port.value}" }
            }
        }
        (wiredPortTypes - portTypes).firstOrNull()?.let { error("Port wiring ${it.value} tidak ada di kosakata pack ${code.value}") }
        requireUnique("seksi", sections.map { it.code.value })
        requireUnique("modul", modules.map { it.id.value })
        val sectionCodes = sections.map { it.code }.toSet()
        val slotCodes = slots.map { it.code }.toSet()
        modules.forEach { m ->
            require(m.section in sectionCodes) { "Modul ${m.id.value} menunjuk seksi tak dikenal ${m.section.value}" }
            m.slot?.let { require(it in slotCodes) { "Modul ${m.id.value} menunjuk slot tak dikenal ${it.value}" } }
        }
        val moduleIds = modules.map { it.id.value }.toSet()
        requireUnique("usulan layar", screenSuggestions.map { it.moduleId.value })
        screenSuggestions.forEach { s ->
            require(s.moduleId.value in moduleIds) {
                "Usulan layar menunjuk modul tak dikenal ${s.moduleId.value} di pack ${code.value}"
            }
        }
        requireUnique("kamus peran", roleHints.map { it.word })
        reservedTerms.forEach { require(it.isNotBlank() && it == it.trim().lowercase()) { "Kosakata cadangan '$it' wajib terisi dan huruf kecil" } }
        requireUnique("kosakata cadangan", reservedTerms)
        roleHints.forEach { h ->
            require(h.moduleId.value in moduleIds) { "Kamus peran '${h.word}' menunjuk modul tak dikenal ${h.moduleId.value} di pack ${code.value}" }
        }
        requireUnique("aksi", actions.map { it.code.name })
        vocabulary.forEach { (key, word) ->
            require(word.isNotBlank()) { "Istilah ${key.name} pack ${code.value} kosong" }
        }
        val portCodes = portTypes.map { it.value }.toSet()
        portLabels.forEach { (port, label) ->
            require(port in portCodes) { "Label port '$port' tidak ada di kosakata pack ${code.value}" }
            require(label.isNotBlank()) { "Label port '$port' pack ${code.value} kosong" }
        }
    }

    /**
     * Label tombol aksi [code] menurut pack ini. Pack yang tidak menyebut aksinya memakai label netral
     * platform (`"Tambah"`), bukan label vertikal mana pun.
     */
    fun actionLabel(code: ModuleActionCode): String =
        actions.firstOrNull { it.code == code }?.label ?: code.neutralLabel

    /** Istilah [key] menurut pack ini; pack yang tidak mendeklarasikannya memakai kata netral. */
    fun term(key: VocabularyKey): String = vocabulary[key] ?: key.neutral

    /**
     * Label tampilan tipe port [code] menurut pack ini. Port tanpa label (mis. port pack data yang
     * belum menyebut kosakatanya) tampil sebagai kodenya sendiri — bukan fallback ke kosakata lain.
     */
    fun portLabel(code: String): String = portLabels[code] ?: code

    fun module(id: ModuleId): ModuleDefinition? = modules.firstOrNull { it.id == id }

    /** Label port mentah (dari spec/JSON) termasuk port yang menyambung modul di pack ini. */
    fun isWired(label: String): Boolean = wiredPortTypes.any { it.value == label }

    val orderedPhases: List<PhaseDefinition> get() = phases.sortedBy { it.order }

    fun phase(code: PhaseCode): PhaseDefinition? = phases.firstOrNull { it.code == code }

    fun slot(code: SlotCode): SlotDefinition? = slots.firstOrNull { it.code == code }

    /** Fase tempat modul ber-slot [code] digambar. Slot tak dikenal = pack tidak lengkap → gagal keras. */
    fun phaseOfSlot(code: SlotCode): PhaseDefinition {
        val slot = requireNotNull(slot(code)) { "Slot ${code.value} tidak ada di pack ${this.code.value}" }
        return requireNotNull(phase(slot.phase)) { "Fase ${slot.phase.value} hilang dari pack ${this.code.value}" }
    }

    private fun requireUnique(kind: String, values: List<String>) {
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull()
            ?.let { error("Pack ${code.value}: $kind ganda '$it'") }
    }
}
