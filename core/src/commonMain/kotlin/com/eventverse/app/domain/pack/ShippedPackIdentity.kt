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
    // Deskripsi modul yang pernah diganti platform (teks lama tercatat di [SupersededModuleText]) tetap diterima.
    val modules = modules.map { m -> if (m.description in SupersededModuleText.descriptions[m.id].orEmpty()) m.copy(description = shipped.module(m.id)?.description ?: m.description) else m }
    val current = copy(modules = modules)
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
 * Deskripsi modul bawaan yang **pernah diganti** platform. Draf yang tersimpan sebelum penggantian membawa teks lama;
 * tanpa catatan ini ia ditolak "wajib identik". Hanya teks yang tercatat di sini yang ditoleransi — deskripsi lain
 * yang berbeda tetap dianggap penulisan ulang. Tambahkan teks lama setiap kali deskripsi modul bawaan diubah.
 */
internal object SupersededModuleText {
    val descriptions: Map<ModuleId, Set<String>> = mapOf(
        GarmentModules.INVOICING to setOf("Penerbitan faktur tagihan sample, termin DP, dan pelunasan garmen berkanvas.")
    )
}
