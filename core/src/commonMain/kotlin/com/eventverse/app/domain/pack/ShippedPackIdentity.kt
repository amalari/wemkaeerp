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
    fun <T> additive(draft: T, platform: T, isEmpty: (T) -> Boolean) = draft == platform || isEmpty(draft)
    if (!additive(roleHints, shipped.roleHints) { it.isEmpty() }) return false
    if (!additive(reservedTerms, shipped.reservedTerms) { it.isEmpty() }) return false
    if (!additive(sharedModules, shipped.sharedModules) { it.isEmpty() }) return false
    return copy(roleHints = shipped.roleHints, reservedTerms = shipped.reservedTerms, sharedModules = shipped.sharedModules) == shipped
}
