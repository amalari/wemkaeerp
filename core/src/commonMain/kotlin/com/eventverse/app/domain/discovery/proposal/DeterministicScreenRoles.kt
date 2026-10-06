package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.prototype.FieldType

/**
 * Tambahan field menurut **peran kemampuan** bawaan agent deterministik (pesanan, antrean, stok, tagihan) —
 * katalog yang sama dengan `DeterministicDiscoveryAgent`. Kuncinya akhiran kode slot (`klinik_tagihan` → `tagihan`);
 * slot tanpa peran dikenal hanya mendapat entitas minimal. Ini kosakata **peran lintas vertikal**, bukan
 * istilah satu industri: "tagihan" berarti hal yang sama di klinik, bengkel, maupun katering.
 */
internal object DeterministicScreenRoles {

    fun extrasFor(slotCode: String): List<FieldProposal> = when (slotCode.substringAfterLast('_')) {
        "pesanan" -> listOf(FieldProposal("tanggal", "Tanggal", FieldType.DATE))
        "antrean" -> listOf(FieldProposal("tanggal", "Tanggal", FieldType.DATE), FieldProposal("mendesak", "Mendesak", FieldType.BOOL))
        "stok" -> listOf(FieldProposal("jumlah", "Jumlah", FieldType.NUMBER))
        "tagihan" -> listOf(FieldProposal("jumlah", "Jumlah (Rp)", FieldType.NUMBER))
        else -> emptyList()
    }
}
