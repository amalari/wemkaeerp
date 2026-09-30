package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.AccessLevel

/**
 * Istilah yang diucapkan pack di chrome layar kerja generik `/m/{code}` (plan A4).
 *
 * Enum ini sah (tenant-variability-rules Kontrak 1): yang variabel antar vertikal adalah **nilainya**
 * (`"pabrik"` vs `"klinik"` vs `"bengkel"`), sedangkan **slot**-nya konsep platform — chrome perlu tahu
 * "kata ini untuk benda apa", dan jumlah slot tidak tumbuh setiap kali ada tenant baru.
 *
 * [neutral] adalah kata platform yang dipakai pack yang **tidak** mendeklarasikan istilahnya. Ia tidak
 * boleh memuat kosakata satu vertikal: pack yang diam harus berbicara netral, bukan berbicara konveksi.
 * Aturan itu dikunci `PackVocabularyTest` — dulu chrome menulis `"pabrik"`/`"SPK"` langsung di Composable,
 * sehingga tenant klinik melihat kata pabrik di layarnya sendiri.
 */
enum class VocabularyKey(val neutral: String) {
    /** Tempat kerja pemakainya: `"pabrik"` (konveksi), `"klinik"`, `"bengkel"`, `"sekolah"`. */
    WORKPLACE("perusahaan"),

    /** Dokumen yang dikerjakan modul: `"Dokumen"`/`"SPK"` (konveksi), `"Kunjungan"` (klinik). */
    DOCUMENT("dokumen")
}

/**
 * Aksi baku layar kerja generik. [requiredLevel] adalah konsep **platform** (wewenang minimum untuk
 * sebuah peran aksi), bukan kosakata vertikal; yang menjadi data pack adalah [ModuleAction.label].
 */
enum class ModuleActionCode(val neutralLabel: String, val requiredLevel: AccessLevel) {
    ADD("Tambah", AccessLevel.OPERATE),
    EDIT("Ubah", AccessLevel.OPERATE),
    APPROVE("Setujui", AccessLevel.MANAGE),
    DELETE("Hapus", AccessLevel.MANAGE);

    companion object {
        /** Titik mulai pack yang belum menyebut aksinya sendiri; urutannya = urutan tombol hari ini. */
        val neutral: List<ModuleAction> get() = entries.map { ModuleAction(it, it.neutralLabel) }
    }
}

/** Satu tombol aksi layar kerja generik: [code] = perannya di sistem, [label] = kalimat pack. */
data class ModuleAction(val code: ModuleActionCode, val label: String) {
    init { require(label.isNotBlank()) { "Label aksi ${code.name} kosong" } }
}
