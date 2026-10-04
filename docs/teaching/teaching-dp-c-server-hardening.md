# 🎓 Modul Pembelajaran: Penguatan Server Modul Pilot & Temuan Aktivasi (Jalur C, rencana data-port: C0 dan C5)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Bukti perilaku lewat tes berbasis database, urutan hasil query yang total, atomisitas validasi, bootstrap yang bergantung pada registry garment
> **Prasyarat**: [teaching-proto-c-handoff-pilot](teaching-proto-c-handoff-pilot.md), [discovery-DP-pilot-activation](../plannings/discovery-DP-pilot-activation.md)

## 1. Apa yang dikerjakan
| Butir | Hasil |
|---|---|
| **C0** discovery | Pack data dimuat malas dari `domain_packs`; **penghalang**: `EnsureTenantWorkingDraftUseCase` mengembalikan `null` untuk pack tanpa blueprint bawaan garment, bahkan bila draf sudah tersimpan (dibuktikan dengan tes sementara). Butuh keputusan koordinator |
| **C5** penguatan | Hak **per verb** dibuktikan dengan tiga jabatan nyata (VIEW/OPERATE/MANAGE); **atomik multi-field** pada `PUT`; **urutan daftar total** |

## 2. Tiga pelajaran
1. **Urutan `created_at` saja tidak total.** Dua baris yang dibuat pada milidetik yang sama bisa bertukar tempat antar-panggilan, sehingga kartu "melompat" di UI. Generator kini mengurutkan `created_at` lalu `id`; tes memanggil daftar berkali-kali dan menuntut urutan identik.
2. **Atomik berarti "tidak ada yang tersimpan bila satu field gagal".** `PUT` menerapkan field berurutan lewat reducer dan baru menyimpan di akhir. Tesnya sengaja memakai **judul sah + status terlarang** — bila implementasi menyimpan per-field, judul akan berubah dan tes gagal.
3. **Pack data bukan berarti draf bisa dibuat.** Memuat pack (sudah dilakukan plugin tenant) dan membangun draf kerja adalah dua jalur; yang kedua masih mengandaikan blueprint di `GarmentBlueprints.all`. Pelajaran umum: **kode yang "umum" sering menyimpan asumsi satu industri di satu baris** — ketahuan hanya bila dicoba dengan pack kedua.

## 3. Bukti
- `LayananChangeRequestApiIntegrationTest`: 5 tes (CRUD + isolasi sebelumnya; baru: hak per verb, atomik, urutan stabil) — hanya di database uji (`DB_NAME` berisi `scratch`), dibuktikan menegakkan lewat **uji mutasi** (satu ekspektasi diubah → tes gagal).
- `SpecScaffoldGeneratorTest`: 11 tes (baru: urutan total di repository hasil generate).
- `RouteGateTest`, `RouteOwnershipTest`, `ModuleSchemaOwnershipTest`: hijau pada database uji.

## 4. Belum
C1–C4 menunggu kontrak B0 (`BlockDataPort`, `DataBinding`) dan keputusan Opsi 1/2 di dokumen discovery. Verifikasi dengan mata (G3) milik A.

## 5. Tantangan
- [ ] Tulis tes yang menuntut `EnsureTenantWorkingDraftUseCase` mengembalikan draf tersimpan untuk pack tanpa blueprint bawaan (merah sekarang, hijau setelah Opsi 1).
- [ ] Turunkan blueprint dari modul pack (Opsi 2) dan buktikan paritas garment.
