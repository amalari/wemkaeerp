# PLAN: Penyatuan Kosakata Tipe Field (keputusan D2 — diputuskan 2026-10-10)

Discovery Note untuk keputusan D2. Aturan pendaftaran: [`.claude/rules/field-component-rules.md`](../../.claude/rules/field-component-rules.md).
Status lama ("belum diputuskan") dicabut oleh pemilik: **CRM belum dipakai user nyata**, jadi penahan utama D2 ("menyentuh CRM yang berjalan") gugur.

## 1. Bukti premis (dibaca dari DB, 2026-10-10)

- `crm_leads`: **1 baris** — hanya tenant demo `ten-demo-001` (lead uji "Test").
- `custom_field_definitions`: 18 baris di 3 tenant demo, tipe tersimpan hanya `CHECKBOX, SINGLE_SELECT, TEXT`.
- Tidak ada definisi DATE/RELATION/FILE/USER_REF/MULTI_SELECT yang tersimpan; tidak ada tenant non-demo.

## 2. Keputusan arah

**CRM migrasi ke kosakata prototype** (`core/.../prototype/EntitySpec.kt` `FieldType` enum + parameter di `FieldSpec`), bukan sebaliknya. Alasan:

1. Sisi prototype punya rantai Kontrak 4 lengkap: katalog agent (`KoogDiscoveryFieldTypeVocabulary`), generator SQL (`SpecColumns`), tes paritas, dan pipa pendaftaran — enum-nya adalah hub.
2. `FieldSpec` sudah membawa parameter superset CRM: `options`, `format`, `currencyCode`, `withTime`, `validation`, `target`, `maxSelections`.
3. CRM sealed `FieldType` tinggal digantikan; penyimpanan nilai (sel bertag `text`/`date`/…) tidak berubah sama sekali.

## 3. Celah yang wajib ditutup A0 (kontrak, sebelum pelaksana)

| Celah | Penyelesaian A0 |
|---|---|
| CRM punya `UserRef` — prototype tidak punya padanannya | Tambah `FieldType.USER_REF` + rantai Kontrak 4 (kolom SQL `VARCHAR`, kontrol UI pemilih user, catatan katalog) |
| CRM `Number` punya `decimals` — prototype tidak | Tambah parameter `decimals` pada `FieldSpec` NUMBER (opsional, wire `FieldParamWire`) |
| Kode tersimpan berbeda: CRM `SINGLE_SELECT`/`CHECKBOX` vs enum `ENUM`/`BOOL` | **Satu parser kompatibilitas** (Kontrak 4): kode legacy CRM → enum, diterima selamanya; tulisan baru memakai nama enum. Tidak ada migrasi data — parser yang menyesuaikan |
| Kosakata CRM tak punya `TIME` | Setelah migrasi, CRM mewarisi `TIME` (dan tipe baru berikutnya) otomatis |

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
