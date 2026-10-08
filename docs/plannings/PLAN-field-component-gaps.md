# PLAN: Celah Komponen Input & Tipe Field — Daftar, Prioritas, dan Cara Menambahkannya

Dibuat 2026-10-08. Aturan pendaftaran: [`.claude/rules/field-component-rules.md`](../../.claude/rules/field-component-rules.md).
**Status: usulan.** Temuan §0 dibaca dari kode; yang belum diverifikasi ditandai. Belum ada TRD dan belum ada kode.

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

**Belum diverifikasi**: apakah semua `when (FieldType)` bebas `else`; jalur unggah file/penyimpanan objek yang sudah ada di server (diperlukan tipe `FILE`); apakah `FieldInput` dipakai juga di luar tiga konteks yang disebut KDoc-nya; perilaku `DATE` di tabel/kanban saat nilai tidak sah.

## 1. Daftar celah dan prioritas

| # | Komponen | Jenis (Kontrak 2) | Kosakata | Ukuran | Prioritas |
|---|---|---|---|---|---|
| C1 | **Date picker** untuk `DATE` | Kontrol baru, **tanpa ubah kosakata** | prototype + CRM | Kecil | **1** |
| C2 | **Tes paritas** iterasi tipe (Kontrak 7) | Pagar | keduanya | Kecil | **1** (sebelum tipe baru apa pun) |
| C3 | `LONG_TEXT` (teks panjang) | Tipe baru di prototype (CRM sudah punya) | prototype | Kecil | 2 |
| C4 | **Mata uang / persen** | Varian: parameter `format` pada `NUMBER` (bukan tipe baru) | prototype (CRM sudah) | Sedang | 2 |
| C5 | `MULTI_SELECT` | Tipe baru (penyimpanan beda: larik) | keduanya | Sedang | 3 |
| C6 | `TIME` / tanggal-waktu | Parameter `withTime` pada `DATE` (CRM sudah punya) | prototype | Kecil–sedang | 3 |
| C7 | `RELATION` (rujukan antar entitas) | Tipe baru, referensial | keduanya | **Besar** | 4 |
| C8 | `FILE` (unggah) | Tipe baru, butuh penyimpanan objek | keduanya | **Besar** | 4 |
| C9 | Format tervalidasi (email, telepon) | Parameter validasi pada `TEXT` | prototype | Kecil | 3 |

Alasan urutan: C1 dan C2 tidak mengubah kosakata sehingga tidak butuh keputusan besar dan menyiapkan pagar. C3–C6 dan C9 adalah
tipe/parameter sederhana. C7 dan C8 melibatkan integritas referensial dan penyimpanan objek, dan bersinggungan dengan
pagar J3 (`RELATION` antar modul harus lewat port, bukan JOIN lintas schema) sehingga butuh TRD sendiri.

## 2. Irisan kerja (Track A/B/C per irisan)

### Irisan 1 — Date picker + tes paritas (C1, C2)
| Track | Isi | Direktori |
|---|---|---|
| **A** | Tes paritas yang mengiterasi `FieldType.entries` (prototype) dan varian sealed (CRM): tiap tipe punya pemetaan SQL, kontrol input, entri katalog agent, round-trip codec. Tes dengan pack non-default | `core/commonTest`, `app/shared/commonTest` |
| **B** | Komponen `ClayDatePicker` di `designsystem/` (buta domain, token Clay, tanpa literal warna) yang mengembalikan string `TTTT-BB-HH`; dipakai `FieldInput` untuk `DATE` | `app/shared/presentation/designsystem`, `.../discovery/fields` |
| **C** | Terapkan juga di CRM (`LeadCustomField`, `AddCustomFieldDialog` untuk `DateField`); cek visual di dua konteks (form, sel tabel) dan di lebar sempit; teaching doc | `app/shared/presentation/crm` |

Catatan desain: date picker di Compose Multiplatform lintas 5 target tidak seragam (komponen Material 3 `DatePicker` ada tetapi
perilakunya per platform berbeda); **keputusan D1** di bawah.

### Irisan 2 — Tipe sederhana (C3, C4, C6, C9)
Tiap tipe/parameter mengikuti alur Kontrak 4 aturan: domain → codec (menolak nilai tak dikenal) → proposal rules →
generator SQL → katalog agent → `FieldInput` → tes paritas. Satu tipe per PR.

### Irisan 3 — `MULTI_SELECT` (C5)
Butuh keputusan penyimpanan (kolom larik vs tabel tautan) dan dampak ke generator; TRD ringkas.

### Irisan 4 — `RELATION`, `FILE` (C7, C8)
TRD sendiri; prasyarat: keputusan tentang rujukan lintas modul (hanya lewat port, TRD-PLAT-004 P4) dan penyimpanan objek.

## 3. Keputusan yang diminta

| # | Keputusan | Rekomendasi | Alasan |
|---|---|---|---|
| **D1** | Date picker: komponen Material 3 `DatePicker` atau komponen Clay buatan sendiri | **Clay buatan sendiri di `designsystem/`**, mengembalikan string | Bahasa visual Clay (outline tebal, hard shadow); perilaku seragam di 5 target; buta domain |
| **D2** | Menyatukan dua kosakata (prototype vs CRM) | **Tidak sekarang**; aturan berlaku per kosakata, tulis keputusan di tiap irisan | Menyentuh CRM yang berjalan; manfaat penyatuan baru terasa setelah tipe ke-6 |
| **D3** | Mata uang sebagai parameter `format` pada `NUMBER` (prototype) | **Ya** | Sejalan KDoc CRM: penyimpanan identik, hanya render yang beda |
| **D4** | Kontrak 8 (komponen belum ada ≠ dipalsukan jadi `TEXT`) berlaku untuk codec yang ada | **Ya**; periksa dulu apakah ada fallback senyap saat ini | Mencegah data berubah tanpa jejak |

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
