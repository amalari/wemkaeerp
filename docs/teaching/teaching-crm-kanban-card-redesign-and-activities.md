# 🎓 Modul Pembelajaran: Redesain Kartu CRM Kanban, Stage Transition Dropdown & Modul Catatan Aktivitas Sales

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Full-Stack End-to-End Integration, PostgreSQL RLS, Ktor REST API, Compose Multiplatform Claymorphism UI  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform, Coroutine Flow, dan PostgreSQL Relational Schema  
> **Referensi Task**: CRM Sales — Redesain Kartu Kanban, Stage Dropdown, & Non-Reply Sales Activity Log Modal

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Dalam proses penjualan B2B konveksi dan garmen (seperti pembuatan seragam, jaket kantor, kaos event), proses penanganan calon pelanggan (*lead*) membutuhkan kecepatan dan kejelasan konteks:
1. **Tombol Stage Raksasa Memakan Ruang Kartu**: Menaruh tombol-tombol aksi transisi besar ("Kualifikasi", "Unqualify") di bawah setiap kartu memenuhi layar dan membuat kartu menjadi terlalu tinggi. Di CRM modern, status tahap (*stage*) idealnya bertindak sebagai badge interaktif dengan dropdown untuk memindahkan tahapan.
2. **Ketiadaan Riwayat Follow-Up**: Sales rep sering kali lupa catatan interaksi terakhir dengan pelanggan ("Kapan terakhir di-WA?", "Apa respons mereka atas harga sampel?"). Jika tidak ada tempat mencatat aktivitas sales langsung di kartu lead, catatan follow-up tercecer di buku catatan fisik atau chat WhatsApp pribadi yang hilang saat terjadi pergantian PIC sales.
3. **Identitas PIC yang Tidak Jelas**: Pada tim penjualan dengan banyak sales, manajer perlu melihat secara sekilas siapa yang bertanggung jawab (*owner PIC*) atas suatu lead tanpa harus membuka detail satu per satu.

### Analogi Sederhana
Bayangkan kartu Kanban ini seperti **Folder Map Prospek Fisik** di meja sales:
- **Badge di pojok kanan atas** adalah label map berwarna yang bisa diganti tab-nya saat status klien naik kelas dari "Calon Baru" ke "Klien Terkualifikasi".
- **Avatar PIC di pojok kanan bawah** adalah inisial stempel nama sales yang memegang map tersebut.
- **Ikon Komentar dengan Counter di pojok kiri bawah** adalah kartu indeks catatan kecil berisi rekam jejak tanggal dan isi percakapan sales dengan klien setiap kali selesai menelepon atau bertatap muka.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Sesuai aturan DDD dan Full-Stack Rule §13 proyek WeMade ERP, urutan pengerjaannya adalah:

```
[1. Pure Domain Layer (core)]
       ↓
[2. Database Migration & RLS (server)]
       ↓
[3. Persistence & Repository Implementation (server)]
       ↓
[4. Ktor REST Routes & Access Guard (server)]
       ↓
[5. Network Client & ViewModel (app/shared)]
       ↓
[6. Claymorphism UI Components (app/shared)]
```

1. **Langkah 1: Pure Domain Layer (`core/`)**
   - Tambahkan properti `activityCount: Int = 0` pada domain entity `CrmLead`.
   - Buat entity murni `LeadActivity` beserta Value Object `LeadActivityId`.
   - Buat repository contract `LeadActivityRepository`.
   - Buat use case bisnis: `AddLeadActivityUseCase` dan `GetLeadActivitiesUseCase`.
   - Perbarui serialisasi wire codec di `CrmLeadCodec.kt`.
2. **Langkah 2: Database Migration & Multi-Tenant RLS (`server/`)**
   - Buat migrasi SQL Flyway `V30__create_crm_lead_activities.sql` dengan relasi `ON DELETE CASCADE` ke tabel `crm_leads` dan aktifkan Row-Level Security (RLS) berbasis tenant.
3. **Langkah 3: Persistence Layer (Exposed ORM)**
   - Buat tabel Exposed `CrmLeadActivitiesTable`.
   - Implementasikan `PostgresLeadActivityRepository` untuk operasi `findByLeadId`, `countByLeadIds`, dan `save`.
   - Perbarui `PostgresCrmLeadRepository` agar secara otomatis menghitung `activityCount` saat membaca lead aktif.
4. **Langkah 4: Backend Routing & Security (`CrmRoutes.kt`)**
   - Tambahkan endpoint `GET /api/tenant/crm/leads/{id}/activities` (izin `VIEW`).
   - Tambahkan endpoint `POST /api/tenant/crm/leads/{id}/activities` (izin `OPERATE`).
