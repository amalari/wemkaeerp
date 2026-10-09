# TRD-FIELD-003: Tipe Field `MULTI_SELECT` — Pilihan Ganda dari Daftar Tertutup (C5, Irisan 3)

## 1. Document Context and Administration

- **Title & Unique ID**: TRD-FIELD-003 — Tipe Field `MULTI_SELECT`
- **Status**: **Diusulkan — menunggu persetujuan R1–R5** (gerbang TRD wajib sesuai `PLAN-field-component-gaps.md` §2 Irisan 3 dan Kontrak 8 `field-component-rules.md`; **A0 belum boleh dimulai** sampai status ini berubah)
- **Revision History**:

| Versi | Tanggal | Penulis | Catatan |
|---|---|---|---|
| 0.1 | 2026-10-09 | Claude (riset dari kode) | TRD gerbang; rekomendasi diberikan dan disetujui untuk ditulis oleh user; belum ada kode |

- **Summary & Business Context**: Kosakata field prototype hanya punya `ENUM` (satu pilihan). Atribut yang
  berlabel ganda (alergi pasien, layanan yang dibeli, jenis bahan) hari ini dipaksa menjadi `TEXT` bebas — data
  tak terstruktur, tak tervalidasi — atau `ENUM` yang salah makna (status kerja). Plan menandai C5 ukuran
  **Sedang** karena penyimpanannya berbeda dari semua tipe lain (kumpulan nilai, bukan satu nilai). Tanpa
  TRD ini, `MULTI_SELECT` **ditolak** di semua jalur (Kontrak 8); dipalsukan jadi `TEXT` atau `ENUM` dilarang.
- **Rujukan**: `PLAN-field-component-gaps.md` §1 C5, §2 Irisan 3, §3 D2; `field-component-rules.md` (seluruh
  kontrak, terutama 2, 4, 6, 7, 8); `tenant-variability-rules.md` Kontrak 4; pola TRD serupa
  `TRD-FIELD-001-relation.md` dan `TRD-FIELD-002-file.md`; A0 sebelumnya (git `a59b86ff`, `666d81c0`,
  `59616dd3`, `7ffd70f7`).
- **Stakeholders & Approvers**: Tech Lead (keputusan §4.1), Product (UX pemilih ganda), QA (tes paritas),
  integrator Irisan 3 (Track A/B/C).
- **Goals (In-Scope)**:
  1. Keputusan **penyimpanan** nilai (kolom larik vs tabel tautan vs teks JSON).
  2. Bentuk nilai di lapisan prototype (sel = string) dan aturan kanonik (urutan, duplikat, kosong).
  3. **Kontrak A0 final** (tanda tangan tipe persis) untuk kosakata prototype; keputusan CRM (D2).
  4. Batasan terhadap `ENUM`/mesin status/kanban supaya dua tipe tidak tertukar.
  5. Kontrak pemetaan SQL/Exposed, route hasil scaffold, katalog agent, dan kontrol UI.
- **Non-Goals (Out-of-Scope)**:
  - `MULTI_SELECT` di kosakata CRM (`customfield/FieldType.kt`) — sengaja ditunda, dicatat di §4.1 R5.
  - Pilihan ganda dengan opsi dinamis dari data (itu `RELATION`, TRD-FIELD-001).
  - Filter/urut SQL berdasarkan opsi tunggal (belum ada layar yang memerlukannya).
  - Pilihan bebas yang boleh ditambah pengguna saat mengisi (daftar tetap milik spesifikasi).

## 2. Functional Requirements

- **FR-1 Tipe baru.** `FieldType.MULTI_SELECT` ada di kosakata prototype. Memakai `options` seperti `ENUM`
  (wajib, unik, dalam batas `ProposalLimits.OPTIONS`) dan parameter opsional `maxSelections`.
- **FR-2 Nilai kanonik.** Nilai sel berbentuk **string JSON array** dari nama opsi, mis.
  `["Gigi","Jantung"]`. Urutan elemen **mengikuti urutan `options`** (bukan urutan klik); tanpa duplikat;
  tiap elemen harus ada di `options`. Nilai yang sama selalu menghasilkan string yang sama.
- **FR-3 Kosong.** Belum diisi = string kosong `""`, sama seperti tipe lain. Array kosong `"[]"` **ditolak**
  (satu bentuk kosong, tidak dua).
- **FR-4 Batas pilihan.** `maxSelections` (Int?, bawaan null = hanya dibatasi jumlah opsi) hanya sah untuk
  `MULTI_SELECT`; bila diisi, `1 ≤ maxSelections ≤ options.size`. Nilai yang melebihi batas ditolak.
