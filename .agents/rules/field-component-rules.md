# WeMade ERP — Aturan Pendaftaran Komponen Input Bersama & Tipe Field

Status sejajar dengan [`design-system-rules.md`](design-system-rules.md) (bagaimana rupanya),
[`module-integration-rules.md`](module-integration-rules.md) (apa yang dikerjakan), dan
[`tenant-variability-rules.md`](tenant-variability-rules.md) (kode vs data). Dokumen ini menjawab satu pertanyaan:
**saat sebuah komponen input atau tipe field bersama dibuat, ke mana ia wajib didaftarkan supaya seluruh sistem —
renderer, validator, generator kode, dan agent AI — mengenalnya?**

Lahir dari temuan 2026-10-08: tipe `DATE` hanya dirender sebagai kolom teks (`TTTT-BB-HH`), tidak ada `DatePicker`
di seluruh `app/shared`, dan kosakata tipe field tersebar di dua tempat yang tidak saling mengenal. Komponen yang
"ada di UI tapi tidak terdaftar di kosakata" tidak bisa dipilih agent, tidak punya tipe kolom SQL, dan tidak
divalidasi — ia hanya terlihat ada.

---

## 1. Dua Kosakata Tipe Field (jangan tertukar)

| Kosakata | Lokasi | Dipakai untuk | Bentuk |
|---|---|---|---|
| **Prototype / Builder** | `core/.../domain/prototype/EntitySpec.kt` `enum class FieldType` (`TEXT, NUMBER, DATE, ENUM, BOOL`) | Layar prototype, draf pack hasil agent, generator kode modul (SQL, route) | enum tertutup |
| **CRM custom field** | `core/.../domain/customfield/FieldType.kt` `sealed interface FieldType` (Text, LongText, Number, SingleSelect, DateField, Checkbox, UserRef, …) | Kolom kustom per tenant di modul CRM | sealed hierarchy ber-parameter |

Keduanya sengaja terpisah hari ini. Menambah tipe ke salah satunya **tidak otomatis** menambah ke yang lain, dan
itu keputusan yang harus ditulis, bukan terjadi karena lupa (lihat §6).

---

## 2. Aturan Wajib

### Kontrak 1 — Komponen common tidak boleh berdiri sendiri
Komponen input bersama baru (date picker, input mata uang, pilihan ganda, unggah file, …) **wajib didaftarkan ke
kosakata tipe field yang relevan dalam PR yang sama**. "Komponennya dulu, daftarnya nanti" ditolak. Komponen yang
tidak terdaftar adalah kode mati bagi agent dan generator.

### Kontrak 2 — Tipe baru atau varian? (tangga keputusan)
1. **Hanya beda tampilan atau format** → *bukan tipe baru*. Jadikan parameter pada tipe yang ada. Contoh yang sudah
   dipakai: mata uang = `Number(format = Currency)`, bukan tipe `Currency` (alasan di KDoc `customfield/FieldType.kt`:
   penyimpanan, filter, urutan, dan koersi identik).
2. **Beda cara menyimpan, memvalidasi, atau mengurutkan** → tipe baru.
3. **Beda hanya di kontrol UI untuk tipe yang sama** (mis. `DATE` dari kolom teks menjadi date picker) → **tidak
   mengubah kosakata**; ganti kontrolnya di komponen bersama (`FieldInput`) dan tulis di Discovery Note.

### Kontrak 3 — Satu pintu komponen input
Kontrol input untuk tipe field hidup di **satu tempat** per kosakata: untuk prototype
`presentation/discovery/fields/FieldInput.kt` (dipakai Form Blok, Form Inline Tabel, Dialog Detail Kanban); komponen
dasarnya di `presentation/designsystem/`. Dilarang menulis kontrol input tipe field di layar fitur — Aturan Tiga Kali
(`design-system-rules.md` Kontrak 4). Komponen `designsystem/` buta domain (menerima `String`/`Color`/lambda).

### Kontrak 4 — Titik pendaftaran tipe baru (kosakata prototype)
Cari semua penyebutnya sebelum menulis kode — daftar di bawah dari `grep` 2026-10-08, **jalankan ulang, jangan
mengandalkan daftar ini**:

```bash
grep -rln "FieldType" --include='*.kt' core/src/commonMain server/src/main app/shared/src/commonMain
```