5. **Langkah 5: Client-Side Integration (`app/shared/`)**
   - Perluas `CrmRemoteDataSource` dan `CrmApiClient` untuk memanggil API aktivitas.
   - Tambahkan state dan event pada `CrmUiState` dan `CrmViewModel` (`OpenActivities`, `CloseActivities`, `SubmitActivity`).
6. **Langkah 6: Shared UI Presentation**
   - Perbarui `CrmKanbanCard.kt` dengan outline warna per-stage, badge dropdown stage, tag phone & email berdampingan, tombol aktivitas dengan counter di kiri bawah, dan avatar PIC di kanan bawah.
   - Buat dialog pop-up `LeadActivitiesDialog.kt` bergaya Claymorphism.
   - Sambungkan callback di `CrmKanbanBoard.kt`, `CrmMobileKanbanView.kt`, dan `CrmWorkspaceScreen.kt`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Domain Entity & Contract (`core/`)

```kotlin
// core/.../domain/crm/LeadActivity.kt
@JvmInline
value class LeadActivityId(val value: String) {
    init {
        require(value.isNotBlank()) { "LeadActivityId cannot be blank" }
    }
}

data class LeadActivity(
    val id: LeadActivityId,
    val tenantId: TenantId,
    val leadId: LeadId,
    val authorEmployeeId: OrgNodeId?,
    val authorName: String,
    val content: String,
    val createdAt: Instant
)
```
**Mengapa ditulis seperti ini?**
- `LeadActivityId` dibungkus dalam `value class` agar tidak bisa tertukar dengan `LeadId` atau ID lainnya saat dikompilasi (Type-Safety).
- Entity `LeadActivity` bersifat immutable (`data class` tanpa `var`) dan tidak bergantung pada framework eksternal apa pun (Pure Kotlin).
- Menyimpan `authorName` secara langsung memastikan riwayat komentar tetap memiliki nama yang terbaca meskipun akun karyawan yang menulis di masa depan dinonaktifkan atau dihapus.

---

### Blok B: Skema Database dengan Row-Level Security (`server/`)

```sql
-- server/src/main/resources/db/migration/V30__create_crm_lead_activities.sql
CREATE TABLE IF NOT EXISTS crm_lead_activities (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    lead_id VARCHAR(64) NOT NULL REFERENCES crm_leads(id) ON DELETE CASCADE,
    author_employee_id VARCHAR(64) REFERENCES employees(id) ON DELETE SET NULL,
    author_name VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crm_lead_activities_lead ON crm_lead_activities(tenant_id, lead_id, created_at DESC);

ALTER TABLE crm_lead_activities ENABLE ROW LEVEL SECURITY;
ALTER TABLE crm_lead_activities FORCE ROW LEVEL SECURITY;

CREATE POLICY crm_lead_activities_tenant_isolation ON crm_lead_activities
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id', true));
```
**Mengapa ditulis seperti ini?**
- `ON DELETE CASCADE` pada `lead_id` memastikan ketika sebuah lead dihapus, seluruh rekam jejak aktivitasnya ikut bersih tanpa meninggalkan data sampah yatim piatu (*orphaned records*).
- Indeks komposit `(tenant_id, lead_id, created_at DESC)` membuat kueri riwayat aktivitas menjadi kueri berkecepatan tinggi $O(\log N)$ yang sudah terurut secara kronologis.
- Multi-tenancy dijaga ketat di level kernel database lewat PostgreSQL RLS: query tanpa tenant session variabel `app.current_tenant_id` tidak akan pernah bisa membaca aktivitas tenant lain.

---

### Blok C: Aggregasi Count Aktivitas di Lead Repository (`PostgresCrmLeadRepository.kt`)

```kotlin
val leadIds = leads.map { it.id.value }
val countColumn = CrmLeadActivitiesTable.id.count()
val counts = CrmLeadActivitiesTable
    .select(CrmLeadActivitiesTable.leadId, countColumn)
    .where {
        (CrmLeadActivitiesTable.tenantId eq tenantId.value) and
        (CrmLeadActivitiesTable.leadId inList leadIds)
    }
    .groupBy(CrmLeadActivitiesTable.leadId)
    .associate { LeadId(it[CrmLeadActivitiesTable.leadId]) to it[countColumn].toInt() }

leads.map { lead -> lead.copy(activityCount = counts[lead.id] ?: 0) }
```
**Mengapa blok ini ditulis begini?**
- **Menghindari Masalah N+1 Kueri**: Alih-alih melakukan kueri `COUNT(*)` terpisah untuk setiap lead satu per satu di loop, kita mengambil ringkasan jumlah aktivitas sekaligus untuk seluruh lead yang ada di layar dalam **satu kueri SQL** menggunakan `GROUP BY lead_id`.

---

