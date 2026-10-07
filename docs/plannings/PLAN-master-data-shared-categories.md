# PLAN — Master Data Bersama: Kategori Material sebagai Data + Kategorisasi Otomatis

**Status:** usulan, menunggu persetujuan · **Tanggal:** 2026-10-07 · **Penulis:** Agent B
**Terkait:** [PROPOSAL-iv-B6](parallel4/PROPOSAL-iv-B6-shared-module.md) (modul bersama), `tenant-variability-rules.md` Kontrak 1, 5, 8

> Tujuan satu kalimat: `master_data` menjadi modul **fondasi bersama** yang dipakai pack mana pun, dengan **kategori material berupa data** (template per pack, salinan per tenant), dan kategori **terisi otomatis** sehingga pengguna tidak perlu memahaminya.

---

## 1. Discovery Note

### 1.1 Kebutuhan
- **Siapa memakai:** admin gudang/pembelian (katalog bahan), staf costing dan teknik (lewat BOM/HPP), superadmin (kebijakan harga).
- **Data milik:** tenant (katalog item, harga, riwayat harga); kategori = definisi per tenant.
- **Berubah kapan:** kategori — sekali saat onboarding, jarang sesudahnya; item — terus-menerus.

### 1.2 Fitur serupa
- Perintah: `scripts/find-similar-feature.sh kategori category klasifikasi classify` dan graphify (`query_graph`).
- **Kategorisasi AI belum ada.** Pola terdekat yang ditiru: port penebak `InterviewGuesser` (deterministik + agent, **validator menegakkan**), kamus `DomainPack.roleHints`, `customfield/` (`CustomAttributes`, `FieldType`), kill-switch `DiscoveryAgents.kt`/`HelpAgents.kt`.
- Keputusan: **Mirip → tiru polanya**; kategori sebagai data = migrasi enum (Strangler Fig, Kontrak 8).

### 1.3 Jenis
**Foundation** (`master_data` sudah `ModuleKind.FOUNDATION`, `GLOBAL_ONLY`, tidak di kanvas). Tidak ada modul baru; kategori dan kategorisasi adalah **fitur dalam modul `master_data`**, mewarisi RBAC dan entitlement-nya.

### 1.4 Uji Variabilitas
| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|
| Himpunan kategori material | ya | ya (benang vs obat) | ya | **Data** | template di pack → salinan per tenant; kategori yang sudah dipakai item tidak boleh dihapus |
| Nama tampil kategori | ya | ya | ya | **Data** | boleh diganti kapan saja |
| Satuan dasar bawaan kategori | ya | ya | ya | **Data** | hanya default; item menyimpan satuannya sendiri |
| Awalan kode (`YRN`, `FAB`…) | ya | ya | jarang | **Data** | **beku setelah kode pertama terbit** (kode item tidak berubah) |
| Kosakata kategorisasi (kata → kategori) | ya | ya | tidak langsung | **Data** | kamus pack + dipelajari dari konfirmasi tenant |
| Daftar satuan ukur (`UnitOfMeasure`) | tidak | sebagian | tidak | Kode (besaran fisik milik sistem) | **celah:** dimensi yang ada hanya massa, panjang, hitungan; volume (liter, ml) belum ada |
| `PriceSource`, status | tidak | tidak | tidak | Kode (milik sistem) | — |

### 1.5 Core & extend
- **Core:** `core/.../domain/masterdata/` (494 baris): `MaterialItem` (punya `customAttributes`), `MaterialCategory` (enum 7 nilai: kode, nama, satuan bawaan, awalan), `CreateMaterialItemUseCase`, repository.
- **Titik extend:** `DomainPack` (kolom opsional kompatibel mundur, pola `roleHints`/`reservedTerms`), codec pack, tabel `master_data.*` (schema bernama kode modul, `ModuleSchemaMap`), pola konfigurasi per tenant (JSONB atau tabel, pola V71/V72).
- **Fakta DB:** `material_items.category` dan `material_code_sequences.category_code` sudah **string**, jadi kategori sebagai data **tidak butuh ubah tipe kolom**.
- **Pemakai `MaterialCategory` di luar paket masterdata (≥ 12 berkas):** UI master data & sampling, BOM tech pack (`BomLineEditorDialog`, `BomCostPreview`), kontrak (`MaterialRef`, `TechPackAndYieldData`, `SampleSpecToTechPackAdapter`), `ApprovedSampleSpecificationMapper`.
- **Jangan disentuh:** file di tabel utang file-size; kelola ratchet tiap PR (`wc -l` sebelum/sesudah).

