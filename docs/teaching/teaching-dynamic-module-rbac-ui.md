# 🎓 Modul Pembelajaran: Desain & Arsitektur Dynamic Module RBAC (UI/UX Pro Max)

> **Level Target**: Junior to Mid-Level Developer  
> **Topik Utama**: Domain-Driven Design (DDD), Dynamic RBAC, Compose Multiplatform, MVI State Architecture, UI/UX Intelligence  
> **Prasyarat**: Dasar Kotlin Multiplatform, pemahaman dasar enum & value class, dan prinsip dasar antarmuka deklaratif Compose.  
> **Referensi Task**: Fitur Dynamic Module RBAC & Role Management ([WeMade ERP Issue Tracker](https://github.com/amalari/wemade-erp))

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata: Mengapa Kita Tidak Menggunakan Model AWS/GCP IAM?
Banyak programmer pemula yang meniru sistem permission dari cloud provider seperti AWS atau GCP: membuat format *JSON policy*, string permission kriptik seperti `garment:production:cutting_orders:approve`, atau puluhan checkbox per modul. 

Bagi pemilik pabrik garmen, manajer HR, atau mandor konveksi:
1. **Mereka merasa terintimidasi**: Membaca istilah teknis membuat mereka takut salah klik dan merusak sistem.
2. **Setup awal sangat lama**: Mengatur puluhan tombol toggle untuk setiap karyawan baru memakan waktu dan melelahkan.
3. **Sukar dikomersialkan (Sulit Dijual)**: Tim sales SaaS Anda akan kesulitan menjelaskan paket langganan jika batasan fitur tidak terstruktur dalam paket modul bisnis yang jelas.

### Analogi Dunia Nyata: "Kartu Akses Lantai Pabrik"
Bayangkan pabrik garmen bertingkat:
- **Lantai 1**: Gudang Kain & Truk Logistik
- **Lantai 2**: Lantai Mesin Jahit & Meja Potong
- **Lantai 3**: Ruang Desain Pola & Tech Pack
- **Lantai 4**: Kantor Keuangan, HPP & Direksi

Daripada membuat daftar izin detail "boleh buka laci meja A, boleh pegang gunting B", kita cukup memberikan **kartu akses per lantai** dengan 4 level yang jelas:
1. 🚫 **Tutup Akses**: Lift tidak bisa berhenti di lantai tersebut.
2. 👁️ **Hanya Lihat**: Boleh masuk dan melihat papan tulis target, tapi dilarang menulis atau mengubah.
3. ✏️ **Input & Kerja**: Boleh mengoperasikan mesin dan mencatat hasil jahitan harian.
4. 👑 **Akses Penuh**: Boleh menandatangani surat jalan, menyetujui anggaran, dan mengatur kunci ruangan.

Ditambah satu opsi: **"Hanya Data Saya"** vs **"Semua Data Pabrik"**. Sangat sederhana, manusiawi, dan mudah dipahami siapa saja.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta membangun fitur ini dari layar kosong, inilah urutan langkah (order of operations) seorang Senior Engineer:

```
[ Step 0: Pure Domain Types ] ➔ [ Step 1: Immutability & Entity ] ➔ [ Step 2: Unit Testing ]
                                                                             │
[ Step 5: Screen Orchestrator ] ⬅ [ Step 4: UI Components ] ⬅ [ Step 3: MVI State & ViewModel ]
```

1. **Langkah 0: Definisikan Modul Bisnis & Level Akses di Pure Domain (`core`)**  
   *Jangan pernah langsung mendesain tombol di UI atau membuat tabel database!* Mulailah dari kosakata domain: `BusinessModule`, `ModuleCategory`, dan `AccessLevel`.
2. **Langkah 1: Bangun Entitas `CustomRole` yang Immutable**  
   Pastikan setiap mutasi menghasilkan salinan baru (`copy()`) dan sediakan preset bawaan pabrik (`createFactoryPresets()`) agar pengguna tidak mulai dari nol.
3. **Langkah 2: Tulis Unit Test Domain Terlebih Dahulu**  
   Verifikasi bahwa hirarki wewenang (`isAtLeast`) dan isolasi data berfungsi sempurna tanpa dependensi library eksternal.
4. **Langkah 3: Bangun State Machine MVI (`UiState`, `UiEvent`, `ViewModel`)**  
   Rancang state reaktif dengan *draft buffer* dan *dirty state tracking* agar pengguna bisa membatalkan editan sebelum disimpan.
5. **Langkah 4: Bangun Komponen UI Modular Berdasarkan Design System**  
   Buat `RoleListSidebar`, `ModuleMatrixRow` (dengan 4-tier segmented selector), dan modal dialog pop-up.
6. **Langkah 5: Komposisikan Master-Detail Screen & Integrasikan ke App**  
   Satukan ke dalam `DynamicRbacScreen` dan daftarkan ke router utama `App.kt`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pure Domain Types (`AccessLevel.kt`)

```kotlin
enum class AccessLevel(
    val displayName: String,
    val shortDescription: String,
    val weight: Int
) {
    NONE("Tutup Akses", "Menu tidak terlihat dan akses diblokir total.", 0),
    VIEW("Hanya Lihat", "Dapat melihat data dan laporan tanpa hak mengubah.", 1),
    OPERATE("Input & Kerja", "Dapat membuat, menginput, dan mengubah tugas harian.", 2),
    MANAGE("Akses Penuh", "Hak penuh termasuk approval, hapus data, dan akses data rahasia.", 3);

    fun isAtLeast(required: AccessLevel): Boolean = weight >= required.weight
}
```

**Mengapa ditulis seperti ini?**
- **Properti `weight` & Helper `isAtLeast()`**: Memungkinkan pengecekan izin bertingkat secara elegan. Jika suatu aksi memerlukan level `OPERATE`, user dengan level `MANAGE` otomatis diizinkan karena `3 >= 2`. Tidak perlu menulis perkondisian rumit `level == OPERATE || level == MANAGE`.
- **String Bahasa Pabrik**: `displayName` dan `shortDescription` berada di domain, menjamin teks yang ditampilkan di UI selalu konsisten dan ramah pengguna.

---

### Blok B: Entitas Domain & Factory Presets (`CustomRole.kt`)

```kotlin
data class CustomRole(
    val id: RoleId,
    val tenantId: TenantId?,
    val name: String,
    val description: String,
    val isSystemDefault: Boolean = false,
    val modulePermissions: Map<BusinessModule, ModuleAccessConfig> = emptyMap(),
    val userCount: Int = 0
) {
    fun updateModuleAccess(
        module: BusinessModule,
        level: AccessLevel,
        scope: DataScope = DataScope.ALL_TENANT_DATA
    ): CustomRole {
        val updated = modulePermissions.toMutableMap()
        updated[module] = ModuleAccessConfig(level, scope)
        return copy(modulePermissions = updated)
    }
}
```

**Mengapa ditulis seperti ini?**
- **Immutability (Prinsip Utama DDD)**: Fungsi `updateModuleAccess` tidak mengubah `this.modulePermissions` secara langsung, melainkan mengembalikan objek `CustomRole` baru via `copy()`. Ini menjaga *thread-safety* dan membuat state tracking di UI menjadi sangat bersih (tidak ada *side-effects* yang tersembunyi).
- **Map-based Lookup**: Menyimpan konfigurasi sebagai `Map<BusinessModule, ModuleAccessConfig>` memberikan performa pencarian $O(1)$ saat mengecek hak akses modul.

---

### Blok C: State Tracking MVI dengan Draft Buffer (`DynamicRbacViewModel.kt`)

```kotlin
// Saat user mengubah opsi akses modul di layar:
is DynamicRbacUiEvent.ChangeModuleAccess -> {
    _uiState.update { state ->
        val currentDraft = state.draftRole ?: return@update state
        val updated = currentDraft.updateModuleAccess(event.module, event.level, event.scope)
        state.copy(draftRole = updated, isDirty = true)
    }
}
```

**Mengapa ditulis seperti ini?**
- **Pemisahan `draftRole` dan `roles`**: Ketika user mengeklik radio button akses di antarmuka, perubahan hanya dicatat pada `draftRole` sementara data asli di `roles` tidak tersentuh. 
- **Indikator `isDirty`**: Tombol *Simpan* hanya aktif jika `isDirty == true`. Jika user merasa salah ubah, mereka cukup menekan tombol *Batal* untuk mengembalikan `draftRole` ke kondisi awal tanpa perlu me-reload halaman dari database.

---

### Blok D: 4-Tier Segmented Selector dengan Visual Feedback (`ModuleMatrixCard.kt`)

```kotlin
val (activeBg, activeText) = when (level) {
    AccessLevel.NONE -> Color(0xFFE2E8F0) to Color(0xFF475569)     // Slate netral
    AccessLevel.VIEW -> Color(0xFFE0F2FE) to Color(0xFF0369A1)     // Sky Blue
    AccessLevel.OPERATE -> Color(0xFFFEF3C7) to Color(0xFFB45309)  // Amber hangat
    AccessLevel.MANAGE -> Color(0xFFD1FAE5) to Color(0xFF047857)   // Emerald aman
}
```

**Mengapa ditulis seperti ini?**
- **Desain Semantik UI/UX Pro Max**: Warna bukan sekadar hiasan. Hijau emerald secara universal menandakan wewenang penuh/kepercayaan tinggi, kuning amber menandakan aksi operasional aktif, biru menandakan pemantauan informasi, dan abu-abu menandakan status non-aktif.
- **Rasio Kontras Memenuhi Standar WCAG AA**: Teks `#047857` di atas latar `#D1FAE5` memiliki kontras lebih dari `4.5:1`, sehingga sangat mudah dibaca oleh mata operator pabrik dalam kondisi pencahayaan apa pun.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan Desain | Alternatif Lain | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **4-Tier Access Level (None, View, Operate, Manage)** | Puluhan toggle checkbox per modul (Read, Create, Edit, Delete, Export, Approve) | Sangat intuitif bagi pemilik pabrik konveksi dan mengurangi kelelahan kognitif (*cognitive overload*). | Pemilik pabrik bingung, sering salah memberi izin, dan onboarding pengguna baru menjadi sangat lama. |
| **Pure Kotlin Domain Model** | Menyimpan JSON string langsung di database tanpa parsing entity | Type-safe, bebas dependensi framework, dan dapat digunakan bersama di Android, Desktop, maupun Web (WasmJS). | Potensi runtime crash jika ada typo string permission di database, tidak bisa di-unit test secara murni. |
| **MVI Pattern dengan Draft Buffer** | Direct mutation / two-way binding langsung ke database saat diklik | Memberi kesempatan pengguna meninjau seluruh perubahan sebelum melakukan commit permanen (*explicit save*). | Kesalahan klik pengguna langsung tersimpan ke server dan mengacaukan izin staf yang sedang bekerja di pabrik. |
| **Segmented Pill Selector** | Native HTML Dropdown (`<select>`) | Seluruh opsi terlihat langsung dengan 1 kali tatap mata (*zero click discovery*), touch target lebar (>= 44dp). | Pengguna harus mengeklik 2 kali untuk tiap baris modul hanya untuk melihat opsi yang tersedia. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

### 1. Jebakan: "Hardcoded Role Check di Controller / Screen"
- **Kesalahan Fatal**: Menulis perkondisian seperti `if (user.role == "MANDOR") showButton()`.
- **Kenapa bahaya**: Begitu owner pabrik membuat jabatan baru bernama "Koordinator Jahit", tombol tersebut tidak akan muncul meskipun tugas mereka sama persis!
- **Solusi Benar**: Selalu periksa wewenang modul: `if (role.hasAccess(BusinessModule.PRODUCTION_MRP, AccessLevel.OPERATE))`.

### 2. Jebakan: "Mengabaikan Dirty State Tracking"
- **Kesalahan Fatal**: Setiap kali user mengeklik level akses, aplikasi langsung menembak API HTTP `POST /api/roles`.
- **Kenapa bahaya**: Menghasilkan puluhan network request yang tidak perlu (spamming backend), dan jika koneksi internet pabrik lambat, state di layar bisa *out of sync* dengan server.
- **Solusi Benar**: Kumpulkan seluruh perubahan di `draftRole`, tandai `isDirty = true`, dan sediakan satu tombol "Simpan Perubahan" yang jelas.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian dibagi menjadi dua level:

### 1. Pure Unit Test di Domain Layer (`core/src/commonTest/`)
Kita tidak membutuhkan database atau browser untuk memverifikasi kebenaran logika bisnis. Jalankan perintah:
```bash
./gradlew :core:jvmTest --rerun-tasks
```
Contoh skenario uji kunci:
- Memastikan preset bawaan pabrik berisi minimal 5 peran standar (*Owner, PPIC, Sales, Gudang, Operator*).
- Memastikan Operator Mesin Jahit tidak dapat melihat modul kalkulasi HPP atau stok gudang.
- Memastikan perubahan akses menghasilkan salinan objek baru tanpa memutasi objek lama.

### 2. WebAssembly Build Validation (`:app:webApp`)
Pastikan kode Compose Multiplatform dapat dikompilasi menjadi WebAssembly tanpa kesalahan kompilator:
```bash
./gradlew :app:webApp:wasmJsBrowserDistribution
```
Hasil: Webpack 5 berhasil membundel Skiko dan Kotlin Wasm executable secara mulus (`BUILD SUCCESSFUL`).

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

Untuk memperdalam pemahamanmu, coba kerjakan latihan berikut:
- [ ] **Tantangan 1**: Tambahkan fitur ekspor ringkasan izin jabatan ke format teks ramah printer (misal format slip PDF/Print: *"Surat Tugas & Batas Akses Sistem"*).
- [ ] **Tantangan 2**: Buat validasi otomatis di `CustomRole`: Jika modul `COSTING_HPP` diberi akses `OPERATE`, modul `TECH_PACK_BOM` minimal harus memiliki akses `VIEW` (karena tidak mungkin menghitung biaya tanpa melihat pola baju).
- [ ] **Tantangan 3**: Tambahkan toggle "Mode Gelap (Dark Mode)" pada kartu modul dan pastikan kontras teks tetap lolos standar WCAG AA (>= 4.5:1).
