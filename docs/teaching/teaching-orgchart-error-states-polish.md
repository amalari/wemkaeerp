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