- **FR-5 Bukan status.** `MULTI_SELECT` tidak boleh menjadi `statusField` entitas dan tidak ikut `StateMachine`;
  pengelompokan kolom kanban hanya dari `ENUM`. Validator usulan menolaknya dengan pesan jelas.
- **FR-6 Penolakan, bukan fallback.** Codec menolak nilai tipe yang tak dikenal dan nilai sel yang bukan
  array JSON sah. Tidak ada jatuh ke `TEXT` (Kontrak 4/8).
- **FR-7 Generator.** `SpecColumns`/`SpecPostgresWriter`/`SpecRoutesWriter` memetakan tipe ini ke kolom
  larik Postgres; route hasil scaffold memvalidasi nilai seperti `dateProblem`/`textProblem`.
- **FR-8 Agent.** `screen_catalog` dan prompt menyebut tipe ini dengan aturan kapan memilihnya dibanding `ENUM`.
- **FR-9 UI.** Satu pintu `FieldInput` menggambar pilihan ganda (chip bisa dipilih ganda) di form, sel tabel
  inline, dan dialog detail kanban; tampilan baca = daftar label dipisah `, `.

## 3. Non-Functional Requirements (NFRs)

- **Paritas & kompilator (Kontrak 6/7):** tidak ada `else` pada `when (FieldType)` baru; tes paritas
  mengiterasi `FieldType.entries` dan wajib gagal bila titik pendaftaran terlewat.
- **Kompatibilitas mundur:** dokumen draf lama tanpa tipe ini tetap terbaca byte-identik; kunci baru
  `maxSelections` ditulis hanya bila bukan bawaan (pola `NumberFormat`/`withTime`).
- **Integritas:** batasan opsi ditegakkan juga di database (CHECK), bukan hanya di aplikasi.
- **Keamanan:** tidak ada endpoint baru; route scaffold memakai gerbang modul yang sama seperti sebelumnya.
- **Ukuran file:** `EntitySpec.kt` (≈140 baris) tidak boleh melewati 250; aturan nilai ditaruh di file bertema
  baru, bukan menambah `EntitySpec.kt`.

## 4. System Architecture & Technical Design

### 4.1 Keputusan yang diminta (dengan rekomendasi)

| # | Keputusan | Rekomendasi | Alasan |
|---|---|---|---|
| **R1** | Penyimpanan | **Kolom `TEXT[]` Postgres** (Exposed `array<String>`) dengan CHECK opsi | Menjaga pola *satu field = satu kolom* di `SpecColumns`; opsi tetap ditegakkan DB; Exposed 0.58.0 punya `ArrayColumnType` |
| **R2** | Bentuk nilai di lapisan prototype | **String JSON array**, urut mengikuti `options`, tanpa duplikat; kosong = `""`, `"[]"` ditolak | Opsi boleh memuat koma/spasi apa pun sehingga pemisah teks akan bentrok; JSON tak ambigu dan parsernya sudah dipakai semua codec |
| **R3** | Parameter | `maxSelections: Int? = null` saja; opsi memakai `options` yang sudah ada | Tidak menambah kosakata baru; sejajar `ENUM` |
| **R4** | Batas terhadap `ENUM`/status | `MULTI_SELECT` ≠ `statusField`, tidak ikut `StateMachine` | Status kerja berpindah lewat transisi; atribut berlabel ganda tidak |
| **R5** | CRM | **Tidak disentuh**; `MultiSelect` CRM tetap ditunda (D2) | Menyentuh CRM yang berjalan; nilainya baru terasa setelah tipe ke-6 |

> **Preseden yang belum ada:** repo ini belum punya kolom larik di migrasi mana pun maupun di generator.
> R1 adalah pemakaian pertama; risikonya dicatat di bagian Risiko dan ditutup oleh tes SQL/Exposed di A0.

### 4.2 Opsi yang ditolak

| Opsi | Mengapa ditolak |
|---|---|
| **Tabel tautan** (satu tabel per field `MULTI_SELECT`) | Memecah pola satu-tabel-per-entitas: `SpecColumns` harus menghasilkan banyak tabel, `SpecPostgresWriter` butuh transaksi dua tabel untuk menulis dan join untuk membaca. Untuk fitur berlabel "Sedang" itu perubahan generator yang besar; manfaat filter relasional belum dibutuhkan layar mana pun |
| **`TEXT` berisi JSON tanpa batasan DB** | Paling cepat dibuat, tetapi database tidak menjaga validitas opsi — melemahkan alasan tipe ini ada (nilai dipalsukan jadi teks bebas) |
| **`JSONB`** | Setara secara nilai (string JSON yang sama masuk tanpa konversi) dan CHECK `<@` dimungkinkan, tetapi butuh `exposed-json` + serializer di kode hasil generate yang belum dipakai generator mana pun; `TEXT[]` memakai tipe bawaan Exposed |
| **Pemisah koma/titik koma** | Bentrok dengan opsi yang memuat tanda itu; butuh escaping buatan sendiri |
| **Urutan klik dipertahankan** | Nilai sama menghasilkan string berbeda sehingga codec tidak byte-stabil dan tes paritas tidak bisa membandingkan |

