# Eval Live Builder - penanya klarifikasi & penyunting isian (flash vs pro)

- Dibuat: 2026-10-07T18:44:37.078858Z (UTC)
- Penilaian: otomatis dan tegas (keputusan bertanya; keadaan field akhir setelah `applyEdits` + validator). Tidak ada model penilai.
- Saldo API: sebelum 1.21 (DeepSeek) -> sesudah 1.21 (DeepSeek)

## Tahap: penanya

| Model | Lulus | Galat model | Rata-rata ms | Maks ms | Token |
|---|---|---|---|---|---|
| deepseek-flash | 10/10 | 0 | 1648 | 3358 | 5878 |
| deepseek-v4-pro | 10/10 | 0 | 6226 | 17494 | 10121 |

| Kasus | deepseek-flash | deepseek-v4-pro |
|---|---|---|
| kabur-1 | LULUS - bertanya 3 (seharusnya bertanya) (2340ms) | LULUS - bertanya 3 (seharusnya bertanya) (6339ms) |
| kabur-2 | LULUS - bertanya 3 (seharusnya bertanya) (1557ms) | LULUS - bertanya 3 (seharusnya bertanya) (3621ms) |
| kabur-3 | LULUS - bertanya 3 (seharusnya bertanya) (1065ms) | LULUS - bertanya 3 (seharusnya bertanya) (3623ms) |
| kabur-4 | LULUS - bertanya 3 (seharusnya bertanya) (1776ms) | LULUS - bertanya 3 (seharusnya bertanya) (3485ms) |
| jelas-klinik | LULUS - tidak bertanya (seharusnya tidak) (3358ms) | LULUS - tidak bertanya (seharusnya tidak) (17494ms) |
| jelas-bengkel | LULUS - tidak bertanya (seharusnya tidak) (1693ms) | LULUS - tidak bertanya (seharusnya tidak) (3039ms) |
| jelas-konveksi | LULUS - tidak bertanya (seharusnya tidak) (966ms) | LULUS - tidak bertanya (seharusnya tidak) (5493ms) |
| jelas-katering | LULUS - tidak bertanya (seharusnya tidak) (1285ms) | LULUS - tidak bertanya (seharusnya tidak) (10026ms) |
| jelas-sekolah | LULUS - tidak bertanya (seharusnya tidak) (976ms) | LULUS - tidak bertanya (seharusnya tidak) (7400ms) |
| revisi-jelas | LULUS - tidak bertanya (seharusnya tidak) (1473ms) | LULUS - tidak bertanya (seharusnya tidak) (1747ms) |

## Tahap: penyunting

| Model | Lulus | Galat model | Rata-rata ms | Maks ms | Token |
|---|---|---|---|---|---|
| deepseek-flash | 9/10 | 0 | 1134 | 2224 | 6314 |
| deepseek-v4-pro | 9/10 | 0 | 4431 | 6789 | 9480 |

| Kasus | deepseek-flash | deepseek-v4-pro |
|---|---|---|
| tambah-tanggal-wajib | LULUS - lolos percobaan 1 (1542ms) | LULUS - lolos percobaan 1 (6789ms) |
| buang-field | LULUS - lolos percobaan 1 (839ms) | LULUS - lolos percobaan 1 (2913ms) |
| ganti-jadi-pilihan | LULUS - lolos percobaan 1 (1172ms) | LULUS - lolos percobaan 1 (3987ms) |
| nama-tidak-wajib | LULUS - lolos percobaan 1 (722ms) | LULUS - lolos percobaan 1 (3326ms) |
| tambah-dua | LULUS - lolos percobaan 1 (1212ms) | LULUS - lolos percobaan 1 (6658ms) |
| di-luar-isian | LULUS - tanpa sunting (benar) (898ms) | LULUS - tanpa sunting (benar) (2866ms) |
| tambah-catatan | LULUS - lolos percobaan 1 (651ms) | LULUS - lolos percobaan 1 (3775ms) |
| duplikat | GAGAL - tidak menyunting apa pun (750ms) | GAGAL - tidak menyunting apa pun (2255ms) |
| jawaban-followup | LULUS - lolos percobaan 1 (2224ms) | LULUS - lolos percobaan 1 (6004ms) |
| status-baru | LULUS - lolos percobaan 1 (1334ms) | LULUS - lolos percobaan 1 (5738ms) |

