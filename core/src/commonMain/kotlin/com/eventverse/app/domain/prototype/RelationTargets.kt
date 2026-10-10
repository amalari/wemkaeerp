package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.customfield.RelationTargetResolver
import com.eventverse.app.domain.tenant.TenantId

/**
 * Sumber daya target RELATION yang dimengerti [RelationTargetResolver]: selalu `"<kodeModul>:<entitas>"`.
 *
 * `FieldSpec.target` boleh `"entityId"` (modul sendiri) atau `"moduleId:entityId"` (lintas modul), sedangkan
 * resolver membaca bagian sebelum ':' sebagai **kode modul**. Tanpa penerjemahan ini target `"change_request"`
 * dianggap modul `change_request` dan SEMUA nilai ditolak (TRD-FIELD-004 §2.2). Parser tunggal: bentuk
 * rusak = galat keras ([relationTargetFormatError]), bukan dibiarkan jatuh ke modul lain.
 */
fun relationTargetResource(ownerModuleCode: String, target: String): String {
    val error = relationTargetFormatError(target)
    require(error == null) { "Target RELATION tidak sah ($error), dapat '$target'" }
    return if (':' in target) target else "$ownerModuleCode:$target"
}

/**
 * Keberadaan target semua field RELATION entitas ini untuk jalur TULIS server modul hasil generate
 * (TRD-FIELD-004 FR-2.1). Pasangan [fileOwnershipProblem]: reducer tenant-buta dan hanya memeriksa bentuk,
 * jadi route wajib memanggil ini SEBELUM reducer. Mengembalikan pesan untuk field pertama yang nilainya tidak
 * ada pada tenant [tenantId], atau `null`. Kosong = belum diisi (dilewati). Pesan **tidak membedakan**
 * "tidak ada" dan "milik tenant lain" (tanpa oracle keberadaan lintas tenant).
 */
suspend fun EntitySpec.relationTargetProblem(
    ownerModuleCode: String,
    tenantId: TenantId,
    values: Map<String, String>,
    resolver: RelationTargetResolver
): String? {
    for (f in fields) {
        if (f.type != FieldType.RELATION) continue
        val v = values[f.key].orEmpty()
        if (v.isBlank()) continue
        val resource = relationTargetResource(ownerModuleCode, requireNotNull(f.target) { "Field RELATION '${f.key}' tanpa target" })
        // Jangkauan eksplisit (TRD-FIELD-004 FR-3.2, tanpa default): target modul hasil generate hanya boleh ke
        // modul GLOBAL_ONLY (target HIERARCHICAL ditolak saat registrasi/generator, Q3), jadi jangkauannya
        // seluruh tenant (null). Mengizinkan target HIERARCHICAL berarti harus menghitung jangkauan pemanggil di sini.
        if (!resolver.exists(tenantId, resource, v, reachableOwnerIds = null)) return "'${f.label}' merujuk data yang tidak ditemukan."
    }
    return null
}
