package com.eventverse.app.domain.tutorial

import kotlin.jvm.JvmInline

private val KEY_PATTERN = Regex("^[a-z][a-z0-9_.]{0,95}$")

/** Identitas tutorial (`crm_new_lead`). Unik lintas platform + pack (TRD-HELP-001 FR-1). */
@JvmInline
value class TutorialId(val value: String) {
    init { require(KEY_PATTERN.matches(value)) { "TutorialId '$value' harus huruf kecil/angka/underscore/titik" } }
}

/**
 * Titik UI yang bisa disorot coach mark (`crm.new_lead_button`). Kode dan UI hanya memakai konstanta di
 * [TutorialAnchorIds] — string anchor literal di Composable membuat anchor yatim tidak tertangkap test.
 */
@JvmInline
value class TutorialAnchorId(val value: String) {
    init { require(KEY_PATTERN.matches(value)) { "TutorialAnchorId '$value' harus huruf kecil/angka/underscore/titik" } }
}

/** Layar non-modul yang punya tutorial (`builder`, `discovery`). Aksesnya ikut izin layar, bukan RBAC modul. */
@JvmInline
value class SurfaceCode(val value: String) {
    init { require(KEY_PATTERN.matches(value)) { "SurfaceCode '$value' harus huruf kecil/angka/underscore/titik" } }
}

/**
 * Letak callout coach mark terhadap elemen yang disorot.
 *
 * Uji Variabilitas: konsep teknis UI — sama untuk semua tenant, industri, dan tidak diubah admin → enum sistem.
 * [CENTER] = tanpa sorotan (dipakai juga saat anchor tidak ditemukan).
 */
enum class CalloutPlacement { TOP, BOTTOM, START, END, CENTER }
