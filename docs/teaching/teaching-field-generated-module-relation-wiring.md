# Modul Pembelajaran: Sumber Baris Produksi & Validasi RELATION di Modul Hasil Generate

> **Level Target**: Mid Developer
> **Topik Utama**: Satu pintu wiring (registri kontribusi), validasi di core vs di kode generate, normalisasi target, fail-closed
> **Prasyarat**: `TenantPackContributions` (TRD-PLAT-004), tipe field RELATION (TRD-FIELD-001), generator `SpecRoutesWriter`
> **Referensi**: `docs/trd/TRD-FIELD-004-file-relation-server-hardening.md` (Track B)

## 1. Masalah

1. **F0**: `fieldFileRecordRows` default `emptyMap()` dan hanya tes yang mengisinya. Di produksi unduh FILE generik selalu 404
   dan resolver RELATION tak punya sumber baris untuk modul hasil generate. Perbaikan di hilir (validasi) tak berefek tanpa sumber.
2. **F2**: rute hasil generate hanya memeriksa *bentuk* nilai RELATION; id sembarang atau milik tenant lain tersimpan.
3. **Ketidakcocokan semantik**: `FieldSpec.target` = `"entitas"` atau `"modul:entitas"`, tetapi resolver membaca bagian sebelum `:`
   sebagai kode **modul**. Target pilot `change_request` akan dianggap modul `change_request` -> semua nilai ditolak.

## 2. Langkah

0. Mulai dari kontribusi, bukan dari `Application`: `Contribution.rows` (kode modul -> `PrototypeRowRepository`), **wajib** eksplisit.
   Instans yang sama dipakai route modul dan registri (satu sumber, bukan dua jalur).
1. `mergeRows`: gabung semua kontribusi, tolak kunci yang bukan modul pack-nya dan modul ganda (*fail-loud*, tanpa penimpaan
   senyap), lalu parameter tes menimpa (titik injeksi tes tetap berfungsi).
2. Tes merah dulu: `TenantPackContributionsRowsTest` gagal kompilasi (`rows`/`mergeRows` belum ada); setelah implementasi hijau,
   termasuk resolver yang tenant-aware (id tenant B ditolak untuk tenant A).
3. `relationTargetResource(owner, target)`: parser tunggal; tanpa `:` -> `"<owner>:<target>"`, bentuk rusak -> galat keras.
4. `EntitySpec.relationTargetProblem(...)` di **core**: satu tempat aturan "kosong dilewati, tak ada = pesan sama dengan milik
   tenant lain". Kode hasil generate cuma memanggilnya (pola `fileOwnershipProblem`), sehingga aturan diuji tanpa mengompilasi
   hasil generate.
5. `SpecRoutesRelationEmitter`: kosong bila tak ada RELATION -> keluaran spec lama identik byte per byte. Bila ada: parameter
   `relationResolver` (non-null, tanpa default) dan `?: relationProblem(...)` sebelum reducer di POST dan PUT.
6. Q3: target ke modul HIERARCHICAL ditolak di generator dan validator usulan (`RelationTargetPolicy`) sampai gerbang target
   (DataScope + VIEW, FR-3.x) ada di rute generate.

## 3. Jebakan yang dihindari

- Menyuntik resolver tanpa sumber baris (F0) -> semua tulis 400; makanya B1 mendahului B2.
- Memanggil resolver dengan target mentah -> semua ditolak; normalisasi wajib sebelum resolver.
- Pesan galat yang membedakan "tak ada" dan "tenant lain" -> oracle keberadaan.
- `else`/fallback ke modul lain untuk target tak dikenal -> data berubah tanpa jejak (Kontrak 4 variability).

## 4. Yang belum (jangan dikira selesai)

Regenerasi `layanan_change_request` (Track C1), tes penjaga berbasis spec (C2), magic byte/header adapter (B3), dan gerbang
VIEW+DataScope target (Track A / FR-3.x). Konfigurasi deployment di luar repo tidak diverifikasi (Q8).
