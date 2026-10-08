package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.NumberFormat

/**
 * Tambahan field menurut **peran kemampuan** bawaan agent deterministik (pesanan, antrean, stok, tagihan) —
 * katalog yang sama dengan `DeterministicDiscoveryAgent`. Kuncinya akhiran kode slot (`klinik_tagihan` → `tagihan`);
 * slot tanpa peran dikenal hanya mendapat entitas minimal. Ini kosakata **peran lintas vertikal**, bukan
 * istilah satu industri: "tagihan" berarti hal yang sama di klinik, bengkel, maupun katering.
 */
internal object DeterministicScreenRoles {

    /**
     * [defaultCurrencyCode] = `DomainPack.defaultCurrencyCode`, **sumber nilai awal** usulan saja: setelah masuk dokumen,
     * kode itu milik field (template disalin, dokumen membeku — mengubah bawaan pack tidak mengubah usulan yang sudah
     * tersimpan). Peran "tagihan" memang menyatakan jumlah uang, jadi `jumlah`-nya berformat CURRENCY; peran lain
     * tetap PLAIN — tidak ada tebakan dari nama field.
     */
    fun extrasFor(slotCode: String, defaultCurrencyCode: String): List<FieldProposal> = when (slotCode.substringAfterLast('_')) {
        "pesanan" -> listOf(FieldProposal("tanggal", "Tanggal", FieldType.DATE))
        "antrean" -> listOf(FieldProposal("tanggal", "Tanggal", FieldType.DATE), FieldProposal("mendesak", "Mendesak", FieldType.BOOL))
        "stok" -> listOf(FieldProposal("jumlah", "Jumlah", FieldType.NUMBER))
        "tagihan" -> listOf(FieldProposal("jumlah", "Jumlah", FieldType.NUMBER, format = NumberFormat.CURRENCY, currencyCode = defaultCurrencyCode))
        else -> emptyList()
    }
}