### 4.3 Kontrak A0 final (tanda tangan persis — komit pertama Track A)

```kotlin
// core/.../domain/prototype/EntitySpec.kt
enum class FieldType { TEXT, LONG_TEXT, NUMBER, DATE, ENUM, MULTI_SELECT, BOOL }

data class FieldSpec(
    // ... parameter yang ada tidak berubah urutannya ...
    val maxSelections: Int? = null            // DITAMBAH di akhir (posisi argumen lama tidak bergeser)
) {
    init {
        // options: wajib & unik untuk ENUM dan MULTI_SELECT; kosong untuk tipe lain (pesan menyebut tipenya)
        // maxSelections: hanya MULTI_SELECT; bila diisi 1..options.size
    }
}

// file baru bertema: core/.../domain/prototype/MultiSelectValues.kt
object MultiSelectValues {
    fun parse(raw: String): List<String>?                       // null bila bukan array JSON string sah
    fun encode(selected: Collection<String>, options: List<String>): String   // urut menurut options, tanpa duplikat
    fun isValid(raw: String, options: List<String>, maxSelections: Int?): Boolean  // "" sah; "[]" tidak
}
```

- **Kawat:** kunci `maxSelections` (Int, opsional, bawaan null → tidak ditulis) di `ScreenProposalCodec`,
  `InteractiveScreenCodec`, `SpecOpCodec`; tipe JSON salah **ditolak** (pembaca ketat `JsonStrictReads`).
  Nama tipe `MULTI_SELECT` kini dikenali; daftar `unknownNames` di tes paritas codec harus **mengeluarkan**
  `MULTI_SELECT`, sedangkan nama lain (`FILE`, `RELATION`, `text`, …) tetap ditolak.
- **`FieldSpec.accepts`:** `MULTI_SELECT -> MultiSelectValues.isValid(value, options, maxSelections)`; tanpa `else`.
- **SQL (`SpecColumns.sqlDefinition`):**
  `TEXT[]$notNull CHECK (kolom <@ ARRAY['a','b']::text[])`; wajib → tambah `CHECK (cardinality(kolom) > 0)`;
  `maxSelections` → `CHECK (cardinality(kolom) <= N)`.
- **Exposed (`SpecPostgresWriter`):** `array<String>(name)`; tulis = `MultiSelectValues.parse(raw)`;
  baca = `MultiSelectValues.encode(list, options)` (string kanonik). Kolom opsional `nullable()`; `null` ↔ `""`.
- **Route scaffold (`SpecRoutesWriter`):** `MULTI_FIELDS` + `multiProblem` dipanggil di POST dan PUT, memanggil
  `FieldSpec.accepts` (pola `textProblem`, keputusan A0(C9)).
- **Validator usulan:** `ProposalEntityRules.checkOptions` memperlakukan `ENUM` dan `MULTI_SELECT` sama
  (opsi wajib, unik, ≤ `ProposalLimits.OPTIONS`); seed divalidasi lewat `MultiSelectValues`. `statusField`
  bertipe `MULTI_SELECT` **sudah ditolak** oleh `checkStatus` (`status.type != FieldType.ENUM`); A0 hanya
  menambah tes yang mengunci perilaku itu (FR-5), tanpa mengubah aturannya.
- **Operasi suntingan:** `SetFieldMaxSelections` (pola `SetFieldWithTime`) — Track A sisa, bukan A0.

### 4.4 Diagram alur (nilai dari klik sampai kolom)

```
UI chip (klik ganda)
  → daftar pilihan → MultiSelectValues.encode(urut menurut options)  → string "[\"Gigi\",\"Jantung\"]"
  → onValueChange(string) → PrototypeReducer.accepts() → MultiSelectValues.isValid()
  → route scaffold: multiProblem() → write: parse(raw) → List<String> → kolom TEXT[]
  ← read: kolom TEXT[] → encode(list, options) → string yang sama (byte-stabil)
```

### 4.5 Titik pendaftaran — hasil grep segar (2026-10-09)

