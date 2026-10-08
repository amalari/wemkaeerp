package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.proposal.ProposalLimits
import com.eventverse.app.domain.discovery.proposal.SkeletonHint
import com.eventverse.app.domain.discovery.proposal.SkeletonWidth
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Kosakata kerangka `CUSTOM_SCREEN` (Irisan 3b, D5/D6) untuk model: bentuk `view`, daftar `hint`/`width`, dan batas.
 * Semuanya dibaca dari `ProposalLimits` dan enum `SkeletonHint`/`SkeletonWidth` — tidak ada angka atau daftar
 * yang disalin sebagai literal, jadi katalog dan prompt tidak bisa bergeser dari validator. Bukan tipe field,
 * jadi tidak masuk kosakata `FieldType` (field-component-rules); ini sketsa **non-interaktif**.
 */
internal object KoogDiscoverySkeletonVocabulary {

    private val hints: String get() = SkeletonHint.entries.joinToString("|") { it.name }
    private val widths: String get() = SkeletonWidth.entries.joinToString("|") { it.name }

    /** Bentuk `view` untuk `screen_catalog` (kolom `view` widget CUSTOM_SCREEN). */
    val viewShape: String
        get() = "{\"blocks\":[{\"label\":\"…\",\"width\":\"$widths\",\"hint\":\"$hints\"}]} " +
            "(opsional; 1–${ProposalLimits.BLOCKS} blok; tanpa view = tiga kotak generik)"

    /** Catatan pemakaian CUSTOM_SCREEN untuk `screen_catalog`. */
    val widgetNote: String
        get() = "hanya bila tak ada jenis yang cocok; boleh menyebut blok bernama lewat view.blocks " +
            "(mis. \"Keranjang\", \"Pembayaran\"); sketsa non-interaktif, bukan layar yang bisa dipakai"

    /** Objek `skeleton` di katalog: kosakata tertutup + batas. */
    fun catalogJson(): JsonValue = jsonObjectOf(
        "hints" to jsonArrayOf(SkeletonHint.entries.map { jsonOf(it.name) }),
        "widths" to jsonArrayOf(SkeletonWidth.entries.map { jsonOf(it.name) }),
        "maxBlocks" to jsonOf(ProposalLimits.BLOCKS),
        "maxLabelChars" to jsonOf(ProposalLimits.TEXT),
        "interactive" to jsonOf(false)
    )

    /** Aturan prompt: pengganti "CUSTOM_SCREEN null". */
    val promptRule: String
        get() = "CUSTOM_SCREEN {blocks:[{label,width?,hint?}]} atau null — blok memakai nama domain dari narasi " +
            "(mis. \"Keranjang\", \"Pembayaran\"), 1–${ProposalLimits.BLOCKS} blok, label unik maksimal " +
            "${ProposalLimits.TEXT} karakter, width ∈ {$widths} (bawaan ${SkeletonWidth.FULL}), " +
            "hint ∈ {$hints} (bawaan ${SkeletonHint.TABLE}); ini sketsa non-interaktif, bukan layar yang " +
            "bisa dipakai, jadi `entity` tetap null dan `seed` kosong"
}
