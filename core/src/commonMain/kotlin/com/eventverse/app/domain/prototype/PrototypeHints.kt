package com.eventverse.app.domain.prototype

/**
 * Petunjuk perilaku papan dari pack: urutan kolom (termasuk yang kosong) dan transisi yang boleh.
 * Tanpa [transitions] kartu bebas pindah ke kolom mana pun. Kunci B2 opsional — kosong/null =
 * perilaku lama.
 *
 * Dua mode bentuk papan (B2.1, mengikuti temuan jalur C di modul pilot):
 *  - **Legacy** (garment): [fields] kosong — field entitas diturunkan dari baris contoh yang wajib
 *    berkunci `"Kolom"` (atau [groupField] bila ditimpa), semua field selain kelompok bertipe TEXT.
 *  - **Dideklarasikan**: [fields] diisi — bentuk entitas dari petunjuk (status ENUM, tanggal DATE,
 *    dsb., bukan semua TEXT), [groupField] **wajib**, dan baris contoh **opsional** karena data
 *    layar berbinding [com.eventverse.app.domain.prototype.DataBinding.Api] datang dari server.
 *    `titleField` = field elemen kartu bergaya TITLE (atau field deklarasi pertama).
 */
data class KanbanHints(
    val columns: List<String>,
    val transitions: Map<String, Set<String>> = emptyMap(),
    /** Nama kolom status di bahasa pack ("Status SPK"), dipakai di pesan penolakan. Null = "Kolom". */
    val groupLabel: String? = null,
    /** Elemen bertipe kartu (B2, plan induk §3.4); kosong = perilaku lama (titleField + detailFields). */
    val card: List<CardElement> = emptyList(),
    /** Metadata kolom (B2): warna data tenant (tintHex) & batas WIP; kunci wajib kolom di [columns]. */
    val columnMeta: Map<String, ColumnMeta> = emptyMap(),
    /** Form saat kartu diketuk (B2); field-nya wajib milik entitas papan (divalidasi spec). */
    val detailForm: FormConfig? = null,
    /**
     * Kunci field kelompok di baris data (B2.1, usulan jalur C): null = `"Kolom"` (garment tetap
     * sama). Wajib diisi bila [fields] dideklarasikan.
     */
    val groupField: String? = null,
    /**
     * Deklarasi field entitas papan (B2.1, usulan jalur C): status ENUM, tanggal DATE, dsb. — bukan
     * semua TEXT. Kuncinya kunci baris server/pack; [groupField] tidak boleh dideklarasikan di sini
     * (type-nya dipaksa ENUM dengan opsi [columns]). Kosong = mode legacy.
     */
    val fields: List<FieldHint> = emptyList()
)

/**
 * Deklarasi tipe satu field tabel dari pack (butir B2): kuncinya = kolom baris contoh. Tipe memakai
 * [FieldType] — kosakata tertutup **milik sistem** (Uji Variabilitas: input/renderer harus bisa
 * menangani tiap tipe di semua vertikal); nama field dan opsinya tetap data pack.
 */
data class FieldHint(
    val key: String,
    val type: FieldType,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    /**
     * Parameter field yang dideklarasikan **pack** (Irisan 2): sumber data sah satu-satunya — usulan tidak menebak dari
     * nama field. Bawaan = perilaku lama. Invarian yang sama dengan [FieldSpec] (mis. `format` hanya NUMBER, CURRENCY wajib
     * kode, `withTime` hanya DATE, `validation` hanya TEXT) ditegakkan saat pack dibangun, bukan saat dipakai.
     */
    val format: NumberFormat = NumberFormat.PLAIN,
    val currencyCode: String? = null,
    val withTime: Boolean = false,
    val validation: TextValidation = TextValidation.NONE,
    /** C7 (TRD-FIELD-001): target rujukan; wajib tepat bila [type] == [FieldType.RELATION] (invarian di [FieldSpec]). */
    val target: String? = null,
    /** A0 (TRD-FIELD-003): batas pilihan MULTI_SELECT; hanya sah untuk MULTI_SELECT (invarian di [FieldSpec]). */
    val maxSelections: Int? = null
) {
    init {
        require(key.isNotBlank()) { "FieldHint.key kosong" }
        if (type == FieldType.ENUM || type == FieldType.MULTI_SELECT) {
            require(options.isNotEmpty() && options.distinct().size == options.size) { "FieldHint ${type.name} '$key' wajib punya opsi unik" }
        } else {
            require(options.isEmpty()) { "FieldHint '$key' bukan ENUM atau MULTI_SELECT tapi punya opsi" }
        }
        toFieldSpec() // invarian parameter (format/withTime/validation/target/maxSelections) = satu sumber: FieldSpec
    }

    /** Jadikan [FieldSpec]; label = kunci, karena nama field pack adalah label tampilannya. */
    fun toFieldSpec(): FieldSpec = FieldSpec(key, key, type, options, required, format, currencyCode, withTime, validation, target, maxSelections)
}

/**
 * Petunjuk perilaku tabel dari pack: kolom [statusColumn] bernilai salah satu [options] dan bisa
 * diubah di baris. Tanpa petunjuk, tabel hanya bisa disortir/difilter. Kunci B2 opsional — kosong/
 * false = perilaku lama.
 */
data class TableHints(
    val statusColumn: String,
    val options: List<String>,
    val transitions: Map<String, Set<String>> = emptyMap(),
    /**
     * Tipe field per kolom (B2): menurunkan [FieldSpec] selain TEXT. Kosong = perilaku lama (semua
     * kolom TEXT kecuali [statusColumn]). Kunci wajib kolom baris contoh — tak koheren ditolak
     * factory (null → gambar statis), bukan diabaikan.
     */
    val fields: List<FieldHint> = emptyList(),
    /** Baris isian + tombol Tambah langsung di tabel (B2). */
    val inlineCreate: Boolean = false,
    /** Sel yang bisa disunting lewat ketuk (B2); status bermesin tidak boleh masuk (divalidasi spec). */
    val editableFields: List<String> = emptyList()
)

/** Petunjuk dasbor dari pack: ubin berlabel [counts].key dihitung dari layar lain, bukan angka statis. */
data class DashboardHints(val counts: Map<String, CountSpec>)

/**
 * Petunjuk formulir dari pack (kontrak v1, butir B2): [fields] = urutan field di form; [required]
 * = field yang wajib diisi (ditegakkan reducer pada `Create`); [options] = field yang berupa
 * pilihan (ENUM) beserta opsinya; [submitLabel] = teks tombol (null = "Simpan").
 *
 * Form **melengkapi** layar sumber satu modul (tabel/papan), bukan layar berdiri sendiri: field
 * [fields] wajib memakai nama kolom yang ada di baris contoh layar sumbernya supaya baris baru
 * hasil `Create` tampil di sana (entitas & id sama, lihat `InteractiveScreenFactory.form`).
 */
data class FormHints(
    val fields: List<String>,
    val required: List<String> = emptyList(),
    val options: Map<String, List<String>> = emptyMap(),
    val submitLabel: String? = null
)
