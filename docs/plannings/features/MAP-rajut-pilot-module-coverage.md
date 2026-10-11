# PETA — Hipotesis Modul untuk Pilot Konveksi Rajut (kasus uji utama wawancara A/B/C)

**Tanggal:** 2026-10-07 · **Status:** draf kerja, **belum divalidasi ke perusahaan sebenarnya**
**Dasar:** `GarmentModules.kt` (15 modul pack garment), `IndustryStageTemplates.kt` (template `KNIT_SWEATER`), `PresetNodeSeeds.kt`, pencarian kata kunci di `core/domain`.
**Prinsip (arahan 2026-10-07, tagline "ERP untukmu"):** ERP dibentuk dari **cerita bisnis**, bukan dari daftar ERP standar. Dokumen ini **bukan** daftar kelengkapan: modul yang "tidak ada" di katalog hanya menjadi kebutuhan bila bisnis itu memang menjalankan prosesnya. Daftar ERP standar dipakai sebagai **bahan pertanyaan** setelah wawancara ("apakah Anda juga mengurus ini?"), bukan sebagai tolok ukur.

**Tujuan:** (1) hipotesis awal modul untuk rajut, untuk dikoreksi lewat wawancara; (2) bahan wawancara G1–G4 dan acuan ukur mutu tebakan AI (% tebakan diterima tanpa diubah).

> Cara baca status: ✅ ada dan sepadan · 🟡 ada tapi perlu dicek kedalamannya · ❌ tidak ditemukan di kode (bukan berarti mustahil — bisa lewat pack/proses/fitur di modul lain).
> Semua kolom "Perlu ditanyakan" adalah asumsi saya, bukan fakta tentang perusahaan itu.

## 1. Alur lantai produksi (tahap `KNIT_SWEATER` yang sudah ada)

Penerimaan pesanan → Program CAM → Rajut Turun Mesin → Linking & Tambahan → Cuci & Softener → Setrika Uap → QC Finishing → Pengemasan → Terkirim/ACC.

| Tahap | Slot | Status | Perlu ditanyakan |
|---|---|---|---|
| Program CAM | `PRODUCT_ENGINEERING` | ✅ (tahap sampling) | Siapa yang memprogram? Apakah dikerjakan sendiri atau vendor? |
| Rajut Turun Mesin | `CUTTING` (peran) | ✅ | Jumlah mesin, jenis (flat/circular), shift, target per mesin |
| Linking & Tambahan | `SEWING` | ✅ | Linking dikerjakan sendiri atau subkon? |
| Cuci/Softener, Setrika | `FINISHING` | ✅ (proses bertag fase) | Cuci dikerjakan sendiri atau vendor? |
| QC Finishing | `QUALITY_CONTROL` | ✅ | AQL atau 100%? Siapa yang menentukan lulus? |
| Pengemasan & kirim | `FULFILLMENT` | ✅ | Format surat jalan, jasa ekspedisi |

Catatan: template rajut ini memang **satu-satunya yang identik dengan enum lama dan sudah dipakai**; template lain berstatus draf.

## 2. Pembanding: area ERP umum vs katalog (bahan pertanyaan, bukan daftar kekurangan)

