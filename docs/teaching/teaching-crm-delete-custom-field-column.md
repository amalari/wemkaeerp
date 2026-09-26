# 🎓 Modul Pembelajaran: Penghapusan Kolom Kustom Dinamis di Modul CRM (End-to-End Full-Stack)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Soft Delete/Archival Strategy, Ktor REST API with RBAC, Compose Multiplatform Claymorphism UI, State Management  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform, Coroutine Flow, dan Clean Architecture  
> **Referensi Task**: Hapus Kolom Kustom CRM Leads (Soft Archival)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Dalam sistem ERP multi-tenant seperti WeMade ERP, setiap konveksi/tenant memiliki alur kerja yang unik. Suatu saat admin tenant menambahkan kolom kustom (misalnya *"Warna Kain Tambahan"* atau *"Ukuran Sablon Khusus"*), namun di kemudian hari kolom tersebut sudah tidak relevan lagi dan ingin dihapus agar form tidak berantakan.

Jika penghapusan kolom ini dilakukan secara sembrono dengan:
1. **Hard Delete pada Database Schema (`DROP COLUMN`)**: Semua data historis ribuan pesanan atau prospek (leads) yang pernah mengisi kolom tersebut akan musnah seketika.
2. **Menghapus Kolom Sistem (System Fields)**: Seeded fields inti dari sistem konveksi (seperti *Kategori Pakaian*, *Jenis Sablon*, *Warna Bahan*) bisa terhapus oleh user yang salah pencet, menyebabkan fitur otomatisasi kalkulasi costing atau techpack breakdown menjadi rusak (*crash*).
3. **Frontend-Only Removal**: Hanya menyembunyikan dari UI client tanpa memvalidasi wewenang RBAC di backend, membuka celah bagi user tanpa hak akses admin (`MANAGE`) untuk memanipulasi skema tenant.

### Solusi Arsitektur
1. **Aturan Domain**: System fields ditandai `isSystem = true` dan `isDeletable = false` sehingga tidak pernah bisa dihapus.
2. **Soft Archival**: Penghapusan kolom kustom dilakukan dengan menstempel `archivedAt = Clock.System.now()`. Kolom tidak lagi muncul di skema aktif form, namun riwayat nilai di payload JSONB dokumen `CrmLead` tetap aman tersimpan untuk audit trail.
3. **RBAC Enforced**: Endpoint backend `DELETE /api/tenant/crm/fields/{id}` memverifikasi wewenang `AccessLevel.MANAGE` sebelum eksekusi.
4. **UX Neo-Brutalist**: Konfirmasi dialog jelas dengan aksi bahaya (Danger button) yang mencegah klik tidak sengaja.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur ini dari nol, urutan menulis yang benar sesuai **Rule 2 (Presentation → Application → Domain ← Infrastructure)** dan **Rule 13 (Full-Stack End-to-End)** adalah:

```
┌────────────────────────────────────────────────────────┐
│ 1. Domain Layer (core/)                                │
│    - Rule: isSystem protection & archivedAt stamp      │
│    - ArchiveCustomFieldDefinitionUseCase               │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ 2. Backend Routing & RBAC (server/)                    │
│    - DELETE /api/tenant/crm/fields/{id}                │
│    - Verifikasi AccessLevel.MANAGE                     │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ 3. Client Remote Data Source & ViewModel (app/shared/) │
│    - CrmRemoteDataSource.deleteCustomField(...)        │
│    - CrmApiClient HTTP client implementasi             │
│    - CrmUiEvent.DeleteCustomField & reload schema      │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│ 4. Shared Compose UI (Claymorphism)                    │
│    - LeadCustomField label action row                  │
│    - LeadInspectorPane confirmation dialog             │
│    - Event bubbling ke CrmWorkspaceScreen              │
└────────────────────────────────────────────────────────┘
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pure Domain Layer (`core/`)

File: `core/src/commonMain/kotlin/com/eventverse/app/domain/customfield/usecases/ArchiveCustomFieldDefinitionUseCase.kt`

```kotlin
class ArchiveCustomFieldDefinitionUseCase(
    private val repository: CustomFieldDefinitionRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        fieldId: CustomFieldDefinitionId,
        archivedAt: Instant = Clock.System.now()
    ): Result<CustomFieldDefinition> = runCatching {
        val existing = repository.findById(tenantId, fieldId)
            ?: throw IllegalArgumentException("Custom field with id '${fieldId.value}' not found")

        require(!existing.isSystem) {
            "System-defined custom fields cannot be deleted or archived"
        }

        val archived = existing.copy(archivedAt = archivedAt)
        repository.save(tenantId, archived)
    }
}
```

**Mental Model & Mengapa Ditulis Begini?**
- **Zero Framework Dependency**: Tidak ada import Ktor, Compose, ataupun database library di layer `core`.
- **Invarian Bisnis**: `require(!existing.isSystem)` menjaga aturan integritas bisnis bahwa seeded custom field sistem dilindungi dari penghapusan pada level domain yang tidak bisa dibypass oleh layer manapun.
- **Immutability**: Objek `existing` tidak dimutasi langsung (`existing.archivedAt = ...`), melainkan menghasilkan salinan baru lewat `.copy(archivedAt = archivedAt)`.

---

### Blok B: Backend API & RBAC Guard (`server/`)

File: `server/src/main/kotlin/com/eventverse/app/routes/CrmRoutes.kt`

```kotlin
val archiveCustomFieldDefinitionUseCase = ArchiveCustomFieldDefinitionUseCase(customFieldRepository)

