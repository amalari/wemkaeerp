# 🎓 Modul Pembelajaran: `BusinessModule` sebagai Data Domain Pack (Jalur B, B6)

> **Level Target**: Mid Developer
> **Topik Utama**: Menghapus enum yang menjadi kunci keamanan, dengan alarm "nol perubahan akses"
> **Prasyarat**: B3 (slot), B5 (gerbang route) · TRD: [`TRD-PLAT-001-business-module.md`](../trd/TRD-PLAT-001-business-module.md)

---

## 💡 1. Kenapa paling berisiko

`BusinessModule` adalah kunci RBAC, entitlement, katalog, menu, dan gerbang. Refactor yang salah tidak menimbulkan
error; yang terjadi, **orang kehilangan atau mendapat akses tanpa ada yang tahu**. Karena itu B6 dimulai dengan
alarm sebelum ada satu baris pun yang dipindah.

## 🧱 2. Tahapan

| Tahap | Isi | Kunci keputusan |
|---|---|---|
| B6a | `ModuleDefinition`/`ModuleSection` di pack, dibangun dari enum, tabel emas | **Urutan** ikut dibekukan (= urutan menu) |
| B6b | Snapshot 690 keputusan nyata dari DB B lewat `/me/access` | Alarm untuk tahap berikutnya; terbukti merah saat 1 keputusan diubah |
| B6c | `ModuleIdCodec` menggantikan 14 parser | NAME persis, code tak peka huruf; nilai tak dikenal **dilog**, tetap ditolak |
| B6d | `typealias BusinessModule = ModuleId`; `GarmentModules` literal | Anggota enum → extension; `entries` → `BusinessModules` (pack aktif) |
| B6e | `ModuleScreenRegistry`, `ModuleSampleRows` | Rantai `if (module == X)` & `when` exhaustive → data |
| B6f | Menu dari `pack.sections/modules`; rute `/m/{code}` | Modul tanpa layar khusus kini **bisa** muncul |
| B6g | Pack e-learning fiktif: katalog, parser, wewenang, menu, rute | Nol baris kode inti yang menyebut e-learning |

## 🔑 3. Tiga keputusan yang membuat "nol migrasi" mungkin

1. **NAME == code.uppercase()** untuk ke-15 modul (dibuktikan test). Satu parser membaca kedua format yang tercampur
   di DB (RBAC/entitlement = NAME, katalog/pipeline = code) tanpa tabel pemetaan.
2. **Format tulis tidak diubah** per lokasi, jadi DB repo A dan B tetap kompatibel untuk cutover.
3. **Audit data sebelum menyamakan semantik**: nol nilai modul huruf campuran di A & B, sehingga "code tak peka
   huruf besar" di semua tempat tidak mengubah apa pun.

## ⚠️ 4. Jebakan

1. **`toString()` value class.** Enum mencetak `CRM_SALES`; `ModuleId` mencetak `ModuleId(value=crm_sales)`. Setiap
   interpolasi, `joinToString()`, dan kunci map disisir manual. Nol ditemukan, karena semua penyimpanan memakai
   `.name`/`.code`.
2. **Snapshot yang ikut data test.** Test integrasi lain membuat tenant `factory-NNNNN` di DB dev setiap run.
   Snapshot mengecualikannya; kalau tidak, alarm berbunyi tanpa ada perubahan akses.
3. **Pemeriksaan import yang tertipu awalan nama.** `import …ModuleIdCodec` membuat skrip mengira `ModuleId` sudah
   diimpor.
4. **Siklus inisialisasi object.** `GarmentModules` ↔ `GarmentSlots` saling merujuk. Konstanta dideklarasikan dulu,
   daftar definisi `lazy`.
5. **Batas ukuran file.** `App.kt` 597/600. Kode rute generik ditaruh di file sendiri, bukan di `App.kt`.

## 🧪 5. Pembuktian

- `GarmentModulesParityTest` (tabel emas), `AccessSnapshotB6Test` (690 keputusan identik di setiap tahap),
  `ModuleIdCodecTest`, `ElearningPackTest` (core), `ElearningNavMenuTest` (klien).
- core 961, app 162, server 242; JVM/Wasm/JS; visual menu Sales, layar RBAC, kanvas, `/m/crm_sales`, `/m/tidak_ada`.

## 🧹 6. Pembersihan pasca-B6

| Sisa | Penyelesaian |
|---|---|
| `enum ModuleCategory` (layar RBAC & dialog entitlement) | Dihapus. `ModuleSection` kini membawa `colorHex`/`tintHex` = token yang dulu dipetakan `when (ModuleCategory)`; tabel emas warna menjaganya. Tampilan kartu identik. |
| Breadcrumb `/m/{code}` menampilkan "Modul" | `AppTopBar(title = …)` = nama modul. Ruangnya didapat dengan memindah `AuthGuardCard` ke file sendiri: `App.kt` **597 → 524** baris. |
| `AccessSnapshotB6Test` | Dipertahankan sampai cutover sebagai alarm RBAC/entitlement. |

**Jebakan**: server dev yang tidak di-restart setelah `core` dibangun ulang memuat kelas dari jar yang sudah berubah,
dan hasilnya 500 `NoClassDefFoundError` yang menyesatkan. Restart server setiap kali `core` berubah.

## 🧭 7. Berikutnya

- **B7**: pack per tenant (`tenants.domain_pack`), pengganti `soleActivePack` & `withSoleActivePackForTest`, plus
  vertikal kedua yang **nyata**.
