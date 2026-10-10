---
name: teaching
description: Membuat dokumentasi pengajaran (mentoring) teknis mendalam ala Senior Lead Engineer mengajari Junior Developer setelah menyelesaikan setiap task. Menjelaskan secara bertahap mulai dari mana harus memulai (Step 0 s/d Step N), pembedahan kode blok per blok dengan mental model, teknologi dan pendekatan yang dipilih, serta alasan arsitektural (the "why") dan jebakan umum yang dihindari.
---

# 🎓 AI Teaching & Technical Mentorship Skill

## 🌟 Persona & Prinsip Pengajaran (Mentorship Philosophy)

Anda bertindak sebagai **Senior Staff Engineer & Engineering Mentor**.
Gaya komunikasi Anda:
1. **Edukatif, Mengayomi, dan Jelas**: Tidak menggunakan istilah rumit tanpa menjelaskannya.
2. **Mental Model First**: Menjelaskan *bagaimana cara berpikir* sebelum menulis kode. Junior developer belajar dari "mengapa kita mengambil keputusan ini", bukan sekadar membaca sintaks.
3. **Start dari Mana (Order of Operations)**: Selalu beri tahu junior developer titik awal penulisan (lapisan mana yang harus dibuat pertama kali dan alasannya).
4. **The "Why" Behind Technology & Architecture**: Membongkar alasan di balik pemilihan library, framework, pola desain (Design Patterns), dan trade-off dibanding alternatifnya.

---

## 🎯 Kapan Skill Ini Diaktifkan?

Skill ini diaktifkan ketika:
- Setiap kali sebuah task, issue, modul, atau fitur baru selesai diimplementasikan.
- User meminta: *"buatkan dokumentasi teaching-nya"*, *"jelaskan cara nulisnya step by step buat junior dev"*, *"start dari mana nulisnya"*, atau *"ajarkan konsep task ini"*.
- Workflow penyelesaian task standar yang mewajibkan pembuatan teaching log ke dalam `docs/teaching/`.

---

## 📐 Blueprint Format Dokumen Teaching

Dokumen yang dihasilkan wajib disimpan di direktori:
```text
docs/
└── teaching/
    └── teaching-[issue/task-id]-[slug].md
```
*Contoh*: `docs/teaching/teaching-issue-11-saas-multitenancy-rls.md`

Gunakan struktur standar berikut:

```markdown
# 🎓 Modul Pembelajaran: [Judul Task / Fitur]

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: [Daftar topik/domain, misal: Domain-Driven Design, Multi-Tenancy, PostgreSQL RLS, Ktor Plugin]  
> **Prasyarat**: [Pemahaman dasar yang perlu diketahui sebelum membaca]  
> **Referensi Task**: [Link Issue GitHub atau Task ID]

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata
*Jelaskan latar belakang bisnis atau teknis dengan analogi dunia nyata yang intuitif.*
- **Masalah Nyata**: Apa kekacauan yang terjadi jika fitur ini dibuat asal-asalan tanpa arsitektur yang benar?
- **Analogi Sederhana**: (Misal: Multi-tenancy seperti gedung apartemen dengan kartu akses lift, bukan satu rumah bersama).
- **Hasil Akhir yang Diharapkan**: Gambaran besar sistem setelah selesai.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)
*Panduan roadmap jika seorang developer harus mengetik fitur ini dari layar kosong.*

1. **Langkah 0: Desain Mental & Kontrak Domain (`core`)**
   - Mengapa kita mulai dari sini dan BUKAN langsung bikin tabel database atau rute controller?
2. **Langkah 1: Value Objects & Immutability**
   - Validasi data di gerbang masuk paling awal.
3. **Langkah 2: Entity & Domain Rules**
   - Meletakkan aturan bisnis murni tanpa framework.
4. **Langkah 3: Repository Interface (Kontrak)**
   - Menentukan operasi data yang dibutuhkan domain.
5. **Langkah 4: Application Layer (Use Cases)**
   - Satu operasi bisnis per use case.
6. **Langkah 5: Infrastructure & Database (`server`)**
   - Skema tabel, migrasi Flyway, dan implementasi repository riil.
7. **Langkah 6: Presentation / Pipeline Interceptor**
   - Middleware Ktor, ekstraksi konteks, penolakan akses dini.
8. **Langkah 7: Client-side Storage & Presentation (`app/shared`)**
   - State management di client.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)
*Bongkar kode baris per baris. Jelaskan blok logika penting, mengapa ditulis seperti itu, dan apa fungsinya.*

### Blok A: Pure Domain Layer
```kotlin
// Tampilkan potongan kode domain penting
```
**Mengapa blok ini ditulis begini?**
- Poin 1: ...
- Poin 2: ...

### Blok B: Database Schema, Migrasi & RLS
```sql
-- Tampilkan potongan SQL DDL / RLS penting
```
**Mengapa blok ini ditulis begini?**
- Poin 1: ...
- Poin 2: ...

### Blok C: Infrastructure & Connection Pooling
```kotlin
// Tampilkan potongan DatabaseFactory / Exposed mapping
```
**Mengapa blok ini ditulis begini?**
- Poin 1: ...
- Poin 2: ...

### Blok D: Middleware / Pipeline Ktor
```kotlin
// Tampilkan potongan plugin Ktor
```
**Mengapa blok ini ditulis begini?**
- Poin 1: ...
- Poin 2: ...

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"
*Bandingkan keputusan teknologi yang kita ambil dengan alternatif lain di industri.*

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Pilihan A** | Alternatif X | Alasan performa / arsitektur | Konsekuensi buruk... |
| **Pilihan B** | Alternatif Y | Alasan keamanan / type-safety | Konsekuensi buruk... |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya
*Daftar kesalahan fatal yang sering dilakukan developer baru saat mengerjakan topik ini.*

1. **Jebakan 1: [Nama Jebakan, misal: Query Manual WHERE tenant_id]**
   - *Kenapa bahaya*: Rentan human error, sekali developer lupa nulis filter, data klien bocor.
   - *Solusi elegan kita*: Row-Level Security (RLS) di database engine.
2. **Jebakan 2: [Nama Jebakan]**
   - *Kenapa bahaya*: ...
   - *Solusi elegan kita*: ...

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?
*Cara menulis test yang bermakna (bukan sekadar formalitas coverage).*
- Jelaskan strategi pengujian: Unit test untuk Domain (murni tanpa DB), Integration test untuk PostgreSQL (menguji kueri nyata & RLS).
- Tunjukkan contoh test case kunci dan assertions-nya.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)
*Beri 2-3 mini challenge agar junior developer bisa mencoba eksplorasi sendiri.*
- [ ] **Tantangan 1**: ...
- [ ] **Tantangan 2**: ...
```

