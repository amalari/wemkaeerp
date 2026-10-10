# Teaching: Layout sempit layar Hak Akses (/rbac)

Mengikuti pola Putaran 2 di `teaching-orgchart-error-states-polish.md`.

## Masalah (temuan visual T3, 360dp)
1. Keadaan Empty/Failed memakai `Box(fillMaxSize)` tanpa scroll: "Coba lagi" dan "Buat jabatan" terpotong.
2. Header `Row(SpaceBetween)`: judul mengambil seluruh lebar, toolbar (chip, "Ke Halaman Login") terdesak habis.
3. Saat Failed, chip "0 Jabatan" / "15 Modul SaaS" tampil padahal angkanya tak diketahui.
4. Tab "Per Jabatan" pecah per suku kata.

## Perbaikan
- `ScrollCenter` di `RbacStatusViews.kt`: `Column(fillMaxSize().verticalScroll())` dengan isi terpusat. Urutan modifier penting: fillMaxSize dahulu, baru scroll.
- `RbacScreenHeader.kt`: `ClayFlowRow` bersarang (membungkus), tombol `maxLines = 1` (Kontrak 13).
- `RbacHeaderChips.forState`: fungsi murni; Loading/Failed tanpa chip, Total Karyawan hanya bila diketahui. Diuji di `RbacHeaderChipsTest`.
- `RbacViewModeTabs.kt`: label `maxLines = 1, softWrap = false`, bilah `horizontalScroll`.
- `DynamicRbacScreen.kt` turun 502 -> 357 baris.

## Pelajaran
Angka yang belum diketahui jangan ditampilkan sebagai 0; sembunyikan. Pisahkan keputusan tampil (murni, teruji) dari rendering.

## Belum diverifikasi
Cek visual di browser (1280 dan 360dp; Loaded/Empty/Failed) belum dijalankan pada sesi ini.