delete("/fields/{id}") {
    val fieldId = call.parameters["id"]
        ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing field ID parameter")

    val (tenant, caller) = call.tenantAndCaller()
    val access = call.crmAccess(caller)
    if (!access.canManage) {
        call.respond(HttpStatusCode.Forbidden, "Requires MANAGE access to delete custom fields")
        return@delete
    }

    archiveCustomFieldDefinitionUseCase(
        tenantId = tenant.id,
        fieldId = CustomFieldDefinitionId(fieldId)
    ).fold(
        onSuccess = {
            call.respond(HttpStatusCode.NoContent)
        },
        onFailure = { error ->
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (error.message ?: "Failed to delete custom field")))
        }
    )
}
```

**Mental Model & Mengapa Ditulis Begini?**
- **Idempotent HTTP Verb**: Operasi penghapusan menggunakan HTTP verb `DELETE`.
- **RBAC Check**: Admin tenant harus memiliki `access.canManage`. User dengan level `READ` atau `WRITE` ditolak dengan status HTTP 403 Forbidden.
- **Result Handling**: Pola fungsional Kotlin `.fold(onSuccess = ..., onFailure = ...)` mengembalikan `204 NoContent` saat sukses tanpa payload berlebih, atau `400 BadRequest` jika ada pelanggaran domain constraint.

---

### Blok C: Client Remote Data Source & ViewModel (`app/shared/`)

File: `app/shared/src/commonMain/kotlin/com/eventverse/app/infrastructure/api/CrmApiClient.kt` & `CrmViewModel.kt`

```kotlin
// In CrmApiClient.kt:
override suspend fun deleteCustomField(tenantSlug: String, fieldId: String): Result<Unit> = runCatching {
    val response = httpClient.delete("/api/tenant/crm/fields/$fieldId") {
        header("X-Tenant-Slug", tenantSlug)
    }
    if (!response.status.isSuccess()) {
        error("Gagal menghapus kolom: ${response.status}")
    }
}

// In CrmViewModel.kt:
is CrmUiEvent.DeleteCustomField -> {
    viewModelScope.launch {
        remoteDataSource.deleteCustomField(tenantSlug, event.fieldId).fold(
            onSuccess = {
                // Refresh skema aktif setelah kolom berhasil diarsipkan
                val refreshedSchema = remoteDataSource.getSchema(tenantSlug).getOrElse { _state.value.schema }
                _state.update { it.copy(schema = refreshedSchema) }
            },
            onFailure = { err ->
                _state.update { it.copy(error = err.message ?: "Gagal menghapus kolom kustom") }
            }
        )
    }
}
```

**Mental Model & Mengapa Ditulis Begini?**
- **Single Source of Truth**: Setelah server mengarsipkan kolom, ViewModel memanggil `getSchema(tenantSlug)` untuk merefleksikan skema terbaru ke `UiState`. Seluruh UI yang me-render form (Kanban, modal, detail pane) secara reaktif diperbarui tanpa manipulasi list manual di memori yang rawan sinkronisasi ganda.

---

### Blok D: Shared Compose UI dengan Claymorphism Design System (`app/shared/`)

File: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/LeadCustomField.kt` & `LeadInspectorPane.kt`

