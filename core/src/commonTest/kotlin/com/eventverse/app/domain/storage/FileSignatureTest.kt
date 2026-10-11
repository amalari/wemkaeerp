package com.eventverse.app.domain.storage

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSignatureTest {
    @Test
    fun `binary signatures accept matching content and reject disguised content`() {
        assertTrue(FileSignature.matches("application/pdf", "%PDF-1.7".encodeToByteArray()))
        assertTrue(FileSignature.matches("image/png", bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
        assertTrue(FileSignature.matches("image/jpeg", bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertTrue(FileSignature.matches("image/webp", "RIFF0000WEBPVP8 ".encodeToByteArray()))

        assertFalse(FileSignature.matches("image/png", "<html>".encodeToByteArray()))
        assertFalse(FileSignature.matches("application/pdf", bytes(0x25, 0x50, 0x44, 0x46)))
        assertFalse(FileSignature.matches("image/webp", "RIFF0000NOTP".encodeToByteArray()))
    }

    @Test
    fun `text requires valid utf8 without nul bytes`() {
        assertTrue(FileSignature.matches("text/plain", "catatan aman".encodeToByteArray()))
        assertTrue(FileSignature.matches("text/csv", "nama,jumlah\nKain,2".encodeToByteArray()))
        assertFalse(FileSignature.matches("text/plain", byteArrayOf('a'.code.toByte(), 0, 'b'.code.toByte())))
        assertFalse(FileSignature.matches("text/csv", bytes(0xC3, 0x28)))
        assertFalse(FileSignature.matches("application/octet-stream", "data".encodeToByteArray()))
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
