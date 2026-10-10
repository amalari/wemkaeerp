# PLAN: Penyatuan Kosakata Tipe Field (keputusan D2 — diputuskan 2026-10-10)

Discovery Note untuk keputusan D2. Aturan pendaftaran: [`.claude/rules/field-component-rules.md`](../../.claude/rules/field-component-rules.md).
Status lama ("belum diputuskan") dicabut oleh pemilik: **CRM belum dipakai user nyata**, jadi penahan utama D2 ("menyentuh CRM yang berjalan") gugur.

## 1. Bukti premis (dibaca dari DB, 2026-10-10)

- `crm_leads`: **1 baris** — hanya tenant demo `ten-demo-001` (lead uji "Test").
- `custom_field_definitions`: 18 baris di 3 tenant demo, tipe tersimpan hanya `CHECKBOX, SINGLE_SELECT, TEXT`.
- Tidak ada definisi DATE/RELATION/FILE/USER_REF/MULTI_SELECT yang tersimpan; tidak ada tenant non-demo.

## 2. Keputusan arah

**CRM migrasi ke kosakata prototype** (`core/.../prototype/EntitySpec.kt` `FieldType` enum, **registry tunggal**), bukan sebaliknya. Alasan:

1. Sisi prototype punya rantai Kontrak 4 lengkap: katalog agent (`KoogDiscoveryFieldTypeVocabulary`), generator SQL (`SpecColumns`), tes paritas, dan pipa pendaftaran — enum-nya adalah hub.
2. CRM tidak memakai `FieldSpec` mentah (bentuk opsinya beda — lihat §2a), jadi CRM mendapat **karier tipis** `CrmFieldType(kind: FieldType, …)` di `core/.../customfield/CrmFieldType.kt`: `kind` menunjuk registry bersama, parameter khas CRM (opsi `SelectOption` berwarna+arsip, `maxCount`, `NumberFormat` CRM) tinggal di karier. Sealed `FieldType` CRM dihapus.

### 2a. Koreksi (ditemukan saat pelaksanaan)

Klaim awal "`FieldSpec` superset CRM" **tidak akurat**. Yang benar: kosakata discovery sengaja tipis (opsi = `List<String>`, tanpa `maxCount`), CRM kaya (opsi berwarna + arsip lembut, `maxCount`, `NumberFormat` sendiri). Karena itu penyatuan dilakukan pada **registry tipe** (enum), bukan pada muatan parameter. Penyatuan parameter (satu `NumberFormat`, satu `SelectOption`, `maxCount` ke `FieldSpec`) **ditunda** — dicatat sebagai follow-up; tidak menghalangi manfaat utama (satu tempat pendaftaran tipe).

## 3. Celah yang wajib ditutup A0 (kontrak, sebelum pelaksana) — ✅ SELESAI

| Celah | Penyelesaian A0 |
|---|---|
| CRM punya `UserRef` — prototype tidak punya padanannya | ✅ `FieldType.USER_REF` + rantai Kontrak 4 (kolom SQL `VARCHAR(120)`, kontrol FieldInput, catatan katalog) |
| CRM `Number` punya `decimals` — prototype tidak | ✅ parameter `decimals` pada `FieldSpec` NUMBER (`0..6`, wire `FieldParamWire`) |
| Kode tersimpan berbeda: CRM `SINGLE_SELECT`/`CHECKBOX` vs enum `ENUM`/`BOOL` | ✅ `CrmLegacyTypeCode` — satu parser, kode legacy diterima selamanya; **tanpa migrasi data** |
| Kosakata CRM tak punya `TIME`/`MULTI_SELECT` | ✅ diwarisi otomatis: validasi (`CustomFieldValidation`), kontrol UI (`TIME_PICKER`→`ClayTimePicker`, `MULTI_CHOICE`→`ClayMultiChoiceChips`), opsi dialog "Jam" |

## 3a. Hasil pelaksanaan (2026-10-10)

- Registry tunggal: `CrmFieldType.kind` = `FieldType` (11 tipe). Sealed CRM dihapus; `FieldType.kt` → `CrmFieldType.kt`.
- Kunci wire config **tidak berubah** (`withTime`, `format`, `currencyCode`, `decimals`, `maxCount`, `targetResource`, `options`); kunci baru hanya `maxSelections` (MULTI_SELECT) dan `decimals` ditulis hanya bila dibatasi.
- Sel MULTI_SELECT CRM = **larik JSON id opsi aktif** (aturan bentuk dipegang `MultiSelectValues`, di sini id diteruskan) — konsisten dengan prototipe yang memakai nama opsi.
- `PreviewFieldTypeChangeUseCase`/`FieldTypeConversion` sadar-parameter (ganti `withTime` = LOSSY; jenis sama = IDENTITY).

Sisa follow-up (bukan penghalang):
1. Opsi **SingleSelect/MULTI_SELECT di dialog "Tambah kolom"** CRM belum ditawarkan (butuh editor opsi berwarna) — pola sama seperti sebelum penyatuan; field-nya tetap terbaca & tervalidasi.
2. Penyatuan muatan parameter (§2a): satu `NumberFormat`, `SelectOption`, `maxCount`→`FieldSpec`.
3. Cek visual CRM dengan field TIME/MULTI_SELECT di tenant uji (dialog + inspektur).

## 4. Irisan pelaksanaan (track per direktori, boleh paralel setelah A0)

| Track | Isi | Direktori |
|---|---|---|
| **A0** | `FieldType.USER_REF` + `decimals` NUMBER + wire + test paritas; parser kompatibilitas kode CRM | `core/.../prototype`, `core/.../customfield` (parser saja) |
| **A (CRM core)** | `CustomFieldValidation`, `CustomAttributesCodec`, `FieldTypeConversion`, `AddCustomFieldDefinitionUseCase`, `PostgresCustomFieldDefinitionRepository` → enum bersama; `CrmFieldTypeParityTest` iterasi enum; kode legacy tetap terbaca | `core/.../customfield`, `server/.../infrastructure` (repo CRM) |
| **C (CRM UI)** | `AddCustomFieldDialog`, `LeadFieldControl`, `LeadCustomField*`, `LeadFormState` → enum bersama; hapus sealed `FieldType` CRM; `LeadFieldControlParityTest` | `app/shared/.../presentation/crm` |
| **Integrasi** | Kompilasi 3 target + `:server:test` + `:app:shared:jvmTest` + `:core:jvmTest`; teaching doc satu per orang integrasi | — |

Aturan tetap: tanpa `else` pada `when`, codec menolak nilai tak dikenal, ratchet ukuran file, dan **tidak ada migrasi data** — parser kompatibilitas yang menyesuaikan (Kontrak 4).

## 5. Di luar rencana ini

- Menambah tipe baru ke kosakata (mis. Formula, Mirror/Rollup) — tetap lewat pipa Kontrak 4 biasa, kini satu tempat.
- Mengubah penyimpanan sel nilai CRM (bertag) — bukan bagian penyatuan.
