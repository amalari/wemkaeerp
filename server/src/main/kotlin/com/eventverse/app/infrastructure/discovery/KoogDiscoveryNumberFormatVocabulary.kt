package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Kosakata format angka (C4, keputusan D3) untuk model: `format` adalah **parameter** pada field `NUMBER`, bukan
 * tipe baru, sehingga tidak masuk daftar `FieldType`. Daftar format dibaca dari `NumberFormat.entries` dan catatan
 * dari `when` tanpa `else` (field-component-rules Kontrak 6). Batas yang sungguh berlaku (dari
 * `ProposalEntityRules`): `format` selain PLAIN hanya sah pada NUMBER; `currencyCode` wajib tepat bila CURRENCY
 * (tiga huruf besar `[A-Z]{3}`, mata uang per field, tanpa daftar tertutup) dan wajib null selain itu. Penyimpanan
 * tetap string angka polos; PERCENT disimpan sebagai angka persen apa adanya ("12.5" = 12,5%).
 */
internal object KoogDiscoveryNumberFormatVocabulary {

    /** Daftar nama format, mis. "PLAIN|CURRENCY|PERCENT" — dibaca dari enum. */
    val names: String get() = NumberFormat.entries.joinToString("|") { it.name }

    /** Catatan per format untuk `screen_catalog.numberFormats` (wajib ada tiap format). */
    fun note(format: NumberFormat): String = when (format) {
        NumberFormat.PLAIN -> "angka biasa (bawaan bila `format` dihilangkan): kuantitas, jumlah, urutan; " +
            "`currencyCode` harus null"
        NumberFormat.CURRENCY -> "nominal uang (harga, tarif, tagihan, saldo); wajib `currencyCode` tiga huruf besar " +
            "(mis. IDR, USD) — IDR bila narasi Indonesia tak menyebut mata uang lain; di seed ditulis angka polos " +
            "\"250000\""
        NumberFormat.PERCENT -> "persentase (diskon, pajak, tarif, progres); nilai disimpan sebagai angka persen " +
            "apa adanya, \"12.5\" berarti 12,5%, bukan 0,125; `currencyCode` harus null"
    }

    /** Objek untuk `screen_catalog`: nama + catatan per format, dan bentuk kunci di field usulan. */
    fun catalogJson(): JsonValue = jsonObjectOf(
        "formats" to jsonArrayOf(
            NumberFormat.entries.map {
                jsonObjectOf("name" to jsonOf(it.name), "note" to jsonOf(note(it)))
            }
        ),
        "fieldKeys" to jsonOf(
            "format (opsional, bawaan ${NumberFormat.PLAIN}, hanya untuk type NUMBER) dan " +
                "currencyCode (wajib tepat bila format ${NumberFormat.CURRENCY}: tiga huruf besar; selain itu null)"
        )
    )

    /** Aturan prompt (satu baris, disisipkan ke teks ber-indentasi). */
    val promptRule: String
        get() = "field NUMBER boleh membawa `format` ∈ {$names} (bawaan ${NumberFormat.PLAIN}; tipe selain NUMBER " +
            "tidak boleh berformat): ${NumberFormat.CURRENCY} untuk nominal uang dan WAJIB disertai `currencyCode` " +
            "tiga huruf besar seperti IDR atau USD (IDR bila narasi Indonesia tak menyebut mata uang lain), " +
            "${NumberFormat.PERCENT} untuk persentase/tarif/diskon (seed ditulis \"12.5\" untuk 12,5%), " +
            "${NumberFormat.PLAIN} untuk kuantitas; `currencyCode` selain pada ${NumberFormat.CURRENCY} harus null"
}
