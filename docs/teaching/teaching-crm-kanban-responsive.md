# 🎓 Modul Pembelajaran: Implementasi Papan Kanban CRM Responsif (Web & Mobile)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Full-Stack End-to-End Architecture, Compose Multiplatform, Claymorphism Design System, Adaptive Viewport Handling  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform, Compose UI state, dan arsitektur Client-Server  
> **Referensi Task**: Implementasi CRM Leads Kanban Board (New Lead, Qualified Lead, Unqualified)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada industri manufaktur garmen dan konveksi, calon pelanggan (brand fashion, korporasi, komunitas) setiap hari masuk melalui WhatsApp, DM media sosial, atau website. 
Jika data prospek hanya ditampilkan sebagai daftar tabel (list view) biasa yang kaku:
1. Staf sales kesulitan membedakan mana kontak yang **baru saja masuk** (belum tahu apa yang dicari), mana prospek yang **sudah terkualifikasi** (kuantiti sesuai MOQ, bahan disepakati, ada estimasi budget), dan mana yang **batal/unqualified**.
2. Nilai pipeline penjualan tidak terlihat sekilas.
3. Di perangkat mobile, tabel yang lebar atau kanban multi-kolom yang dipaksakan berdampingan akan membuat teks terpotong dan kartu berdempetan ~100dp per kolom, sehingga tidak bisa digunakan di lapangan oleh tim sales.

### Analogi Sederhana
Bayangkan sebuah **papan fisik berkolom** di ruang kantor divisi sales konveksi:
- **Kotak 1 (New Lead)**: Catatan tempel nomor telepon customer baru yang baru menyapa "Halo kak, mau tanya harga kaos". Belum ada nominal transaksi.
- **Kotak 2 (Qualified Lead)**: Prospek yang sudah berdiskusi: mau pesan 500 pcs kaos sablon combed 24s senilai Rp 35.000.000.
- **Kotak 3 (Unqualified)**: Prospek yang minta jahit 1 pcs (di bawah MOQ pabrik 50 pcs) atau budget tidak mencukupi.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika seorang developer harus membangun fitur ini dari nol, urutan lapisan yang wajib dikerjakan adalah:

1. **Langkah 1: Pure Domain Layer (`core/`)**
   - Mendefinisikan enum `LeadStage` (`NEW_LEAD`, `QUALIFIED`, `UNQUALIFIED`).
   - Menerapkan aturan transisi antar status pada fungsi `LeadStage.canTransitionTo`.
   - Mengapa domain dulu? Karena aturan bisnis tidak boleh bergantung pada UI atau library database apa pun.

2. **Langkah 2: Database & Persistence Layer (`server/`)**
   - Membuat migrasi Flyway (`V24__align_crm_lead_stages_kanban.sql`) untuk mengupdate default kolom dan data stage.
   - Menambahkan indeks komposit `(tenant_id, stage)` untuk mempercepat pengelompokan kanban.
   - Menyesuaikan tabel Exposed `CrmLeadsTable` dan repository `PostgresCrmLeadRepository`.

3. **Langkah 3: Client State & Business Integration (`app/shared/`)**
   - Menambahkan mode tampilan `CrmViewMode` (`KANBAN` vs `LIST`) dan `activeMobileStage` pada `CrmUiState`.
   - Menyediakan fungsi komputasi untuk mengelompokkan lead (`leadsByStage`) dan menghitung total estimasi nilai (`stageTotalEstimatedValue`).
   - Menangani event perubahan mode tampilan dan pemindahan tahap di `CrmViewModel`.

4. **Langkah 4: Presentation Layer — Komponen Claymorphism (`app/shared/presentation/crm/`)**
   - `CrmKanbanCard`: Kartu neo-brutalist dengan outline 3dp, info brand, nominal rupiah, dan quick action button.
   - `CrmKanbanColumn`: Kolom status dengan counter pill dan scrollable cards.
   - `CrmKanbanBoard`: Layout desktop horizontal multi-kolom yang bersanding dengan Side Inspector Drawer.
   - `CrmMobileKanbanView`: Layout smartphone dengan **Segmented Tab Switcher** agar tidak sempit.
   - `CrmWorkspaceScreen`: Menghubungkan breakpoint responsif (`ClayBreakpoints.MasterDetail`) dan view toggle.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pure Domain `LeadStage` & Nullable Value Model