| Area ERP standar | Modul yang ada | Status | Catatan |
|---|---|---|---|
| Struktur organisasi, RBAC | `ORG_CHART`, `DYNAMIC_RBAC` | ✅ | Governance |
| Penjualan / prospek / PO | `CRM_SALES` | ✅ | Ada kasus PO induk dan lampiran PO |
| Sampling & persetujuan sample | `SAMPLING_ORDER` | ✅ | Paling matang |
| Spesifikasi & BOM | `TECH_PACK_BOM` | 🟡 | Tanya: apakah BOM rajut berbasis berat benang (kg) per pcs, bukan yard kain? |
| Data induk bahan (benang, aksesoris) | `MASTER_DATA` | 🟡 | Ada katalog benang; cek atribut khusus benang (Ne, komposisi, lot/dye lot) |
| Persediaan | `INVENTORY` | 🟡 | Deskripsi menyebut kain rol; cek dukungan benang cone/kg, lot, dan stok opname |
| Subkontrak | `VENDOR_CONTACTS` + `vendor/` | ✅ | Ada kebutuhan subkon dan tarif per vendor |
| Costing / HPP | `COSTING_HPP` | 🟡 | Deskripsi berbasis jahit per menit; rajut biasanya HPP per pcs/berat + upah rajut. Cek formula sebagai data per tenant |
| Perencanaan & penjadwalan | `PRODUCTION_MRP` | 🟡 | Deskripsi "10 mesin jahit"; cek apakah mesin rajut bisa dimodelkan sebagai kapasitas |
| Eksekusi lantai (operator) | `OPERATOR_EXEC` | ✅ | |
| QC | `QUALITY_CONTROL` | ✅ | |
| Pengiriman | `FULFILLMENT` | ✅ | |
| Penagihan | `INVOICING` | ✅ | DP, termin, pelunasan |
| Traceability | `traceability/` (fitur) | ✅ | Kode lacak per kontainer |
| Pembelian (PO ke supplier benang) | — | ❌ | Tidak ada modul pembelian. Hanya ada "harga pembelian aktual" sebagai input costing |
| Akuntansi / keuangan (jurnal, kas, hutang-piutang) | — | ❌ | Tidak ada. Invoicing bukan akuntansi. Kandidat modul bersama (I5) |
| Gaji / upah borongan operator | — | ❌ | Ada tarif upah sebagai input costing, tapi tidak ada modul penggajian/borongan |
| Perawatan mesin | — | ❌ | Tidak ditemukan |
| SDM (absensi, cuti) | `ORG_CHART` (data karyawan saja) | 🟡 | Hanya data karyawan |

## 3. Temuan (semua bersyarat pada hasil wawancara)

1. **Tiga area belum ada di katalog: pembelian, keuangan/akuntansi, dan upah borongan.** Belum tentu kebutuhan: tanyakan dulu apakah bisnis ini mengurusnya dan bagaimana. Upah borongan operator sering menjadi komponen biaya besar di konveksi, jadi layak ditanyakan lebih dulu.
2. **Banyak modul "ada tapi berbau jahit".** Deskripsi `INVENTORY`, `COSTING_HPP`, `PRODUCTION_MRP` mengasumsikan kain, jahit per menit, dan mesin jahit. Tanyakan ke pemilik apakah itu hanya deskripsi atau perilaku di kode; kalau perilaku, itu pekerjaan "kembangkan" (asal `EXTEND`), bukan "pakai ulang".
3. **Lantai rajut sudah cukup tertutup.** Dari tahap CAM sampai kirim, template sudah ada. Risiko lebih besar ada di sisi administrasi (pembelian, keuangan, upah) daripada lantai.

## 4. Pertanyaan wawancara untuk pemilik (urutan G1 → G4)

- **G1 Divisi:** divisi apa yang ada hari ini? (Penjualan, Desain/CAM, Rajut, Linking, Finishing, QC, Gudang benang, Keuangan, ...)
- **G2 Peran:** siapa yang memegang tiap divisi? Siapa admin gudang, siapa yang memantau mesin rajut, siapa yang menagih?
- **G3 Modul & fitur:** apa yang dicatat manual sekarang (Excel/buku)? Mana yang paling sering salah atau terlambat?
- **G4 Sambungan:** benang datang → siapa mencatat → siapa yang mengeluarkan ke mesin → siapa yang menghitung hasil → siapa menghitung upah?

## 5. Cara memakai peta ini sebagai alat ukur

1. Sebelum bertemu pemilik: tulis tebakan AI/Anda untuk G1–G4 dari narasi 1 paragraf.
2. Setelah bertemu: catat per tebakan = diterima / diubah / ditolak.
3. Hitung % diterima. Bandingkan antara agent deterministik dan Koog.
4. Yang berulang diubah → kandidat kamus peran baru (`kamus peran → modul`, data pack) dan kandidat modul/fitur baru.

## 6. Yang belum saya periksa

- Isi perilaku (bukan deskripsi) `INVENTORY`, `COSTING_HPP`, `PRODUCTION_MRP` untuk kasus benang/mesin rajut.
- Apakah fitur seperti stok opname, retur ke supplier, atau upah per pcs ada sebagai fitur di dalam modul yang tidak muncul lewat pencarian kata kunci.
- Kondisi nyata perusahaan: seluruh tabel ini perlu dikoreksi setelah wawancara.