### Blok D: Isolasi Event Hit-Testing pada UI Compose Multiplatform (`CrmKanbanCard.kt`)

```kotlin
ClayCard(
    modifier = modifier.fillMaxWidth(),
    outlineColor = cardOutline,
    borderWidth = ClayBorder.Medium,
    selected = selected,
    onClick = null, // Hindari parent container menelan event klik anak-anaknya!
    contentPadding = PaddingValues(ClaySpacing.Lg)
) {
    // Header
    Row(...) {
        Text(text = lead.title, modifier = Modifier.clickable { onSelectLead(lead.id) })
        val badgeInteractionSource = remember { MutableInteractionSource() }
        Box {
            ClayBadge(
                text = "${lead.stage.displayName} ▾",
                modifier = Modifier.clickable(interactionSource = badgeInteractionSource, indication = null) {
                    stageMenuExpanded = true
                }
            )
            DropdownMenu(...)
        }
    }

    // Body yang bisa diklik membuka Lead Inspector Modal
    Column(modifier = Modifier.fillMaxWidth().clickable { onSelectLead(lead.id) }) {
        // Tag telepon & email, estimasi nilai...
    }

    // Footer
    val activityInteractionSource = remember { MutableInteractionSource() }
    Row(...) {
        // Tombol aktivitas terisolasi
        Row(
            modifier = Modifier
                .clayFlat(shape = ClayShapes.Pill, ...)
                .clickable(interactionSource = activityInteractionSource, indication = null) {
                    onOpenActivities(lead)
                }
        ) {
            Text("💬")
            Text("${lead.activityCount} Aktivitas")
        }

        // Avatar PIC
        Box(...) { Text(getInitials(owner.name)) }
    }
}
```
**Mengapa teknik ini sangat krusial di Compose Wasm?**
- Jika kita menaruh `onClick = { onSelectLead(lead.id) }` langsung pada `ClayCard`, maka `Column` terluar dari kartu tersebut akan menelan semua pointer input sebelum elemen tombol di dalamnya sempat bereaksi.
- Dengan memisahkan area klik:
  1. Area teks/konten lead membuka dialog detail lead (`onSelectLead`).
  2. Badge status membuka menu pilihan stage dropdown (`stageMenuExpanded = true`).
  3. Tombol pill komentar membuka dialog catatan aktivitas sales (`onOpenActivities`).
  4. Penggunaan `remember { MutableInteractionSource() }` terpisah mencegah konflik state penekanan (*pressed state collision*).

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Jebakan N+1 Query pada Agregasi Counter**:
   - *Salah*: Memanggil `activityRepo.countByLeadId(lead.id)` satu per satu di dalam loop map DTO. Ini membuat 100 lead menghasilkan 101 kueri database.
   - *Benar*: Gunakan satu kueri SQL dengan `inList` dan `groupBy`.
2. **Lupa Policy RLS pada Tabel Baru**:
   - *Salah*: Membuat tabel `crm_lead_activities` tanpa menyertakan `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`. Akibatnya, tenant A bisa membaca aktivitas tenant B jika query dilakukan tanpa filter manual.
   - *Benar*: Selalu pasang policy RLS multi-tenant di file migrasi Flyway.
3. **Membuat Nested Reply yang Berlebihan**:
   - *Sesuai Requirement*: Modul aktivitas sales ini dirancang sebagai catatan log linier (*flat feed*) aktivitas sales, bukan forum diskusi bertingkat (Reddit-style). Jangan menambah kompleksitas `parentActivityId` jika spesifikasi bisnis hanya meminta catatan feed non-reply.

---

## 🧪 5. Verifikasi & Pengujian Mandiri

Untuk memverifikasi kebenaran implementasi:
1. **Verifikasi Kompilasi & Database**:
   ```bash
   ./gradlew :server:compileKotlin :app:shared:compileKotlinWasmJs
   ```
2. **Verifikasi Browser E2E**:
   - Buka `http://localhost:3000/crm-sales`.
   - Periksa kartu: nomor handphone dan email tampil berdampingan sebagai `ClayTag`.
   - Periksa avatar PIC bulat di pojok kanan bawah menampilkan inisial (misal: "DK" untuk Dedi Kurniawan, atau "?" jika kosong).
   - Klik tombol `💬 0 Aktivitas`: dialog modal muncul menampilkan feed dan form input.
   - Ketikkan pesan follow-up, klik "Kirim": komentar muncul di daftar dengan inisial pembuat dan tanggal.
   - Tutup modal: periksa counter kartu berubah menjadi `1 Aktivitas`.
   - Klik badge status di kanan atas: menu dropdown muncul, pilih opsi transisi (misal: "Kualifikasi"), dan kartu akan berpindah ke kolom tujuan secara instan.
