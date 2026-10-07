package com.eventverse.app.domain.pack

/**
 * Semua vertikal yang dikenal proses ini (B7, TRD-PLAT-001-tenant-pack):
 * - [shipped]: pack yang dikirim sebagai kode (garment);
 * - pack **data**: dimuat dari tabel `domain_packs` (server) atau `GET /api/tenant/pack` (klien) lewat [register].
 *
 * **Identitas global** (FR-1): `ModuleId` dan `SlotCode` berarti hal yang sama di seluruh platform, sehingga definisi
 * modul dapat dicari tanpa konteks tenant ([moduleDefinition]). Konsekuensinya:
 * - id yang muncul di lebih dari satu pack (modul platform seperti `org_chart`) wajib punya definisi **identik**;
 * - modul & slot **baru** di pack data wajib berprefiks `<kode pack>_`, supaya tidak bisa merebut id pack lain.
 *
 * Pertanyaan milik tenant (modul apa saja yang ada, fase kanvas, port) dijawab oleh pack tenant itu, bukan registry ini.
 */
object DomainPackRegistry {

    val shipped: List<DomainPack> by lazy { listOf(GarmentDomainPack.pack) }

    /** Copy-on-write: pembaca tidak pernah melihat peta setengah jadi. Penulis diserialkan pemanggil (cache server). */
    private var loaded: Map<DomainPackCode, DomainPack> = emptyMap()

    val all: List<DomainPack> get() = shipped + loaded.values

    /** Kode tak dikenal → null. Pemanggil wajib menolak, bukan jatuh ke garment (Kontrak 4). */
    fun find(code: DomainPackCode): DomainPack? = all.firstOrNull { it.code == code }

    fun isShipped(code: DomainPackCode): Boolean = shipped.any { it.code == code }

    /**
     * Mendaftarkan (atau mengganti) pack data. Ditolak bila melanggar identitas global. [violations] juga dipakai
     * sebelum pack disimpan, supaya pack rusak gagal saat ditulis, bukan saat dipakai.
     */
    fun register(pack: DomainPack) {
        violations(pack).firstOrNull()?.let { throw IllegalArgumentException(it) }
        loaded = loaded + (pack.code to pack)
    }

    /** Hanya untuk test: melepas pack data yang didaftarkan test. */
    fun unregister(code: DomainPackCode) {
        loaded = loaded - code
    }

    /** Pelanggaran [pack] untuk registri: identitas global **dan** rujukan modul bersama (B6). Kosong = sah. */
    fun violations(pack: DomainPack): List<String> =
        identityViolations(pack) + ModuleReferenceRules.validate(pack).map { "${it.path}: ${it.message}" }

    /** Pelanggaran identitas global [pack] terhadap pack lain yang dikenal (tanpa rujukan); kosong = sah. */
    fun identityViolations(pack: DomainPack): List<String> {
        if (isShipped(pack.code)) return listOf("Kode ${pack.code.value} milik pack bawaan platform")
        val others = all.filter { it.code != pack.code }
        val prefix = "${pack.code.value.lowercase()}_"
        val out = mutableListOf<String>()
        pack.modules.forEach { m ->
            val existing = others.firstNotNullOfOrNull { it.module(m.id) }
            if (existing != null && SupersededModuleText.normalized(existing) != SupersededModuleText.normalized(m)) out += "Modul ${m.id.value} sudah dipakai pack lain dengan definisi berbeda"
            if (existing == null && !m.id.value.startsWith(prefix)) out += "Modul baru ${m.id.value} wajib berprefiks '$prefix'"
        }
        pack.slots.forEach { s ->
            val existing = others.firstNotNullOfOrNull { it.slot(s.code) }
            if (existing != null && existing != s) out += "Slot ${s.code.value} sudah dipakai pack lain dengan definisi berbeda"
            if (existing == null && !s.code.value.lowercase().startsWith(prefix)) out += "Slot baru ${s.code.value} wajib berprefiks '$prefix'"
        }
        return out
    }

    /** Definisi modul di pack mana pun (identik lintas pack). */
    fun moduleDefinition(id: ModuleId): ModuleDefinition? = all.firstNotNullOfOrNull { it.module(id) }

    /** Pack pertama yang memuat [id]; seksi modul bersama diambil dari pack bawaan. */
    fun ownerOf(id: ModuleId): DomainPack? = all.firstOrNull { it.module(id) != null }

    fun slotDefinition(code: SlotCode): SlotDefinition? = all.firstNotNullOfOrNull { it.slot(code) }

    fun ownerOf(code: SlotCode): DomainPack? = all.firstOrNull { it.slot(code) != null }
}
