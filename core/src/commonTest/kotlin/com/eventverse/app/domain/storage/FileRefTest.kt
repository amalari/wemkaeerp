package com.eventverse.app.domain.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit `FileRef` (C8, TRD-FIELD-002 FR-5 + risiko sanitasi nama): bentuk key adalah satu-satunya
 * integritas tipe FILE sebelum Track B — referensi rusak/buatan wajib ditolak, `fileName` dari klien
 * tidak tepercaya (path traversal, pemisah, kontrol) dan hanya [FileRef.build] yang menyusun key.
 */
class FileRefTest {

    // ---- isValid --------------------------------------------------------------------------------

    @Test
    fun isValid_acceptsWellFormedFieldKeys() {
        assertTrue(FileRef.isValid("fields/ten-bordir/pesanan_bordir/r-1/scan-a1b2c3-scan.pdf"))
        assertTrue(FileRef.isValid("fields/x/y/z/f"), "minimal: namespace + 3 segmen")
    }

    @Test
    fun isValid_rejectsBrokenOrForgedReferences() {
        listOf(
            "",                                  // kosong
            "scan.pdf",                          // tanpa namespace fields/
            "uploads/ten/r-1/scan.pdf",          // namespace salah
            "/fields/ten/r-1/scan.pdf",          // awalan absolut
            "fields/../ten/rahasia",             // path traversal
            "fields/ten/..%2f/rahasia",          // traversal terenkode manual tetap memuat ".."
            "fields/ten/r-1/baris\nbaru.pdf",    // kontrol karakter
            "fields/ten/r-1/carriage\rreturn",   // kontrol karakter
            "fields/${"a".repeat(300)}/r-1/scan.pdf" // melewati batas 300
        ).forEach { raw ->
            assertTrue(!FileRef.isValid(raw), "'${raw.take(40)}' harus ditolak")
        }
    }

    // ---- build ----------------------------------------------------------------------------------

    @Test
    fun build_producesDeterministicNamespacePerTenant_andValidRef() {
        val ref = FileRef.build("ten-bordir", "pesanan_bordir", "r-1", "lampiran", "scan.pdf")
        assertTrue(ref.value.startsWith("fields/ten-bordir/pesanan_bordir/r-1/lampiran-"), ref.value)
        assertTrue(ref.value.endsWith("-scan.pdf"), ref.value)
        assertTrue(FileRef.isValid(ref.value), "key hasil build wajib lolos isValid sendiri")
    }

    @Test
    fun build_sanitizesHostileFileNames_neverEscapesNamespace() {
        listOf(
            "../../etc/passwd",
            "a/b/c.pdf",
            "back\\slash.pdf",
            "..\n../evil.pdf",
            "   ",
            "",
            "nama..aneh!!!?.PDF"
        ).forEach { hostile ->
            val ref = FileRef.build("ten", "modul", "r-1", "f", hostile)
            assertTrue(FileRef.isValid(ref.value), "hostile '$hostile' -> ${ref.value} harus tetap sah")
            assertTrue(!ref.value.contains(".."), ref.value)
            assertTrue(!ref.value.contains('\\'), ref.value)
        }
        assertEquals("file", FileRef.build("ten", "modul", "r-1", "f", "").value.substringAfterLast('/').substringAfter('-').substringAfter('-'), "nama kosong jadi 'file'")
    }

    @Test
    fun build_rejectsBlankOrSlashIdentitySegments_failClosed() {
        listOf(
            listOf("", "modul", "r-1", "f", "a.pdf"),
            listOf("ten", "", "r-1", "f", "a.pdf"),
            listOf("ten", "mo/dul", "r-1", "f", "a.pdf"),
            listOf("ten", "modul", "", "f", "a.pdf"),
            listOf("ten", "modul", "r/1", "f", "a.pdf"),
            listOf("ten", "modul", "r-1", "", "a.pdf"),
            listOf("ten", "modul", "r-1", "f/", "a.pdf")
        ).forEach { args ->
            val error = assertFailsWith<IllegalArgumentException>("args ${args.take(4)} harus ditolak") {
                FileRef.build(args[0], args[1], args[2], args[3], args[4])
            }
            assertTrue(error.message.orEmpty().startsWith("FileRef.build"), error.message)
        }
    }
}
