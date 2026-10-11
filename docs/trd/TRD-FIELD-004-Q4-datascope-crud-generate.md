# TRD-FIELD-004-Q4: DataScope Filtering untuk Modul Generate CRUD

## Status Sekarang

| Fase | Status | Keterangan |
|---|---|---|
| **Done** | ✅ Selesai | Reject HIERARCHICAL modules + owner_id column |
| **MVP** | 🔄 Aktif | Owner-based read filtering (basic) |
| **Next Phase** | ⏳ Belum | Full DataScope (OWN/SUB/ALL) |

---

## 1. MVP Phase — Urgent (Sekarang)

### Goals
- Protect data leaks untuk modul generate SAMPAI ada DataScope penuh

### Yang Sudah Selesai

| # | Item | Status |
|---|---|---|
| 1 | Reject HIERARCHICAL modules saat generate | ✅ |
| 2 | owner_id column di generated tables | ✅ |
| 3 | ownerId read/write di repository | ✅ |

### Effect Tanpa MVP Ini
- User bisa lihat data orang lain (privacy leak)

### Estimasi
- **Low** — sudah selesai

---

## 2. Next Phase — Full DataScope (Nanti)

### Goals
- Support modul HIERARCHICAL dengan filtering lengkap

### Yang Belum

| # | Item | Keterangan |
|---|---|---|
| 1 | EmployeeId context di routes | Butuh akses EmployeeRepository |
| 2 | DataScope resolution | OWN_ONLY / SUBORDINATE / ALL_TENANT |
| 3 | WHERE clause filtering di generated queries | SQL-level filtering |
| 4 | Subordinate resolution | Dapat subordinate IDs |
| 5 | UI untuk scope selection | Di modal wewenang |

### Effect Dengan Next Phase
- Modul HIERARCHICAL bisa di-generate
- User lihat data sesuai wewenangnya

### Estimasi
- **Medium-High** —涉及 routing, repository, employee context

---

## 3. Keputusan Sekarang

### Opsi A: Cukup sampai MVP (Recommended untuk MVP Builder)
- Modul generate tetap GLOBAL_ONLY
- DataScope filtering = ticket terpisah
- Tidak ada data leak karena semua data visible

### Opsi B: Lanjut Next Phase Penuh
- Modul HIERARCHICAL bisa di-generate
- Butuh lebih banyak work

---

## 4. Detail Teknis Next Phase (kalau memutuskan lanjut)

### Yang Diubah:
1. `SpecRoutesWriter` - inject DataScope filtering
2. `ApplicationCall` - dapat employeeId dari principal
3. `PrototypeRowRepository.list()` - accept `ownerIds: Set<String>?` parameter
4. Generated routes - pass employeeId + scope ke repository

### Test Cases:
1. A (OWN_ONLY) → hanya lihat miliknya
2. B (SUBORDINATE) → lihat miliknya + subordinates  
3. Manager (ALL) → lihat semua
