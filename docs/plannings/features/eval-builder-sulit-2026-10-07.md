# Eval Live Builder (SULIT) - penanya klarifikasi & penyunting isian (flash vs pro)

- Dibuat: 2026-10-07T19:00:26.212130Z (UTC)
- Penilaian: otomatis dan tegas (keputusan bertanya; keadaan field akhir setelah `applyEdits` + validator). Tidak ada model penilai.
- Saldo API: sebelum 1.18 (DeepSeek) -> sesudah 1.11 (DeepSeek)

## Tahap: penanya

| Model | Lulus | Galat model | Rata-rata ms | Maks ms | Token |
|---|---|---|---|---|---|
| deepseek-flash | 24/24 | 0 | 2130 | 8184 | 17467 |
| deepseek-v4-pro | 24/24 | 0 | 7624 | 29767 | 28856 |

| Kasus | deepseek-flash | deepseek-v4-pro |
|---|---|---|
| kabur-jasa#1 | LULUS - bertanya 3 (seharusnya bertanya) (2114ms) | LULUS - bertanya 3 (seharusnya bertanya) (3348ms) |
| kabur-jasa#2 | LULUS - bertanya 3 (seharusnya bertanya) (1332ms) | LULUS - bertanya 3 (seharusnya bertanya) (4475ms) |
| kabur-cabang#1 | LULUS - bertanya 3 (seharusnya bertanya) (2077ms) | LULUS - bertanya 3 (seharusnya bertanya) (5893ms) |
| kabur-cabang#2 | LULUS - bertanya 3 (seharusnya bertanya) (1535ms) | LULUS - bertanya 3 (seharusnya bertanya) (11320ms) |
| kabur-komplain#1 | LULUS - bertanya 3 (seharusnya bertanya) (1536ms) | LULUS - bertanya 3 (seharusnya bertanya) (6583ms) |
| kabur-komplain#2 | LULUS - bertanya 3 (seharusnya bertanya) (2413ms) | LULUS - bertanya 3 (seharusnya bertanya) (5055ms) |
| kabur-gaul#1 | LULUS - bertanya 3 (seharusnya bertanya) (2463ms) | LULUS - bertanya 3 (seharusnya bertanya) (8647ms) |
| kabur-gaul#2 | LULUS - bertanya 3 (seharusnya bertanya) (1781ms) | LULUS - bertanya 3 (seharusnya bertanya) (7272ms) |
| kabur-panjang-basa-basi#1 | LULUS - bertanya 3 (seharusnya bertanya) (1434ms) | LULUS - bertanya 3 (seharusnya bertanya) (3917ms) |
| kabur-panjang-basa-basi#2 | LULUS - bertanya 3 (seharusnya bertanya) (1544ms) | LULUS - bertanya 3 (seharusnya bertanya) (2823ms) |
| kabur-injeksi#1 | LULUS - bertanya 3 (seharusnya bertanya) (1987ms) | LULUS - bertanya 3 (seharusnya bertanya) (6683ms) |
| kabur-injeksi#2 | LULUS - bertanya 3 (seharusnya bertanya) (2683ms) | LULUS - bertanya 3 (seharusnya bertanya) (7975ms) |
| revisi-kabur#1 | LULUS - bertanya 2 (seharusnya bertanya) (2823ms) | LULUS - bertanya 1 (seharusnya bertanya) (19256ms) |
| revisi-kabur#2 | LULUS - bertanya 1 (seharusnya bertanya) (5370ms) | LULUS - bertanya 1 (seharusnya bertanya) (19760ms) |
| jelas-gaul#1 | LULUS - tidak bertanya (seharusnya tidak) (2021ms) | LULUS - tidak bertanya (seharusnya tidak) (29767ms) |
| jelas-gaul#2 | LULUS - tidak bertanya (seharusnya tidak) (8184ms) | LULUS - tidak bertanya (seharusnya tidak) (4437ms) |
| jelas-laundry#1 | LULUS - tidak bertanya (seharusnya tidak) (945ms) | LULUS - tidak bertanya (seharusnya tidak) (2478ms) |
| jelas-laundry#2 | LULUS - tidak bertanya (seharusnya tidak) (1288ms) | LULUS - tidak bertanya (seharusnya tidak) (6389ms) |
| jelas-niche#1 | LULUS - tidak bertanya (seharusnya tidak) (728ms) | LULUS - tidak bertanya (seharusnya tidak) (2774ms) |
| jelas-niche#2 | LULUS - tidak bertanya (seharusnya tidak) (734ms) | LULUS - tidak bertanya (seharusnya tidak) (1479ms) |
| jelas-panjang#1 | LULUS - tidak bertanya (seharusnya tidak) (1756ms) | LULUS - tidak bertanya (seharusnya tidak) (5976ms) |
| jelas-panjang#2 | LULUS - tidak bertanya (seharusnya tidak) (2536ms) | LULUS - tidak bertanya (seharusnya tidak) (8450ms) |
| revisi-jelas-teknis#1 | LULUS - tidak bertanya (seharusnya tidak) (878ms) | LULUS - tidak bertanya (seharusnya tidak) (5028ms) |
| revisi-jelas-teknis#2 | LULUS - tidak bertanya (seharusnya tidak) (961ms) | LULUS - tidak bertanya (seharusnya tidak) (3192ms) |