```kotlin
// core/.../domain/crm/CrmLeadValueObjects.kt
enum class LeadStage(val displayName: String) {
    NEW_LEAD("New Lead"),
    QUALIFIED("Qualified Lead"),
    UNQUALIFIED("Unqualified");

    fun canTransitionTo(target: LeadStage): Boolean = this != target

    companion object {
        fun fromCode(code: String?): LeadStage = when (code) {
            "NEW_LEAD", "INQUIRY" -> NEW_LEAD
            "QUALIFIED", "TECHPACK_SPEC", "QUOTATION_SENT", "SAMPLE_APPROVAL", "DEAL_DP_CONFIRMED" -> QUALIFIED
            "UNQUALIFIED", "LOST" -> UNQUALIFIED
            else -> entries.firstOrNull { it.name == code } ?: NEW_LEAD
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
- `fromCode` memetakan kode lama (seperti `INQUIRY` dan `LOST`) ke tahap baru secara otomatis. Ini menjaga agar aplikasi tidak melempar deserialization error saat membaca data lama di database.
- `canTransitionTo`: Pada papan Kanban, perpindahan antar kolom bersifat fleksibel (bisa memindahkan prospek baru ke qualified, atau mengembalikan prospek unqualified jika customer kembali menghubungi).

### Blok B: Penanganan Nilai Nullable (`MoneyIdr?`)

```kotlin
// app/shared/.../CrmKanbanCard.kt
val estValue = lead.estimatedValue
if (estValue != null) {
    Text(
        text = formatRupiah(estValue.amount),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = WeMadeColors.Primary
    )
} else {
    Text(
        text = "Nilai: Belum Diestimasi",
        fontSize = 11.sp,
        fontStyle = FontStyle.Italic,
        color = WeMadeColors.OnSurfaceMuted
    )
}
```
**Mental Model**:
- Di tahap awal (`NEW_LEAD`), prospek belum memiliki kesepakatan harga atau kuantiti. Karena itu, `estimatedValue` dan `estimatedPcs` berstatus **`nullable`** (`null`).
- Penggunaan variabel lokal `val estValue = lead.estimatedValue` menghindari masalah Smart Cast pada Kotlin Multiplatform saat mengakses properti publik dari modul `:core`.

### Blok C: Adaptasi Mobile Menggunakan Segmented Tab Switcher

```kotlin
// app/shared/.../CrmMobileKanbanView.kt
Row(
    modifier = Modifier.fillMaxWidth().clayFlat(...).padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
) {
    LeadStage.entries.forEach { stage ->
        val stageCount = leads.count { it.stage == stage }
        val isSelected = stage == activeStage

        ClayButton(
            text = "${stage.iconLabel()} ${stage.displayName.take(9)} ($stageCount)",
            style = if (isSelected) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
            onClick = { onSelectStage(stage) },
            modifier = Modifier.weight(1f)
        )
    }
}
```
**Mengapa teknik ini dipilih untuk Mobile?**
- Jika kita memaksakan 3 kolom horizontal di layar ponsel berlebar 360dp, masing-masing kolom hanya kebagian lebar ~100dp. Teks nama brand dan nomor telepon akan terpotong parah.
- Dengan **Segmented Tab Switcher**, pengguna memilih tahap yang ingin dilihat, dan daftar kartu di bawahnya tetap mendapatkan lebar penuh (full-width) layar ponsel, memberikan pengalaman pengguna (UX) yang sangat nyaman.

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Jebakan Smart Cast Lintas Modul**:
   Di Kotlin, properti `public val` pada `data class` di modul lain (`:core`) tidak dapat di-smart-cast secara otomatis dengan `if (lead.estimatedValue != null)` karena compiler tidak dapat menjamin ketiadaan custom getter di modul eksternal. Selalu simpan ke variabel lokal: `val value = lead.estimatedValue; if (value != null) { ... }`.

2. **Memaksakan Desain Desktop ke Mobile**:
   Membuat layout kanban tanpa breakpoint akan merusak tampilan smartphone. Pisahkan arsitektur presentation menjadi dua varian: `CrmKanbanBoard` (Desktop) dan `CrmMobileKanbanView` (Mobile) yang diatur oleh `BoxWithConstraints(maxWidth >= ClayBreakpoints.MasterDetail)`.

3. **Lupa Menghandle Partial Linkage di Wasm**:
   Setiap tipe data dari library pihak ketiga yang diekspos oleh modul domain publik (seperti `Instant` atau `LocalDate` dari `kotlinx-datetime`) wajib dideklarasikan sebagai `api(...)` di Gradle agar tertaut secara penuh ke binary WebAssembly executable.

---

## 🧪 5. Cara Verifikasi

1. **Unit Test Suite**:
   ```bash
   ./gradlew :core:jvmTest :app:shared:jvmTest
   ```
   Memastikan logika bisnis `LeadStageTest` dan codec serialisasi berjalan benar.
2. **Kompilasi Wasm Bundle**:
   ```bash
   ./gradlew :app:webApp:wasmJsBrowserDevelopmentWebpack
   ```
   Memastikan seluruh komponen Compose Multiplatform berhasil di-bundle menjadi WebAssembly.
3. **Uji Visual Responsif**:
   - Buka `http://localhost:3000/crm-sales`.
   - Di Desktop: Periksa 3 kolom berdampingan, klik tombol "+ Tambah Lead", coba ubah tahap dengan tombol "Kualifikasi" atau "Unqualify".
   - Di Mobile (DevTools Responsive Mode 375px): Periksa tab switcher `[📥 New] [⭐ Qual] [⛔ Un]`, pastikan kartu memenuhi lebar layar secara proporsional.