`grep -rln "FieldType" core/src/commonMain server/src/main app/shared/src/commonMain` → 62 berkas; yang relevan
untuk kosakata **prototype** (CRM `domain/customfield/*` dan `crm/*` dikecualikan, D2):

| Lapisan | Titik (kompilator memaksa lewat `when` tanpa `else`) |
|---|---|
| Domain | `EntitySpec.kt` (enum, `init`, `accepts`), `PrototypeSpec.kt`, `PrototypeHints.kt` (`FieldHint`), `InteractiveScreenFactory.kt`, `ChangeWidgetOp.kt`, `SpecOpApplier.kt`, `FieldParamOps.kt`, `DeterministicSpecOpProposer.kt` |
| Usulan | `ProposalEntityRules.kt` (opsi, seed, status), `ProposalEdit.kt` (`accepts`, `sampleValue`), `ProposalViewRules.kt`, `DeterministicScreenProposer.kt` (`cardOf`), `DeterministicScreenRoles.kt`, `PackSuggestionMapping.kt`, `ScreenProposal.kt` |
| Codec | `ScreenProposalCodec`, `InteractiveScreenCodec`, `SpecOpCodec`, `ScreenSuggestionCodec` (`FieldHint`) |
| Generator | `SpecColumns.kt`, `SpecPostgresWriter.kt`, `SpecRoutesWriter.kt` |
| Agent | `KoogDiscoveryTools.kt` (`screen_catalog`), `KoogDiscoveryFieldTypeVocabulary.kt` (catatan per tipe, `when` tanpa `else`), `KoogDiscoveryPrompt.kt`, `KoogModuleEditor.kt` + `KoogModuleEditorFieldParams.kt` |
| UI | `FieldInput.kt`, `TableCell.kt`, `InlineRowEditor.kt`, `KanbanDetailDialog.kt`, `KanbanCardContent.kt`, `InteractiveFormState.kt`, `InteractiveTableState.kt`, `NumberFormatting.kt` (`displayValue`), `PrototypeChatEditPanel.kt` |

Ada penyebut `FieldType.ENUM` di ±40 tempat; tiap tempat yang memperlakukan `ENUM` sebagai "pilihan" perlu
diputuskan eksplisit apakah `MULTI_SELECT` ikut (mis. `ProposalEntityRules:50` `checkOptions` — ikut;
`ProposalEntityRules:68` status — **tidak, dan sudah ditolak hari ini**: baris itu menolak `statusField` yang
bukan `ENUM` dengan pesan "wajib field bertipe ENUM", jadi FR-5 terpenuhi tanpa kode baru dan A0 cukup menambah
tes; `DeterministicScreenProposer:54` membuat field status `ENUM` — tidak berubah).
**Jalankan ulang grep saat A0**, jangan mengandalkan daftar ini.

### 4.6 Rencana Track (pola §2 plan; A0 **berurutan** setelah A0 4a/4b bila Irisan 4 jalan lebih dulu — berbagi `EntitySpec.kt`)

| Track | Isi | Direktori |
|---|---|---|
| **A0** | Kontrak §4.3 + kawat + CHECK/Exposed + route scaffold + penegakan invarian + tes paritas; tanpa operasi suntingan | `core/` |
| **A sisa** | `SetFieldMaxSelections`, `ProposalEdit` (`reconcileFor`, `sampleValue`), `FieldHint`/`ScreenSuggestionCodec`, `DeterministicScreenProposer` (kartu), tes paritas lengkap | `core/` |
| **B** | Katalog + prompt (aturan `MULTI_SELECT` vs `ENUM`; tidak boleh jadi status), `KoogModuleEditor` menolak nilai salah, evaluasi deterministik, pemeriksaan route scaffold ter-check-in | `server/` |
| **C** | Kontrol chip pilihan ganda di `FieldInput` (+ tabel/kanban/dialog), komponen dasar `designsystem/` buta domain, `displayValue`, cek visual | `app/shared` |

## 5. Testing, Deployment, and Operations

### Technical Acceptance Criteria

- [ ] `FieldType.entries` memuat `MULTI_SELECT` dan **tidak ada** `else` baru pada `when (FieldType)`.
- [ ] `MultiSelectValues`: `""` sah; `"[]"` ditolak; elemen di luar `options`, duplikat, melebihi `maxSelections`, dan bukan
      array JSON ditolak; `encode` menghasilkan urutan sesuai `options` (deterministik).
