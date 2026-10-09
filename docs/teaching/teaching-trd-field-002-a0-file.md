# 🎓 Modul Pembelajaran: A0 Tipe Field `FILE` (TRD-FIELD-002)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Object Storage Port, Referensi vs Payload, Value Class & Sanitasi Input, Fail-Closed Codec, Komentar Bersarang Kotlin
> **Prasyarat**: Sudah membaca `teaching-trd-field-001-a0-relation.md` (pola A0 & pagar kompilator identik)
> **Referensi Task**: `docs/trd/TRD-FIELD-002-file.md` (komit A0 4b — Track A tahap pertama)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah Nyata**: Tenant ingin melampirkan scan PO, foto, atau CSV di sel field. Cara naif: simpan byte berkas di kolom database — bahkan *base64* di jsonb. Beberapa bulan kemudian: jsonb bengkak, backup Postgres makan jam, query `SELECT *` menyeret megabyte, dan tidak ada cara memberi akses unduh berbeda dari akses baca baris.

**Analogi Sederhana**: Sel `FILE` itu seperti **lembar arsip yang menulis lokasi rak** ("Box A-123, rak biru"), bukan **menyelipkan fotokopi dokumennya ke dalam lembar arsip**. Fotokopi = byte di DB: menggemaskan saat demo, bencana saat arsip tumbuh.

**Hasil Akhir A0 4b**: kedua kosakata field punya tipe `FILE`; nilai sel = **`FileRef`** (string key `fields/...`), port **`ObjectStorage`** didefinisikan di domain untuk infrastruktur server mengimplementasikan (Track B), dan seluruh pendaftaran lain mengikuti pagar kompilator — persis pola RELATION. UI unggah (`ClayFileField`) dan route (gate modul induk, 403/413/415/503) = Track B/C.

---

## 🧭 2. "Start dari Mana?" — Alur Penulisan

1. **Langkah 0**: Baca TRD-FIELD-002 §4.4 — kontrak A0 adalah tanda tangan persis (`FileRef`, `ObjectStorage`, enum, codec).
2. **Langkah 1**: File baru `domain/storage/FileRef.kt` + `ObjectStorage.kt` — port murni, nol import framework.
3. **Langkah 2**: Enum prototype + `accepts(FILE)` = `FileRef.isValid(value)` (kosong = belum diisi).
4. **Langkah 3**: CRM `FieldType.File` (`data object` — tanpa parameter, beda dari `Relation`) + `ALL_CODES` literal.
5. **Langkah 4**: Codec kedua kosakata (FILE tanpa config; validasi sel = bentuk FileRef).
6. **Langkah 5**: Generator SQL — kolom FILE tetap `TEXT` (isi ref), bukan `BYTEA`.
7. **Langkah 6**: Ikuti error kompilator sampai habis; perbarui fixture tes paritas.

**Kenapa `FileRef` *private constructor* + `build`?** Karena key bukan string bebas — ia alamat objek di bucket. Konstruksi liar = path traversal. Hanya `build` (server) yang menyusun key dari `fileName` yang **tidak tepercaya**; klien hanya memakai `isValid`.

---

## 🧱 3. Bedah Blok per Blok

### Blok A: `FileRef` — alamat, bukan isi

```kotlin
@JvmInline
value class FileRef private constructor(val value: String) {
    companion object {
        const val PREFIX = "fields/"
        fun isValid(raw: String): Boolean =
            raw.startsWith(PREFIX) && !raw.contains("..") && !raw.startsWith("/") &&
                raw.length <= 300 && raw.none { it == '\n' || it == '\r' }
        fun build(tenantId: String, ..., fileName: String): FileRef { ... }
    }
}
```

**Mengapa begini?**
- **`value class`** = nol alokasi; ref hanya string, tapi tipenya mencegah `String` pola disalahkan sebagai key.
- **Prefix `fields/` di depan** (bukan tenant di depan): ref yang tersimpan selalu berawalan namespace supaya `isValid` bisa memverifikasi bentuk *tanpa tahu tenant*. Pemetaan ke layout bucket tenant-first (`{tenantId}/fields/...`, konvensi sweep orphan FR-4) = urusan adapter Track B. Pelajaran penting: **bentuk ref (domain) dan layout penyimpanan (infra) adalah dua keputusan berbeda** — jangan dicampur di satu string.
- **Sanitasi `fileName`**: `split('/', '\\').last()` (buang folder induk), buang `..`, buang karakter non-aman, batas 120 char, kosong → `"file"`. Hostile name seperti `../../etc/passwd` tidak bisa lolos dari namespace.

### Blok B: `ObjectStorage` — port domain ala hexagonal

```kotlin
interface ObjectStorage {
    suspend fun put(key: String, bytes: ByteArray, contentType: String): Result<Unit>
    suspend fun downloadUrl(key: String): Result<String>
    val isConfigured: Boolean
}
```

**Mengapa begini?**
- Domain hanya tahu *apa* yang bisa dilakukan (put, minta URL unduh presigned), bukan *siapa* (S3? MinIO?) — SDK/kredensial/bucket tidak pernah bocor masuk.
- **`isConfigured`** eksplisit: route nanti menolak 503 dengan pesan env yang kurang — fail-closed, bukan `null` error misterius. Dan sengaja **tidak ada** operasi `read(bytes)`: unduhan lewat URL presigned, server Ktor tidak pernah jadi pipa byte.

### Blok C: Codec CRM — `data object` tanpa config

