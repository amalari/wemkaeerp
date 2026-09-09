# 🎓 Modul Pembelajaran: Penghapusan Divisi Redundan "Direksi" & Penegasan Direksi Non-Divisi

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Corporate Governance Modeling, Flyway Database Migration, Data Cleansing  
> **Prasyarat**: Pemahaman dasar relasi database PostgreSQL, Domain Layer vs Infrastructure Layer, dan State Management UI  
> **Referensi Task**: Eliminasi Divisi Redundan 'Direksi' (`dept-exec`) dan Sinkronisasi 'Direksi (Non-Divisi)'

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Ketika membuka form input karyawan atau filter divisi di sistem WeMade ERP, pengguna menemukan dua opsi yang membingungkan:
1. **Direksi (Non-Divisi)**
2. **Direksi** (sebagai salah satu pilihan divisi biasa, sejajar dengan Sales, Produksi, Gudang, QC).

Bagi pengguna awam (misal HR atau Admin Konveksi), timbul pertanyaan:
*"Jika saya ingin mendaftarkan anggota jajaran pimpinan, apakah saya harus memilih 'Direksi (Non-Divisi)' atau divisi 'Direksi'? Mengapa ada dua hal yang terdengar sama persis?"*

Bahkan ketika admin mencoba menghapus atau mengarsipkan divisi "Direksi" dari antarmuka, sistem menolaknya dengan pesan error:
> *"Tidak dapat mengarsipkan divisi 'Keuangan & Direksi' karena masih memiliki 1 karyawan aktif."*

Karyawan aktif tersebut ternyata adalah **Bpk. Hendra Kusuma** (Direktur Utama / Owner).

### Analogi Sederhana
Bayangkan sebuah pabrik konveksi berlantai 4:
- **Lantai 1**: Departemen Gudang & Logistik (Kain, Resleting, Benang).
- **Lantai 2**: Departemen Produksi & Meja Potong.
- **Lantai 3**: Departemen Penjualan & Quality Control.
- **Lantai 4**: Kantor Direksi & Ruang Rapat Pemegang Saham.

Direktur Utama (CEO) adalah pemilik yang membawahi **seluruh gedung pabrik**, bukan mandor atau kepala ruangan di lantai 2 atau 3. Memasukkan Direktur ke dalam "Divisi Direksi" seolah-olah menganggap Direktur Utama adalah kepala bagian yang terisolasi di satu sudut departemen, bukan pengambil keputusan lintas divisi (*cross-functional governance*).

### Hasil Akhir yang Diharapkan
1. Opsi divisi operasional murni hanya berisi departemen fungsional pabrik: **Sales**, **Produksi**, **Gudang**, **QC**, dan **Keuangan**.
2. Jajaran eksekutif puncak menggunakan wewenang **Direksi (Non-Divisi)** dengan `department_id = NULL` dan `level = HierarchyLevel.EXECUTIVE`.
3. Entri lama `dept-exec` dihapus tuntas dari database dan digantikan oleh migrasi Flyway resmi (`V7__remove_direksi_department.sql`), sehingga kebingungan data tereliminasi 100%.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika junior developer diminta membersihkan anomali data seperti ini, berikut urutan kerja profesional yang harus dilalui:

