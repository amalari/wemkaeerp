# 🎓 Modul Pembelajaran: Seed Garment Ekspor — Satu Sumber Data untuk Semua Layar Prototype (TRD-PLAT-003)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Data pack deterministik, konsistensi lintas layar, test yang menjaga data
> **Prasyarat**: [dasbor & checklist](teaching-builder-interactive-prototype-dashboard-checklist.md)

## 1. Masalah
Setiap layar prototype punya `sampleRows` yang diketik sendiri: PO yang sama bisa berbeda nomor di CRM dan MRP, data masih bernuansa konveksi lokal ("PT Sinar Jaya", truk rental), dan dasbor menampilkan angka karangan. Demo ke pabrik ekspor gagal meyakinkan karena datanya tidak terbaca sebagai pabrik mereka.

## 2. Pendekatan
`GarmentExportSeed` (core, `domain/pack`) menyimpan **sekali** daftar pembeli (fiktif), artikel (style), PO, dan SPK sampling. Semua `sampleRows` diturunkan lewat fungsi (`crmRows()`, `samplingRows()`, `mrpRows()`, `suratJalanRow()`, …) sehingga nomor PO, nama artikel, dan jumlah pcs tidak pernah diketik ulang. Nilai turunan (jumlah pcs bertitik ribuan, "USD 82.200") **dihitung**, bukan ditulis.

Alur: PO NW-26-0412 (Hoodie, jahit Lini 2) muncul di CRM (status Produksi), Jadwal Potong (Berjalan), Kanban Lini Jahit, dan SPK Fit sample; NW-26-0371 (Henley) Selesai produksi dan muncul di Surat Jalan; HP-26-0431 masih Sampling sehingga *tidak* ada di MRP.

## 3. Keputusan penting
- **Seed = data pack**, bukan mesin: mesin tidak mengenal "PO", "FOB", atau "hoodie".
- **Konsistensi dijaga test**, bukan disiplin manusia (`GarmentExportSeedTest`): setiap nomor PO di layar mana pun harus ada di seed; semua PO ada di CRM; hanya PO non-sampling di MRP; status/kolom baris wajib berada dalam opsi hint pack; tak ada sisa data lama.
- **Kolom status CRM kini ENUM** (Prospek/Sampling/Produksi/Siap kirim) sehingga bisa diubah di baris — sebelumnya teks bebas.
- Dasbor "Order aktif" (hitungan dari papan MRP) dan "Nilai order produksi (FOB)" memakai definisi yang sama (potong+jahit) agar angkanya tidak saling bertentangan.

## 3. Jebakan
1. Mengetik nilai turunan (`"USD 82.200"`) — berubah diam-diam saat seed diubah. Hitung dari data.
2. Dua dasbor memakai definisi "aktif" berbeda — angka tampak salah padahal hanya definisinya beda.
3. Nilai teks panjang menjepit label di blok cetak (Kontrak 13): nilai diberi `weight(1f, fill=false)` + rata kanan.

## 4. Verifikasi
Test seed lulus (5 test), seluruh suite core lulus, kompilasi JVM/Wasm/JS/server lolos; dilihat di `/builder/prototype`: data konsisten lintas layar, dasbor 2 PO / USD 82.200, surat jalan terbaca.
**Belum:** kaitan status CRM ↔ papan MRP (mengubah status di CRM belum menggeser kartu di MRP), Android (CI), lebar ~1280dp.

## 5. Tantangan
- [ ] Ikat status CRM ke papan MRP lewat sumber entitas bersama (satu PO, satu status).
- [ ] Seed sub-industri kedua (bordir) di pack terpisah dan jalankan `GarmentExportSeedTest` versi bordir.
- [ ] Ubin dasbor "Menipis" dari status tabel stok.