```kotlin
FieldType.File.code -> FieldType.File   // decode; config diabaikan (tak ada parameter)
is FieldType.File -> { val raw = (v as? JsonValue.Str)?.value
    if (raw == null || !FileRef.isValid(raw)) mismatch(def) else null }
```

**Mengapa begini?** Sel FILE = Str key; validasi = **bentuk** (`FileRef.isValid`), bukan keberadaan objek — keberadaan dicek server saat unduh (404). Referensi rusak = `TypeMismatch`, tidak pernah fallback ke teks (Kontrak 4).

### Blok D: Generator SQL — kolom tetap `TEXT`

```kotlin
FieldType.TEXT, FieldType.LONG_TEXT, FieldType.FILE -> "TEXT$notNull" + CHECK(btrim...)
```

**Mengapa?** Kolom menyimpan **ref** (string ≤300 char), bukan byte — jadi `TEXT`, bukan `BYTEA`. Kalau suatu hari ada yang menaruh byte di sini, bentuk `FileRef` tidak akan sah; validasi gerbang menolaknya.

---

## ⚖️ 4. Keputusan & The "Why"

| Keputusan | Alternatif | Kenapa | Risiko alternatif |
|---|---|---|---|
| Ref (`FileRef`) di sel | Byte/base64 di kolom | jsonb, backup, dan query tetap kurus | DB bengkak; backup lambat; sel jadi payload |
| Port `ObjectStorage` baru | Reuse `PoFileStorage` (domain deal) | Field umum tak boleh bergantung domain deal; strangler tanpa menyentuh jalur berjalan | Ketergantungan silang antar domain; deal berubah = field ikut patah |
| `data object File` | `data class File(config...)` | v1 tak punya parameter (batas/allowlist = konstanta route, bukan per-field) | Parameter fiktif = permukaan codec tanpa kebutuhan |
| Presigned GET 15 menit | Server mem-proxy byte | Ktor tidak jadi bottleneck; waktu unduh tetap terkontrol | Proxy byte = memori/latensi; URL permanen = kebocoran akses |

---

## ⚠️ 5. Jebakan Pemula

1. **Komentar bersarang Kotlin.** Menulis `fields/**` di dalam KDoc membuka *nested block comment* — `*/` berikutnya hanya menutup yang nested, dan errornya samar (`Missing '}`... / `Unclosed comment` di EOF). Solusi: tulis `fields/...` di komentar.
2. **Menyamakan bentuk ref dengan layout bucket.** Ref = `fields/tenant/...`; layout bucket = `tenant/fields/...` (FR-4). Menggabungkannya membuat `isValid` butuh tahu tenant — kontrak bocor.
3. **Mempercayai `fileName`.** Nama dari klien = vektor path traversal. Hanya `FileRef.build` yang menyusun key, dan tes hostile-name wajib (`FileRefTest.build_sanitizesHostileFileNames`).
4. **Fallback `TEXT` untuk ref rusak.** Referensi yang gagal `isValid` = tolak (`TypeMismatch`), bukan jadi string kosong — fallback senyap = data berubah tanpa jejak.
5. **Lupa `import kotlin.jvm.JvmInline`** di commonMain — `@JvmInline` tidak otomatis tersedia; errornya "Unresolved reference" yang tidak selalu jelas arahnya.

---

## 🧪 6. Membuktikan Kode Bekerja

- **Unit `FileRef`** (`FileRefTest`): bentuk sah, 9 bentuk rusak ditolak (traversal, absolut, kontrol, >300), `build` menghasilkan ref valid berprefix `fields/`, sanitasi 7 hostile name, identitas kosong/ber-slash ditolak fail-closed.
- **Paritas SQL**: `accepts_fileField_acceptsOnlyValidFileRefShape` + iterasi `FieldType.entries` otomatis mencakup FILE (kolom `TEXT`, Exposed `text(`).
- **Paritas codec**: FILE round-trip keempat kawat prototype + CRM (`configCodec_everySample_roundTripsWithParameters`); `"FILE"` dikeluarkan dari daftar kode tak dikenal kedua tes.
- **Paritas CRM**: validasi sel (Str FileRef sah vs `scan.pdf` → TypeMismatch), konversi dari/ke File = FORBIDDEN (else-forbidden), sampel per `ALL_CODES`.
- **Paritas kontrol UI**: FILE punya kontrol sendiri `LeadFieldControl.FILE` / render baca-saja — tidak dipalsukan teks.
- **Kompilasi & test**: JVM+WasmJs+JS hijau; `:core:jvmTest` (1756 tes), `:app:shared:jvmTest`, test server discovery/CRM hijau. Android tidak dijalankan (SDK tidak tersedia di mesin ini — semua perubahan di `commonMain`).

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Track B membuat `S3ObjectStorage`. Draft adapter-nya: bagaimana memetakan ref `fields/tenant/...` ke bucket key `tenant/fields/...`, dan apa yang harus terjadi bila env `S3_BUCKET_FILES` kosong? (Petunjuk: `isConfigured` + 503.)
- [ ] **Tantangan 2**: Rancang tes 403 untuk route unggah (Track B): peran berwenang di modul pemegang tapi tanpa `OPERATE` — di file mana tes itu hidup dan mengapa bukan di `DealRoutes.kt`? (Petunjuk: ratchet §14.)
- [ ] **Tantangan 3**: `accepts` FILE menolak key >300 char, tapi `build` membatasi nama 120 char. Hitung panjang key terburuk dan buktikan `build` tak mungkin menghasilkan ref >300 — kalau bisa melebihi, di mana guard yang tepat?
