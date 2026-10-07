package com.eventverse.app.domain.discovery.interview

import kotlin.jvm.JvmInline

private val SLUG = Regex("^[a-z][a-z0-9_]{0,63}$")

/** Kode divisi (slug) — kunci tersimpan di draf. Parser tunggal; nilai rusak ditolak, tidak dinormalkan diam-diam. */
@JvmInline
value class DivisionCode(val value: String) {
    init { require(SLUG.matches(value)) { "DivisionCode '$value' harus huruf kecil/angka/underscore, diawali huruf, maks 64" } }
}

/** Kunci peran di dalam wawancara (slug). Bukan `CustomRole.id`: peran baru lahir saat draf dibangun, bukan di sini. */
@JvmInline
value class RoleKey(val value: String) {
    init { require(SLUG.matches(value)) { "RoleKey '$value' harus huruf kecil/angka/underscore, diawali huruf, maks 64" } }
}

/**
 * Langkah wawancara (plan §1: G1→G5, lalu selesai).
 *
 * Enum sah menurut Uji Variabilitas: urutan giliran adalah **mekanik platform** (apa yang ditebak lebih dulu),
 * identik di semua industri; yang berbeda per industri adalah *isi* tebakan, dan itu data pack ([code] dipakai codec,
 * bukan `name`, supaya mengganti nama konstanta tidak mengubah dokumen tersimpan).
 */
enum class InterviewStep(val code: String) {
    G1_DIVISI("g1_divisi"),
    G2_PERAN("g2_peran"),
    G3_MODUL("g3_modul"),
    G4_SAMBUNGAN("g4_sambungan"),
    G5_RINGKASAN("g5_ringkasan"),
    DONE("done");

    companion object {
        fun fromCode(code: String): InterviewStep? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Asal sebuah modul di usulan wawancara. Kosakata **tertutup milik sistem**: estimasi harga membedakan asal
 * (pakai ulang < kembangkan < baru), jadi ia harus bisa dihitung, bukan teks bebas yang ditafsirkan.
 */
enum class ModuleOrigin(val code: String) {
    /** Modul tata kelola/fondasi yang sudah dikirim platform (mis. bagan organisasi). */
    REUSE_PLATFORM("reuse_platform"),
    /** Modul operasional bawaan pack yang sudah dikirim. */
    REUSE_PACK("reuse_pack"),
    /** Pack sudah punya modulnya, fiturnya kurang — [RoleModuleLink.features] memuat tambahannya. */
    EXTEND("extend"),
    /** Belum ada; dirakit khusus untuk usaha ini (berprefiks kode pack). */
    NEW("new");

    companion object {
        fun fromCode(code: String): ModuleOrigin? = entries.firstOrNull { it.code == code }
    }
}

/** Status konfirmasi satu tebakan. Enum sah (Uji Variabilitas): mekanik wawancara milik platform, sama di semua industri. [SKIPPED] = "terima semua tebakan" — dicatat jujur, bukan disamarkan jadi CONFIRMED. */
enum class Confirmation(val code: String) {
    GUESSED("guessed"), CONFIRMED("confirmed"), CHANGED("changed"), SKIPPED("skipped");

    companion object {
        fun fromCode(code: String): Confirmation? = entries.firstOrNull { it.code == code }
    }
}

/** Siapa yang melahirkan sebuah divisi/peran: tebakan sistem atau jawaban pengguna. Enum sah (Uji Variabilitas): konsep platform, bukan kosakata vertikal. */
enum class ItemSource(val code: String) {
    GUESS("guess"), ANSWER("answer");

    companion object {
        fun fromCode(code: String): ItemSource? = entries.firstOrNull { it.code == code }
    }
}

/** Batas anti-bengkak wawancara (plan §6) — satu tempat, dipakai validator, prompt, dan klien. */
object InterviewLimits {
    const val DIVISIONS = 12
    const val ROLES = 40
    const val LINKS = 60
    const val TURNS = 8
    const val FEATURES_PER_LINK = 10
    const val HANDOFFS = 60
    const val TEXT = 200
}
