package com.eventverse.app.domain.pack

/**
 * Resolver tema visual pack (TRD-PLAT-013, T5).
 *
 * Satu-satunya titik yang menjawab "tema apa yang dipakai sebuah pack". Service **murni** tanpa I/O:
 * pemanggil (server maupun klien) menyerahkan [DomainPack] yang sudah di-resolve lewat
 * `ResolveDomainPackUseCase`, jadi kedua sisi tidak mungkin berbeda pendapat soal tema.
 *
 * ## Kebijakan fallback — bukan fallback senyap ke pack lain
 * - Pack yang tidak menyatakan tema (`theme == null`, termasuk pack lama sebelum TRD ini) memakai [default].
 * - Pack tak dikenal (`null`) memakai [default] yang sama.
 *
 * [default] adalah nilai netral milik **platform** ([PackTheme] bawaan = CLAY), bukan tema pack garment
 * atau pack vertikal mana pun. Menyalin tema vertikal lain ke pack yang tidak menyatakan temanya akan
 * mengubah tampilan tenant tanpa jejak (tenant-variability-rules Kontrak 4).
 */
object PackThemeResolver {

    /** Tema netral platform; dipakai pack yang tidak menyatakan tema dan sama untuk semua vertikal. */
    val default: PackTheme = PackTheme()

    /**
     * Tema efektif [pack].
     *
     * @param pack pack tenant hasil resolusi, atau `null` bila pack tak dikenal.
     * @return tema yang dinyatakan [pack], atau [default] (CLAY) bila pack tidak menyatakannya.
     */
    fun resolve(pack: DomainPack?): PackTheme = pack?.theme ?: default
}
