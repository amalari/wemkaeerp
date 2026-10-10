# PLAN: Celah Komponen Input & Tipe Field — Daftar, Prioritas, dan Cara Menambahkannya

Dibuat 2026-10-08. Aturan pendaftaran: [`.claude/rules/field-component-rules.md`](../../.claude/rules/field-component-rules.md).
**Status: selesai (2026-10-10).** Seluruh celah C1–C10 terimplementasi — lihat tabel status di §1a. Temuan §0 dibaca dari kode per 2026-10-08 (G1–G8) dan 2026-10-09 (G9); sebagian sudah tidak berlaku (G2: `ClayDatePicker` kini ada; G7: tes paritas kini ada di `core/commonTest` dan `app/shared/commonTest`).
Gerbang TRD Irisan 4 sudah ada dan **disetujui** (2026-10-08): [TRD-FIELD-001](../trd/TRD-FIELD-001-relation.md) (`RELATION`, 4a) dan [TRD-FIELD-002](../trd/TRD-FIELD-002-file.md) (`FILE`, 4b; R4-nya diputuskan Track C).

## 1a. Status pelaksanaan (2026-10-10)

| # | Status | Bukti utama |
|---|---|---|
| C1 | ✅ Selesai | `designsystem/ClayDatePicker.kt`; `teaching-clay-datepicker-field-tanggal-crm.md` |
| C2 | ✅ Selesai | `core/commonTest` `PrototypeFieldTypeSqlParityTest`, `PrototypeFieldTypeCodecParityTest`; `app/shared/commonTest` `CrmFieldTypeParityTest` (kosakata CRM), `LeadFieldControlParityTest` |
| C3 | ✅ Selesai | `LONG_TEXT` di `EntitySpec.kt`; `ClayTextArea` |
| C4 | ✅ Selesai | `NumberFormat { PLAIN, CURRENCY, PERCENT }` + `CurrencyCode.kt` + `SpecOp.SetFieldFormat`; `teaching-field-number-format-currency-percent.md` |
| C5 | ✅ Selesai | TRD-FIELD-003 disetujui; A0/A/B/C selesai (`MultiSelectValues.kt`, `ClayMultiChoiceChips`, `SpecPostgresWriter` `TEXT[]`); teaching `trd-field-003-*` |
| C6 | ✅ Selesai | Prototype: `FieldSpec.withTime` + `ClayDateTimePicker` + kolom `TIMESTAMP`. CRM: `DateField(withTime)` kini dirender `ClayDateTimePicker` (`LeadFieldControl.DATE_TIME_PICKER`); validasi tulis `CustomFieldValidation` sadar-`withTime` (`TTTT-BB-HH'T'JJ:MM` ketat dua arah); konversi tipe sadar-`withTime` (ganti `withTime` = LOSSY). `ClayTimePicker` mandiri (`JJ:MM`) ada di `designsystem/` (D7) — **belum terdaftar ke kosakata field** (tidak ada tipe `TIME`; daftar bila tipe itu dibuat) |
| C7 | ✅ Selesai | TRD-FIELD-001; `FieldType.Relation` + `ClayRelationPicker` + `RelationTargetResolver` (Track B); teaching `trd-field-001-*` |
| C8 | ✅ Selesai | TRD-FIELD-002; `FieldType.File` + `FileRef` + port `ObjectStorage` (adapter S3) + `FieldFileRoutes`; teaching `trd-field-002-*` |
| C9 | ✅ Selesai | `TextValidation` (EMAIL/PHONE) + `teaching-field-text-validation.md` |
| C10 | ✅ Selesai | `ViewProposal.Skeleton` (`SkeletonBlock`: label, lebar, petunjuk tertutup D5), codec menolak nilai tak sah, renderer `SkeletonSketch`; `teaching-field-skeleton-custom-screen.md` |