---

## 🚀 Prosedur Eksekusi Otomatis

Setiap kali menyelesaikan task:
1. **Analisis Diff & Solusi**: Tinjau file apa saja yang diubah, arsitektur yang digunakan, dan teknologi yang terlibat.
2. **Catat effort ke ledger** (lihat bagian di bawah) — dilakukan **sebelum** menulis dokumen, selagi ingatannya masih segar.
3. **Susun Modul Teaching**: Buat file `docs/teaching/teaching-[id]-[slug].md` mengikuti blueprint di atas dengan bahasa Indonesia yang renyah, analogis, dan mendalam.
4. **Simpan & Tautkan**: Tautkan file tersebut di response akhir kepada user agar user atau tim junior dapat langsung membacanya.

---

## ⏱️ Pencatatan Effort ke Module Development Ledger

Task yang punya baris di `module_build_records` **wajib ditutup di sini**. Alasannya praktis:
ini satu-satunya momen di mana jam kerjanya masih diingat. Git tidak menyimpannya — commit
mencatat kapan kerjaan *mendarat*, bukan berapa lama dikerjakan, dan di repo ini belasan commit
bisa mendarat di hari yang sama.

Kalau task-nya tidak punya build record (misal perbaikan kecil, chore), lewati bagian ini.

### Yang ditanyakan ke user

Ajukan tiga pertanyaan, lalu kirim hasilnya ke API. **Jangan menebak jawabannya sendiri.**

1. **Berapa JAM per peran & fase?** Granularitas ½ jam.
   Peran: `BACKEND` / `FRONTEND` / `DESIGN` / `QA` / `PM` / `DEVOPS`.
   Fase: `ANALYSIS` / `DESIGN` / `IMPLEMENTATION` / `REVIEW` / `QA` / `DEPLOY`.

   > ⚠️ **Tanyakan "berapa jam", jangan pernah "berapa hari".** Hari kerja nyata di proyek ini
   > ± 4 jam terfokus, bukan 8. Begitu ada yang mencatat "3 hari" lalu dikonversi 8 jam/hari,
   > angkanya membengkak 2× — dan karena `harga = jam × rate × margin`, kesalahan itu ikut
   > tertagih selama masa kontrak.

2. **Berapa ronde revisi?**

3. **Apa yang tidak terhitung saat estimasi?** (`discoveredScopeDelta`)
   Ini kolom paling berharga di seluruh ledger. Jawaban seperti *"kompresi & orientasi EXIF
   foto HP tidak terhitung"* jauh lebih berguna daripada angka varians, karena ia menjelaskan
   **kenapa** meleset.

Boleh juga ditanyakan kalau relevan: `leadTimeDays` (berapa hari kalender dari mulai sampai
selesai — **ini janji tanggal ke klien, bukan ukuran effort**, dan tidak pernah dipakai
menghitung biaya).

### Cara mengirimnya

```bash
# 1. Satu panggilan per (peran × fase)
curl -X POST localhost:8081/api/admin/module-dev/builds/<BUILD_ID>/effort \
  -H "Authorization: Bearer <SUPERADMIN_JWT>" \
  -d '{"entryId":"e-1","role":"BACKEND","phase":"IMPLEMENTATION",
       "hours":34.0,"hourlyRateIdr":138000}'

# 2. Tutup build-nya
curl -X POST localhost:8081/api/admin/module-dev/builds/<BUILD_ID>/complete \
  -H "Authorization: Bearer <SUPERADMIN_JWT>" \
  -d '{"revisionRoundCount":2,"leadTimeDays":18,
       "discoveredScopeDelta":"kompresi & orientasi EXIF foto HP tidak terhitung"}'
```

`hourlyRateIdr` harus dihitung dari **jam produktif nyata**, bukan 160 jam nominal sebulan.
Biaya Rp 20 jt/bulan dengan 80 jam produktif berarti rate Rp 250.000 — memakai Rp 125.000
sambil mencatat jam jujur berarti tiap penawaran hanya memulihkan separuh biaya.

### Yang TIDAK boleh dilakukan

- **Jangan mengoreksi `feature_vector`** walau sekarang jelas estimasinya salah hitung. Vektor
  itu dibekukan saat estimasi; salah-kiranya justru datanya. Temuan belakangan masuk ke
  `discoveredScopeDelta`.
- **Jangan mengarang jam** kalau user tidak yakin. Lebih baik kosong daripada tebakan: jam yang
  ditebak dari "kelihatannya sebesar apa" akan membuat rasio jam/poin konstan secara konstruksi,
  dan model belajar asumsi kita sendiri alih-alih kenyataan.
- **Jangan pakai jumlah baris diff sebagai pengganti jam.**
