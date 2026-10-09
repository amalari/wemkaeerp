package com.eventverse.app.domain.orgchart

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.VocabularyKey

/**
 * Predikat "pack ini punya **contoh bagan organisasi**" (TRD-PLAT-010 K3).
 *
 * Contoh yang ada hari ini ([Department.defaultPresets], [OrgNode.createSampleEmployees]) adalah divisi dan karyawan
 * **konveksi**. Karena itu ia hanya sah dimuat ke tenant ber-pack garment; menulisnya ke tenant klinik/bordir
 * menanam divisi industri lain ke database mereka.
 *
 * **Jembatan Strangler yang dinyatakan, bukan tebakan**: kelak contoh menjadi data pack (`DomainPack.starterOrgChart`,
 * T4 — ditunda, Q5) dan predikat ini cukup menjadi "pack punya contoh". Sampai saat itu hanya pack bawaan garment
 * yang menyediakannya; pack lain (termasuk hasil handoff) **tidak punya contoh** dan permintaan memuatnya ditolak.
 * Hanya menjawab "apakah ada contoh"; wewenang pemanggil diperiksa lebih dulu oleh lapisan route (fail-closed).
 */
object StarterOrgChartPolicy {

    fun isAvailableFor(pack: DomainPack): Boolean = pack.code == GarmentDomainPack.CODE

    /** Pesan penolakan berkosakata pack (Q7): memakai tempat kerja milik pack, kata netral bila pack tak menyebutnya. */
    fun unavailableMessage(pack: DomainPack): String =
        "Jenis usaha ini belum punya contoh bagan organisasi untuk ${pack.term(VocabularyKey.WORKPLACE)}. " +
            "Tambahkan divisi dan karyawan secara manual."
}