Sisa terbuka (follow-up, bukan celah tabel C1–C10):
1. `AddCustomFieldDialog` CRM belum menawarkan opsi "tanggal berwaktu" — field `withTime` kini dibuat via API/seed; render/edit sudah penuh.
2. MULTI_SELECT untuk kosakata CRM sengaja tidak disentuh (keputusan TRD-FIELD-003).
3. Cek visual dengan mata (DoD design system) untuk `ClayTimePicker` dan alur tanggal berwaktu CRM masih perlu dijalankan di tenant uji non-garment.

## 0. Temuan terverifikasi

| # | Temuan | Sumber |
|---|---|---|
| G1 | Kosakata prototype: `enum FieldType { TEXT, NUMBER, DATE, ENUM, BOOL }`. Kontrolnya: teks, teks ber-keyboard angka, **teks `TTTT-BB-HH`**, chip pilihan, kotak centang | `EntitySpec.kt`, `fields/FieldInput.kt` |
| G2 | **Tidak ada `DatePicker` di seluruh `app/shared`.** Field tanggal CRM juga kolom teks `YYYY-MM-DD` | grep `DatePicker` |
| G3 | Kosakata CRM custom field berbeda: sealed interface dengan `Text, LongText, Number(format, decimals), SingleSelect, DateField(withTime), Checkbox, UserRef`. `MultiSelect`, `Relation`, `Formula`, `Mirror/Rollup`, `File`, `Timeline` **sengaja ditunda**; mata uang = `Number(format = Currency)` | `customfield/FieldType.kt` |
| G4 | Generator kode memetakan tipe prototype ke SQL: `TEXT`, `NUMERIC(18,4)`, `DATE`, `VARCHAR(120)` + `CHECK IN (...)`, `BOOLEAN` | `handoff/SpecColumns.kt` |
| G5 | Agent Koog membaca daftar blok lewat `screen_catalog()`; keluaran model divalidasi, bukan kode bebas | `KoogDiscoveryTools.kt` |
| G6 | `CUSTOM_SCREEN` hanya kerangka kotak berlabel (satu `Text` per blok), tanpa entitas/view; tidak punya bentuk interaktif | `PrototypeRenderer.kt`, `ScreenProposalConversion.kt` |
| G7 | Tidak ada tes yang mengiterasi `FieldType.entries` di `core/commonTest` | grep |
| G8 | Tidak ada kontrak "komponen belum ada → bagaimana": baru ada pesan "gambar statis" untuk blok tanpa bentuk interaktif | `ScreenProposalConversion.kt` |

| G9 | Bentuk usulan `CUSTOM_SCREEN` = `ViewProposal.None` (`ProposalViewRules` mewajibkannya); kerangkanya selalu **tiga blok generik** dari `WidgetRegistry.sampleRowsFor` ("Ringkasan {modul}" penuh, "Daftar {modul}" separuh, "Panel aksi" separuh). Agent tidak punya tempat menyebut blok kustom. Sampel dibaca server (`DiscoverySummary`) dan dikirim ke klien; batas ukuran usulan ada di `ProposalLimits` (`FIELDS`, `TEXT`, `TILES`, `SCREENS`, …) | `WidgetRegistry.kt`, `ProposalViewRules.kt`, `DiscoverySummary.kt`, `ScreenProposal.kt` |

**Belum diverifikasi**: apakah ada pembaca sampel `CUSTOM_SCREEN` selain renderer klien dan `DiscoverySummary` (di `core` hanya `WidgetRegistry` yang ditemukan); apakah semua `when (FieldType)` bebas `else`; jalur unggah file/penyimpanan objek yang sudah ada di server (diperlukan tipe `FILE`); apakah `FieldInput` dipakai juga di luar tiga konteks yang disebut KDoc-nya; perilaku `DATE` di tabel/kanban saat nilai tidak sah.

## 1. Daftar celah dan prioritas