```
[1. Analisis Akar Masalah (Root Cause)] 
  ↳ Mengapa divisi 'Direksi' bisa ada di database? (Cek riwayat V4 migration vs Domain Model)
        ↓
[2. Validasi Domain Invariant (Core)]
  ↳ Pastikan OrgNode.kt & Department.kt sudah sepakat bahwa Executive adalah nullable department
        ↓
[3. Rancang Migration SQL (Flyway V7)]
  ↳ Unbind karyawan executive terlebih dahulu (department_id = NULL)
  ↳ Hapus entri divisi usang (DELETE dept-exec)
  ↳ Pastikan divisi operasional pengganti (dept-finance) terdaftar
        ↓
[4. Eksekusi & Validasi Database Nyata]
  ↳ Jalankan migration pada instance Postgres aktif
        ↓
[5. Validasi Live Endpoints & Regression Tests]
  ↳ Uji endpoint API GET /departments dan GET /employees
  ↳ Jalankan test suite JVM di core, server, dan app/shared
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Flyway Migration `V7__remove_direksi_department.sql`

File: [server/src/main/resources/db/migration/V7__remove_direksi_department.sql](file:///Volumes/amalari/Projects/wemade/server/src/main/resources/db/migration/V7__remove_direksi_department.sql)

```sql
-- 1. Lepaskan keterikatan department_id untuk semua karyawan level EXECUTIVE (Direksi)
UPDATE employees
SET department_id = NULL
WHERE level = 'EXECUTIVE' AND department_id = 'dept-exec';

-- 2. Alihkan karyawan non-executive jika ada yang terikat pada dept-exec (fallback to NULL)
UPDATE employees
SET department_id = NULL
WHERE department_id = 'dept-exec';

-- 3. Hapus divisi 'dept-exec' dari tabel departments
DELETE FROM departments
WHERE id = 'dept-exec';

-- 4. Pastikan divisi operasional 'Keuangan & Akuntansi' (dept-finance) terdaftar
INSERT INTO departments (id, tenant_id, code, display_name, short_name, color_hex, is_custom)
VALUES ('dept-finance', 'ten-demo-001', 'finance', 'Keuangan & Akuntansi', 'Keuangan', 4286339821, FALSE)
ON CONFLICT (id) DO NOTHING;
```

#### Mengapa blok ini ditulis begini?
1. **Langkah 1 & 2 (Foreign Key Safety)**:
   Tabel `employees` memiliki relasi referensial ke `departments(id)`. Jika kita langsung menjalankan `DELETE FROM departments WHERE id = 'dept-exec'`, database akan melempar error pelanggaran integritas referensial (*foreign key violation*) atau aturan bisnis `ArchiveDepartmentUseCase` akan memblokir penghapusan karena ada karyawan aktif. Kita harus membebaskan Bpk. Hendra Kusuma (`level = EXECUTIVE`) terlebih dahulu ke `department_id = NULL`.
2. **Langkah 3 (Penghapusan Entri Usang)**:
   Menghapus `dept-exec` secara permanen sehingga tidak lagi muncul di API maupun dropdown/chip UI.
3. **Langkah 4 (Idempotent Restoration)**:
   Pada migrasi V4 awal, divisi ke-5 diberi label "Keuangan & Direksi". Ketika "Direksi" dipisahkan, divisi "Keuangan" tetap dibutuhkan untuk pencatatan HPP konveksi dan kas operasional. Sintaks `ON CONFLICT (id) DO NOTHING` menjamin query ini bersifat *idempotent* (aman dijalankan berulang kali tanpa risiko duplikasi error).

---

### Blok B: Penegasan Domain Rule di Pure Kotlin Layer

File: [core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/OrgNode.kt](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/orgchart/OrgNode.kt)

```kotlin
// 1. Executive / Owner (Direksi berdiri di puncak struktur, TIDAK memiliki divisi)
OrgNode(
    id = hendraId,
    name = "Bpk. Hendra Kusuma",
    email = "hendra.owner@wemade.id",
    department = null, // Invariant: Direksi bebas dari keterikatan satu divisi
    level = HierarchyLevel.EXECUTIVE,
    roleTitle = "Direktur Utama / Owner",
    reportsToId = null,
    phone = "081122334455",
    tenantId = tenantId
)
```

#### Mengapa blok ini ditulis begini?
- `department: Department?` dibuat *nullable* secara eksplisit.
- Jika level adalah `HierarchyLevel.EXECUTIVE`, aturan validasi di use case menegaskan bahwa ia tidak wajib memiliki divisi dan tidak memiliki atasan langsung (`reportsToId == null`).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Pendekatan Alternatif | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Flyway Database Migration (`V7__...`)** | Hardcode filter di UI: `departments.filterNot { it.code == "finance_executive" }` | Menyelesaikan masalah pada akarnya (Single Source of Truth di Database). Data bersih untuk Web, Android, iOS, dan Desktop. | **Technical Debt Akut**: Data kotor tetap tersimpan di database. Jika ada endpoint mobile atau API pihak ketiga baru, bug duplikasi akan muncul kembali. |
| **Nullable Department (`department = null`)** | Membuat departemen virtual bernama "EXECUTIVE_DEPARTMENT" | Mencerminkan struktur korporat riil. Direksi mengawasi seluruh unit bisnis, bukan satu silo departemen. | Mengacaukan pelaporan keuangan per departemen, perhitungan beban overhead gaji konveksi, dan bagan organisasi T-Shape. |
| **Idempotent Migration (`ON CONFLICT DO NOTHING`)** | `INSERT INTO departments ...` biasa tanpa klausul konflik | Aman saat re-run di berbagai environment (Local Docker, Staging, Production). | Build CI/CD gagal jika baris data kebetulan sudah dimasukkan secara manual. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Memperbaiki Tampilan di UI Saja (Quick Patch Syndrome)**
   - *Kenapa bahaya*: Pemula sering tergoda menyembunyikan chip "Direksi" dengan menambah `if (dept.name != "Direksi")` di `OrgChartScreen.kt`. Ini adalah jebakan berbahaya karena data kotor di database tetap ada, dan use case backend tetap menghitung `dept-exec` saat agregasi.
   - *Solusi elegan kita*: Buat migrasi resmi di backend untuk membersihkan data lama secara struktural.

2. **Jebakan 2: Menghapus Parent Table Tanpa Menangani Child Table**
   - *Kenapa bahaya*: Menjalankan `DELETE FROM departments WHERE id = 'dept-exec'` saat masih ada baris di `employees` yang mengarah ke id tersebut akan memicu `PSQLException: foreign key constraint failure`.
   - *Solusi elegan kita*: Jalankan `UPDATE employees SET department_id = NULL` terlebih dahulu.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### A. Pengujian Langsung terhadap Live REST API
Gunakan `curl` untuk memastikan endpoint departemen dan karyawan merespons data yang telah bersih:

```bash
# 1. Pastikan divisi 'dept-exec' sudah hilang dan digantikan oleh 'dept-finance'
curl -s http://localhost:8080/api/tenant/departments -H "X-Tenant-Slug: wemade-demo"