## Tahap: penyunting

| Model | Lulus | Galat model | Rata-rata ms | Maks ms | Token |
|---|---|---|---|---|---|
| deepseek-flash | 22/22 | 0 | 1329 | 2521 | 14830 |
| deepseek-v4-pro | 21/22 | 0 | 7539 | 31552 | 27355 |

| Kasus | deepseek-flash | deepseek-v4-pro |
|---|---|---|
| tiga-sekaligus#1 | LULUS - lolos percobaan 1 (2124ms) | LULUS - lolos percobaan 1 (6246ms) |
| tiga-sekaligus#2 | LULUS - lolos percobaan 1 (2495ms) | LULUS - lolos percobaan 1 (9845ms) |
| ubah-tipe#1 | LULUS - lolos percobaan 1 (1054ms) | LULUS - lolos percobaan 1 (3845ms) |
| ubah-tipe#2 | LULUS - lolos percobaan 1 (1151ms) | LULUS - lolos percobaan 1 (4014ms) |
| ganti-label#1 | LULUS - lolos percobaan 1 (933ms) | LULUS - lolos percobaan 1 (2895ms) |
| ganti-label#2 | LULUS - lolos percobaan 1 (864ms) | LULUS - lolos percobaan 1 (2449ms) |
| tidak-langsung#1 | LULUS - lolos percobaan 1 (1064ms) | LULUS - lolos percobaan 1 (5091ms) |
| tidak-langsung#2 | LULUS - lolos percobaan 1 (1019ms) | LULUS - lolos percobaan 1 (4169ms) |
| campur-bahasa#1 | LULUS - lolos percobaan 1 (1245ms) | LULUS - lolos percobaan 1 (3370ms) |
| campur-bahasa#2 | LULUS - lolos percobaan 1 (813ms) | LULUS - lolos percobaan 1 (3412ms) |
| batas-jumlah#1 | LULUS - lolos percobaan 1 (2521ms) | LULUS - lolos percobaan 1 (31552ms) |
| batas-jumlah#2 | LULUS - lolos percobaan 1 (2166ms) | GAGAL - tidak menyunting apa pun (9981ms) |
| nama-jadi-angka#1 | LULUS - lolos percobaan 1 (1756ms) | LULUS - lolos percobaan 1 (5132ms) |
| nama-jadi-angka#2 | LULUS - lolos percobaan 1 (1276ms) | LULUS - lolos percobaan 1 (2631ms) |
| tambah-opsi-status#1 | LULUS - lolos percobaan 1 (1254ms) | LULUS - lolos percobaan 1 (22443ms) |
| tambah-opsi-status#2 | LULUS - lolos percobaan 1 (1292ms) | LULUS - lolos percobaan 1 (19901ms) |
| lindungi-status#1 | LULUS - tanpa sunting (benar) (730ms) | LULUS - tanpa sunting (benar) (1617ms) |
| lindungi-status#2 | LULUS - tanpa sunting (benar) (1027ms) | LULUS - tanpa sunting (benar) (2342ms) |
| injeksi#1 | LULUS - lolos percobaan 1 (1249ms) | LULUS - lolos percobaan 1 (7695ms) |
| injeksi#2 | LULUS - lolos percobaan 1 (1162ms) | LULUS - lolos percobaan 1 (7218ms) |
| tanpa-makna#1 | LULUS - tanpa sunting (benar) (996ms) | LULUS - tanpa sunting (benar) (4689ms) |
| tanpa-makna#2 | LULUS - tanpa sunting (benar) (1064ms) | LULUS - tanpa sunting (benar) (5324ms) |