| # | Komponen | Jenis (Kontrak 2) | Kosakata | Ukuran | Prioritas |
|---|---|---|---|---|---|
| C1 | **Date picker** untuk `DATE` | Kontrol baru, **tanpa ubah kosakata** | prototype + CRM | Kecil | **1** |
| C2 | **Tes paritas** iterasi tipe (Kontrak 7) | Pagar | keduanya | Kecil | **1** (sebelum tipe baru apa pun) |
| C3 | `LONG_TEXT` (teks panjang) | Tipe baru di prototype (CRM sudah punya) | prototype | Kecil | 2 |
| C4 | **Mata uang / persen** | Varian: parameter `format` pada `NUMBER` (bukan tipe baru) | prototype (CRM sudah) | Sedang | 2 |
| C5 | `MULTI_SELECT` | Tipe baru (penyimpanan beda: larik) | keduanya | Sedang | 3 |
| C6 | **Time picker & Date-time picker** (`TIME` / tanggal-waktu) | Kontrol baru di `designsystem/` + parameter `withTime` pada `DATE` (CRM sudah punya) | keduanya (kontrol di `designsystem/`, prototype + CRM) | Sedang | 3 |
| C7 | `RELATION` (rujukan antar entitas) | Tipe baru, referensial | keduanya | **Besar** | 4 |
| C8 | `FILE` (unggah) | Tipe baru, butuh penyimpanan objek | keduanya | **Besar** | 4 |
| C9 | Format tervalidasi (email, telepon) | Parameter validasi pada `TEXT` | prototype | Kecil | 3 |
| C10 | **Kerangka `CUSTOM_SCREEN` yang dinyatakan agent** (daftar blok: label, lebar, petunjuk jenis) | Kosakata **tingkat layar**, bukan tipe field | prototype | Sedang | 3 |

Alasan urutan: C1 dan C2 tidak mengubah kosakata sehingga tidak butuh keputusan besar dan menyiapkan pagar. C3–C6 dan C9 adalah
tipe/parameter sederhana. C7 dan C8 melibatkan integritas referensial dan penyimpanan objek, dan bersinggungan dengan
pagar J3 (`RELATION` antar modul harus lewat port, bukan JOIN lintas schema) sehingga butuh TRD sendiri.

## 2. Irisan kerja (Track A/B/C per irisan, dirancang untuk agent paralel)

### Aturan paralel (berlaku untuk semua irisan)

Track dipotong **per lapisan dan kepemilikan direktori**, bukan per fitur, supaya tiga agent tidak menyunting file yang sama.

| Track | Pemilik | Direktori yang boleh disunting | Dilarang menyentuh |
|---|---|---|---|
| **A — Domain** | kosakata, validasi, codec, usulan layar, generator SQL, tes paritas sisi domain | `core/src/commonMain`, `core/src/commonTest` | `server/`, `app/` |
| **B — Agent & Server** | katalog `screen_catalog`, prompt Koog, evaluasi deterministik, route/infrastruktur server | `server/src/main`, `server/src/test`, `KoogDiscovery*` | `core/`, `app/` |
| **C — UI** | kontrol input, komponen `designsystem/`, layar, cek visual, tes UI | `app/shared/src/commonMain/.../presentation`, `app/shared/src/commonTest` | `core/`, `server/` |

