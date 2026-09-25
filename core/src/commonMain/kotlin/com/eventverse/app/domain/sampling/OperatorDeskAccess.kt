package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.rbac.ModuleAccessConfig

/**
 * Wewenang per meja lantai produksi (`OPERATOR_EXEC`).
 *
 * Sebuah penugasan divisi boleh membatasi meja mana saja (Rajut sampai Kemas) yang boleh dilihat
 * penyimpanya. Nilai `null` berarti tanpa batasan — seluruh meja — dan itulah arti data lama yang
 * belum mengenal kolom ini, jadi penugasan yang sudah ada tidak berubah perilaku.
 *
 * Aturan penerapannya satu kalimat: **batasan meja hidup di sumbu divisi**. Kalau sumbu divisi
 * memberi akses tanpa batasan meja, seluruh meja terbuka; kalau memberi dengan daftar, hanya
 * daftar itu. Bypass owner/superadmin selalu membuka semuanya — menyaring layar untuk owner
 * berarti menyembunyikan satu-satunya tempat untuk memperbaiki konfigurasinya.
 */

/** Kode meja yang dikenal → tahapnya. Kode asing (data usang, salah ketik) diabaikan diam-diam. */
fun deskStageForCode(code: String): SamplingPipelineStage? =
    SamplingPipelineStage.entries.firstOrNull { it.name == code && it.isOperatorDesk }

/**
 * Daftar kode meja dari konfigurasi → himpunan tahap. `null` (tanpa batasan) diteruskan apa
 * adanya; daftar kosong tetap kosong — kesalahan konfigurasi admin jujur ditampilkan, bukan
 * diam-diam diperlakukan sebagai "semua meja".
 */
fun Collection<String>?.toAllowedOperatorDesks(): Set<SamplingPipelineStage>? =
    this?.mapNotNull(::deskStageForCode)?.toSet()

/**
 * Meja yang boleh diakses persona, atau `null` bila seluruh meja terbuka.
 *
 * @param bypass owner atau superadmin platform — tidak pernah dibatasi.
 * @param departmentAccess wewenang sumbu divisi dari [com.eventverse.app.domain.rbac.AccessDecision];
 *        sumbu yang tidak memberi akses (NONE / tanpa divisi) tidak membawa batasan apa pun.
 */
fun resolveAccessibleOperatorDesks(
    bypass: Boolean,
    departmentAccess: ModuleAccessConfig
): Set<SamplingPipelineStage>? {
    if (bypass) return null
    if (!departmentAccess.isAccessible) return null
    return departmentAccess.allowedDesks.toAllowedOperatorDesks()
}