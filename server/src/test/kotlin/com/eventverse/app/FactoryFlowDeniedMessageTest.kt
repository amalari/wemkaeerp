package com.eventverse.app

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.AccessSource
import com.eventverse.app.routes.factoryFlowDeniedMessage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * TRD-PLAT-012 Q2: pesan 403 Alur Pabrik harus memberi konteks **dan** tindakan, bukan sekadar
 * "akses ditolak". Fungsi murni [factoryFlowDeniedMessage] diuji di sini tanpa HTTP supaya kata
 * "butuh apa, minta ke siapa" tidak bisa menghilang diam-diam di kemudian hari.
 */
class FactoryFlowDeniedMessageTest {

    private val pesan = factoryFlowDeniedMessage(
        required = AccessLevel.VIEW,
        current = AccessLevel.NONE,
        source = AccessSource.ROLE
    )

    @Test
    fun mentionsWhatIsRequiredAndWhatTheCallerHas() {
        assertTrue(pesan.contains(AccessLevel.VIEW.displayName), "level yang dibutuhkan tak disebut: $pesan")
        assertTrue(pesan.contains(AccessLevel.NONE.displayName), "level pemanggil tak disebut: $pesan")
        assertTrue(pesan.contains(AccessSource.ROLE.label), "sumber wewenang tak disebut: $pesan")
    }

    @Test
    fun tellsTheCallerHowToAskForAccess() {
        assertTrue(pesan.contains("Hak Akses (RBAC)"), "cara meminta akses tak dijelaskan: $pesan")
        assertTrue(pesan.contains("pemilik pabrik"), "ke siapa meminta tak jelas: $pesan")
    }

    @Test
    fun avoidsInternalJargonAndStaysLatin1() {
        assertTrue(!pesan.contains("modul", ignoreCase = true), "istilah internal 'modul' bocor: $pesan")
        assertTrue(pesan.all { it.code in 32..126 }, "pesan harus Latin-1/ASCII agar tampil di Nunito: $pesan")
    }
}