Gerbang dan aturan main:
1. **Gerbang A0 (kontrak, harus merge lebih dulu, kecil).** Sebelum B dan C mulai, A merge komit minimal yang berisi *hanya bentuk* tipe/kontrak (entri enum atau sealed, tanda tangan fungsi, KDoc). Tanpa ini B dan C gagal kompilasi karena `when` tanpa `else` (Kontrak 6) atau tidak punya tipe untuk dirujuk. Setelah A0 ada, **B dan C berjalan paralel dengan sisa A** (validasi, codec, SQL, tes).
2. **Satu worktree per agent** (`isolation: "worktree"`), cabang bertingkat dari komit A0. Agent B dan C **tidak menyunting `core/`**; kebutuhan perubahan kontrak dikembalikan ke A sebagai permintaan, bukan ditambal lokal.
3. **Urutan merge:** A0 → A sisa → B dan C (urutan bebas) → komit integrasi.
4. **Integrator (satu orang/agent, bukan A/B/C)** menjalankan gerbang 7: kompilasi 5 target, tes core/app/server segar, `scripts/audit-variability.sh`, tes paritas, cek visual, lalu **satu** teaching doc per irisan. Agent track tidak menulis teaching doc agar tidak bentrok.
5. **Ratchet ukuran file** (CLAUDE.md §14): pemilik track mengukur `wc -l` file yang disentuh sebelum mulai. Bila sebuah file sudah di atas hard limit, pemecahannya menjadi tugas track pemilik direktori itu, dikerjakan sebelum menambah isi.
6. **Antar-irisan:** Irisan 1 dan 3b tidak berbagi file sehingga boleh jalan bersamaan. Irisan 2, 3, 4 menyentuh `EntitySpec.kt` dan `FieldInput.kt` yang sama, jadi A0 mereka **berurutan** (satu A0 per tipe), tetapi B dan C tipe ke-n boleh jalan bersamaan dengan A0/A tipe ke-(n+1) (pipa).

### Irisan 1 — Date picker + tes paritas (C1, C2)

Tidak mengubah kosakata, jadi **tidak ada A0 kosakata**; gerbangnya adalah tanda tangan komponen, dibekukan dari awal:
`ClayDatePicker(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier, enabled: Boolean, isError: Boolean)` dengan `value` berformat `TTTT-BB-HH` atau kosong, buta domain. Track B membuatnya; Track C dan A hanya memakai tanda tangan itu.

| Track | Isi | Direktori |
|---|---|---|
| **A** | Tes paritas sisi domain yang mengiterasi `FieldType.entries` (prototype) dan varian sealed (CRM): tiap tipe punya pemetaan SQL, entri katalog agent, round-trip codec yang menolak nilai tak dikenal. Pack non-default. Tes paritas "kontrol input ada untuk tiap tipe" ditulis di Track C (butuh UI) | `core/commonTest` |
| **B** | Komponen `ClayDatePicker` di `designsystem/` (buta domain, token Clay, tanpa literal warna), dipakai `FieldInput` untuk `DATE`; konteks form dan sel tabel/kanban (`TableCell`, `InlineRowEditor`, `KanbanDetailDialog`) ikut diperiksa. Jalankan `scripts/find-similar-feature.sh` dulu untuk memastikan belum ada date picker lain | `app/shared/presentation/designsystem`, `.../discovery/fields` |
| **C** | Terapkan juga di CRM (`LeadCustomField`, `AddCustomFieldDialog` untuk `DateField`) memakai komponen Track B; tes paritas "kontrol input ada untuk tiap tipe"; cek visual di dua konteks dan di lebar sempit (Wasm dan JVM) | `app/shared/presentation/crm`, `app/shared/commonTest` |

**Pengecualian pemetaan lapisan:** karena Irisan 1 tidak punya kerja agent/server, Track B dan C di sini sama-sama di `app/shared`, dipisah per direktori (discovery vs CRM). Hanya B yang boleh menyunting `designsystem/`.
Ketergantungan: C bergantung pada B hanya lewat tanda tangan di atas, jadi keduanya jalan bersamaan; C merge setelah B. Track A tidak bergantung pada keduanya.

Catatan desain: date picker di Compose Multiplatform lintas 5 target tidak seragam (komponen Material 3 `DatePicker` ada tetapi
perilakunya per platform berbeda); **keputusan D1** di bawah.

### Irisan 2 — Tipe sederhana (C3, C4, C6, C9)
Satu tipe/parameter per PR, masing-masing dengan A0. Prasyarat: Irisan 1 Track A (tes paritas) sudah merge, supaya entri baru tanpa padanan langsung menggagalkan tes.

