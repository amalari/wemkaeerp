# 🎓 Modul Pembelajaran: Slot Modul dari Domain Pack (Jalur B, B3)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Menghapus enum yang dipakai 193 kali tanpa mengubah perilaku atau format tersimpan
> **Prasyarat**: [`teaching-b2-port-types-from-pack.md`](teaching-b2-port-types-from-pack.md)

---

## 💡 1. Apa yang berubah

| Sebelum | Sesudah |
|---|---|
| `enum class ModuleArchetype` (10 slot konveksi) | `GarmentSlots` (data pack garment) + `typealias ModuleArchetype = SlotCode` |
| `ModuleArchetype.SEWING` (193 referensi) | `GarmentSlots.SEWING` |
| `archetype.displayName` / port default (anggota enum) | extension di `pipeline/ModuleArchetype.kt` yang membaca pack |
| `ModuleArchetype.fromCode/forModule/forModuleCode/entries` | `GarmentSlots.fromCode/forModule/forModuleCode/all` |
| `archetype.ordinal` (urut prospek) | `GarmentSlots.all.indexOf(…)` |

## 🧱 2. Bedah Keputusan

### Blok A — `typealias` sebagai jembatan

Nama tipe `ModuleArchetype` dipakai di ±60 file. `typealias ModuleArchetype = SlotCode` membuat semuanya
tetap terkompilasi, sementara **identitasnya sudah data**: pack e-learning bisa punya slot `grading` tanpa
menyentuh kode mesin. Mengganti nama tipe secara langsung (B3b) murni kosmetik dan bisa dikerjakan terpisah.

### Blok B — Dua format tersimpan, keduanya dipertahankan

| Lokasi | Format lama | Format sekarang |
|---|---|---|
| JSON pipeline, katalog modul, kerangka tahap, prospek | code `sewing` | `SlotCode.value` = `sewing` (sama) |
| Katalog proses opsional (kolom DB + JSON) | NAME enum `SEWING` | `GarmentSlots.legacyNameOf` = `SEWING` (sama) |

Repo A dan B memakai **DB dev yang sama**. Kalau B menulis format lain, A bisa rusak. Round-trip NAME dijaga
oleh test.

### Blok C — Kompilator sebagai pemeriksa

Enum memberi `when` exhaustive; value class tidak. Kalau ada `when` sebagai ekspresi tanpa `else`, kompilasi
akan gagal. Tidak ada yang gagal, artinya tidak ada `when` yang diam-diam kehilangan cabang. `forModule`
tetap exhaustive karena ia `when` atas `BusinessModule` (enum sampai B6).

### Blok D — Utang yang sengaja **tidak** diperbaiki

`ProcessCatalogCodec` dan `ProspectCodec` jatuh ke `CUSTOM_EXTENSION` bila slot tidak dikenal (fallback
senyap, melanggar Kontrak 4). Refactor tidak boleh mengubah perilaku, jadi perilaku ini dipertahankan.
Perbaikannya PR tersendiri dengan test perilaku baru.

## ⚠️ 3. Jebakan

1. **Rekursi inisialisasi**: extension `displayName` membaca pack. Pack yang dibangun *dari* extension itu
   akan membaca dirinya sendiri. Pack garment dibangun dari `GarmentSlots.meta`, bukan dari extension.
2. **Import extension**: anggota enum ikut ter-import bersama tipenya, sedangkan extension tidak. 35 file
   butuh import eksplisit, dan kompilator menunjukkan semuanya.
3. **Dev server**: `wasmJsBrowserDevelopmentRun` **tanpa** `--continuous` keluar setelah build, jadi port
   3000 kosong. Menghentikan task latar yang me-`nohup` server ikut mematikan server itu.

## 🧪 4. Pembuktian

- `GarmentDomainPackParityTest`: tabel emas 10 slot (code, nama, port, modul pewakil) dari enum terakhir
  (`171038e`), urutan deklarasi, fase tiap slot, lookup tak peka huruf, round-trip NAME.
- core 944, app 158, server 231 hijau; JVM, Wasm, JS terkompilasi.
- Visual `bordir-uji`: kanvas identik; papan sampling bordir (kolom Digitizing, lalu R&D dengan Hooping,
  Bordir, Trimming, QC, Kemas) tetap benar.

## 🧭 5. Berikutnya

- **B3b** (opsional, kosmetik): ganti nama tipe `ModuleArchetype` → `SlotCode`, hapus typealias.
- **B4**: `GarmentBusinessPreset` (FOB/CMT/D2C) → Blueprint milik pack.
