# 🎓 Modul Pembelajaran: Editor Kerangka Tahap (TRD-FLOW-001 Tahap 3c)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Editor tipis di atas aturan server, "naik/turun" sebagai sisip-sesudah, kode turunan nama, sinkron state antar-ViewModel
> **Prasyarat**: [Tahap 3b](teaching-flow-001-tahap-3b-edit-api-and-fail-closed.md)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — Tahap 3c (v0.9)

---

## 💡 1. Konsep Dasar

Admin pabrik membuka **Template Alur Pabrik** di papan sampling dan kini melihat bagian
**Kerangka Tahap**: tiap tahap bisa diganti nama, tahap kerja bisa dinaikkan/diturunkan/dihapus,
tahap baru bisa disisipkan, dan kerangka bisa diganti ke template industri lain (dengan konfirmasi).

Prinsipnya: **editor tidak punya aturan sendiri.** Semua aturan (QC wajib ada, jangkar terkunci,
proses berjangkar, wewenang) tinggal di server; layar hanya menampilkan penolakannya.

---

## 🧱 2. Bedah Kode

### Blok A — Naik/turun = satu operasi server

API hanya punya "pindahkan X ke sesudah Y". Dua fungsi murni menerjemahkannya:

```kotlin
moveUpTarget(stages, code)   = stages[i - 2]  // hanya bila tahap di atasnya tahap kerja
moveDownTarget(stages, code) = stages[i + 1]  // hanya bila tahap di bawahnya tahap kerja
```

Tombol dinonaktifkan dari fungsi yang sama, jadi tombol dan perilaku tidak bisa berbeda.

### Blok B — Admin tidak mengetik kode

`StageCode` adalah kunci tersimpan (kolom SPK, leg transfer). Admin mengetik nama
"Aplikasi Kain" → `APLIKASI_KAIN`, dengan akhiran `_2` bila bentrok. Nama tanpa huruf ditolak di
layar, sebelum permintaan dikirim.

### Blok C — Dua ViewModel, satu kebenaran

Editor punya ViewModel sendiri; papan punya `SamplingViewModel`. Setelah setiap operasi sukses,
editor memanggil `onChanged(stages)` → `SamplingUiEvent.StageFlowUpdated` → papan dan panel alur
proses di dialog yang sama langsung memakai urutan baru, tanpa muat ulang.

---

## ⚠️ 3. Jebakan Pemula

1. **Menduplikasi aturan server di layar** ("sembunyikan Hapus kalau QC terakhir"). Dua salinan
   aturan pasti berbeda suatu hari. Cukup tampilkan pesan server.
2. **Reset tanpa konfirmasi.** Reset membuang semua suntingan — minta konfirmasi eksplisit.
3. **Mengira editor mengubah SPK yang berjalan.** Tidak (FR-5b); teksnya menyebut itu.

---

## 🧪 4. Pembuktian

- `StageFlowEditorViewModelTest` (6): muat & beri tahu papan; naik/turun jadi sisip-sesudah dan
  tidak melewati jangkar; kode unik dari nama; nama tanpa huruf ditolak lokal; penolakan server
  ditampilkan dan kerangka tak berubah.
- Visual di `bordir-uji` (owner): daftar tahap + tombol terkunci benar; klik **Naik** pada Bordir
  memindahkannya di server dan panel alur proses di bawahnya ikut berubah; **Hapus** QC menampilkan
  "Kerangka wajib punya satu tahap QC". Urutan dipulihkan sesudahnya.
- `core` 920, `app:shared` 158 lulus.

---

## 🏆 5. Tantangan Mandiri

- [ ] Sembunyikan editor untuk pengguna tanpa `FACTORY_FLOW MANAGE` (sekarang terlihat, server menolak 403).
- [ ] Tahap kustom (mis. `APLIKASI_KAIN`) belum dikenal `FlowNodeRef.parse` — pemetaan lokasinya dibuang.
