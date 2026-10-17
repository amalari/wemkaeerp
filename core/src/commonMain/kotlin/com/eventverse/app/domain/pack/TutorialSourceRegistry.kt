package com.eventverse.app.domain.pack

import com.eventverse.app.domain.tutorial.ModuleTutorial
import com.eventverse.app.domain.tutorial.TutorialAnchorId

/**
 * Sumber tutorial satu pack. Implementasi dikirim bersama rilis pack (shipped) atau
 * didaftarkan oleh pack data/tenant (J3).
 *
 * Mirip [TutorialSource] tetapi bekerja dengan [DomainPack] lengkap, bukan hanya kode.
 */
interface PackTutorialSource {
    /** Tutorial yang dimiliki pack ini. */
    fun tutorialsFor(pack: DomainPack): List<ModuleTutorial>

    /** Anchor UI yang dideklarasikan pack ini; langkah tutorial hanya boleh merujuk anchor di sini. */
    fun anchorsFor(pack: DomainPack): Set<TutorialAnchorId>
}

/**
 * Fallback untuk pack yang tidak mendaftarkan tutorial source — hanya dapat tutorial platform.
 * Ini menjaga perilaku aman untuk pack dari DB yang tidak dikenal [PackTutorialSource]-nya.
 */
object EmptyPackTutorialSource : PackTutorialSource {
    override fun tutorialsFor(pack: DomainPack): List<ModuleTutorial> = emptyList()
    override fun anchorsFor(pack: DomainPack): Set<TutorialAnchorId> = emptySet()
}

/**
 * Registry yang memetakan [DomainPackCode] ke [PackTutorialSource].
 *
 * Paket shipped (garment) dan pack data/tenant (mis. layanan) mendaftarkan sourcenya di sini.
 * Pack yang tidak terdaftar mendapat [EmptyPackTutorialSource] — hanya tutorial platform.
 *
 * Pendaftaran dilakukan saat modul di-load (shipped) atau saat kontribusi tenant dipasang (J3).
 */
object TutorialSourceRegistry {
    private val sources = mutableMapOf<DomainPackCode, PackTutorialSource>()

    /**
     * Mendapatkan [PackTutorialSource] untuk pack tertentu.
     * Pack yang tidak terdaftar → [EmptyPackTutorialSource].
     */
    fun forPack(pack: DomainPack): PackTutorialSource = sources[pack.code] ?: EmptyPackTutorialSource

    /**
     * Mendaftarkan [PackTutorialSource] untuk kode pack tertentu.
     * Dipanggil saat modul di-load atau saat kontribusi tenant dipasang.
     */
    fun register(code: DomainPackCode, source: PackTutorialSource) {
        sources[code] = source
    }

    /**
     * Semua kode pack yang punya tutorial source terdaftar (selain platform).
     */
    fun registeredPackCodes(): Set<DomainPackCode> = sources.keys.toSet()
}