## Penyunting: lolos pada percobaan pertama (tanpa umpan balik)
- deepseek-flash: 9/10
- deepseek-v4-pro: 9/10

## Pertanyaan yang diajukan penanya (untuk ditinjau manusia - mutu isi tak bisa dinilai mesin)
- [deepseek-flash/kabur-1] Usaha ini bergerak di bidang apa dan menjual atau menyediakan apa kepada pelanggan? | Pekerjaan utama sehari-hari dari pesanan atau permintaan masuk sampai selesai itu apa saja dan bagaimana urutannya? | Siapa yang biasanya membeli atau memesan dari usaha ini, dan dari mana barang atau bahannya diperoleh?
- [deepseek-flash/kabur-2] Bisnis ini bergerak di bidang apa dan menjual apa ke siapa? | Dari barang atau jasa masuk sampai uang diterima, tahapan kerjanya berurutan seperti apa? | Siapa saja orang yang terlibat sehari-hari, misalnya pemasok, pegawai, dan pelanggan, serta tugas utama mereka?
- [deepseek-flash/kabur-3] Usaha ini bergerak di bidang apa dan menjual atau menghasilkan apa? | Pekerjaan apa saja yang setiap hari dilakukan dari awal sampai selesai, misalnya dari pesanan masuk sampai barang dikirim? | Siapa saja yang terlibat dan berhubungan dengan usaha ini, misalnya pelanggan, pemasok, atau karyawan?
- [deepseek-flash/kabur-4] Usaha Anda bergerak di bidang apa dan menjual apa kepada pelanggan? | Bagaimana urutan pekerjaan sehari-hari dari pesanan masuk sampai selesai atau barang terkirim? | Siapa saja yang terlibat dalam pekerjaan itu, misalnya pemilik, karyawan, dan pemasok?
- [deepseek-v4-pro/kabur-1] Apa jenis usaha Anda dan produk atau jasa apa yang Anda jual? | Bisa ceritakan urutan kegiatan dari mulai menerima pesanan sampai selesai? | Siapa saja yang menjadi pelanggan dan pemasok Anda?
- [deepseek-v4-pro/kabur-2] Bisnisnya bergerak di bidang apa dan menjual produk atau jasa apa? | Bagaimana alur kerja utama dari awal sampai selesai, misalnya dari menerima pesanan sampai barang atau jasa diterima pelanggan? | Siapa saja pihak yang terlibat, seperti pelanggan, pemasok, atau karyawan, dan apa peran mereka?
- [deepseek-v4-pro/kabur-3] Usaha Anda bergerak di bidang apa dan barang atau jasa apa yang dijual? | Bagaimana urutan kegiatan utama dari menerima pesanan sampai selesai? | Siapa saja yang terlibat, seperti pelanggan, pemasok, atau karyawan?
- [deepseek-v4-pro/kabur-4] Bisnis apa yang dijalankan dan produk atau jasa apa yang dijual? | Bagaimana urutan kegiatan utama dari awal menerima permintaan sampai selesai? | Siapa saja pihak yang terlibat, seperti pelanggan, pemasok, atau karyawan?

## Catatan penilai (ditambahkan setelah run)

- Satu-satunya `GAGAL` di kedua model (`duplikat`, "Tambah isian nama") adalah **kesalahan penilai**: field `nama` sudah ada, jadi
  jawaban benar adalah tidak menyunting, dan itulah yang dilakukan kedua model. Skor penyunting yang adil: **flash 10/10, pro 10/10**.
  Angka di tabel di atas sengaja tidak diubah (itu keluaran asli run); kasusnya sudah dikoreksi di harness.
- Kedua model **jenuh** pada kasus ini (semua lulus, 9/10 lolos percobaan pertama): eval ini tidak bisa membedakan keduanya pada
  kasus yang lebih sulit. Kesimpulan "flash = pro" hanya berlaku untuk 10 kasus penanya dan 10 kasus penyunting ini.

## Keputusan

- Model penanya (flash / pro): _
- Model penyunting (flash / pro): _
