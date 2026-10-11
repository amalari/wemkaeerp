# 🎓 Modul Pembelajaran: FIELD-004-B3C — Hardening FILE & RELATION Hasil Generate

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Trust boundary berkas, magic byte, presigned S3, code generation drift, migrasi aditif  
> **Prasyarat**: Kotlin, Ktor routing, PostgreSQL/Flyway, dan konsep repository  
> **Referensi Task**: TRD-FIELD-004 B3 + C1-C3

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Nama MIME dari klien mirip label di kardus: berguna, tetapi pengirim bisa menulis "gambar PNG" pada kardus
berisi HTML. Allowlist hanya memeriksa label. `FileSignature` membuka sedikit kardusnya dan memeriksa ciri
format sebelum byte masuk object storage.

Masalah kedua adalah drift generator. Pack `layanan` sudah menyatakan field `lampiran` dan `rujukan`, tetapi
route/tabel terkomit masih berasal dari spec lama. Sumber data dan executable code seperti dua salinan denah
gedung; test drift memastikan keduanya tidak berubah sendiri-sendiri.

Hasil akhirnya:

- isi berkas harus cocok dengan MIME yang diizinkan;
- unduhan selalu diperlakukan sebagai attachment privat;
- route/tabel/repository pilot sesuai spec terkini;
- modul generated baru otomatis masuk jaring pengaman FILE/RELATION.

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — tetapkan trust boundary.** Query `contentType`, nama file, dan byte adalah input tidak tepercaya.
2. **Langkah 1 — tulis validator murni.** `FileSignature` tidak mengenal Ktor atau S3, sehingga cepat diuji di `commonTest`.
3. **Langkah 2 — pasang validator setelah pembacaan berbatas.** Ukuran diperiksa dahulu; signature diperiksa sebelum `put`.
4. **Langkah 3 — perkeras adapter.** Metadata upload dan override presign menjadi tanggung jawab `S3ObjectStorage`.
5. **Langkah 4 — generate ulang dari spec.** Keluaran generator adalah sumber; pemindahan package tenant hanya adaptasi kepemilikan.
6. **Langkah 5 — migrasi secara aditif.** V90 tidak ditulis ulang; V100 menambah kolom nullable FILE/RELATION.
7. **Langkah 6 — pasang dua pagar.** Satu test mendeteksi drift artifact, satu test membuktikan input jahat ditolak sebelum save.

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Signature murni

```kotlin
fun matches(contentType: String, bytes: ByteArray): Boolean = when (contentType) {
    "application/pdf" -> bytes.startsWith(0x25, 0x50, 0x44, 0x46, 0x2D)
    "text/plain", "text/csv" -> bytes.none { it == 0.toByte() } &&
        runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.isSuccess
    else -> false
}
```

Signature tidak membuktikan file aman dari malware. Ia membuktikan klaim format tidak jelas-jelas bohong.
Teks tidak punya magic byte, jadi kontraknya adalah UTF-8 valid tanpa NUL.

### Blok B: Urutan gerbang route

```kotlin
val bytes = call.readBoundedBody() ?: return@post
if (bytes.isEmpty()) return@post call.respond(HttpStatusCode.BadRequest, "Body berkas kosong")
if (!FileSignature.matches(contentType, bytes)) {
    call.rejectUnsupportedMediaType(contentType)
    return@post
}
objectStorage.put(ref.value, bytes, contentType)
```

Pembatas ukuran harus mendahului signature agar pemeriksaan tidak membuka kembali risiko konsumsi memori.
`put` berada paling akhir supaya byte tertolak tidak pernah menjadi orphan object.

### Blok C: Header object storage

```kotlin
PutObjectRequest.builder()
    .contentType(contentType)
    .contentDisposition("attachment")
    .cacheControl("private, no-store")
```

Saat presign, nama file dibersihkan lagi sebelum dimasukkan ke `Content-Disposition`. Ini defense in depth:
meskipun route sekarang mengambil nama dari `FileRef` yang sudah disanitasi, adapter tidak bergantung pada asumsi itu.

### Blok D: Migrasi aditif

```sql
ALTER TABLE layanan_change_request.change_requests
    ADD COLUMN IF NOT EXISTS lampiran TEXT,
    ADD COLUMN IF NOT EXISTS rujukan VARCHAR(64);
```

Mengubah V90 akan membuat checksum Flyway berbeda pada database yang pernah menjalankannya. Migrasi baru menjaga
riwayat database tetap linear dan aman untuk rollout parsial.

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih

| Pendekatan | Alternatif | Mengapa dipilih | Risiko alternatif |
|---|---|---|---|
| Signature kecil per MIME | Percaya query MIME | Murah, deterministik, tanpa library native | File tersamar masuk storage |
| Attachment + no-store | Inline default S3 | Mengurangi rendering aktif dan cache data privat | Browser mencoba menampilkan konten |
| Migrasi V100 aditif | Edit V90 | Aman untuk DB yang sudah bermigrasi | Flyway checksum mismatch |
| Test dari `Contribution.spec` | Daftar modul manual | Modul kontribusi baru dipaksa ikut test | Daftar basi dan guard terlewat |

## ⚠️ 5. Jebakan Pemula

1. **Menganggap ekstensi adalah signature.** `foto.png` tetap bisa berisi HTML; periksa byte.
2. **Membaca semua byte sebelum batas ukuran.** Signature tidak boleh mengorbankan proteksi DoS.
3. **Menyisipkan nama file mentah ke header.** CR/LF dapat menjadi header injection; sanitasi di adapter.
4. **Mengedit migrasi lama.** File yang sudah diterapkan adalah sejarah; tambahkan migrasi baru.
5. **Menguji generator saja.** Generator benar belum berarti artifact terkomit terbaru; bandingkan keduanya.

## 🧪 6. Bagaimana Membuktikan Kode Bekerja?

- `FileSignatureTest`: format biner benar/salah, UTF-8 invalid, dan byte NUL.
- `FieldFileRoutesTest`: mismatch MIME menghasilkan 415 dan storage tetap kosong pada jalur generik maupun CRM.
- `FieldFileTenantIsolationTest`: metadata request S3, sanitasi filename, dan query presign attachment.
- `GeneratedRouteMatchesPackSpecTest`: kode executable artifact sama dengan keluaran generator setelah package/import dinormalisasi.
- `GeneratedRouteWriteGuardTest`: iterasi kontribusi, FILE tenant asing dan RELATION tidak ada menghasilkan 400 tanpa persistensi.

Tes presigner tidak membutuhkan jaringan. Header `nosniff` tetap harus dibuktikan dengan `curl -I` pada deployment
MinIO/proxy nyata karena header itu bukan kemampuan `GetObjectRequest` S3.

## 🏆 7. Tantangan Mandiri

- [ ] Tambahkan fixture WebP rusak dengan `RIFF` benar tetapi marker `WEBP` salah.
- [ ] Buat integration test MinIO opsional yang memeriksa metadata objek setelah `put`.
- [ ] Rancang scanning antivirus asynchronous tanpa membuat file bisa diunduh sebelum statusnya bersih.