### 1.6 I/O & kanvas
- Port: **tidak ada** (fondasi, bukan node kanvas). Perlu dicek: `providedReferenceTypes` `master_data` di `FoundationModuleCatalog` (belum dibaca).
- Telemetri: tidak berlaku.

### 1.7 Governance
| Operasi | Level minimum | Peran yang ditolak (dites 403) |
|---|---|---|
| Lihat katalog, kategori | VIEW (+ modul pemakai: costing, sampling, tech pack) | peran tanpa akses `MASTER_DATA` |
| Tambah/ubah item, minta saran kategori | OPERATE | peran VIEW-only |
| Kelola himpunan kategori (tambah/ubah/hapus) | MANAGE | OPERATE dan di bawahnya |
| Kebijakan harga | MANAGE (sudah ada) | OPERATE ke bawah |
| Kill-switch AI | superadmin platform | semua peran tenant |
- Gate: `MASTER_DATA` (`moduleGate`, tulis fail-closed) · `ScopeCapability.GLOBAL_ONLY` · entitlement ikut modul.

### 1.8 Ukuran → TRD?
Agregat baru (definisi kategori per tenant), migrasi tabel dan backfill, ≥ 12 pemakai enum → **TRD perlu** (`TRD-MDATA-001`, dibuat di P0).

---

## 2. Rancangan inti

### 2.1 Kategori sebagai data
```kotlin
@JvmInline value class MaterialCategoryCode(val value: String)          // slug, kunci tersimpan; parser tunggal, tolak bukan fallback
data class MaterialCategoryDefinition(val code: MaterialCategoryCode, val displayName: String,
                                      val defaultUom: UnitOfMeasure, val codePrefix: String)
DomainPack.materialCategories: List<MaterialCategoryDefinition>          // template pack, opsional (kosong = tak ada template)
TenantMaterialCategories                                                 // salinan per tenant, tabel master_data.material_categories (+RLS)
```
- Template garment = **tujuh kategori sekarang, identik** (kode, nama, satuan, awalan). Dikunci tes paritas yang **mengiterasi enum**.
- Setiap tenant mendapat satu kategori sistem **"Belum dikategorikan"** supaya pengisian tidak pernah terblokir.
- Tidak ada fallback senyap: kode kategori tak dikenal **ditolak** berpath.

### 2.2 Kategorisasi otomatis — pendapat saya
**Ya, dan itu pilihan yang benar**, dengan enam pagar:
1. **Himpunan tertutup, bukan teks bebas.** AI memilih dari kategori tenant. Kategori baru hanya sebagai **usulan** yang dikonfirmasi sekali. Tanpa ini katalog pecah ("Benang", "benang jahit", "Thread").
2. **Deterministik dulu, AI untuk sisanya.** Kamus kata→kategori per pack (pola `roleHints`) menangani kasus jelas secara gratis dan konsisten; AI hanya untuk yang tak dikenali. Lebih murah, cepat, dan bisa dites tanpa LLM.
3. **Kategori bukan hiasan di sini**, ia menentukan awalan kode, satuan bawaan, dan filter. Karena itu hasil otomatis membawa `source` (`RULE` / `AI` / `USER`) dan `confidence`. Di atas ambang → terisi sendiri; di bawah → tetap terisi "Belum dikategorikan" lalu masuk antrean tinjau. **Tidak pernah memblokir pembuatan item.**
4. **Konfirmasi mengajar sistem.** Koreksi pengguna masuk kamus tenant, jadi makin lama makin jarang memanggil AI (pola buku demand).
5. **Kode terbit tidak berubah** bila kategori dikoreksi kemudian (awalan beku setelah dipakai); yang berubah hanya kategorinya.
6. **Privasi dan biaya:** hanya nama dan deskripsi item yang dikirim (tanpa harga, pemasok, atau tenant); cache per nama ternormalisasi; kill-switch env; tes otomatis tidak memanggil LLM; eval live opt-in dengan estimasi biaya digandakan marginnya.

