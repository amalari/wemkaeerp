package com.eventverse.app.domain.prototype

/**
 * Validasi **bentuk** teks untuk [FieldType.TEXT] (A0(C9) Irisan 2). Parameter pada tipe yang ada, bukan tipe baru:
 * penyimpanan, filter, dan urutan **identik** dengan teks polos (kolom SQL `TEXT`, nilai disimpan **apa adanya** seperti
 * diketik — tanpa normalisasi diam-diam); hanya aturan nilai sah dan kontrol masukan yang beda.
 *
 * Lolos Uji Variabilitas sebagai enum: ini kosakata **milik sistem** untuk bentuk nilai yang aturannya sama di semua
 * tenant dan industri (email dan telepon tidak berbeda antara klinik dan bordir), bukan data tenant. Nama field, label,
 * dan *apakah* sebuah field divalidasi tetap data per field. Sah hanya untuk [FieldType.TEXT] — bukan
 * [FieldType.LONG_TEXT] dan bukan tipe lain. Aturan bentuk ada di satu tempat: [TextValidations].
 */
enum class TextValidation { NONE, EMAIL, PHONE }
