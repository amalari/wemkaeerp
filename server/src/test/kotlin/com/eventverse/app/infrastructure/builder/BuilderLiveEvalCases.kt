package com.eventverse.app.infrastructure.builder

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.FieldType

/** Satu kasus penanya klarifikasi: cerita yang **seharusnya** ditanyakan ([shouldAsk]) atau langsung disusun. */
internal data class ClarifyEvalCase(
    val name: String,
    val narrative: String,
    val shouldAsk: Boolean,
    val existingModules: List<String> = emptyList()
)

/**
 * Satu kasus penyunting isian. [check] menilai usulan **setelah** sunting diterapkan (null = lolos, teks = alasan gagal);
 * [expectNoChange] = permintaan di luar isian yang harus dijawab tanpa sunting. [answered] meniru jawaban follow-up.
 */
internal data class EditEvalCase(
    val name: String,
    val message: String,
    val answered: List<Pair<String, String>> = emptyList(),
    val expectNoChange: Boolean = false,
    val check: (ScreenProposal) -> String? = { null }
)

/**
 * Kasus eval live Builder (PLAN-builder-interview-chat). Penilaian **otomatis dan tegas** — keputusan bertanya/tidak
 * dan keadaan field akhir — sehingga tidak ada model lain yang menilai model. Mutu *isi* pertanyaan tidak bisa dinilai
 * mesin; pertanyaannya dicetak ke laporan untuk ditinjau manusia.
 */
internal object BuilderLiveEvalCases {

    val clarify: List<ClarifyEvalCase> = listOf(
        ClarifyEvalCase("kabur-1", "Saya punya usaha kecil dan ingin sistemnya.", shouldAsk = true),
        ClarifyEvalCase("kabur-2", "Mau bikin aplikasi untuk bisnis saya.", shouldAsk = true),
        ClarifyEvalCase("kabur-3", "Usaha saya lagi berkembang, perlu yang lebih rapi.", shouldAsk = true),
        ClarifyEvalCase("kabur-4", "Tolong bantu saya membuat sistem.", shouldAsk = true),
        ClarifyEvalCase("jelas-klinik", "Kami klinik gigi: pasien mendaftar antrean per poli, ada stok obat, dan tagihan pembayaran kasir.", shouldAsk = false),
        ClarifyEvalCase("jelas-bengkel", "Bengkel servis motor: pelanggan booking servis lewat telepon, mekanik mengerjakan, lalu kasir menagih dan mencatat sparepart yang dipakai.", shouldAsk = false),
        ClarifyEvalCase("jelas-konveksi", "Kami konveksi pakaian jadi; kain kami beli sendiri, mulai potong sampai jahit, QC, dan kirim ke pembeli.", shouldAsk = false),
        ClarifyEvalCase("jelas-katering", "Katering harian: pesanan langganan tiap minggu, dapur memasak per shift, lalu kurir mengantar dan ada laporan pengiriman bulanan.", shouldAsk = false),
        ClarifyEvalCase("jelas-sekolah", "Sekolah kursus komputer: pendaftaran siswa baru tiap semester, jadwal kelas, dan pembayaran SPP bulanan.", shouldAsk = false),
        ClarifyEvalCase("revisi-jelas", "Tambah modul pengiriman.", shouldAsk = false, existingModules = listOf("klinik_antrean", "klinik_stok", "klinik_tagihan"))
    )

    /** Layar dasar tenant non-garment: tabel pasien dengan tiga isian dan satu baris contoh. */
    val baseProposal = ScreenProposal(
        "s_poli", ModuleId("klinik_poli"), "Antrean Poli", WidgetKind.TABLE, "karena uji",
        EntityProposal("pasien", "Pasien", listOf(
            FieldProposal("nama", "Nama", FieldType.TEXT, true),
            FieldProposal("keluhan", "Keluhan", FieldType.TEXT),
            FieldProposal("tgl", "Tanggal", FieldType.DATE)
        )),
        ViewProposal.Table(listOf("nama", "keluhan", "tgl")),
        seed = listOf(mapOf("nama" to "Budi", "keluhan" to "ngilu", "tgl" to "2026-10-01"))
    )

    private fun ScreenProposal.fields() = entity!!.fields
    private fun ScreenProposal.field(key: String) = fields().firstOrNull { it.key == key }
    private fun need(ok: Boolean, reason: String): String? = if (ok) null else reason

    val edit: List<EditEvalCase> = listOf(
        EditEvalCase("tambah-tanggal-wajib", "Tambah input tanggal kunjungan bertipe tanggal dan wajib diisi") { p ->
            need(p.fields().any { it.type == FieldType.DATE && it.required && (it.label + it.key).contains("kunjungan", true) },
                "tidak ada field DATE wajib bernama kunjungan")
        },
        EditEvalCase("buang-field", "Buang isian keluhan") { p ->
            need(p.field("keluhan") == null && p.field("nama") != null, "keluhan masih ada atau nama ikut hilang")
        },
        EditEvalCase("ganti-jadi-pilihan", "Ganti keluhan jadi pilihan: ngilu, bengkak, berlubang") { p ->
            val f = p.field("keluhan")
            need(f != null && f.type == FieldType.ENUM && f.options.map { it.lowercase() }.containsAll(listOf("ngilu", "bengkak", "berlubang")),
                "keluhan bukan ENUM dengan tiga pilihan")
        },
        EditEvalCase("nama-tidak-wajib", "Isian nama tidak usah wajib") { p ->
            need(p.field("nama")?.required == false, "nama masih wajib")
        },
        EditEvalCase("tambah-dua", "Tambah dua isian: nomor telepon (teks) dan usia (angka)") { p ->
            need(p.fields().any { it.type == FieldType.NUMBER && (it.label + it.key).contains("usia", true) } &&
                p.fields().any { it.type == FieldType.TEXT && (it.label + it.key).contains("telepon", true) || (it.label + it.key).contains("telp", true) },
                "usia (angka) atau telepon (teks) tidak ada")
        },
        EditEvalCase("di-luar-isian", "Ubah warna tombol jadi merah", expectNoChange = true),
        EditEvalCase("tambah-catatan", "Tambah catatan") { p ->
            need(p.fields().any { (it.label + it.key).contains("catatan", true) }, "tidak ada field catatan")
        },
        // "nama" sudah ada: jawaban yang BENAR adalah tidak menyunting (atau paling tidak tidak menggandakan). Eval pertama
        // (2026-10-07) menilai "tanpa sunting" sebagai gagal - itu salah penilai, bukan salah model; dikoreksi di sini.
        EditEvalCase("duplikat", "Tambah isian nama", expectNoChange = true),
        EditEvalCase(
            "jawaban-followup", "nama dan keluhan wajib",
            answered = listOf("Dari isian Pasien, mana yang wajib diisi?" to "nama dan keluhan wajib")
        ) { p -> need(p.field("nama")?.required == true && p.field("keluhan")?.required == true, "nama/keluhan belum sama-sama wajib") },
        EditEvalCase("status-baru", "Tambah isian status kunjungan dengan pilihan baru, selesai, batal") { p ->
            need(p.fields().any { it.type == FieldType.ENUM && it.options.map { o -> o.lowercase() }.containsAll(listOf("baru", "selesai", "batal")) },
                "tidak ada ENUM dengan baru/selesai/batal")
        }
    )
}
