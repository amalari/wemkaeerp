# 🎓 Modul Pembelajaran: Dasbor yang Dihitung dari Layar Lain & Checklist Interaktif (TRD-PLAT-003, tahap 3)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Sesi bersama lintas layar, agregat tertutup, state Compose reaktif, spec tanpa entitas
> **Prasyarat**: [kanban](teaching-builder-interactive-prototype-kanban.md), [tabel](teaching-builder-interactive-prototype-table.md)

## 1. Masalah
Dasbor lama menampilkan "Order aktif 12 PO" yang diketik tangan: memindah kartu tidak mengubah apa pun, jadi prototype tidak terasa seperti satu sistem. Checklist hanya gambar centang.

## 2. Start dari mana
1. **Model agregat tertutup** (`DashboardSpec.kt`): `CountSpec` (hitung baris layar modul lain, filter `equals`/`notEquals`, `suffix`), `TileSpec`, `DashboardConfig`, `DashboardEvaluator` (murni; sumber baris disuntikkan).
2. **`ScreenSpec.entityId` nullable**: dasbor tidak punya entitas, layar data wajib punya — dijaga di konstruktor.
3. **Factory**: `checklist` (butir + BOOL "ya"/"tidak") dan `dashboard` (hanya "hidup" bila minimal satu ubin terikat hitungan; semua-statis → `null`, digambar statis).
4. **Data pack**: `DashboardHints` memetakan *label ubin → CountSpec*; garment mengikat "Order aktif" ke papan Jadwal Potong dan "SPK sampling berjalan" ke papan Sampling.
5. **Sesi** (`PrototypeSession`): memegang state semua blok; `rowsOf(moduleId)` jadi sumber dasbor.
6. UI: `InteractiveChecklist`, `InteractiveDashboard`, `InteractiveBlock` (dispatcher by state).

## 3. Keputusan penting
- **Sumber agregat diacu dengan `moduleId`**, bukan id layar: pack tahu modul, bukan id layar turunan draf.
- **Nilai statis tetap ada sebagai cadangan** bila layar sumber tak bisa dimainkan (mis. difilter/hilang) — dasbor tidak pernah kosong atau salah angka diam-diam.
- **Reaktivitas gratis**: baris papan adalah `mutableStateOf`; dasbor membaca lewat `tiles()` di komposisi, jadi otomatis tergambar ulang saat kartu dipindah. Tidak ada event bus.
- **Sesi dibangun dari SEMUA layar draf**, bukan hanya yang sedang difilter, supaya dasbor tetap bisa menghitung dari modul yang disembunyikan.
- Angka seed kini mengikuti data (3 PO, bukan "12 PO" karangan). Data realistis menyusul di tahap seed.

## 4. Jebakan
1. Membuat state per-blok dengan `remember` di tiap composable → dasbor tak bisa melihat papan. Sesi harus di atas semuanya.
2. Dasbor yang semua ubinnya statis dianggap interaktif — menyesatkan; factory menolaknya.
3. Layout bergeser setelah drag (kolom bertambah tinggi): uji klik berurutan harus mengambil screenshot baru, jangan memakai koordinat lama.

## 5. Verifikasi
- Test core: `InteractiveDashboardChecklistTest` (centang lewat reducer & nilai BOOL tak sah ditolak, hitungan dasbor + cadangan, dasbor semua-statis → null, round-trip codec, dasbor dengan entitas ditolak, layar garment interaktif).
- Mata di `/builder/prototype`: memindah SP-1048 ke Selesai mengubah "SPK sampling berjalan" 4 → 3; mencentang butir ketiga mengubah checklist menjadi "4 dari 4 selesai".

## 6. Tantangan
- [ ] Tambah agregat `SUM` (butuh parser angka berunit/format Indonesia).
- [ ] Tampilkan ubin "Menipis" dari status tabel Stok Kain (`CountSpec` pada tabel).
- [ ] Ikat ubin ke dua modul sekaligus dan uji prioritas filter.
