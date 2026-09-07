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
2. **Susun Modul Teaching**: Buat file `docs/teaching/teaching-[id]-[slug].md` mengikuti blueprint di atas dengan bahasa Indonesia yang renyah, analogis, dan mendalam.
3. **Simpan & Tautkan**: Tautkan file tersebut di response akhir kepada user agar user atau tim junior dapat langsung membacanya.