| Lapisan | Titik | Yang dipastikan |
|---|---|---|
| Domain | `EntitySpec.kt` (enum, validasi `FieldSpec`), `PrototypeSpec.kt`, `InteractiveScreenFactory.kt`, `ChangeWidgetOp.kt`, `SpecOpApplier.kt`, `DeterministicSpecOpProposer.kt` | Nilai tipe baru valid di spec, dapat diubah lewat `SpecOp`, dan reducer menegakkan validasinya |
| Usulan layar | `proposal/ProposalEntityRules.kt`, `ProposalEdit.kt`, `ProposalViewRules.kt`, `DeterministicScreenProposer.kt`, `DeterministicScreenRoles.kt`, `PackSuggestionMapping.kt` | Validator usulan mengenal tipe; usulan deterministik tidak menghasilkan tipe yang tak didukung renderer |
| Codec | `shared/pack/InteractiveScreenCodec.kt`, `SpecOpCodec.kt`, `ScreenSuggestionCodec.kt`, `shared/discovery/ScreenProposalCodec.kt` | Dokumen berisi tipe baru terbaca; **nilai tak dikenal ditolak** (Kontrak 4 variability), bukan jatuh ke `TEXT` |
| Generator kode | `handoff/SpecColumns.kt` (tipe kolom SQL), `SpecPostgresWriter.kt`, `SpecRoutesWriter.kt` | Tipe punya pemetaan SQL, tulis, dan baca |
| Agent AI | `infrastructure/discovery/KoogDiscoveryTools.kt` (`screen_catalog`), `KoogDiscoveryPrompt.kt`, `infrastructure/builder/KoogModuleEditor.kt` | Katalog yang dibaca model memuat tipe baru dan aturan pemakaiannya; prompt tidak menyebut daftar tipe yang basi |
| UI | `presentation/discovery/fields/FieldInput.kt`, `TableCell.kt`, `InlineRowEditor.kt`, `KanbanDetailDialog.kt`, `InteractiveFormState.kt`, `InteractiveTableState.kt` | Setiap konteks (form, sel tabel, kartu kanban) tahu menggambar dan menyunting tipe baru |

### Kontrak 5 — Titik pendaftaran tipe baru (CRM custom field)
`customfield/FieldType.kt`, `CustomFieldValidation.kt`, `CustomAttributesCodec.kt`, `FieldTypeConversion.kt`
(aturan konversi antar tipe), `AddCustomFieldDefinitionUseCase.kt`, `PostgresCustomFieldDefinitionRepository.kt`
(kolom `field_type`), serta UI `AddCustomFieldDialog.kt` dan `LeadCustomField*.kt`. Tipe baru yang butuh integritas
referensial memakai `isReferential`; **tidak** ada migrasi skema untuk tipe tanpa penyimpanan baru.

### Kontrak 6 — Kompilator menjaga, bukan ingatan
`when (fieldType)` pada kosakata mana pun **dilarang memakai `else`**. Cabang yang dipaksa kompilator itulah daftar
titik pendaftaran yang tidak bisa terlewat. Tipe baru yang membuat kompilasi gagal di sebuah `when` berarti titik itu
memang harus disentuh — jangan "menenangkan" kompilator dengan `else`.

### Kontrak 7 — Tipe baru wajib punya tes paritas
- Tes yang **mengiterasi `FieldType.entries`** (atau varian sealed-nya) dan memastikan tiap tipe punya: pemetaan SQL,
  kontrol input, entri katalog agent, serta round-trip codec. Entri baru tanpa padanan → tes gagal.
- Tes tenant/kontekst kedua: tipe baru diuji di minimal **dua** konteks (mis. form dan sel tabel) dan pada pack
  non-default.

### Kontrak 8 — Komponen yang belum ada tidak boleh dipalsukan
Bila kebutuhan di luar kosakata (mis. unggah file belum didukung), **jangan** memetakannya diam-diam ke `TEXT`.
Pilihannya: (a) tolak dengan pesan yang jelas, (b) pakai `CUSTOM_SCREEN` sebagai sketsa kerangka, atau (c) ajukan
sebagai permintaan pembuatan komponen lewat antrean build (TRD-PLAT-006/007). Memetakan ke `TEXT` mengubah data
tanpa jejak.

**Pengecualian sah: `CUSTOM_SCREEN` sebagai kerangka (C10).** Layar yang butuh komponen belum ada boleh diusulkan
sebagai `ViewProposal.Skeleton` berisi `SkeletonBlock` (label singkat, lebar `FULL`/`HALF`, hint dari daftar tertutup
`TABLE`/`FORM`/`METRIC_CARDS`/`ACTIONS`). Ini cara sah **menolak tanpa memalsukan**: sketsa non-interaktif untuk
dinilai, bukan data yang tersimpan sebagai tipe lain. Batasnya: kerangka **bukan tipe field** dan tidak
menggantikan entri di kosakata mana pun; field tetap wajib bertipe yang terdaftar (tak dikenal ditolak). Hint/lebar
tak sah ditolak oleh codec (bukan fallback senyap), dan kerangka hanya berlaku untuk `CUSTOM_SCREEN`.

