# Modul Pembelajaran: Gerbang Record & Jangkauan Data untuk FILE dan RELATION (TRD-FIELD-004 Track A)

> **Level Target**: Mid Developer
> **Topik Utama**: Isolasi *dalam* tenant (record, DataScope, modul target), satu fungsi gerbang bersama, parameter wajib tanpa default sebagai alat paksa kompilator, TDD keamanan
> **Prasyarat**: `teaching-field-file-tenant-ref-guard.md` (isolasi lintas tenant), RBAC `requireModuleAccess`, `DataScope`
> **Referensi**: `docs/trd/TRD-FIELD-004-file-relation-server-hardening.md`, `TRD-FIELD-001-relation.md`, `TRD-FIELD-002-file.md`

---

## 1. Masalah: kebocoran yang bukan lintas tenant

KRITIS-1 menutup tenant A membaca objek tenant B. Sisanya terjadi **di dalam satu tenant**:

| Celah | Contoh |
|---|---|
| Unggah generik tanpa cek record | `POST .../records/{id-karangan}/fields/f/upload` -> 201, objek yatim |
| Tanpa DataScope di modul hierarkis | Staf `OWN_DATA_ONLY` mengunggah ke record milik rekan |
| Ref tak terikat record | Ref berkas record X ditulis ke sel record Y, lalu diunduh lewat gerbang Y |
| Guard RELATION CRM hanya cek "ada" | Pengguna tanpa VIEW atas modul target menguji id (200 vs 400) dan menautkannya |

Analogi: kartu akses gedung (tenant) sudah dicek, tetapi lift tidak mengecek lantai mana yang boleh Anda datangi.

## 2. Urutan kerja (TDD, satu komit per butir)

1. **A1 tes merah.** 26 tes baru; 14 gagal terhadap kode lama dengan pesan seperti
   `expected:<404 Not Found> but was:<201 Created>` (unggah ke `rec-9` yang tak ada),
   `expected:<403 Forbidden> but was:<200 OK>` (unduh ref `rec-2` dari sel `rec-1`; guard CRM tanpa VIEW target),
   `expected:<400 Bad Request> but was:<200 OK>` (ref lead lain ditulis ke lead sendiri; lead di luar jangkauan).
   Sisa 12 hijau sengaja: kontrol (peran tak berwenang 403, record sendiri 201, tenant lain 404).
2. **A2 `FieldFileRecordGate.kt`.** Satu fungsi `requireReachableRecord` untuk unggah **dan** unduh.
3. **A3 ikatan `recordId`.** Perubahan domain (`FileRef`, `EntitySpec`, `CustomFieldValidation`) + generator.
4. **A4 `authorizeRelationTarget`.** Gerbang target dipakai `RelationRoutes` dan guard tulis CRM.

## 3. Pembedahan kode

### 3.1 Gerbang record (A2)

```kotlin
internal suspend fun ApplicationCall.requireReachableRecord(tenant, module, recordId, decision, rows, employeeRepository): PrototypeRow?
```

Urutan: (1) sumber baris/pemilik ada? Modul `HIERARCHICAL` tanpa sumber pemilik = **403 fail-closed**, modul
`GLOBAL_ONLY` tanpa penyimpan = 404. (2) record ada pada **tenant pemanggil** (record tenant lain = "tidak ada" = 404).
(3) modul hierarkis: pemilik record harus ada dalam `callerOwnerReach(...)`. Dipanggil setelah RBAC dan **sebelum**
storage/`fileName`/MIME/body — prinsip "403 sebelum body" (Kontrak 7) tetap terjaga.

Kenapa interface `RecordOwnerSource` (core) yang *opsional diimplementasikan* penyimpan baris, bukan kelas baru?
`PrototypeRowRepository` tidak punya konsep pemilik, dan modul generate hari ini `GLOBAL_ONLY`. Dengan
`rows as? RecordOwnerSource` tak ada parameter wiring baru di `Application.kt` (file yang sudah di atas batas, aturan
Ratchet), dan modul hierarkis tanpa sumber pemilik otomatis fail-closed. Jangan menebak pemilik dari data lain.

### 3.2 Ikatan record (A3) dan keputusan FILE saat create

`FileRef.isValidFor(tenantId, raw, moduleCode, recordId)`: segmen ketiga ref harus sama dengan record yang ditulis.
`fileOwnershipProblem(tenantId, recordId: String?, values)` — `recordId` **wajib tanpa default**, bertipe `String?`:
`null` berarti "record baru" dan nilai FILE terisi ditolak 400 (keputusan Q1). Alasan: alasan lama "unggah dengan id
sementara" gugur karena klien memang hanya mengunggah untuk record yang sudah ada; id sementara akan membuka kategori
objek yatim baru. Parameter wajib membuat **kompilator** menunjukkan semua pemanggil (generator, CRM, master data) —
sama seperti pelajaran `when` tanpa `else`.

Konsekuensi yang harus dipahami: field FILE `isRequired` tidak bisa dipenuhi saat create (nilainya belum mungkin ada).
Itu ketegangan nyata antara "wajib" dan "unggah setelah simpan", bukan bug; jawabannya desain field (opsional) atau
alur dua langkah.

### 3.3 Gerbang target rujukan (A4)

`authorizeRelationTarget` menggabungkan: modul target dikenal+operasional (403), `VIEW` atas modul **target** (403),
modul ada di pack tenant (404), lalu menghitung jangkauan DataScope target **sekali**. Hasilnya diteruskan ke
`exists(tenantId, targetResource, recordId, reachableOwnerIds)`. Parameter jangkauan **wajib tanpa default** pada
`RelationTargetSource.exists` dan `RelationTargetResolver.exists`: sumber hierarkis (CRM) harus memutuskan,
sumber kolektif boleh mengabaikan. Record di luar jangkauan dijawab `false`, yaitu **400 yang sama** dengan
"tidak ditemukan"; kalau kodenya dibedakan, penyerang memakainya sebagai oracle keberadaan. Pesan galat hanya
memantulkan id yang dikirim pemanggil, dan tes `crmRelation_leadOutOfReach_sameAnswerAsMissing` memastikan badannya
identik.

`RelationRoutes.kt` menyusut 138 -> 70 baris karena dua salinan logika gerbang kini satu. Pola yang sama dengan utang
"tiga salinan guard" di TRD-PLAT-012: lebih baik angkat fungsi sekarang daripada menyalin yang ketiga.

## 4. Jebakan yang dihindari

- **Modul hierarkis jangan jatuh ke "semua data"** hanya karena tidak ada sumber pemilik. Fail-closed 403.
- **Jangan membedakan "tak ada" dan "di luar jangkauan"** pada respons ke pemanggil (oracle).
- **Jangan menambah file besar**: logika baru di file bertema (`FieldFileRecordGate.kt`, `RelationTargetAuthorization.kt`);
  `FieldFileRoutes.kt` 283 -> 280, `CrmRoutes.kt` tetap 489 (hanya argumen pemanggil yang ditukar).
- **Tes lama yang mengunggah ke record tak ada harus disesuaikan**, bukan gerbangnya dilonggarkan: fixture diberi
  record `rec-1` (cermin perilaku klien nyata).
- Tes memakai pack `layanan` (non-default) dan tenant kedua, bukan hanya garmen (Kontrak 6 variability).

## 5. Yang belum (Track B/C)

Wiring produksi sumber baris per modul (F0), emisi `relationProblem` di generator (F2), magic byte dan header objek
(F4), regenerasi `layanan_change_request` dan tes penjaga spec (F5). Q4/Q6/Q8 masih terbuka.