- [ ] `maxSelections` pada tipe selain `MULTI_SELECT`, atau di luar `1..options.size`, ditolak.
- [ ] Tes mengunci bahwa `MULTI_SELECT` sebagai `statusField` ditolak validator usulan (aturan sudah ada di `checkStatus`).
- [ ] Round-trip di tiga codec dan dokumen draf lama byte-identik; nama tipe tak dikenal tetap ditolak.
- [ ] SQL: `TEXT[]` + CHECK opsi (+ `cardinality`); Exposed `array<String>`; tulis/baca round-trip string kanonik.
- [ ] Route scaffold memvalidasi nilai di POST dan PUT; `LayananChangeRequestRoutes` (ter-check-in) tidak berubah.
- [ ] Katalog agent dan prompt memuat tipe dan aturan pembeda dari `ENUM`; paritas dua arah terhadap `FieldType.entries`.
- [ ] Kontrol UI bekerja di ≥ 2 konteks (form dan sel tabel) pada pack **non-garment**; cek visual (login superadmin demo).

### Testing Strategy

- **Core (murni):** `MultiSelectValuesTest`, tes paritas `FieldType.entries`, SQL/Exposed (teks yang dihasilkan),
  kawat dan penolakan, pack klinik/bordir (pasien: alergi; pesanan: jenis bahan).
- **Integrasi Postgres (opt-in, DB scratch):** satu tes yang benar-benar membuat tabel hasil generate dengan
  kolom `TEXT[]`, menulis, membaca, dan melanggar CHECK — karena **ini pemakaian kolom larik pertama** dan
  tes yang hanya memeriksa teks SQL tidak membuktikan Exposed berperilaku benar. Memakai pagar scratch
  (`wemake_erp_scratch_test`).
- **Server:** pola `KoogDiscoveryNumberFormatTest` — katalog, prompt, validator, editor menolak.
- **UI:** logika murni (`displayValue`, penyusunan nilai); jelaskan batas bila tak merender Compose.

### Monitoring & Error Handling

- Tidak ada endpoint atau metrik baru. Nilai tak sah mengembalikan pesan galat yang sama seperti tipe lain
  (`Nilai '…' tidak sah untuk '<label>'`).

### Deployment & Rollback Plan

- Tipe baru bersifat aditif; dokumen lama tidak memuatnya. Rollback = tidak memakai tipe itu; kolom larik
  hanya muncul pada tabel hasil scaffold baru yang memuat field tersebut.

## Risiko

- **Kolom larik pertama di repo:** perilaku Exposed `array<String>` dengan driver/skema ini belum terbukti di
  proyek ini. *Mitigasi:* tes integrasi Postgres di A0 sebelum Track B/C dimulai; bila gagal, kembali ke opsi
  `TEXT` berisi JSON dengan CHECK `jsonb` (tanpa mengubah kontrak nilai prototype).
- **Filter/urut SQL sulit** pada kolom larik. *Diterima:* belum ada layar yang memfilter berdasarkan opsi;
  migrasi ke tabel tautan tetap mungkin karena kontrak nilai (string JSON array) tidak berubah — hanya generator.
- **Opsi yang berubah:** menghapus/mengganti nama opsi membuat nilai tersimpan tak lagi sah. *Mitigasi:* operasi
  suntingan yang mengubah `options` menolak bila ada seed yang melanggar (pola `FieldParamOps`); data runtime
  pengguna adalah urusan migrasi, di luar cakupan TRD ini.
- **Tampilan kartu kanban:** `CardStyle.BADGE` untuk `ENUM` akan menampilkan JSON mentah. *Mitigasi:* Track C
  menetapkan gaya tampil daftar label; bukan keputusan kontrak.
- **Tertukar dengan `ENUM`:** model memilih tipe salah. *Mitigasi:* aturan pembeda di katalog/prompt dan
  penolakan `statusField` oleh validator (FR-5).
- **Teks bahasa:** font Nunito tak punya glyph non-ASCII; label pilihan dan pemisah tampil hanya ASCII/Latin-1.

## Keputusan (menunggu user)

| # | Keputusan | Rekomendasi dokumen | Status |
|---|---|---|---|
| R1 | Penyimpanan | `TEXT[]` + CHECK | **Menunggu** |
| R2 | Bentuk nilai | String JSON array, urut menurut `options`, `""` kosong, `"[]"` ditolak | **Menunggu** |
| R3 | Parameter | `maxSelections: Int?` saja | **Menunggu** |
| R4 | Batas dengan `ENUM`/status | `MULTI_SELECT` ≠ `statusField` | **Menunggu** |
| R5 | CRM | Tidak disentuh (D2) | **Menunggu** |

Setelah R1–R5 disetujui, ubah status di §1 menjadi "Disetujui — gerbang lewat, A0 boleh dimulai" dan catat
revisi 0.2 di tabel riwayat (pola TRD-FIELD-001/002).