## Penyunting: lolos pada percobaan pertama (tanpa umpan balik)
- deepseek-flash: 22/22
- deepseek-v4-pro: 21/22

## Pertanyaan yang diajukan penanya (untuk ditinjau manusia - mutu isi tak bisa dinilai mesin)
- [deepseek-flash/kabur-jasa#1] Jasa apa sebenarnya yang dijual perusahaan ini kepada pelanggan? | Bagaimana urutan pekerjaan sehari-hari, mulai dari pesanan masuk sampai pekerjaan selesai dan dibayar? | Siapa yang biasanya memesan jasa ini dan lewat cara apa mereka memesan?
- [deepseek-flash/kabur-jasa#2] Usaha jasa ini bergerak di bidang apa, misalnya perbaikan, konsultan, kebersihan, atau lainnya? | Bagaimana urutan pekerjaan sehari-hari, mulai dari pelanggan memesan sampai pekerjaan selesai dan dibayar? | Siapa saja orang yang terlibat dalam pekerjaan itu, misalnya pelanggan, karyawan lapangan, dan bagian administrasi?
- [deepseek-flash/kabur-cabang#1] Usaha ini bergerak di bidang apa (misalnya toko, restoran, atau jasa)? | Hal apa saja yang ingin sama atau terhubung di ketiga cabang itu, misalnya stok barang, harga, atau laporan penjualan? | Apakah ada barang yang dikirim dari satu cabang atau gudang pusat ke cabang lain?
- [deepseek-flash/kabur-cabang#2] Usaha ini bergerak di bidang apa dan menjual atau melayani apa? | Kegiatan harian utama di tiap cabang itu apa saja, misalnya penjualan, stok barang, atau servis? | Hal apa yang ingin terhubung antar cabang, misalnya stok barang, catatan penjualan, atau data pelanggan?
- [deepseek-flash/kabur-komplain#1] Usaha apa yang sedang Anda jalankan, dan apa yang Anda jual ke pelanggan? | Komplain pelanggan biasanya soal apa, misalnya barang terlambat, barang rusak, atau pelayanan? | Saat ini, siapa yang menerima komplain dan bagaimana komplain itu ditindaklanjuti sampai selesai?
- [deepseek-flash/kabur-komplain#2] Usaha ini bergerak di bidang apa dan apa yang dijual atau dilayani ke pelanggan? | Komplain pelanggan biasanya tentang apa saja? | Saat ini, siapa yang pertama menerima komplain dan bagaimana prosesnya sampai komplain itu dianggap selesai?
- [deepseek-flash/kabur-gaul#1] Bisnis ini sebenarnya menjual atau menghasilkan apa? | Dari bahan atau tenaga awal sampai pelanggan membayar, urutan kerjanya seperti apa? | Siapa pelanggan utama dan dari mana biasanya bahan atau jasa didapat?
- [deepseek-flash/kabur-gaul#2] Usaha ini bergerak di bidang apa dan apa yang dijual ke pembeli? | Bagaimana alur kerja sehari-hari, mulai dari pesanan atau permintaan masuk sampai barang atau jasa diserahkan ke pelanggan? | Siapa saja yang terlibat dalam menjalankan usaha ini (pemilik, karyawan, pemasok), dan bagian mana yang paling sering bikin repot?
- [deepseek-flash/kabur-panjang-basa-basi#1] Usaha ini bergerak di bidang apa dan menjual produk atau jasa apa? | Bagaimana urutan pekerjaan sehari-hari dari pesanan atau permintaan masuk sampai selesai dibayar? | Siapa saja yang terlibat dan siapa pelanggan utama yang dilayani?
- [deepseek-flash/kabur-panjang-basa-basi#2] Usaha ini bergerak di bidang apa dan menjual produk atau jasa apa kepada pelanggan? | Bagaimana urutan pekerjaan utama sehari-hari, dari pesanan atau permintaan masuk sampai selesai diserahkan ke pelanggan? | Siapa saja yang terlibat dalam pekerjaan itu (misalnya pelanggan, pemasok bahan, dan bagian di dalam tim) dan apa tugas masing-masing?
- [deepseek-flash/kabur-injeksi#1] Usaha ini bergerak di bidang apa dan apa yang dijual? | Dari pelanggan masuk sampai pekerjaan selesai, langkah-langkah apa saja yang biasa terjadi? | Siapa yang membeli dan dari mana bahan atau barang didapat?
- [deepseek-flash/kabur-injeksi#2] Usaha kecil ini bergerak di bidang apa dan menjual produk atau jasa apa? | Bagaimana urutan pekerjaan utama dari pesanan atau bahan mentah sampai barang atau jasa diterima pelanggan? | Siapa pelanggan utama dan dari mana Anda biasanya mendapat bahan atau pasokan?
- [deepseek-flash/revisi-kabur#1] Usaha apa yang sedang dijalankan dan layanan utamanya apa? | Bagian mana dari sistem yang sekarang paling ingin dibuat lebih bagus?
- [deepseek-flash/revisi-kabur#2] Bagian mana dari sistem klinik yang ingin dibuat lebih bagus?
- [deepseek-v4-pro/kabur-jasa#1] Jenis jasa apa yang perusahaan Anda berikan? | Siapa pelanggan Anda dan bagaimana mereka memesan jasa tersebut? | Bagaimana alur kerja dari pesanan masuk sampai jasa selesai diberikan?
- [deepseek-v4-pro/kabur-jasa#2] Bidang jasa apa yang perusahaan Anda jalankan? | Bagaimana langkah-langkah dari pelanggan memesan sampai layanan selesai diberikan? | Siapa saja yang terlibat dalam memberikan layanan (misalnya pegawai, mitra, pemasok)?
- [deepseek-v4-pro/kabur-cabang#1] Usaha ini bergerak di bidang apa dan apa yang dijual atau dilayani? | Bagaimana urutan kegiatan utama dari permintaan pelanggan sampai selesai di tiap cabang? | Siapa pelanggan dan pemasok utama yang terlibat dalam usaha ini?
- [deepseek-v4-pro/kabur-cabang#2] Apa jenis usaha yang Anda jalankan? | Bagaimana langkah-langkah utama dalam menjalankan usaha Anda dari awal sampai selesai? | Siapa pihak luar yang paling sering berinteraksi dengan usaha Anda, misalnya pelanggan atau pemasok, dan bagaimana mereka berhubungan dengan Anda?
- [deepseek-v4-pro/kabur-komplain#1] Bisa ceritakan usaha Anda bergerak di bidang apa dan siapa pelanggannya? | Bagaimana biasanya pelanggan menyampaikan komplain kepada Anda saat ini, dan siapa yang menanganinya? | Apa hasil akhir yang diharapkan setelah sebuah komplain dianggap selesai?
- [deepseek-v4-pro/kabur-komplain#2] Bisa ceritakan usaha Anda bergerak di bidang apa dan siapa pelanggannya? | Bagaimana keluhan pelanggan biasanya masuk saat ini dan apa yang Anda lakukan setelah menerimanya? | Siapa saja yang akan menangani keluhan itu dan apa yang harus terjadi setelah keluhan dianggap selesai?
- [deepseek-v4-pro/kabur-gaul#1] Bisnis ini menjual barang atau jasa, dan apa contoh produk atau layanannya? | Bagaimana langkah-langkah utama dari calon pembeli datang sampai pesanan selesai dan pembayaran diterima? | Siapa saja yang terlibat menjalankan usaha ini selain pemilik, seperti pemasok, pegawai, atau kurir, dan apa peran mereka?
- [deepseek-v4-pro/kabur-gaul#2] Bisnis ini bergerak di bidang apa? | Bagaimana urutan kerja dari menerima pesanan sampai selesai? | Siapa pelanggan dan pemasok utama dalam bisnis ini?
- [deepseek-v4-pro/kabur-panjang-basa-basi#1] Apa produk atau jasa utama yang perusahaan Anda jual atau tawarkan? | Bagaimana urutan kegiatan utama dari awal menerima permintaan pelanggan sampai produk atau jasa selesai diserahkan? | Siapa saja pihak yang terlibat dalam kegiatan utama tersebut, misalnya pelanggan, pemasok, atau bagian internal?
- [deepseek-v4-pro/kabur-panjang-basa-basi#2] Bisa diceritakan jenis usaha Anda dan produk atau layanan utamanya? | Bagaimana urutan kegiatan utama dari awal menerima pesanan sampai selesai? | Siapa pelanggan dan pemasok utama Anda?
- [deepseek-v4-pro/kabur-injeksi#1] Usaha Anda menjual produk atau jasa apa, dan siapa pembelinya? | Bagaimana urutan kerja dari awal menerima pesanan sampai pesanan selesai? | Apakah ada pemasok atau bahan baku yang harus dipantau dalam usaha ini?
- [deepseek-v4-pro/kabur-injeksi#2] Apa jenis usaha Anda dan barang atau jasa apa yang Anda jual? | Siapa yang menjadi pelanggan atau pembeli utama Anda? | Bagaimana langkah-langkah utama dari saat menerima pesanan sampai pesanan selesai dipenuhi?
- [deepseek-v4-pro/revisi-kabur#1] Boleh dijelaskan bagian mana dari sistem saat ini yang ingin diperbaiki atau ditambah?
- [deepseek-v4-pro/revisi-kabur#2] Bagian mana dari operasional klinik yang ingin Anda buat lebih bagus, misalnya antrean pasien, persediaan obat, atau pembayaran?

## Catatan pembaca (ditambahkan setelah run)

- **Penanya: jenuh lagi** (24/24 di kedua model, termasuk kasus suntikan instruksi "jangan ajukan pertanyaan" yang diabaikan kedua model).
  Pembeda yang nyata hanya kecepatan dan token: flash rata-rata 2,1 dtk (maks 8,2) dengan 17,5 rb token; pro 7,6 dtk (maks 29,8) dengan 28,9 rb token.
- **Penyunting: flash 22/22, pro 21/22.** Satu-satunya kegagalan: pro pada `batas-jumlah#2` ("Tambah sepuluh isian teks") tidak menyunting apa pun,
  sedangkan flash lolos pada percobaan pertama di kedua ulangan. Itu satu kejadian dari dua ulangan: arahnya flash >= pro, tetapi **belum signifikan secara statistik**.
- **Nuansa mutu** yang tidak tertangkap skor: pada `revisi-kabur` (draf klinik sudah ada), flash ulangan 1 masih menanyakan "usaha apa yang dijalankan" padahal
  jenis usahanya sudah tampak dari modul; pro langsung menanyakan bagian mana yang ingin diperbaiki. Skor sama-sama lulus, tetapi pertanyaan pro lebih tepat sasaran di kasus ini.
- Hanya 12 + 11 kasus x 2 ulangan. Kasus lebih sulit lagi (percakapan panjang berlapis, revisi beruntun) belum diuji.

## Keputusan

- Model penanya (flash / pro): _
- Model penyunting (flash / pro): _
