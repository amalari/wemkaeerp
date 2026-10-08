package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.prototype.DateFieldValues
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.domain.prototype.TextValidations
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Kosakata parameter `withTime` (field `DATE`, C6) dan `validation` (field `TEXT`, C9) untuk model. Keduanya
 * **parameter** pada tipe yang ada, bukan tipe baru, jadi tidak masuk `FieldType`. Daftar validasi dibaca dari
 * `TextValidation.entries` dan catatannya dari `when` tanpa `else` (field-component-rules Kontrak 6); contoh nilai
 * dibaca dari `DateFieldValues.sample` / `TextValidations.sample` supaya tak menyimpang dari aturan produksi.
 * Batas yang berlaku (dari `ProposalEntityRules`): `withTime` hanya sah untuk DATE, `validation` hanya untuk TEXT
 * (bukan LONG_TEXT); nilai seed dinilai apa adanya, tanpa trim dan tanpa koersi.
 */
internal object KoogDiscoveryDateTimeValidationVocabulary {

    /** Daftar nama validasi, mis. "NONE|EMAIL|PHONE" — dibaca dari enum. */
    val validationNames: String get() = TextValidation.entries.joinToString("|") { it.name }

    /** Catatan per validasi untuk `screen_catalog.fieldParams.validations` (wajib ada tiap entri). */
    fun note(validation: TextValidation): String = when (validation) {
        TextValidation.NONE -> "tanpa pemeriksaan bentuk (bawaan bila `validation` dihilangkan): nama, kode, judul, " +
            "alamat jalan; alamat panjang/catatan pakai tipe LONG_TEXT"
        TextValidation.EMAIL -> "alamat surel; seed ditulis apa adanya, mis. \"${TextValidations.sample(TextValidation.EMAIL)}\" " +
            "(satu @, domain bertitik, tanpa spasi, maks ${TextValidations.EMAIL_MAX_LENGTH} karakter)"
        TextValidation.PHONE -> "nomor telepon/WhatsApp; seed ditulis apa adanya, mis. " +
            "\"${TextValidations.sample(TextValidation.PHONE)}\" (opsional `+` di awal, ${TextValidations.PHONE_MIN_DIGITS}–" +
            "${TextValidations.PHONE_MAX_DIGITS} digit, pemisah boleh spasi - ( ))"
    }

    /** Catatan parameter `withTime` untuk `screen_catalog.fieldParams.withTime`. */
    val withTimeNote: String
        get() = "hanya untuk type ${FieldType.DATE} (bawaan false = tanggal \"${DateFieldValues.sample(false)}\"). " +
            "true bila yang dicatat adalah waktu kejadian bermenit (jadwal, janji temu, jam masuk/keluar, waktu " +
            "kunjungan): seed ditulis \"${DateFieldValues.sample(true)}\" (YYYY-MM-DDTHH:MM, waktu dinding tanpa zona, " +
            "tanpa detik). false untuk tanggal kalender (tenggat, tanggal lahir, tanggal masuk); nilai tanggal-saja " +
            "pada field withTime (atau sebaliknya) ditolak"

    /** Objek untuk `screen_catalog.fieldParams`: catatan `withTime` + daftar `validation` per entri. */
    fun catalogJson(): JsonValue = jsonObjectOf(
        "withTime" to jsonOf(withTimeNote),
        "validations" to jsonArrayOf(
            TextValidation.entries.map { jsonObjectOf("name" to jsonOf(it.name), "note" to jsonOf(note(it))) }
        ),
        "fieldKeys" to jsonOf(
            "withTime (opsional boolean, bawaan false, hanya untuk type ${FieldType.DATE}) dan validation " +
                "(opsional ∈ {$validationNames}, bawaan ${TextValidation.NONE}, hanya untuk type ${FieldType.TEXT})"
        )
    )

    /** Aturan prompt (satu baris, disisipkan ke teks ber-indentasi). */
    val promptRule: String
        get() = "field DATE boleh membawa `withTime` (boolean, bawaan false; tipe selain DATE tidak boleh): true " +
            "untuk waktu kejadian bermenit seperti jadwal atau janji temu, seed ditulis \"${DateFieldValues.sample(true)}\", " +
            "false untuk tanggal kalender seperti tenggat atau tanggal lahir, seed \"${DateFieldValues.sample(false)}\"; " +
            "field TEXT boleh membawa `validation` ∈ {$validationNames} (bawaan ${TextValidation.NONE}; tipe selain " +
            "${FieldType.TEXT}, termasuk ${FieldType.LONG_TEXT}, tidak boleh): ${TextValidation.EMAIL} untuk alamat surel " +
            "(seed \"${TextValidations.sample(TextValidation.EMAIL)}\"), ${TextValidation.PHONE} untuk nomor telepon " +
            "(seed \"${TextValidations.sample(TextValidation.PHONE)}\"), ${TextValidation.NONE} untuk nama/kode/judul/alamat; " +
            "seed yang tak memenuhi bentuknya ditolak"
}
