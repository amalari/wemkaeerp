package com.eventverse.app.infrastructure.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Menyimpan gambar mockup yang diekstrak dari dalam berkas Excel, mengembalikan URL yang bisa
 * dipakai UI.
 */
interface BenchmarkImageStorage {
    suspend fun store(tenantSlug: String, fileName: String, contentType: String, bytes: ByteArray): String?
}

/**
 * Penyimpanan ke folder lokal, di-serve sebagai berkas statis.
 *
 * ## Mengapa lokal, bukan langsung MinIO seperti berkas PO?
 * Impor arsip adalah operasi sekali jalan di mesin yang menjalankan script, sering kali laptop
 * operator tanpa kredensial S3. Memaksa S3 di sini berarti impor 100 berkas tidak bisa
 * dijalankan sampai infrastruktur objek storage siap — padahal nilai arsipnya justru ingin
 * dipakai hari itu juga. [BenchmarkImageStorage] tetap menjadi antarmuka sehingga adapter S3
 * bisa dipasang belakangan tanpa menyentuh use case.
 *
 * Nama berkas diturunkan dari **hash isinya**, bukan dari nama aslinya: dua berkas Excel yang
 * memuat mockup yang sama tidak menggandakan gambar, dan nama berkas Indonesia dengan spasi
 * serta tanda kurung tidak pernah bocor ke URL.
 */
class LocalBenchmarkImageStorage(
    private val rootDir: File = File(
        System.getenv("WEMADE_UPLOAD_DIR")?.takeIf { it.isNotBlank() } ?: "data/uploads"
    ),
    private val publicPathPrefix: String = "/uploads"
) : BenchmarkImageStorage {

    override suspend fun store(
        tenantSlug: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext null

        val extension = fileName.substringAfterLast('.', "").lowercase()
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
            ?: contentType.substringAfterLast('/', "png")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .take(16)
            .joinToString("") { byte -> "%02x".format(byte) }

        val tenantDir = File(rootDir, "benchmarks/$tenantSlug").apply { mkdirs() }
        val target = File(tenantDir, "$digest.$extension")
        if (!target.exists()) target.writeBytes(bytes)

        "$publicPathPrefix/benchmarks/$tenantSlug/$digest.$extension"
    }
}
