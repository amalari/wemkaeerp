package com.eventverse.app.domain.prototype

/**
 * Asal data satu layar prototype (kontrak antar-agent, plan induk §3.2):
 *  - [Memory] — isi layar hidup di store memori sesi (demo garment); ini bawaan agar spec/pack lama
 *    tanpa kunci binding tetap terbaca (kompatibel mundur).
 *  - [Api] — isi layar dimuat/ditulis ke endpoint CRUD modul (jalur MVP); [Api.basePath] adalah basis
 *    URL yang sudah dikontrak route hasil generate (`GET/POST basePath`, `PUT/DELETE basePath/{id}`).
 *
 * Kosakata ini milik **sistem** (kode, bukan data tenant): pilihan memori-vs-API menentukan jalur
 * teknis penyimpanan, bukan istilah vertikal mana pun.
 */
sealed interface DataBinding {
    /** Data demo di memori: seed di [InteractiveScreen], hilang saat sesi berakhir. */
    data object Memory : DataBinding

    /** Data di server lewat CRUD modul; [basePath] wajib terisi (ditolak, bukan ditebak). */
    data class Api(val basePath: String) : DataBinding {
        init { require(basePath.isNotBlank()) { "DataBinding.Api.basePath kosong" } }
    }
}
