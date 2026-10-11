# Panduan & Template: PLAN-[slug].md — Eksekusi Paralel 4-Track

Dokumen rencana kerja di `docs/plannings/PLAN-[slug].md` wajib dirancang agar dapat dieksekusi secara paralel tanpa saling blocking.

---

## 1. Effort Estimation & Reasoning

Setiap PLAN wajib menyatakan estimasi effort dan justifikasinya:
- **Tingkat Effort**: `Low` atau `Medium` (atau `High` bila perombakan sistemik).
- **Reasoning**:
  - Berapa perkiraan file yang dibuat/disentuh?
  - Apakah melibatkan migrasi skema database (Postgres) dan RLS?
  - Apakah memerlukan bridging sementara (Strangler Fig)?
  - Kompleksitas aturan bisnis, validasi, atau state management.

---

## 2. Struktur Pembagian Track

Pekerjaan **TIDAK** menggabungkan kontrak ke dalam Track A (jangan gunakan "A0"). Pisahkan secara tegas menjadi:

### Track 0 — Contract Phase (Serial / Prasyarat Gerbang Pembuka)
- **Tujuan**: Membekukan kontrak tipe data, Value Object (VO), antarmuka port repository di `core`, DTO JSON schema, dan mock fixture di `commonTest/resources`.
- **Sifat**: Berjalan serial lebih dulu (kecil & cepat, 0.5 hari).
- **Hasil**: Setelah Track 0 merge/beku, Track A, B, dan C dapat berjalan 100% paralel di worktree masing-masing tanpa race condition.

### Track A — Core & Domain (Fully Coding)
- **Lingkup**: `core/**/domain/**`, `core/**/shared/**`.
- **Tugas**: Agregat, rules, use cases, template domain pack, test paritas.
- **Sifat**: Murni coding dan unit test di target KMP (`compileKotlinJvm/Js/WasmJs` + `jvmTest`).

### Track B — Server & Persistence (Fully Coding)
- **Lingkup**: `server/src/main/**`, migrasi Vxx, route Ktor, repository Postgres.
- **Tugas**: Migrasi aditif, implementasi repository, endpoint API fail-closed, test peran tak berwenang (403), ModuleSchemaOwnershipTest.
- **Sifat**: Murni coding dan test server/database.

### Track C — Client & UI Implementation (Fully Coding)
- **Lingkup**: `app/shared/**/infrastructure/api/**`, `app/shared/**/presentation/**`.
- **Tugas**: ApiClient, ViewModel, pemecahan composable/form, token Clay, unit test ViewModel dengan mock fixture dari Track 0.
- **Sifat**: Murni coding dan unit test — **TIDAK** dicampur dengan proses cek visual manual/runtime.

### Track D — UI Visual Check & Verification (Terpisah)
- **Lingkup**: Verifikasi runtime dan visual aplikasi.
- **Tugas**:
  - Dijalankan setelah Track C (dan Track B) selesai.
  - Cek visual dengan mata di 2 tenant: tenant rajut (`wemade-demo`, superadmin) dan tenant non-rajut (`bordir-uji`) pada lebar ~1280dp.
  - Verifikasi responsivitas, navigasi, dan interaksi form (error states, loading).
  - Memastikan Track C tetap fokus pada coding murni tanpa terhambat langkah verifikasi visual.

---

## 3. Template Rencana (Copy-Paste Ready)

```markdown
# PLAN: [Nama Fitur] — Eksekusi Paralel Track 0 / A / B / C / D

Spesifikasi: [TRD-XXX](../trd/TRD-XXX.md).

## 0. Effort Estimation & Reasoning
- **Tingkat Effort**: Low / Medium
- **Reasoning**: [Alasan pemilihan rating: jumlah file, migrasi DB, kompleksitas logika]

## 1. Track 0 — Kontrak Awal (Serial Prasyarat)
*Merge/dibekukan lebih dulu agar Track A/B/C bisa paralel tanpa blocking.*
- [ ] Value Objects & parser tunggal di `core`
- [ ] Port/Repository interface di `core`
- [ ] DTO schema & JSON fixture di `commonTest/resources`

## 2. Pembagian Track Paralel (Batas Kepemilikan File)

| Track | Lingkup File | Tugas Utama | Selesai Bila |
|---|---|---|---|
| **Track A (Core)** | `core/**` | Domain logic, use cases, parity tests | Test core hijau (3 target) |
| **Track B (Server)** | `server/**` | DB migration, repository, API, 403 test | Server test hijau segar |
| **Track C (Client)** | `app/shared/**` | ApiClient, ViewModel, screens | Test ViewModel hijau |
| **Track D (UI Check)** | Runtime / Browser | Cek visual 2 tenant (~1280dp) | Dilihat mata di 2 tenant |

### Track A — Core & Domain (Fully Coding)
1. A1 ...
2. A2 ...

### Track B — Server & Persistence (Fully Coding)
1. B1 ...
2. B2 ...

### Track C — Client & UI Implementation (Fully Coding)
1. C1 ...
2. C2 ...

### Track D — UI Visual Check & Verification
1. D1 Cek visual di `wemade-demo` (superadmin).
2. D2 Cek visual di tenant non-rajut (`bordir-uji`).
3. D3 Uji respon error & loading state.

## 3. Urutan Merge & Integrasi
```
Track 0 ──┬── Track A (Coding) ──┐
          ├── Track B (Coding) ──┼── Integrasi ──► Track D (UI Check)
          └── Track C (Coding) ──┘
```
```
