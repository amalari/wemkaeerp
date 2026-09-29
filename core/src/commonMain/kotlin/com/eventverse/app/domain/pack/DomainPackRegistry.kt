package com.eventverse.app.domain.pack

/**
 * Daftar vertikal yang dikirim platform. Vertikal baru = satu objek pack + satu baris di sini (B7).
 * Pemilihan pack per tenant belum ada (Discovery B0 Q2): semua tenant saat ini garment.
 */
object DomainPackRegistry {

    val all: List<DomainPack> by lazy { listOf(GarmentDomainPack.pack) }

    /**
     * Pack yang berlaku untuk seluruh tenant **sampai B7** (pemilihan pack per tenant). Nama ini
     * sengaja bukan `default`: ia bukan fallback kunci tersimpan, melainkan satu-satunya vertikal
     * yang dijalankan. B7 mengganti setiap pemakaiannya dengan resolusi pack tenant.
     */
    val soleActivePack: DomainPack get() = GarmentDomainPack.pack

    /** Kode tak dikenal → null. Pemanggil wajib menolak, bukan jatuh ke garment (Kontrak 4). */
    fun find(code: DomainPackCode): DomainPack? = all.firstOrNull { it.code == code }
}
