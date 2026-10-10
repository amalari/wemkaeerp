package com.eventverse.app.domain.storage

import kotlin.jvm.JvmInline

/**
 * Key objek berkas field (C8, TRD-FIELD-002 FR-2). Nilai sel `FILE` = string ini; byte hidup di
 * [ObjectStorage], TIDAK PERNAH di kolom/jsonb. Disusun hanya oleh server lewat [build] (`fileName`
 * dari klien tidak tepercaya); sisi klien/validator hanya memakai [isValid] — tanpa fallback.
 */
@JvmInline
value class FileRef private constructor(val value: String) {
    companion object {
        const val PREFIX = "fields/"

        /** Bentuk key sah: berawalan namespace `fields/`, tanpa `..`, tanpa awalan `/`, tanpa baris baru, ≤300 karakter. */
        fun isValid(raw: String): Boolean =
            raw.startsWith(PREFIX) && !raw.contains("..") && !raw.startsWith("/") &&
                raw.length <= 300 && raw.none { it == '\n' || it == '\r' }

        /**
         * Kepemilikan (hardening lintas tenant): [isValid] SAJA hanya memeriksa bentuk, sehingga tenant A bisa
         * menulis `fields/<tenantB>/...` ke selnya lalu meminta URL unduh objek tenant B. Ref sah untuk
         * [tenantId] hanya bila bentuknya sah DAN segmen pertama setelah `fields/` sama persis dengan
         * [tenantId] (perbandingan satu segmen utuh — tenant `abc` tidak cocok dengan `abcd`), diikuti
         * minimal segmen modul, record, dan nama. [moduleCode] diisi oleh rute yang tahu modulnya (unduh):
         * segmen kedua harus sama. [recordId] (TRD-FIELD-004 FR-1.3) mengikat ref ke record pemiliknya: segmen
         * ketiga harus sama, sehingga ref berkas record X tidak sah ditulis ke record Y. `null` hanya untuk
         * pemanggil domain yang memang tak punya record (tes bentuk); **semua rute server wajib mengisinya** —
         * alasan lama "alur record baru mengunggah dengan id sementara" gugur (klien hanya mengunggah untuk
         * record yang sudah ada; FILE pada create ditolak). Tanpa fallback ke [isValid]; [tenantId] kosong = tidak sah.
         */
        fun isValidFor(tenantId: String, raw: String, moduleCode: String? = null, recordId: String? = null): Boolean {
            if (tenantId.isBlank() || !isValid(raw)) return false
            val segments = raw.removePrefix(PREFIX).split('/')
            if (segments.size < 4 || segments.any { it.isEmpty() }) return false
            return segments[0] == tenantId &&
                (moduleCode == null || segments[1] == moduleCode) &&
                (recordId == null || segments[2] == recordId)
        }

        /**
         * Dipakai server saat menyusun key (bukan klien): `{PREFIX}{tenantId}/{moduleCode}/{recordId}/
         * {fieldKey}-{acak}-{fileName}` — ref yang tersimpan di sel SELALU berawalan `fields/` (kontrak
         * [isValid]); pemetaan ke layout bucket tenant-first (`{tenantId}/fields/...`, konvensi sweep
         * orphan FR-4) dikerjakan adapter penyimpanan di Track B. Deterministik per tenant supaya orphan
         * bisa disapu. Nama berkas disanitasi (path traversal, pemisah, kontrol) — gagal keras bila
         * bagian identitas kosong, bukan diam-diam jadi key lain.
         */
        fun build(tenantId: String, moduleCode: String, recordId: String, fieldKey: String, fileName: String): FileRef {
            require(tenantId.isNotBlank() && !tenantId.contains('/')) { "FileRef.build: tenantId tidak sah" }
            require(moduleCode.isNotBlank() && !moduleCode.contains('/')) { "FileRef.build: moduleCode tidak sah" }
            require(recordId.isNotBlank() && !recordId.contains('/')) { "FileRef.build: recordId tidak sah" }
            require(fieldKey.isNotBlank() && !fieldKey.contains('/')) { "FileRef.build: fieldKey tidak sah" }
            val safeName = sanitizeFileName(fileName)
            val random = kotlin.random.Random.nextInt(0, 0x1000000).toString(16).padStart(6, '0')
            val key = "$PREFIX$tenantId/$moduleCode/$recordId/$fieldKey-$random-$safeName"
            require(isValid(key)) { "FileRef.build: key hasil penyusunan tidak sah: $key" }
            return FileRef(key)
        }

        /** Nama berkas = input tidak tepercaya: buang pemisah path, `..`, dan karakter kontrol. */
        private fun sanitizeFileName(fileName: String): String {
            val cleaned = fileName.split('/', '\\').last()
                .replace("..", "")
                .filter { it.isLetterOrDigit() || it in "._- ()" }
                .trim()
                .take(120)
            return cleaned.ifBlank { "file" }
        }
    }
}
