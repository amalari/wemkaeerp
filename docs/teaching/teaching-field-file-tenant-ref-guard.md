# Modul Pembelajaran: Menutup Kebocoran Berkas Lintas Tenant (Ref FILE) + Batas Memori Unggah

> **Level Target**: Mid Developer
> **Topik Utama**: Validasi bentuk vs validasi kepemilikan, defense in depth, TDD keamanan, DoS memori
> **Prasyarat**: Tipe field `FILE` (TRD-FIELD-002), `ObjectStorage`, multi-tenant Ktor
> **Referensi**: `docs/trd/TRD-FIELD-002-file.md` bagian Keamanan v0.4

---

## 1. Masalah

Sel tipe FILE menyimpan **ref**: `fields/{tenantId}/{modul}/{record}/{field}-{hex}-{nama}`. Validator `FileRef.isValid`
hanya bertanya "bentuknya benar?". Tenant A menulis `fields/<tenantB>/...` ke selnya (bentuknya benar!), lalu
`GET /download` membuat URL bertanda tangan 15 menit untuk objek tenant B, karena adapter S3 memetakan bucket dari
segmen tenant **di dalam ref**, bukan dari tenant pemanggil. Analogi: formulir pengambilan barang yang hanya dicek
"nomor rak berformat benar", bukan "rak ini milik Anda".

Masalah kedua: `call.receive<ByteArray>()` memuat seluruh body ke memori **sebelum** cek 10 MB, jadi satu request
raksasa cukup untuk menekan server.

## 2. Urutan TDD

1. **Tes merah di server** (`FieldFileTenantIsolationTest`) pada kode lama: unduh dengan ref tenant lain harus 403 dan
   `downloadUrl` tidak boleh dipanggil (fake storage mencatat panggilan); PATCH CRM dengan ref asing harus 400.
   Hasil sebelum perbaikan: 5 dari 9 gagal (`expected 403 but was 200`, PATCH diterima 200).
2. Fungsi murni di core: `FileRef.isValidFor` (segmen utuh, `abc` != `abcd`).
3. Pakai di semua jalur **tulis** (CRM `CustomFieldValidation`, `EntitySpec.fileOwnershipProblem` untuk modul
   prototype/generator) dan jalur **unduh** (sabuk kedua).
4. Generator rute (`SpecRoutesWriter`) memanggil `fileProblem(...)` di kode yang digenerate; tes golden memastikan
   dipanggil di POST dan PUT, sebelum reducer.
5. `readBoundedBody()`: `Content-Length` dicek dulu, lalu stream dibatasi batas + 1 byte.

## 3. Kenapa `isValid` saja tak cukup

Bentuk menjawab "apakah string ini mungkin ref?", kepemilikan menjawab "apakah ref ini milik orang yang bertanya?".
Keduanya pertanyaan berbeda; menggabungkan jawabannya di satu fungsi tanpa parameter tenant mustahil, karena fungsi
itu tidak tahu siapa penanya. Karena itu tanda tangan berubah (menerima `tenantId`) dan **tidak ada fallback** ke
`isValid` di jalur tulis.

## 4. Jebakan

- `startsWith("fields/abc")` cocok dengan `fields/abcd/...`. Bandingkan **segmen utuh**.
- Reducer prototype dipakai klien dan port in-memory (tanpa tenant); kepemilikan dicek di rute server SEBELUM reducer,
  termasuk di **kode yang digenerate**, kalau tidak modul baru lahir bocor.
- Cek di unduh adalah sabuk kedua, bukan satu-satunya: data lama atau jalur tulis lain tidak boleh jadi satu titik gagal.
- Jangan mencatat key penuh di log; cukup segmen tenant yang diklaim.
- Cek `Content-Length` saja tidak cukup (chunked / header bohong), makanya stream juga dibatasi.
