package com.eventverse.app.domain.pack

import kotlin.jvm.JvmInline

private val CODE_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]{0,63}$")

private fun requireCode(kind: String, value: String) {
    require(CODE_PATTERN.matches(value)) { "$kind '$value' harus huruf/angka/underscore, diawali huruf, maks 64" }
}

/** Kode vertikal platform (`garment`, `elearning`). Kunci tersimpan — tidak ada fallback senyap. */
@JvmInline
value class DomainPackCode(val value: String) {
    init { requireCode("DomainPackCode", value) }
}

/**
 * Fase kanvas (kolom). Untuk pack garment nilainya **persis** nama `PipelineStage` lama
 * (`COMMERCIAL`, …) supaya tidak ada migrasi (Discovery B0 Q3).
 */
@JvmInline
value class PhaseCode(val value: String) {
    init { requireCode("PhaseCode", value) }
}

/**
 * Slot kemampuan modul. Untuk pack garment nilainya **persis** `ModuleArchetype.code`
 * (`order_ingestion`, …) — kode yang sudah tersimpan di JSON pipeline tenant.
 */
@JvmInline
value class SlotCode(val value: String) {
    init { requireCode("SlotCode", value) }
}

/** Tipe dokumen yang mengalir lewat port (`TechPackAndYieldData`). */
@JvmInline
value class PortType(val value: String) {
    init { requireCode("PortType", value) }
}