Dua fungsi AI yang berbeda, jangan dicampur:
- **A. Usulan himpunan kategori** untuk pack/tenant baru (dari cerita atau wawancara) — jarang, dikonfirmasi pemilik.
- **B. Penentuan kategori item** (satu per satu dan impor massal) — sering, otomatis.

Mutu diukur dengan **% tebakan yang diterima tanpa diubah** dan set emas per pack (garment, klinik, bengkel), bukan jumlah kategori.

---

## 3. Tahap pengerjaan
| Tahap | Isi | Gerbang |
|---|---|---|
| **P0** | Persetujuan plan, `TRD-MDATA-001`, keputusan §5 | keputusan terbuka terjawab |
| **P1** | Katalog kategori sebagai data: tipe, `DomainPack.materialCategories` + codec, tabel + backfill 7 kategori ke tenant yang ada, template garment identik | tes paritas iterasi enum; pack lama terbaca |
| **P2** | Pindahkan pembaca satu paket per PR: masterdata (domain + server) → BOM/tech pack → sampling → kontrak → UI | tiap PR: kode item, satuan bawaan, awalan **identik** untuk tenant garment; pemindai "jembatan yang bisa melempar" kosong |
| **P3** | Tenant kedua: pack klinik/bengkel dengan kategorinya sendiri; putuskan celah satuan (volume) | tes template non-default; **cek mata** di tenant non-garment |
| **P4** | Netralkan teks `master_data`, bagikan lewat salinan identik (pola B6/invoicing) | paritas garment; tes salinan identik |
| **P5** | Kategorisasi deterministik: kamus pack + kamus tenant, port `MaterialCategorizer`, endpoint saran (OPERATE), field kategori terisi otomatis di UI | deterministik byte-per-byte; antrean "Belum dikategorikan"; 403 peran VIEW |
| **P6** | Agent AI (Koog) di belakang port yang sama + eval (set emas ≥ 10 kasus per pack) + kill-switch + impor massal | % diterima dilaporkan; validator menolak kategori di luar himpunan |
| **P7** | Pembersihan: hapus enum, audit variabilitas, teaching doc | `scripts/audit-variability.sh` 0 temuan baru |

Urutan P1→P2→P3 wajib berurutan (Strangler Fig); P5/P6 baru setelah P3 supaya kategorisasi tidak dibangun di atas enum.

## 4. Verifikasi (setiap tahap)
`./gradlew :core:jvmTest` · kompilasi server + `:app:shared` JVM/WasmJS/JS · tes server di DB scratch · tes **dokumen lama** (buang kolom baru, harus tetap sah — pelajaran regresi draf garment) · tes peran tak berwenang (403) · cek visual di tenant non-garment · teaching doc.

## 5. Keputusan yang dibutuhkan
1. **Kategori wajib atau opsional?** Usul: tetap wajib di DB, tapi **selalu terisi otomatis** (fallback "Belum dikategorikan").
2. **Ambang auto-terapkan** (confidence) dan apakah di bawah ambang masuk antrean tinjau atau tetap "Belum dikategorikan" saja.
3. **Satuan ukur:** perluasan dimensi (volume) masuk plan ini (P3) atau plan terpisah? Dimensi yang terlihat hanya massa, panjang, hitungan.
4. **Admin tenant boleh mengubah himpunan kategori sendiri** (MANAGE), atau hanya pemilik pack/superadmin?
5. **Kebijakan teks `master_data`:** setelah dibagikan, nama/deskripsi tunggal untuk semua pack, atau label per pack seperti `ModuleReference.label`?

## 6. Risiko
| Risiko | Mitigasi |
|---|---|
| Kode item/satuan tenant garment berubah diam-diam saat migrasi | template identik + tes paritas iterasi enum + backfill diverifikasi |
| ≥ 12 pemakai enum: PR besar sulit direview | satu paket per PR; ratchet ukuran file; `wc -l` dicatat |
| AI salah kategori → awalan/satuan keliru | `source`+`confidence`, ambang, antrean tinjau, kode tak berubah saat koreksi |
| Biaya LLM tak terduga | deterministik dulu, cache, kill-switch, eval live opt-in dengan margin 2× |
| Kategori menjamur/ganda | himpunan tertutup; kategori baru hanya via usulan terkonfirmasi |
| Draf/pack lama tertolak "wajib identik" | kolom aditif opsional + tes dokumen lama |
