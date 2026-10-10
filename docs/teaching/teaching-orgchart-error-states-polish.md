# Teaching: Poles Keadaan Galat Org Chart (T1)

Empat perbaikan kecil pada `presentation/orgchart` setelah TRD-PLAT-010 T1.

## Step 1 - Satu fungsi pemetaan galat
`OrgChartErrorMessages.friendly(cause)` adalah satu-satunya jalan galat menjadi teks. Galat transport (teks
"proxy", ECONNREFUSED, HTTP 502/503/504, `ConnectException` di rantai penyebab) menjadi
"Server tidak dapat dihubungi. Periksa koneksi Anda lalu coba lagi."; pesan bisnis server dipertahankan.
Dipakai oleh `OrgChartLoadState` (kartu Gagal) dan semua toast galat di ViewModel. `println` log tetap
mencetak pesan mentah: pengembang butuh detail, pengguna tidak.

## Step 2 - Toast punya tingkat keparahan
Dulu banner selalu hijau, termasuk untuk 409 "Muat contoh". `OrgChartToastBanner` memilih warna dari
`severityOf(message)`: SUCCESS (Success/SuccessBg), WARNING (Warning/WarningBg), ERROR (Error/ErrorBg).
Tingkat dibaca dari awalan pesan ("Gagal", "Error", "Tidak terhubung", "Anda tidak berwenang",
"Server tidak dapat" = galat; "Peringatan" atau "tidak boleh kosong" = peringatan). 409 kini
"Peringatan: <teks server>". Pilihan ini menghindari menambah field state yang harus di-reset di puluhan
titik; harganya, pesan galat baru wajib memakai awalan itu (dijaga tes).

## Step 3 - Sembunyikan yang tak bermakna
Di keadaan Failed tombol "+ Tambah Karyawan" disembunyikan. Bilah "Pilih Bagan Divisi" (yang memuat pill
Direksi) hanya tampil bila ada divisi atau karyawan.

## Step 4 - "Pulihkan Contoh yang Hilang" + konfirmasi
Menu header berganti nama dan kini membuka `OrgChartRestoreConfirmDialog` sebelum aksi, karena aksinya
menambah data pada tenant berisi. Semantik server tidak berubah. Tombol "Muat contoh" di keadaan kosong
tetap langsung (tak ada data yang bisa tertimpa). State dialog lokal di layar: murni UI, bukan logika bisnis.

## Ratchet ukuran
ToastBanner dipindah ke `OrgChartFeedbackViews.kt`. OrgChartScreen.kt 2405 -> 2381, OrgChartViewModel.kt
1057 -> 1048 (keduanya di atas hard limit; tidak bertambah).

## Tes
`OrgChartErrorMessagesTest` (10) + penyesuaian `OrgChartViewModelServerStateTest` (409 = peringatan).

## Putaran 2 - Layout terpotong di tampilan Owner (bug lama, bukan regresi)
Gejala: header berjarak ~190dp dari top bar, panel mulai ~390dp lebih rendah, tombol "Muat contoh" terpotong,
chip statistik hilang di 360dp. Hanya muncul pada Owner karena toolbar (Opsi Struktur, + Divisi Baru, + Tambah
Karyawan) hanya tampil untuk yang berwenang tulis. Header itu tidak berubah sejak commit clay (1c38371e).

Akar masalah (tiga, bukan satu):
1. `OrgChartHeader` memakai `Row(SpaceBetween)`. Row mengukur anak pertama (judul) dengan lebar tak terbatas,
   toolbar kebagian sisa beberapa dp, tombolnya pecah per kata ("+ Divisi Baru" jadi 3 baris) dan tinggi header
   membengkak. Perbaikan: header disusun dengan `ClayFlowRow` bersarang (membungkus) + `maxLines = 1` pada
   tombol (parameter baru `ClayButton`/`ClayGuardedButton`, Kontrak 13). Header dipindah ke `OrgChartHeader.kt`.
2. Panel form berlebar tetap 420dp di `Row` bersama panel bagan: di bawah ~880dp bagan terdesak habis. Perbaikan:
   `BoxWithConstraints`; sempit -> panel ditumpuk dalam kolom yang bisa di-scroll (bagan dulu, lalu form,
   masing-masing bertinggi tetap karena isinya scroll sendiri; scroll bersarang tanpa tinggi tetap akan crash).
3. `OrgChartEmptyState`/`OrgChartFailedView` memakai `Box(fillMaxSize)` tanpa scroll, jadi isi yang lebih tinggi
   dari ruangnya terpotong. Perbaikan: `Column(fillMaxSize().verticalScroll())` - urutan modifier penting:
   fillMaxSize dulu supaya terpusat bila muat, lalu scroll.

## Putaran 2 - Menu dan konfirmasi
- Menu "Opsi Struktur" kini ditutup lebih dulu oleh item yang dipilih; sebelumnya dialog konfirmasi menutup tapi
  menu tetap terbuka di belakangnya karena state menu hanya ditutup VM saat aksi benar-benar jalan.
- "Mulai dari Kosong" kini dikonfirmasi lewat `OrgChartClearConfirmDialog` (tombol Danger). Dialog dirangkum
  jadi `OrgChartConfirmDialog` generik; dialog pemulihan dan pengosongan hanya pembungkus teks (Aturan Tiga Kali).
- Catatan: aksi "Mulai dari Kosong" hanya mengosongkan state lokal, tidak menghapus di server; perlu keputusan
  terpisah. `DropdownMenu` Material masih berbayangan blur (celah komponen: belum ada `ClayDropdownMenu`).