| Track | Isi (per tipe) | Direktori |
|---|---|---|
| **A** | **A0:** entri tipe/parameter + validasi `FieldSpec` + tanda tangan format simpan (mis. `LONG_TEXT` = string; `NUMBER(format = Currency/Percent)`; `DATE(withTime = true)` = format ISO string `TTTT-BB-HH'T'JJ:MM`; `TEXT(validation = Email/Phone)`). **Sisa A:** `SpecOp`/`SpecOpApplier`, `ProposalEntityRules`, `ProposalEdit`, `DeterministicScreenProposer`, codec (`InteractiveScreenCodec`, `SpecOpCodec`, `ScreenProposalCodec` menolak nilai tak dikenal), `SpecColumns`/`SpecPostgresWriter`/`SpecRoutesWriter` (`TIMESTAMP WITH TIME ZONE` bila `withTime == true` vs `DATE`), tes paritas | `core/commonMain`, `core/commonTest` |
| **B** | `screen_catalog` memuat tipe baru dan aturan pemakaiannya, `KoogDiscoveryPrompt` dan `KoogModuleEditor` tidak menyebut daftar tipe basi, evaluasi agent deterministik (tanpa LLM berbayar), codec sisi server bila ada | `server/.../infrastructure/discovery`, `.../builder` |
| **C** | Kontrol di `FieldInput` + `TableCell`, `InlineRowEditor`, `KanbanDetailDialog`, `InteractiveFormState`, `InteractiveTableState`, serta CRM (`LeadCustomFieldControls`); komponen dasar di `designsystem/`: **`ClayTimePicker`** (format waktu `JJ:MM`, dialog spinner/grid jam & menit) dan **`ClayDateTimePicker`** (format `TTTT-BB-HH'T'JJ:MM`, menyatukan kalender + waktu); area teks untuk `LONG_TEXT`; input format mata uang/persen; cek visual di dua konteks dan pack non-garment | `app/shared/presentation/discovery/fields`, `.../designsystem`, `.../crm` |

Urutan pipa: A0(C3) → [A(C3) ‖ B(C3) ‖ C(C3) ‖ A0(C4)] → … Tipe C4 (mata uang/persen) paling besar di Track C karena format tampil dan parsing masukan. Tipe C6 mencakup penyediaan komponen pemilih waktu `ClayTimePicker` dan pemilih tanggal-waktu `ClayDateTimePicker` di `designsystem/` yang seragam di 5 target KMP dan dipakai bila `withTime == true` di CRM maupun Prototype.

### Irisan 3 — `MULTI_SELECT` (C5)
**Gerbang awal (bukan track):** TRD ringkas yang memutuskan penyimpanan — kolom larik vs tabel tautan — dan bentuk nilai di codec. Tanpa keputusan ini A0 tidak boleh dimulai.
**Status gerbang:** [`TRD-FIELD-003-multi-select.md`](../trd/TRD-FIELD-003-multi-select.md) **disetujui 2026-10-09 — gerbang lewat, A0 boleh dimulai** (kolom `TEXT[]` + CHECK; nilai sel = string JSON array berurut menurut `options`; `maxSelections` saja; CRM tidak disentuh). A0 berurutan dengan A0 4a/4b (berbagi `EntitySpec.kt`); tes integrasi Postgres untuk kolom larik wajib hijau sebelum Track B/C.

| Track | Isi | Direktori |
|---|---|---|
| **A** | **A0:** tipe `MULTI_SELECT` (opsi + batas pilihan) dan format nilai (larik terurut, tanpa duplikat). **Sisa A:** validasi, codec (dua kosakata sesuai keputusan D2: prototype dulu, CRM dicatat), `SpecColumns`/penulis SQL untuk penyimpanan terpilih, `SpecOp`, tes paritas | `core/commonMain`, `core/commonTest` |
| **B** | Katalog agent dan aturan prompt (kapan memilih `MULTI_SELECT` vs `ENUM`), evaluasi deterministik, route hasil scaffold membaca/menulis larik | `server/.../infrastructure/discovery`, `.../builder` |
| **C** | Kontrol chip pilihan ganda di `FieldInput` (+ konteks tabel/kanban), komponen dasar `designsystem/` buta domain, cek visual | `app/shared/presentation/discovery/fields`, `.../designsystem` |