# Output yang benar (5 Divisi Operasional):
# [Sales, Produksi, Gudang, QC, Keuangan] -> Tidak ada lagi divisi "Direksi"!

# 2. Pastikan Bpk. Hendra Kusuma (EXECUTIVE) memiliki department = null
curl -s http://localhost:8080/api/tenant/employees -H "X-Tenant-Slug: wemade-demo"

# Output yang benar:
# {"id":"emp-hendra", ..., "department": null, "level": "EXECUTIVE", ...}
```

### B. Otomasi Test Suite (Regression Safety)
Jalankan pengujian lintas layer:
```bash
./gradlew :server:test :core:jvmTest :app:shared:jvmTest
```
Semua test dipastikan berstatus `BUILD SUCCESSFUL` tanpa kegagalan.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka database via psql `docker exec wemade-postgres psql -U postgres -d wemade_erp -c "\d employees"` dan perhatikan definisi foreign key pada kolom `department_id`. Mengapa `department_id` diizinkan bernilai `NULL`?
- [ ] **Tantangan 2**: Amati fungsi `EmployeeDialog` di [OrgChartScreen.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/orgchart/OrgChartScreen.kt). Ketika chip `Direksi (Non-Divisi)` diklik, telusuri event apa yang dikirim ke `OrgChartViewModel` dan nilai state apa saja yang diperbarui.
