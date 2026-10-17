package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.pack.ModuleId
import kotlin.jvm.JvmInline

/**
 * Skenario = jalur bisnis ujung ke ujung yang melintasi beberapa modul (TRD-BLDR-002), mis. "lead jadi invoice, invoice
 * jadi pesanan sampel". **Deskriptif dan hanya untuk simulasi tampilan**: tidak menyimpan record sungguhan.
 */
@JvmInline
value class ScenarioId(val value: String) {
    init { require(value.isNotBlank()) { "ScenarioId kosong" } }
}

/**
 * Asal skenario. Enum sah (Uji Variabilitas): mekanik platform — siapa yang menyusunnya — bukan kosakata industri.
 * Ditampilkan ke pengguna supaya tebakan sistem tidak menyamar sebagai keputusan pengguna.
 */
enum class ScenarioSource { AI, DETERMINISTIC, USER }

/** Batas keluaran skenario (TRD-BLDR-002 FR-2). Satu sumber angka untuk validator, prompt, dan klien. */
object ScenarioLimits {
    const val SCENARIOS_MAX = 3
    const val STEPS_MAX = 7
    const val TITLE_MAX_CHARS = 80
    const val LABEL_MAX_CHARS = 40
    const val CARRIES_MAX_PER_STEP = 12
    const val QUESTIONS_MAX = 2
    /** Kontrak payload (TRD-BLDR-003 FR-7): 1 = legacy (langkah tanpa evidence), 2 = langkah berevidence. */
    const val CONTRACT_VERSION_LEGACY = 1
    const val CONTRACT_VERSION_EVIDENCE = 2
}

/**
 * Satu pasangan field yang dibawa dari layar langkah ini ke layar langkah **berikutnya**: nilai [fromField] pada baris
 * terpilih menjadi nilai awal [toField] pada baris baru. Kunci field adalah data tenant — keberadaan dan kecocokan
 * tipenya ditegakkan validator (berpath), bukan konstruktor ini.
 */
data class FieldCarry(val fromField: String, val toField: String) {
    init {
        require(fromField.isNotBlank()) { "FieldCarry.fromField kosong" }
        require(toField.isNotBlank()) { "FieldCarry.toField kosong" }
    }
}

/**
 * Satu langkah skenario. [screenId] `null` = modul belum punya layar berproposal → langkah ditampilkan sebagai
 * **kerangka**, tidak dipalsukan jadi layar teks (`field-component-rules.md` Kontrak 8). [carries] menunjuk langkah
 * berikutnya; langkah terakhir tidak punya pembawa. [reworkTo] = indeks langkah **sebelumnya** tempat dokumen kembali
 * bila ditolak (satu loop rework yang dideklarasikan), `null` = tidak ada. [evidence] = bukti asal langkah
 * (TRD-BLDR-003 FR-5); `null` hanya sah pada payload legacy v1 — `FieldCarry` sengaja tidak berevidence karena
 * keabsahannya adalah derivasi struktural dari katalog screen/field.
 */
data class ScenarioStep(
    val moduleId: ModuleId,
    val screenId: String?,
    val label: String,
    val carries: List<FieldCarry> = emptyList(),
    val reworkTo: Int? = null,
    val evidence: EvidenceRef? = null,
) {
    init {
        require(label.isNotBlank()) { "ScenarioStep.label kosong" }
        require(screenId == null || screenId.isNotBlank()) { "ScenarioStep.screenId tidak boleh string kosong (pakai null untuk kerangka)" }
        require(reworkTo == null || reworkTo >= 0) { "ScenarioStep.reworkTo harus >= 0" }
    }
}

/**
 * Satu skenario di draf. Invarian lintas-bagian (modul aktif, layar milik modul itu, field ada, tipe cocok, batas jumlah,
 * `reworkTo` mundur, skenario ganda) **sengaja bukan di konstruktor** — milik [ScenarioValidator] supaya tebakan tak sah bisa
 * dikembalikan ke agent dengan path (pola `InterviewValidator`). [confirmed] `false` = baru diusulkan: tidak masuk ekspor
 * brief/handoff sampai pengguna mengonfirmasi (FR-8). [contractVersion] menentukan kewajiban evidence pada validator
 * generasi v2 (TRD-BLDR-003 FR-7.3); draf lama terbaca tanpa transformasi.
 */
data class ScenarioSketch(
    val id: ScenarioId,
    val title: String,
    val steps: List<ScenarioStep>,
    val source: ScenarioSource,
    val confirmed: Boolean = false,
    val contractVersion: Int = ScenarioLimits.CONTRACT_VERSION_LEGACY,
) {
    init {
        require(title.isNotBlank()) { "ScenarioSketch.title kosong" }
        require(
            contractVersion == ScenarioLimits.CONTRACT_VERSION_LEGACY ||
                contractVersion == ScenarioLimits.CONTRACT_VERSION_EVIDENCE
        ) { "contractVersion tak dikenal: $contractVersion" }
    }
}
