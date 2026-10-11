# PLAN: Q4 DataScope CRUD Generate — Eksekusi Paralel Track 0 / A / B

Spesifikasi: [TRD-FIELD-004-Q4](TRD-FIELD-004-Q4-datascope-crud-generate.md).

## 0. Effort Estimation & Reasoning
- **Tingkat Effort**: Medium
- **Reasoning**: Sentuh generator (SpecRoutesWriter, SpecPostgresWriter) + domain (PrototypeRow); tidak ada migrasi data; sudah ada pola serupa di CRM routes; estimasi 3-5 hari.

## 1. Track 0 — Kontrak Awal (Serial Prasyarat)
*Merge/dibekukan lebih dulu agar Track A/B bisa paralel tanpa blocking.*
- [ ] Tambah `owner: String?` ke `PrototypeRow` di `core/domain/prototype/PrototypeRow.kt`
- [ ] Definisikan JSON fixture untuk test dengan owner

## 2. Pembagian Track Paralel (Batas Kepemilikan File)

| Track | Lingkup File | Tugas Utama | Selesai Bila |
|---|---|---|---|
| **Track A (Core)** | `core/**` | PrototypeRow owner field, generator filter logic | Test core hijau (3 target) |
| **Track B (Server)** | `server/**` | Repository generate emit owner_id, route filter | Server test hijau segar |
| **Track C (Client)** | - | Tidak ada (CRUD tidak butuh UI baru) | N/A |

### Track A — Core & Domain (Fully Coding)
1. A1. Modifikasi `PrototypeRow` di `core/src/commonMain/kotlin/com/eventverse/app/domain/prototype/PrototypeRow.kt` — tambah field `owner: String?`
2. A2. Update `InMemoryPrototypeRowRepository` test support
3. A3. Tambah KDoc di `SpecRoutesWriter` bahwa HIERARCHICAL ditolak sementara

### Track B — Server & Persistence (Fully Coding)
1. B1. Modifikasi `SpecPostgresWriter` — emit kolom `owner_id` nullable di tabel generate
2. B2. Modifikasi `SpecRoutesWriter` — filter di GET `/list` berdasarkan DataScope:
   - `OWN_DATA_ONLY` → filter `owner == currentEmployeeId`
   - `SUBORDINATE_DATA` → filter `owner in subordinateIds`
   - `ALL_TENANT_DATA` → tanpa filter
3. B3. Update tes golden untuk memastikan spec non-HIERARCHICAL tidak berubah

## 3. Urutan Merge & Integrasi

```
Track 0 ──► Track A (Coding) ──┐
                               ├──► Merge ke main
         Track B (Coding) ──────┘
```

## 4. Acceptance Criteria

- [ ] GET `/list` dengan OWN_DATA_ONLY hanya return record miliknya
- [ ] GET `/list` dengan SUBORDINATE_DATA return miliknya + subordinates
- [ ] GET `/list` dengan ALL_TENANT_DATA return semua record
- [ ] Generator menolak pack HIERARCHICAL (sudah ada di KDoc)
- [ ] Spec non-HIERARCHICAL golden test tetap hijau
