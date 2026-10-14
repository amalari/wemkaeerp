package com.eventverse.app.domain.pack

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ProposalLimits
import com.eventverse.app.domain.fulfillment.HandoverRoute
import com.eventverse.app.domain.i18n.LocalizedText
import com.eventverse.app.domain.i18n.PackLanguages
import com.eventverse.app.domain.prototype.CurrencyCode

/** Satu kolom kanvas. [colorHex] adalah data vertikal, bukan keputusan design system. */
data class PhaseDefinition(
    val code: PhaseCode,
    val order: Int,
    val displayName: String,
    val subtitle: String,
    val colorHex: Long,
    /**
     * Jembatan Strangler Fig TRD-I18N-002 (P1): nama fase dua bahasa. [displayName] tetap `String` = teks `id`
     * untuk pembaca lama; pembaca baru memakai [displayNameText]`.resolve(bahasa)`. Bawaan = hanya `id`.
     */
    val displayNameText: LocalizedText = LocalizedText.of(displayName),
    /** Subjudul dua bahasa; `null` = pack tidak menerjemahkannya (subjudul kosong tak bisa jadi [LocalizedText]). */
    val subtitleText: LocalizedText? = subtitle.takeIf { it.isNotBlank() }?.let { LocalizedText.of(it) }
) {
    init {
        require(order > 0) { "Urutan fase ${code.value} harus positif" }
        require(displayName.isNotBlank()) { "Nama fase ${code.value} kosong" }
        require(displayNameText.idText == displayName) { "Fase ${code.value}: displayNameText.id harus sama dengan displayName" }
        require(subtitleText == null || subtitleText.idText == subtitle) { "Fase ${code.value}: subtitleText.id harus sama dengan subtitle" }
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
    val defaultStatuses: List<String> = emptyList(),
    /** Jembatan Strangler Fig TRD-I18N-002 (P2): nama slot dua bahasa; [displayName] = teks `id`. */
    val displayNameText: LocalizedText = LocalizedText.of(displayName)
) {
    init {
        require(displayName.isNotBlank()) { "Nama slot ${code.value} kosong" }
        require(displayNameText.idText == displayName) { "Slot ${code.value}: displayNameText.id harus sama dengan displayName" }
        if (defaultStatuses.isNotEmpty()) {
            require(defaultWidget == WidgetKind.KANBAN || defaultWidget == WidgetKind.TABLE) {
                "Slot ${code.value}: defaultStatuses hanya bermakna untuk defaultWidget KANBAN atau TABLE"
            }
            require(defaultStatuses.size in 2..ProposalLimits.STATUSES) {
                "Slot ${code.value}: defaultStatuses wajib 2-${ProposalLimits.STATUSES} butir, dapat ${defaultStatuses.size}"
            }
            require(defaultStatuses.distinct().size == defaultStatuses.size) { "Slot ${code.value}: defaultStatuses ada yang kembar" }
            require(defaultStatuses.all { it.isNotBlank() && it.length <= ProposalLimits.TEXT }) {
                "Slot ${code.value}: butir defaultStatuses wajib terisi dan <= ${ProposalLimits.TEXT} karakter"
            }
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
    val reservedTerms: List<String> = emptyList(),
    /**
     * Modul pack ini yang **ditawarkan sebagai modul bersama**: pack lain boleh merujuknya lewat `ModuleReference`
     * (B6). Opt-in per modul, data pack bawaan — bukan semua modul operasional otomatis jadi bersama.
     */
    val sharedModules: Set<ModuleId> = emptySet(),
    /** Rujukan ke modul bersama platform (B6, lihat [ModuleReference]); aturan di [ModuleReferenceRules]. Data saja, belum ke RBAC/kanvas. */
    val moduleReferences: List<ModuleReference> = emptyList(),
    /** Template rute serah terima karung (TRD-FLOW-003). Kosong = pack tanpa rute bawaan, bukan rute pack lain. */
    val handoverRouteTemplate: List<HandoverRoute> = emptyList(),
    /**
     * Kode mata uang **bawaan** pack (C4 Irisan 2): hanya **sumber nilai awal** untuk usulan field `NUMBER(CURRENCY)`
     * yang dibuat pembuat deterministik. Setelah masuk dokumen, kode itu **milik field** ([com.eventverse.app.domain.prototype.FieldSpec.currencyCode]):
     * template disalin dan dokumen membeku, jadi mengubah bawaan pack tidak mengubah usulan/spec yang sudah ada.
     * Pack yang tidak menyebutnya memakai bawaan global `IDR`; kode tak berbentuk tiga huruf besar **menggagalkan**
     * pembangunan pack (tidak ada fallback ke `IDR`). Tidak ada daftar mata uang tertutup di kode.
     */
    val defaultCurrencyCode: String = DEFAULT_CURRENCY_CODE,
    /**
     * Starter alur milik pack ini (TRD-PLAT-008), disalin ke tenant saat handoff. Tenant menyimpan **kodenya**; kode
     * di-resolve lewat [resolveBlueprint]. Kosong = pack tanpa starter data — pack bawaan garment sengaja kosong
     * (starter-nya tetap `GarmentBlueprints`, supaya bytes pack garment tak berubah). Invarian: [requireBlueprintsValid].
     */
    val blueprints: List<Blueprint> = emptyList(),
    /**
     * Bahasa yang **diklaim lengkap** pack ini (TRD-I18N-002 F2). Bawaan hanya `id`. Pack yang mengklaim `en` harus
     * lolos [missingTranslations] — pembuktian ada di validator, bukan di niat.
     */
    val languages: PackLanguages = PackLanguages.ID_ONLY,
    /**
     * Konfigurasi tema visual pack (TRD-PLAT-013). Null = pack lama menggunakan tema default CLAY.
     * Tema adalah properti pack, bukan tenant — setiap tenant yang menggunakan pack ini akan mendapat tema yang sama.
     */
    val theme: PackTheme? = null,
    /**
     * Jembatan Strangler Fig TRD-I18N-002 (P5): [vocabulary] dan [portLabels] dua bahasa. Nilai `String` lama tetap
     * = teks `id`; bawaan diturunkan darinya. Kunci kedua peta wajib sama dengan peta lamanya.
     */
    val vocabularyText: Map<VocabularyKey, LocalizedText> = vocabulary.mapValues { LocalizedText.of(it.value) },
    val portLabelTexts: Map<String, LocalizedText> = portLabels.mapValues { LocalizedText.of(it.value) }
) {
    init {
        requireBlueprintsValid()
        require(CurrencyCode.isValid(defaultCurrencyCode)) {
            "Pack ${code.value}: defaultCurrencyCode '$defaultCurrencyCode' harus tiga huruf besar (mis. IDR)"
        }
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
        sharedModules.forEach { id ->
            require(modules.any { it.id == id && it.slot != null }) { "Modul bersama ${id.value} harus modul operasional milik pack ${code.value}" }
        }
        requireUnique("kamus peran", roleHints.map { it.word })
        reservedTerms.forEach { require(it.isNotBlank() && it == it.trim().lowercase()) { "Kosakata cadangan '$it' wajib terisi dan huruf kecil" } }
        requireUnique("kosakata cadangan", reservedTerms)
        roleHints.forEach { h ->
            require(h.moduleId.value in moduleIds || moduleReferences.any { it.platformModuleId == h.moduleId }) { "Kamus peran '${h.word}' menunjuk modul tak dikenal ${h.moduleId.value} di pack ${code.value}" }
        }
        requireUnique("rute serah terima", handoverRouteTemplate.map { it.code.value })
        requireUnique("aksi", actions.map { it.code.name })
        vocabulary.forEach { (key, word) ->
            require(word.isNotBlank()) { "Istilah ${key.name} pack ${code.value} kosong" }
        }
        require(vocabularyText.keys == vocabulary.keys && vocabularyText.all { (k, v) -> v.idText == vocabulary[k] }) {
            "Pack ${code.value}: vocabularyText harus sepasang dengan vocabulary (teks id sama)"
        }
        require(portLabelTexts.keys == portLabels.keys && portLabelTexts.all { (k, v) -> v.idText == portLabels[k] }) {
            "Pack ${code.value}: portLabelTexts harus sepasang dengan portLabels (teks id sama)"
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

    companion object {
        /** Bawaan global untuk pack yang tidak menyebut mata uangnya. */
        const val DEFAULT_CURRENCY_CODE: String = "IDR"
    }
}