### Irisan 3b — Kerangka layar kustom (C10)
### Irisan 3b — Kerangka layar kustom (C10)
Masalah: agent tidak bisa menyebut "Keranjang" atau "Pembayaran" pada layar kustom; pratinjau selalu tiga kotak generik (G9).

**Kontrak A0:** `SkeletonBlock(label, width ∈ {FULL, HALF}, hint ∈ daftar tertutup D5)` dan `ViewProposal.Skeleton(blocks)`. Track A merge ini lebih dulu; B dan C hanya memakai tipe itu.

| Track | Isi | Direktori |
|---|---|---|
| **A** | **A0** di atas. **Sisa A:** `Skeleton` menggantikan `None` **hanya** untuk `CUSTOM_SCREEN`; batas baru di `ProposalLimits` (jumlah blok, panjang label); `ProposalViewRules` dan codec (`ViewProposalCodec`) diperbarui, nilai tak sah **ditolak**; `WidgetRegistry.sampleRowsFor` memakai blok dari usulan bila ada, jatuh ke tiga blok generik bila tidak (draf lama tetap hidup); tes round-trip dan validator dengan pack non-garment | `core/domain/discovery/proposal`, `core/shared/discovery`, `WidgetRegistry` |
| **B** | `viewShape`/`widgetNote` di `screen_catalog` dan aturan prompt Koog menjelaskan bentuk baru dengan batas yang sama dengan validator (batas dibaca dari `ProposalLimits`, tidak disalin); `DiscoverySummary` meneruskan sampel; evaluasi agent deterministik | `server/infrastructure/discovery` |
| **C** | Renderer menggambar blok dari data, tampil sebagai sketsa (bukan interaktif); cek visual di dua pack | `app/shared/presentation/discovery` |

Bukan tipe field, jadi aturan `field-component-rules.md` hanya berlaku sebagian (katalog agent dan codec menolak nilai tak dikenal); aturan itu
perlu mencatat pengecualian ini agar tidak dianggap terlupa. **Tetap non-interaktif** (keputusan D6).

### Irisan 4 — `RELATION`, `FILE` (C7, C8)
Dua sub-irisan terpisah (4a `RELATION`, 4b `FILE`), masing-masing dengan TRD sendiri sebagai **gerbang awal** dan A0 sendiri. 4a dan 4b tidak berbagi kontrak baru sehingga boleh jalan paralel, selama A0 mereka berurutan (berbagi `EntitySpec.kt`).

**Gerbang TRD:**
- 4a: rujukan lintas modul hanya lewat port, bukan JOIN lintas schema (TRD-PLAT-004 P4 dan pagar J3); keputusan siapa pemilik integritas referensial.
- 4b: jalur penyimpanan objek (belum diverifikasi apakah server sudah punya jalur unggah), batas ukuran, tipe konten, dan siapa boleh mengunduh.

| Track | 4a `RELATION` | 4b `FILE` | Direktori |
|---|---|---|---|
| **A** | **A0:** tipe `RELATION(target)` dengan `isReferential`; validasi target ada dan dapat dirujuk; codec; `SpecColumns` (kunci asing logis, tanpa FK lintas schema); tes paritas + tes target tidak sah | **A0:** tipe `FILE` (nilai = referensi objek, bukan byte); antarmuka `ObjectStorage` di domain; codec menolak referensi tak dikenal; `SpecColumns`; tes paritas | `core/commonMain`, `core/commonTest` |
| **B** | Route pencarian opsi rujukan dengan gate modul target (`requireModuleAccess`), **fail-closed**, tes 403 untuk peran tak berwenang; katalog agent dan prompt | Implementasi `ObjectStorage` (infrastruktur), endpoint unggah/unduh dengan gate modul induk, **fail-closed**, tes 403, batas ukuran; katalog agent dan prompt | `server/src/main`, `server/src/test` |
| **C** | Kontrol pemilih rujukan (pencarian, tampilan label) di `FieldInput` + tabel/kanban | Kontrol unggah (progres, error, batas ukuran) di `FieldInput` + tabel/kanban; komponen dasar `designsystem/` | `app/shared/presentation/discovery/fields`, `.../designsystem` |

