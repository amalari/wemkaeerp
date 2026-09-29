package com.eventverse.app.domain.pack

/**
 * Daftar vertikal yang dikirim platform. Vertikal baru = satu objek pack + satu baris di sini (B7).
 * Pemilihan pack per tenant belum ada (Discovery B0 Q2): semua tenant saat ini garment.
 */
object DomainPackRegistry {

    val all: List<DomainPack> by lazy { listOf(GarmentDomainPack.pack) }

    /** Kode tak dikenal → null. Pemanggil wajib menolak, bukan jatuh ke garment (Kontrak 4). */
    fun find(code: DomainPackCode): DomainPack? = all.firstOrNull { it.code == code }
}
