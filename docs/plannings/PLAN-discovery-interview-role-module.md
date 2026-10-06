# PLAN — Wawancara Discovery: Divisi → Peran → Modul + Fitur (+ modul bersama)

**Tanggal:** 2026-10-07 · **Status:** usulan untuk ditinjau (belum ada kode) · **Induk:** [PLAN-screen-proposal-contract-koog](PLAN-screen-proposal-contract-koog.md)
**Asal:** arahan produk 2026-10-07 — dari cerita pengguna, tentukan dulu divisi apa saja, ubah alurnya menjadi modul dan fitur, tanyakan peran mana menangani modul mana (untuk menghubungkan), perkirakan sendiri lalu minta konfirmasi (wawancara), dan pakai ulang modul yang sudah ada bila cocok (mis. keuangan, data port bisa berbeda).

---

## 0. Discovery Note (template `wemade-feature-discovery`)

### 1. Kebutuhan
- **Siapa memakai:** calon klien / pemilik usaha di Studio Discovery (`/discovery`); superadmin untuk meninjau.
- **Data milik:** draf discovery (`ops.discovery_drafts`, platform-global, per pemilik) — **bukan** data tenant produksi.
- **Berubah kapan:** sekali per draf, berulang selama sesi wawancara; hasil beku saat draf dikunci (Kontrak 5).

### 2. Fitur serupa
- `scripts/find-similar-feature.sh wawancara interview divisi department role` + graphify (`Department`, `DepartmentModuleAssignment`, `CustomRole`, `DiscoveryDraft`, `ScreenProposal`).
- **Sudah ada (dipakai ulang, bukan dibuat paralel):** `Department` + `DepartmentTier` (divisi per tenant), `DepartmentModuleAssignment` (divisi/jabatan ↔ modul, akses, scope), `CustomRole` (akses modul per jabatan), buku demand (`DiscoveryDemand`: modul terwakili vs istilah belum), agent Koog + validator + `ScreenProposal`.
- **Belum ada:** percakapan bertahap (wawancara); divisi/peran sebagai bagian **draf** discovery; asal modul (pakai ulang / kembangkan / baru); referensi modul platform dengan adaptor port.
- **Keputusan:** **Mirip → tiru pola** `DiscoveryDraft` + codec + validator (draf tetap satu dokumen berversi) dan `DepartmentModuleAssignment` (bentuk hasil wawancara).

### 3. Jenis
**Governance/Foundation untuk data hasilnya; fitur di dalam Studio Discovery untuk layarnya.** Bukan modul operasional baru: tidak dijual, tidak dihitung kuota, tidak di kanvas Factory Flow. Alasan: wawancara hanya menghasilkan keputusan (draf); modul operasional yang dihasilkan baru lahir saat draf dibangun.

### 4. Uji Variabilitas
| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|
| Daftar divisi hasil wawancara | ya | ya | ya | **Data** (di draf) | draf beku saat `LOCKED`; disalin ke `Department` saat dibangun |
| Peran/jabatan per divisi | ya | ya | ya | **Data** | idem, ke `CustomRole`/`DepartmentTier` |
| Pemetaan peran → modul | ya | ya | ya | **Data** (`RoleModuleLink`) | idem, ke `DepartmentModuleAssignment` |
| Asal modul (`REUSE_PLATFORM` / `REUSE_PACK` / `NEW`) | tidak | tidak | tidak | **Kode** (kosakata tertutup, milik sistem) | — |
| Tebakan peran → modul ("operator rajut → modul operator mesin") | ya | ya | — | **Data pack** (kamus peran per pack, seperti `defaultWidget`) | ikut pack |
| Daftar modul platform yang boleh dirujuk | tidak | tidak | tidak | **Kode** (registri platform) | — |
| Pemetaan port saat modul dipakai ulang | ya | ya | ya | **Data** (`portMapping` di referensi) | beku bersama draf |
| Jumlah/urutan pertanyaan | — | — | — | **Kode** (batas sistem, mis. ≤ 8 pertanyaan) | — |

