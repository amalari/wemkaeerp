package com.eventverse.app.domain.pack

/**
 * Identitas pack bawaan untuk validator draf: dokumen berkode pack bawaan wajib **identik** dengan yang dikirim
 * platform — kecuali **kolom aditif opsional** yang ditambahkan setelah draf lama tersimpan ([DomainPack.roleHints],
 * [DomainPack.reservedTerms], [DomainPack.sharedModules]). Untuk kolom itu, draf boleh membawa nilai yang sama persis
 * dengan pack bawaan **atau kosong** (draf lama yang belum mengenalnya). Nilai lain = ditulis ulang → ditolak.
 *
 * Tanpa ini, setiap draf garment yang tersimpan sebelum sebuah kolom aditif ditambahkan akan ditolak "wajib identik"
 * saat direvisi (regresi nyata yang ditemukan setelah B2/B5). Kolom yang mengubah makna dokumen (modul, slot, port,
 * rujukan modul) **tidak** termasuk dan tetap harus identik.
 */
fun DomainPack.matchesShipped(shipped: DomainPack): Boolean {
    // Nama/deskripsi modul yang pernah diganti platform (teks lama tercatat di [SupersededModuleText]) tetap diterima.
    val current = copy(modules = modules.map(SupersededModuleText::normalized))
    return current.matchesShippedAdditive(shipped)
}

private fun DomainPack.matchesShippedAdditive(shipped: DomainPack): Boolean {
    fun <T> additive(draft: T, platform: T, isEmpty: (T) -> Boolean) = draft == platform || isEmpty(draft)
    if (!additive(roleHints, shipped.roleHints) { it.isEmpty() }) return false
    if (!additive(reservedTerms, shipped.reservedTerms) { it.isEmpty() }) return false
    if (!additive(sharedModules, shipped.sharedModules) { it.isEmpty() }) return false
    return copy(roleHints = shipped.roleHints, reservedTerms = shipped.reservedTerms, sharedModules = shipped.sharedModules) == shipped
}

/**
 * Nama dan deskripsi modul bawaan yang **pernah diganti** platform. Dokumen yang tersimpan sebelum penggantian
 * (draf garment, pack non-garment yang menyalin modul bersama) membawa teks lama; tanpa catatan ini ia ditolak
 * "wajib identik" / "definisi berbeda". Hanya teks yang tercatat di sini yang ditoleransi — teks lain yang berbeda
 * tetap dianggap penulisan ulang. **Tambahkan teks lama setiap kali nama/deskripsi modul bawaan diubah.**
 */
internal object SupersededModuleText {
    val descriptions: Map<ModuleId, Set<String>> = mapOf(
        GarmentModules.INVOICING to setOf("Penerbitan faktur tagihan sample, termin DP, dan pelunasan garmen berkanvas."),
        GarmentModules.ORG_CHART to setOf("Struktur divisi, jenjang jabatan, dan data karyawan pabrik."),
        GarmentModules.VENDOR_CONTACTS to setOf("Buku kontak vendor subkon, daftar harga layanan per vendor, dan penunjukan vendor ke proses Vendor Luar.")
    )
    val names: Map<ModuleId, Set<String>> = mapOf(
        GarmentModules.VENDOR_CONTACTS to setOf("Kontak Vendor & Makloon")
    )

    /** [m] dengan nama/deskripsi lama yang tercatat diganti teks bakunya sekarang; selain itu apa adanya. */
    fun normalized(m: ModuleDefinition): ModuleDefinition {
        val current = DomainPackRegistry.shipped.firstNotNullOfOrNull { it.module(m.id) } ?: return m
        return m.copy(
            displayName = if (m.displayName in names[m.id].orEmpty()) current.displayName else m.displayName,
            description = if (m.description in descriptions[m.id].orEmpty()) current.description else m.description
        )
    }
}
