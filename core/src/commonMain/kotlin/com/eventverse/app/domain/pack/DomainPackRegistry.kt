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
    val soleActivePack: DomainPack get() = testOverride ?: GarmentDomainPack.pack

    private var testOverride: DomainPack? = null

    /**
     * **Hanya untuk test** (B6g): menjalankan [block] seolah [pack] adalah vertikal yang aktif — bukti bahwa modul pack
     * lain muncul di menu & tergerbang tanpa menyentuh kode inti. Dihapus saat B7 memberi resolusi pack per tenant.
     * Tidak aman dipakai paralel; test JVM di repo ini berjalan berurutan.
     */
    fun <T> withSoleActivePackForTest(pack: DomainPack, block: () -> T): T {
        val previous = testOverride
        testOverride = pack
        try { return block() } finally { testOverride = previous }
    }

    /** Kode tak dikenal → null. Pemanggil wajib menolak, bukan jatuh ke garment (Kontrak 4). */
    fun find(code: DomainPackCode): DomainPack? = all.firstOrNull { it.code == code }
}
