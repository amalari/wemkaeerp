# Eval Live Koog — ScreenProposal (SP C5)

- Agent: `koog/deepseek-flash/draft-v2` (model `deepseek-flash`)
- Dibuat: 2026-10-06T16:57:07.266181Z
- Batas koreksi: 3 putaran per run
- Penilaian: `DiscoveryEvalGrader` (kriteria §6); baseline deterministik wajib 100%

## Perkiraan biaya sebelum jalan

```
estimasi | kasus=11 ulangan=3 putaran-maks=4 panggilan-LLM-maks=132 token-maks≈792000
```

## Hasil per kasus

| Kasus | Lulus | Putaran koreksi (min–maks) | Waktu (min–maks ms) | Token | Catatan |
|---|---|---|---|---|---|
| garment-fob | 0/3 | 4–4 | 65734–82763 | 189437 | cakupan_modul: pack=garment blueprint=garment_starter modules=[org_chart, dynamic_rbac, factory_flow, master_data, vendor_contacts, crm, status_bermakna: garment_stok_kain_table: status 'status' tanpa kondisi awal, garment_stok_kain_table: status 'status' tanpa kondisi akhi |
| garment-cmt | 0/3 | 4–8 | 95205–177103 | 332518 | cakupan_modul: pack=garment blueprint=garment_makloon_starter modules=[org_chart, dynamic_rbac, factory_flow, master_data, vendor_conta, cakupan_modul: pack=garment blueprint=makloon_starter modules=[org_chart, dynamic_rbac, factory_flow, master_data, vendor_contacts, crm |
| garment-d2c | 0/3 | 5–11 | 69222–180825 | 428687 | cakupan_modul: pack=distro blueprint=distro_starter modules=[distro_produksi, distro_stok, distro_pesanan, distro_pengiriman, distro_la, status_bermakna: distro_stok_table: status 'status' tanpa kondisi awal, distro_stok_table: status 'status' tanpa kondisi akhir, cakupan_modul: pack=distro blueprint=distro_starter modules=[distro_bahan, distro_produksi, distro_pesanan, distro_pengiriman, distro_l, cakupan_modul: pack=distro blueprint=distro_starter modules=[distro_produk, distro_produksi, distro_stok, distro_pesanan, distro_kirim,, status_bermakna: distro_stok_list: status 'status' tanpa kondisi awal, distro_stok_list: status 'status' tanpa kondisi akhir |
| sablon-bordir | 0/3 | 4–7 | 84120–170738 | 338259 | cakupan_modul: pack=garment blueprint=garment_sablon_starter modules=[org_chart, dynamic_rbac, factory_flow, master_data, vendor_contac, status_bermakna: garment_bahan_table: status 'status' tanpa kondisi awal, garment_bahan_table: status 'status' tanpa kondisi akhir, status_bermakna: quality_control_inspeksi_checklist: status 'status' tanpa kondisi awal, cakupan_modul: pack=sablon blueprint=sablon_starter modules=[sablon_order, sablon_gelombang, sablon_proses, sablon_bahan, sablon_serah] |
| klinik | 0/3 | 4–7 | 54782–91912 | 352877 | cakupan_modul: pack=klinik_gigi modules=[klinik_gigi_pendaftaran, klinik_gigi_antrean, klinik_gigi_obat, klinik_gigi_kasir, klinik_gigi, jenis_tampilan: klinik_gigi_pendaftaran_form (klinik_gigi_pendaftaran): FORM di luar himpunan [KANBAN, TABLE], klinik_gigi_kasir_print (, status_bermakna: klinik_gigi_obat_list: status 'status' tanpa kondisi awal, klinik_gigi_obat_list: status 'status' tanpa kondisi akhir, jenis_tampilan: klinik_antrean_form (klinik_antrean): FORM di luar himpunan [KANBAN, TABLE], status_bermakna: klinik_obat_daftar: status 'status' tanpa kondisi awal, klinik_obat_daftar: status 'status' tanpa kondisi akhir, jenis_tampilan: klinik_kasir_kuitansi (klinik_kasir): PRINT di luar himpunan [KANBAN, TABLE, FORM, CHECKLIST, DASHBOARD], status_bermakna: klinik_obat_table: status 'status' tanpa kondisi awal, klinik_obat_table: status 'status' tanpa kondisi akhir |
| bengkel | 1/3 | 5–8 | 71510–139636 | 376576 | status_bermakna: bengkel_sparepart_table: status 'status' tanpa kondisi awal, bengkel_sparepart_table: status 'status' tanpa kondisi akhi, status_bermakna: bengkel_sparepart_list: status 'status' tanpa kondisi awal, bengkel_sparepart_list: status 'status' tanpa kondisi akhir |
| katering | 1/3 | 4–7 | 51004–96485 | 262726 | status_bermakna: katering_pelanggan_list: status 'status' tanpa kondisi awal, katering_pelanggan_list: status 'status' tanpa kondisi akhi, jenis_tampilan: katering_laporan_print (katering_laporan): PRINT di luar himpunan [KANBAN, TABLE, FORM, CHECKLIST, DASHBOARD] |
| retail | 0/3 | 4–5 | 40780–62603 | 189906 | cakupan_modul: pack=kelontong modules=[kelontong_kasir, kelontong_barang, kelontong_stok, kelontong_laporan]; pack diharapkan retail, status_bermakna: kelontong_stok_list: status 'status' tanpa kondisi awal, kelontong_stok_list: status 'status' tanpa kondisi akhir, cakupan_modul: pack=kelontong modules=[kelontong_kasir, kelontong_stok, kelontong_laporan]; pack diharapkan retail, status_bermakna: kelontong_stok_list: status 'status' tanpa kondisi awal, status_bermakna: kelontong_stok_table: status 'status' tanpa kondisi awal, kelontong_stok_table: status 'status' tanpa kondisi akhir, kel |
| jasa-it | 0/3 | 3–5 | 32874–65709 | 184194 | cakupan_modul: pack=servis_it modules=[servis_it_pesanan, servis_it_klien, servis_it_teknisi, servis_it_laporan]; pack diharapkan kusto, cakupan_modul: pack=itstudio modules=[itstudio_pesanan, itstudio_pekerjaan, itstudio_pelanggan, itstudio_laporan]; pack diharapkan kust, jenis_tampilan: itstudio_laporan_cetak (itstudio_laporan): PRINT di luar himpunan [KANBAN, TABLE, FORM, CHECKLIST, DASHBOARD], status_bermakna: itstudio_pelanggan_list: status 'status' tanpa kondisi awal, itstudio_pelanggan_list: status 'status' tanpa kondisi akhi, cakupan_modul: pack=servis_it modules=[servis_it_pesanan, servis_it_teknisi, servis_it_laporan]; pack diharapkan kustom |
| sekolah | 0/3 | 4–5 | 37442–74197 | 213925 | cakupan_modul: pack=kursus modules=[kursus_pendaftaran, kursus_siswa, kursus_pembayaran, kursus_laporan]; pack diharapkan sekolah, jenis_tampilan: kursus_pembayaran_kwitansi (kursus_pembayaran): PRINT di luar himpunan [KANBAN, TABLE, FORM, CHECKLIST, DASHBOARD], status_bermakna: kursus_siswa_table: status 'status' tanpa kondisi awal, cakupan_modul: pack=kursus modules=[kursus_pendaftaran, kursus_kelas, kursus_spp, kursus_laporan]; pack diharapkan sekolah; kemampuan k |
| logistik | 0/3 | 4–9 | 45043–99167 | 235668 | cakupan_modul: pack=logistik modules=[logistik_penjemputan, logistik_pengiriman, logistik_laporan]; kemampuan kurang=[[pesanan]], cakupan_modul: pack=logistik modules=[logistik_penjemputan, logistik_pengiriman, logistik_kurir, logistik_laporan]; kemampuan kurang=[[, jenis_tampilan: logistik_laporan_print (logistik_laporan): PRINT di luar himpunan [KANBAN, TABLE, FORM, CHECKLIST, DASHBOARD], status_bermakna: logistik_kurir_table: status 'status' tanpa kondisi awal |

## Variasi antar-ulangan

- garment-fob: 0/3 lulus, stabil
- garment-cmt: 0/3 lulus, fluktuatif (putaran [4, 7, 8])
- garment-d2c: 0/3 lulus, fluktuatif (putaran [5, 7, 11])
- sablon-bordir: 0/3 lulus, fluktuatif (putaran [4, 6, 7])
- klinik: 0/3 lulus, fluktuatif (putaran [4, 6, 7])
- bengkel: 1/3 lulus, fluktuatif (putaran [5, 7, 8])
- katering: 1/3 lulus, fluktuatif (putaran [4, 5, 7])
- retail: 0/3 lulus, fluktuatif (putaran [4, 5])
- jasa-it: 0/3 lulus, fluktuatif (putaran [3, 4, 5])
- sekolah: 0/3 lulus, fluktuatif (putaran [4, 5])
- logistik: 0/3 lulus, fluktuatif (putaran [4, 9])

## Perbandingan dengan baseline deterministik

`evals | deterministic/keyword-v1 | garment-fob | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | garment-cmt | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | garment-d2c | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | sablon-bordir | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | klinik | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | bengkel | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | katering | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | retail | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | jasa-it | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | sekolah | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  
`evals | deterministic/keyword-v1 | logistik | PASS | valid=ok; cakupan_modul=ok; jenis_tampilan=ok; status_bermakna=ok; field_memadai=ok; kemurnian_vertikal=ok`  

## Rekomendasi (diisi koordinator — G3)

- Skor LLM: 2 dari 33 ulangan; baseline: 100%.
- Keputusan (Koog default / hanya bila deterministik gagal / belum layak): _
