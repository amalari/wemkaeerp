package com.eventverse.app.domain.pack

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

/** Satu slot kemampuan: modul yang mengisinya digambar di [phase] dan lazimnya menerima/mengeluarkan port default. */
data class SlotDefinition(
    val code: SlotCode,
    val displayName: String,
    val phase: PhaseCode,
    val defaultInput: PortType,
    val defaultOutput: PortType
) {
    init { require(displayName.isNotBlank()) { "Nama slot ${code.value} kosong" } }
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
    val vocabulary: Map<VocabularyKey, String> = emptyMap()
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
        requireUnique("aksi", actions.map { it.code.name })
        vocabulary.forEach { (key, word) ->
            require(word.isNotBlank()) { "Istilah ${key.name} pack ${code.value} kosong" }
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