```kotlin
// Di LeadCustomField.kt: Baris Label & Aksi Hapus
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = descriptor.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        if (descriptor.isRequired) {
            Text(text = "*", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Error)
        }
    }
    if (onDelete != null && descriptor.isDeletable) {
        Text(
            text = "Hapus",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.Error,
            modifier = Modifier.clickable(onClick = onDelete)
        )
    }
}
```

```kotlin
// Di LeadInspectorPane.kt: Dialog Konfirmasi Neo-Brutalist
val pending = fieldPendingDeletion
if (pending != null) {
    Dialog(onDismissRequest = { fieldPendingDeletion = null }) {
        ClayCard(modifier = Modifier.width(380.dp)) {
            Text(
                text = "Hapus Kolom Kustom?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "Kolom \"${pending.label}\" akan diarsipkan dari form lead. Data yang sudah tersimpan sebelumnya tetap tersimpan di riwayat sistem.",
                fontSize = 13.sp,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(vertical = ClaySpacing.Md)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayButton(
                    text = "Batal",
                    onClick = { fieldPendingDeletion = null },
                    style = ClayButtonStyle.Secondary,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = "Hapus Kolom",
                    onClick = {
                        val idToDelete = pending.fieldId
                        fieldPendingDeletion = null
                        onDeleteField?.invoke(idToDelete)
                    },
                    style = ClayButtonStyle.Danger,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
```

---

## 🛡️ 4. Jebakan Pemula (Common Pitfalls & What to Avoid)

| No | Jebakan Pemula | Risiko & Masalah | Pendekatan yang Benar |
|---|---|---|---|
| 1 | **Menghapus record fisik dari database (`DELETE FROM ...`)** | Menghilangkan relasi data transaksi lama atau menyebabkan constraint violation error. | Gunakan Soft Archival (`archived_at = NOW()`). Filter query aktif dengan `WHERE archived_at IS NULL`. |
| 2 | **Lupa melindungi `isSystem` fields** | User admin dapat menghapus field krusial sistem konveksi (seperti jenis sablon). | `isDeletable = !def.isSystem` di descriptor, dan `require(!existing.isSystem)` di domain use case. |
| 3 | **Menaruh status konfirmasi hapus di level global ViewModel** | State modal polusi di state global, padahal ini UI flow lokal yang hanya relevan saat modal detail terbuka. | Gunakan local Compose state `var fieldPendingDeletion by remember { mutableStateOf<LeadFieldDescriptor?>(null) }`. |
| 4 | **Memakai warna literal hex langsung di Composable** | Melanggar aturan Design System Rule 12 (`Color(0xFF...)` dilarang di luar theme). | Selalu gunakan token semantik: `WeMadeColors.Error`, `WeMadeColors.OnSurfaceMuted`. |

---

## 🧪 5. Verifikasi & Tantangan Mandiri

### Langkah Verifikasi E2E
1. Buka browser pada `http://localhost:3000/crm-sales`.
2. Klik lead card mana saja untuk membuka Inspector Modal.
3. Periksa section **Properti Kustom**. Pastikan kolom bawaan sistem (*Kategori Pakaian*, *Jenis Sablon*, dll.) tidak memiliki tombol merah "Hapus".
4. Klik tombol **`+ Kolom`**, masukkan nama kustom baru (contoh: *"Catatan Pengiriman"*), lalu klik **Tambah**.
5. Kolom *"Catatan Pengiriman"* muncul dengan tombol merah **Hapus** di pojok kanan atas label.
6. Klik tombol **Hapus**. Pastikan pop-up dialog konfirmasi muncul dengan pesan penjelasan pengarsipan data.
7. Klik tombol **Hapus Kolom** di dialog. Pastikan kolom kustom menghilang dari form, dan server menerima request `DELETE /api/tenant/crm/fields/{id}` dengan respon HTTP `204`.

### Tantangan Mandiri untuk Junior Developer
1. **Fitur Restore Kolom**: Cobalah buat endpoint `POST /api/tenant/crm/fields/{id}/restore` yang membersihkan `archivedAt = null`, dan tambahkan tab "Kolom Terarsip" di dialog pengaturan skema.
2. **Badge Status**: Tambahkan indikator visual tag kecil `[Wajib]` atau `[Opsional]` di samping label kolom untuk membedakan field required dengan elegan.