### 5. Core & extend
- **Core (baru):** paket `core/.../domain/discovery/interview/` — `InterviewSession`, `DivisionDraft`, `RoleDraft`, `RoleModuleLink`, `ModuleOrigin`, `InterviewQuestion`, aturan "apa pertanyaan berikutnya" sebagai fungsi murni.
- **Titik extend:** `DiscoveryDraft` (kunci baru opsional `interview`, kompatibel mundur seperti `proposal`), `DiscoveryDraftValidator` (aturan wawancara berpath), `DiscoveryDraftCodec`, agent Koog (prompt + alat baru), `DeterministicDiscoveryAgent` (kamus peran → modul sebagai data pack).
- **Contoh yang ditiru:** `ScreenProposal` + `ScreenProposalValidator` (kontrak + validator tunggal + galat berpath), `PackScreenProposer`/`DeterministicScreenProposer` (acuan + deterministik), `DepartmentModuleAssignment` (bentuk hasil).
- **Jangan disentuh:** `DiscoveryRoutes.kt` (di atas batas lunak — tambah route di file baru), tabel utang file-size.

### 6. I/O & kanvas
- **Masuk:** narasi + jawaban pengguna per giliran. **Keluar:** `InterviewSession` terkonfirmasi → draf (pack + blueprint + layar) **dan** seed `Department`/`DepartmentModuleAssignment`/`CustomRole` saat dibangun.
- **Kanvas:** tidak ada (bukan node). Hasilnya mengisi kanvas lewat modul yang terbentuk.
- **Telemetri:** jumlah giliran, % tebakan yang dikonfirmasi tanpa ubah (ukuran mutu tebakan), waktu sampai draf kunci.

### 7. Governance
| Operasi | Level minimum | Peran yang ditolak (dites 403) |
|---|---|---|
| Mulai/jawab wawancara pada draf sendiri | pemilik draf | pengguna lain (403); tanpa login (401) |
| Lihat wawancara draf orang lain | superadmin platform | semua selain superadmin (403) |
| Kunci draf (membekukan hasil wawancara) | pemilik draf | pengguna lain (403) |
- **Gate:** sama dengan `DiscoveryRoutes` (`mayAccess`); **tulis fail-closed** (Kontrak 7). `ScopeCapability`: tidak berlaku (bukan modul). Entitlement: ikut funnel discovery (bukan paket tenant).

### 8. Ukuran → TRD?
Agregat baru + kunci dokumen + route + langkah wizard + prompt/alat + fase eval ⇒ **TRD perlu** (`trd-generator`) sebelum kode. Migrasi DB: **tidak perlu** bila wawancara disimpan di dokumen draf (JSONB); perlu bila sesi dipisah.

---

## 1. Alur wawancara (workflow)

Prinsip: **tebak dulu, tanya untuk konfirmasi** — jangan menanyakan yang bisa disimpulkan dari narasi. Satu giliran = satu kelompok keputusan (bukan satu pertanyaan per modul). Batas ≤ 8 giliran; selalu ada tombol "lewati / terima semua tebakan".

```
Narasi ─► G1 Divisi ─► G2 Peran per divisi ─► G3 Modul & fitur per peran ─► G4 Sambungan ─► G5 Ringkasan & kunci
```

| Giliran | Sistem menebak | Pengguna mengonfirmasi | Hasil (data) |
|---|---|---|---|
| **G1 Divisi** | divisi dari cerita ("potong, jahit, QC" → Potong, Jahit, QC; "pesanan masuk" → Penjualan) | benar / tambah / hapus / ganti nama | `DivisionDraft[]` |
| **G2 Peran** | jabatan per divisi dari kata kerja & pelaku ("operator rajut", "admin gudang"); kamus peran dari pack | benar / tambah / hapus; siapa kepala divisi | `RoleDraft[]` |
| **G3 Modul & fitur** | peran → modul: operator rajut → modul operator mesin rajut. Setiap tebakan membawa **asal**: **pakai ulang** modul platform/pack, **kembangkan** (pack ada, fitur kurang), atau **baru** | setuju / pindahkan ke modul lain / ubah asal; fitur apa saja di tiap modul | `RoleModuleLink[]` + `ModuleOrigin` + fitur |
| **G4 Sambungan** | serah-terima antar modul dari urutan cerita ("kain datang, lalu dipotong") | benar / ubah urutan; siapa menyerahkan ke siapa | sambungan port antar modul |
| **G5 Ringkasan** | ringkasan 1 halaman: divisi → peran → modul (asal) → fitur → sambungan | kunci / kembali ke giliran X | draf siap dikunci |

