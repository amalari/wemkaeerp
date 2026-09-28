package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageTrait

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

/** Meja operator kerangka rajut — default bagi pemanggil yang belum memegang kerangka pabrik. */
val DEFAULT_OPERATOR_DESKS: List<StageDefinition> = SamplingRoute.DEFAULT_STAGES.filter { it.has(StageTrait.OPERATOR_DESK) }

/**
 * Daftar kode meja dari konfigurasi → himpunan kode meja yang ada di [desks] (kerangka pabrik).
 * Kode asing (data usang, salah ketik, tahap yang bukan meja) diabaikan diam-diam. `null` (tanpa
 * batasan) diteruskan apa adanya; daftar kosong tetap kosong — kesalahan konfigurasi admin jujur
 * ditampilkan, bukan diam-diam diperlakukan sebagai "semua meja".
 */
fun Collection<String>?.toAllowedOperatorDesks(desks: List<StageDefinition> = DEFAULT_OPERATOR_DESKS): Set<StageCode>? =
    this?.mapNotNull { raw -> desks.firstOrNull { it.code.value == raw }?.code }?.toSet()

/**
 * Meja yang boleh diakses persona, atau `null` bila seluruh meja terbuka.
 *
 * @param bypass owner atau superadmin platform — tidak pernah dibatasi.
 * @param departmentAccess wewenang sumbu divisi dari [com.eventverse.app.domain.rbac.AccessDecision];
 *        sumbu yang tidak memberi akses (NONE / tanpa divisi) tidak membawa batasan apa pun.
 * @param desks meja pada kerangka pabrik (TRD-FLOW-001) — sumber validasi kode meja.
 */
fun resolveAccessibleOperatorDesks(
    bypass: Boolean,
    departmentAccess: ModuleAccessConfig,
    desks: List<StageDefinition> = DEFAULT_OPERATOR_DESKS
): Set<StageCode>? {
    if (bypass) return null
    if (!departmentAccess.isAccessible) return null
    return departmentAccess.allowedDesks.toAllowedOperatorDesks(desks)
}
