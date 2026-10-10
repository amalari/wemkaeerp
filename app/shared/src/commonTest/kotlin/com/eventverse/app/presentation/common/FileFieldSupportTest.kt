package com.eventverse.app.presentation.common

import com.eventverse.app.domain.prototype.FieldSpec
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.infrastructure.api.FieldFileHttpException
import com.eventverse.app.presentation.discovery.fields.displayValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Kontrak kecil yang dipertaruhkan tipe field `FILE` (TRD-FIELD-002 Track C): nama tampil dari
 * ref, batas ukuran, dan pemetaan galat unggah ke pesan manusiawi. Murni — tanpa Compose.
 */
class FileFieldSupportTest {

    @Test
    fun displayName_usesLastRefSegment() {
        assertEquals(
            "lampiran-a1b2c3-scan.pdf",
            fileRefDisplayName("fields/ten/item-1/lampiran-a1b2c3-scan.pdf")
        )
    }

    @Test
    fun displayName_fallsBackToRawWhenNoSlash() {
        assertEquals("akuntansi", fileRefDisplayName("akuntansi"))
    }

    @Test
    fun formatSize_switchesUnits() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("2 KB", formatFileSize(2048))
        assertEquals("10 MB", formatFileSize(10L * 1024 * 1024))
        assertEquals("1,5 MB", formatFileSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun errorMapping_coversFailClosedStatuses() {
        // 413: batas dimiliki server — pesan server dipakai bila ada, kalau tidak pakai batas kontrak.
        assertEquals(
            "payload terlalu besar dari server",
            fieldFileErrorMessage(FieldFileHttpException(413, "payload terlalu besar dari server"))
        )
        assertEquals(
            "Ukuran berkas melebihi batas 10 MB.",
            fieldFileErrorMessage(FieldFileHttpException(413, "   "))
        )
        assertEquals(
            "Tipe berkas tidak didukung - gunakan PDF, PNG, JPEG, WebP, TXT, atau CSV.",
            fieldFileErrorMessage(FieldFileHttpException(415, "unsupported"))
        )
        assertEquals(
            "Penyimpanan berkas belum siap di server (env S3 belum diatur). Hubungi admin.",
            fieldFileErrorMessage(FieldFileHttpException(503, "storage off"))
        )
        assertEquals(
            "Anda tidak berwenang memproses berkas pada data ini.",
            fieldFileErrorMessage(FieldFileHttpException(403, "nope"))
        )
        assertEquals(
            "Data atau berkas tidak ditemukan - mungkin sudah dihapus.",
            fieldFileErrorMessage(FieldFileHttpException(404, "gone"))
        )
    }

    @Test
    fun sizeGuard_blocksOnlyOverLimit() {
        assertNull(fieldFileClientSizeError(10L * 1024 * 1024))
        assertEquals(
            "Berkas 12 MB melebihi batas 10 MB.",
            fieldFileClientSizeError(12L * 1024 * 1024)
        )
    }

    @Test
    fun errorMapping_passesThroughNonFileThrowable() {
        assertEquals("koneksi putus", fieldFileErrorMessage(IllegalStateException("koneksi putus")))
    }

    @Test
    fun prototypeDisplayValue_showsFileNameNotRef() {
        val file = FieldSpec(key = "lampiran", type = FieldType.FILE, label = "Lampiran")
        assertEquals(
            "lampiran-a1b2c3-scan.pdf",
            file.displayValue("fields/ten/item-1/lampiran-a1b2c3-scan.pdf")
        )
        // Kosong tetap kosong (belum diisi) — bukan placeholder palsu.
        assertEquals("", file.displayValue(""))
    }
}
