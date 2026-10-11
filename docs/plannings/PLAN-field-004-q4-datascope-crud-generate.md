# PLAN: Q4 DataScope CRUD Generate — MVP vs Next Phase

Spesifikasi: [TRD-FIELD-004-Q4](TRD-FIELD-004-Q4-datascope-crud-generate.md).

---

## Status: MVP ✅ SELESAI

| Fase | Item | Status |
|---|---|---|
| MVP | Reject HIERARCHICAL modules | ✅ |
| MVP | owner_id column + repository support | ✅ |
| MVP | KDoc workaround di SpecRoutesWriter | ✅ |

---

## MVP Phase — Done ✅

**Tidak ada yang perlu di-track lagi.**

---

## Next Phase — Full DataScope Filtering

**Trigger:** Saat ada modul HIERARCHICAL nyata yang perlu di-generate

### Estimasi: Medium-High

### Yang Diubah:

| Track | File | Tugas |
|---|---|---|
| **A** | `PrototypeRowRepository` | Tambah `list(tenantId, ownerIds: Set<String>?)` |
| **A** | `SpecRoutesWriter` | Inject employeeId context + DataScope resolution |
| **A** | `SpecPostgresWriter` | Emit WHERE clause dengan owner filter |
| **B** | Employee repository | Dapat subordinate IDs |
| **B** | Generated routes | Pass employeeId ke repository |
| **C** | - | Tidak ada UI changes |

### Acceptance Criteria:

- [ ] Modul HIERARCHICAL bisa di-generate tanpa error
- [ ] OWN_DATA_ONLY → hanya lihat miliknya
- [ ] SUBORDINATE_DATA → lihat miliknya + subordinates
- [ ] ALL_TENANT_DATA → lihat semua

---

## Kesimpulan

**Untuk MVP Builder:**
- Q4 sudah cukup — tidak ada data leak
- Full DataScope = ticket terpisah kapan perlu

**Tidak ada action needed sekarang.**