Pengecualian Kontrak 8: bila TRD belum selesai, tipe ini **ditolak**, tidak dipetakan ke `TEXT`.

## 3. Keputusan yang diminta

| # | Keputusan | Rekomendasi | Alasan |
|---|---|---|---|
| **D1** | Date picker: komponen Material 3 `DatePicker` atau komponen Clay buatan sendiri | **Clay buatan sendiri di `designsystem/`**, mengembalikan string | Bahasa visual Clay (outline tebal, hard shadow); perilaku seragam di 5 target; buta domain |
| **D2** | Menyatukan dua kosakata (prototype vs CRM) | **Tidak sekarang**; aturan berlaku per kosakata, tulis keputusan di tiap irisan | Menyentuh CRM yang berjalan; manfaat penyatuan baru terasa setelah tipe ke-6 |
| **D3** | Mata uang sebagai parameter `format` pada `NUMBER` (prototype) | **Ya** | Sejalan KDoc CRM: penyimpanan identik, hanya render yang beda |
| **D4** | Kontrak 8 (komponen belum ada ≠ dipalsukan jadi `TEXT`) berlaku untuk codec yang ada | **Ya**; periksa dulu apakah ada fallback senyap saat ini | Mencegah data berubah tanpa jejak |
| **D5** | Kerangka kustom: label bebas atau petunjuk dari daftar tertutup | **Petunjuk tertutup** (mis. `TABEL`, `FORM`, `KARTU_ANGKA`, `AKSI`) + label singkat | Label bebas mengarah ke "UI bebas" yang sengaja dihindari; petunjuk menandai blok mana yang kelak bisa menjadi blok sungguhan |
| **D6** | Kerangka tetap non-interaktif | **Ya** | Ia sketsa untuk dinilai prospek; interaktif berarti jadi blok sungguhan lewat jalur biasa |
| **D7** | Time picker & DateTime picker: komponen terpisah atau menyatu | **`ClayTimePicker` mandiri (format `JJ:MM`) dan `ClayDateTimePicker` terpadu (format ISO-8601 `YYYY-MM-DDTHH:mm`) di `designsystem/`** | Memungkinkan pemilihan waktu murni (mis. jam operasional) dan tanggal-waktu terpadu (mis. deadline deal, jadwal inspeksi QC, `withTime = true`); buta domain; bebas dependensi platform |

## 4. Risiko
- Date picker lintas target: perilaku fokus/keyboard di Wasm/JS vs Android berbeda → cek visual di minimal Wasm dan JVM.
- Tipe baru yang tidak dikenali dokumen lama: codec menolak, jadi dokumen draf tersimpan tidak boleh memuat tipe yang belum dirilis.
- Menambah tipe di UI tanpa katalog agent: agent tidak akan pernah memilihnya (dicegah Kontrak 1 dan tes paritas).
- File besar: `FieldInput.kt` dan `InteractiveFormState.kt` perlu diukur terhadap batas ukuran file sebelum disentuh (belum diukur).

## 5. Di luar plan ini
- Mengubah `CUSTOM_SCREEN` menjadi penampung komponen bebas (sengaja tidak; ia tetap sketsa).
- Hubungan komponen yang belum ada dengan antrean build (TRD-PLAT-006/007), selain Kontrak 8 yang menyebutnya sebagai jalur.
- Menyatukan dua kosakata (D2).

## 6. Verifikasi umum tiap irisan
Gerbang 7 `wemade-feature-workflow`: kompilasi 5 target, tes core/app/server segar, `scripts/audit-variability.sh`, tes paritas
(C2) hijau, cek visual (login superadmin demo, lalu tenant non-garment), dan teaching doc (CLAUDE.md §12).