**Fallback tanpa LLM:** G1–G5 memakai kamus peran → modul sebagai **data pack** (pola `defaultWidget`); pack tanpa kamus ⇒ tanya terbuka, bukan tebak.

**Konfirmasi dicatat:** tiap tebakan menyimpan `confidence` dan `confirmed = true/false/diubah` supaya mutu tebakan bisa diukur dan dibawa ke buku demand (istilah yang selalu diubah = kandidat kamus baru).

## 2. Model data (usulan)

```kotlin
data class InterviewSession(val divisions: List<DivisionDraft>, val roles: List<RoleDraft>,
    val links: List<RoleModuleLink>, val handoffs: List<ModuleHandoff>, val step: InterviewStep)
data class RoleModuleLink(val roleKey: String, val moduleId: ModuleId, val origin: ModuleOrigin,
    val features: List<String>, val confirmed: Confirmation)
enum class ModuleOrigin { REUSE_PLATFORM, REUSE_PACK, EXTEND, NEW }      // kosakata tertutup milik sistem
data class ModuleReference(val platformModuleId: String, val portMapping: Map<PortType, PortType>)   // modul bersama
```

Bentuk hasil yang sudah ada dipakai ulang: divisi → `Department`; peran → `DepartmentTier`/`CustomRole`; peran ↔ modul → `DepartmentModuleAssignment` (kunci NAME enum, bukan code — lihat `module-integration-rules` §5.5).

## 3. Modul bersama (mis. keuangan) — keputusan desain terbuka

- **Sekarang:** modul pack baru wajib berawalan kode pack dan id platform dilarang dipakai ⇒ pack baru **tidak bisa** merujuk modul keuangan platform.
- **Usulan:** `ModuleReference` ke modul platform yang terdaftar (`OperationalModuleCatalog`) + **adaptor port** (kosakata port pack ↔ kosakata platform). Validator: rujukan hanya ke id yang terdaftar, `portMapping` lengkap dan tipenya kompatibel (kontrak input/output modul).
- **Dampak:** estimasi harga membedakan asal (pakai ulang < kembangkan < baru); prompt Koog diberi katalog modul platform + asalnya; eval menambah kriteria "asal modul masuk akal".
- **Risiko:** pack pewaris/komposisi pack mengubah invarian "pack bawaan identik" — butuh keputusan sebelum kode.

## 4. Tahap pengerjaan (usulan)

| Tahap | Isi | Gerbang |
|---|---|---|
| **I0** | TRD + kontrak `InterviewSession` + validator + codec (kunci opsional di draf), fixture non-garment | draf lama terbaca; tes per aturan; fixture 2 industri |
| **I1** | Pembuat tebakan deterministik (kamus peran → modul sebagai data pack) + aturan "pertanyaan berikutnya" (fungsi murni) | deterministik byte-per-byte; pack tanpa kamus ⇒ tanya terbuka |
| **I2** | Route wawancara (file baru, bukan `DiscoveryRoutes.kt`), gate + tes 403/401, fail-closed | tes peran tak berwenang |
| **I3** | Langkah wizard "Wawancara" (A) — konfirmasi per giliran, ringkasan, tombol terima semua | cek mata di tenant non-garment |
| **I4** | Koog: prompt + alat `interview_state`; LLM menebak, validator menegakkan; eval baru (≥ 10 kasus) | skor tebakan terkonfirmasi ≥ target yang disepakati |
| **I5** | Modul bersama (`ModuleReference` + adaptor port) | keputusan §3 selesai dulu |

## 5. Pertanyaan terbuka untuk Anda

1. **Sablon/bordir:** pack sendiri atau bagian garment? (menentukan daftar kemurnian vertikal dan kasus eval — lihat laporan eval ronde 2.)
2. **Wawancara wajib atau opsional?** Usul: opsional, dengan "terima semua tebakan".
3. **Batas giliran** (usul ≤ 8) dan apakah tiap giliran boleh berisi beberapa keputusan sekaligus.
4. **Modul bersama (§3):** boleh mengubah invarian "pack bawaan identik" dan "id platform dilarang"?
5. **Hasil wawancara ke tenant:** otomatis membuat `Department`/`DepartmentModuleAssignment` saat draf dibangun, atau hanya usulan yang ditinjau superadmin?