---

## 3. Alur Kerja Saat Menambah Komponen/Tipe

1. **Discovery** (`wemade-feature-discovery`): komponen ini tipe baru, varian (Kontrak 2), atau hanya kontrol baru?
   Siapa yang memakainya (prototype, CRM, keduanya)? Tulis di Discovery Note.
2. **Kosakata dulu**: tambahkan tipe/parameter + validasi di domain, biarkan kompilator menunjukkan titik yang
   terlewat (Kontrak 6).
3. **Satu pintu UI**: kontrolnya di komponen bersama + dasarnya di `designsystem/` (Kontrak 3), ikuti
   `design-system-rules.md` (token, Clay, tanpa literal warna).
4. **Agent**: perbarui `screen_catalog` dan aturan prompt; jalankan evaluasi agent deterministik (tanpa LLM berbayar).
5. **Generator**: pemetaan SQL + route; coba hasil scaffold pada modul uji.
6. **Tes paritas** (Kontrak 7) dan cek visual di dua konteks.
7. **Dokumentasi**: teaching doc (CLAUDE.md §12); bila aturan berubah → `scripts/sync-agent-config.sh`.

---

## 4. Anti-Pola yang Ditolak

- Komponen input baru ditulis langsung di layar fitur tanpa lewat komponen bersama.
- Tipe baru ditambahkan di UI saja (agent dan generator tidak tahu).
- `else ->` pada `when (FieldType)`.
- Nilai tipe tak dikenal dibaca sebagai `TEXT`.
- Menambah tipe untuk sekadar perbedaan format (mata uang, persen, telepon) — jadikan parameter.
- Mengubah salah satu kosakata dan mengira yang lain ikut berubah tanpa menuliskan keputusannya.

---

## 5. Checklist Verifikasi Sebelum Merge (Definition of Done)

- [ ] Discovery Note menyatakan tipe baru / varian / kontrol baru, dan kosakata mana yang terdampak
- [ ] `grep -rln "FieldType"` dijalankan ulang; setiap penyebut ditinjau
- [ ] Tidak ada `else ->` baru pada `when (FieldType)`
- [ ] Kontrol input hanya di komponen bersama; tidak ada literal warna/bentuk (design-system-rules)
- [ ] Codec menolak nilai tak dikenal; tidak ada fallback senyap ke `TEXT`
- [ ] Katalog agent (`screen_catalog`) dan aturan prompt memuat tipe baru
- [ ] Generator kode: pemetaan SQL, tulis, dan baca; scaffold diuji pada modul uji
- [ ] Tes paritas yang mengiterasi tipe; tes di ≥ 2 konteks dan pack non-default
- [ ] Kompilasi 5 target; cek visual (login superadmin demo, lalu tenant uji non-garment)
- [ ] Teaching doc dibuat; `scripts/sync-agent-config.sh` dijalankan bila aturan berubah

---

## 6. Keputusan Kosakata (diputuskan 2026-10-10)

- **Dua kosakata DISATUKAN — CRM migrasi ke kosakata prototype** (`EntitySpec.FieldType` + parameter `FieldSpec`).
  Diputuskan pemilik dengan premis terverifikasi: CRM belum dipakai user nyata (1 lead demo; definisi tersimpan
  hanya CHECKBOX/SINGLE_SELECT/TEXT). Rencana: [`docs/plannings/PLAN-unify-field-vocabulary.md`](../../docs/plannings/PLAN-unify-field-vocabulary.md).
  Selama migrasi: parser kompatibilitas kode legacy CRM (`SINGLE_SELECT`→ENUM, `CHECKBOX`→BOOL) wajib ada di satu
  tempat dan kode lama tetap terbaca (Kontrak 4 — tanpa migrasi data). Setelah selesai, aturan pendaftaran Kontrak 4
  berlaku SATU kosakata saja; CRM mewarisi tipe baru (TIME, USER_REF) otomatis.
- ~~Tes paritas Kontrak 7 belum ada untuk kedua kosakata~~ — sudah ada untuk keduanya
  (`PrototypeFieldType*ParityTest`, `CrmFieldTypeParityTest`, `LeadFieldControlParityTest`).
