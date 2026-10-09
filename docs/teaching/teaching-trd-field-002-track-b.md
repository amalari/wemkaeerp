# 🎓 Modul Pembelajaran: TRD-FIELD-002 Track B — Server Berkas Tipe Field `FILE`

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Hexagonal Port/Adapter (ObjectStorage), S3-compatible storage (MinIO path-style), Presigned URL, Ktor Routing, Fail-Closed RBAC Gate, Strangler Fig
> **Prasyarat**: Paham struktur DDD repo ini (`core` domain, `server` infra/routes), membaca [TRD-FIELD-002](../trd/TRD-FIELD-002-file.md), dan tahu bahwa nilai sel field `FILE` hanyalah **string referensi**, bukan byte.
> **Referensi Task**: TRD-FIELD-002 (C8, Irisan 4b) — Track B; A0 (Track A) sudah merge di `core/.../domain/storage/`.

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata**: Kosakata field platform butuh tipe berkas (scan PO, foto kerusakan, lampiran CSV). Kalau byte berkas disimpan ke kolom jsonb, database membengkak: backup melambat, query `SELECT *` menyeret megabyte, dan jsonb yang seharusnya "peta kecil" jadi gudang biner. Fitur PO deal pernah mengalami jalur ini dan **sudah menemukan obatnya**: byte di object storage, DB hanya menyimpan *kunci* objek.

**Analogi sederhana**: Sel field = label rak gudang. Yang tersimpan di sel bukan kotaknya, melainkan tulisan "Rak B-12, susun ke-3". Yang benar-benar menyimpan kotak adalah gudang (S3/MinIO). Label bisa disalin ke banyak dokumen tanpa menduplikasi kotaknya.

**Hasil akhir (Track B)**:
1. `S3ObjectStorage` — adapter S3 umum untuk port domain `ObjectStorage`, bucket terpisah `S3_BUCKET_FILES`.
2. `FieldFileRoutes` — endpoint unggah/unduh dengan gerbang RBAC modul induk: generik per `moduleCode` + varian khusus CRM lead.
3. Fail-closed menyeluruh: 403 sebelum body dibaca, 503/400/415/413/404 dengan pesan yang jujur, WARN tanpa isi body.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Kalau kamu harus mengetik ini dari nol, urutannya penting:

1. **Langkah 0: Baca kontrak yang sudah ada, jangan mengarang.** A0 (Track A) sudah mengunci bentuknya: `ObjectStorage` (port domain), `FileRef.build/isValid` (bentuk key), dan kontrak endpoint di TRD §4.4 — *persis*, bukan perkiraan. Track B = mengisi port + melayani endpoint.
2. **Langkah 1: Salin pola yang terbukti, jangan menemukan ulang.** `S3PoFileStorage` sudah menyelesaikan masalah MinIO (path-style!), presign 15 menit, dan 503 saat env kosong. Adapter baru meniru bentuk itu — satu-satunya logika baru adalah **pemetaan ref → bucket key**.
3. **Langkah 2: Route baru di file baru.** `DealRoutes.kt` 719 baris = utang teknisi di atas hard limit (ratchet §14) — melarang menambah satu baris pun. Semua kode hidup di `FieldFileRoutes.kt`.
4. **Langkah 3: Gerbang dulu, isi kemudian.** Susun handler dalam urutan tolakan: tenant → RBAC → 503 → 400 → 415 → baca body → 400/413 → 404 → simpan.
5. **Langkah 4: Wiring additive.** Suntik lewat `Application.module` → `DomainRouteWiring` (pola `poFileStorage`), plus satu entri `RouteOwnership` supaya audit kepemilikan route tetap lengkap.
6. **Langkah 5: Env/ops additive.** `.env`, `.env.example`, bucket di `docker-compose.yml` (`minio-init`). `dev.sh` tidak perlu disentuh — ia me-load seluruh `.env` lewat `set -a`.
7. **Langkah 6: Tes yang membuktikan kontrak, bukan coverage.** Fake `ObjectStorage` in-memory + fixture RBAC in-memory (pola `LayananChangeRequestRoutesGateTest`).

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Dua Lapis Key yang Tidak Boleh Dicampur (FR-4 rev-0.3)

```kotlin
// S3ObjectStorage.kt
internal fun bucketKey(ref: String): String {
    require(ref.startsWith(FileRef.PREFIX)) { "S3ObjectStorage: key bukan FileRef: $ref" }
    val rest = ref.removePrefix(FileRef.PREFIX)              // "ten-a/quality_control/rec-1/..."
    val tenant = rest.substringBefore('/')
    require(tenant.isNotBlank() && rest.contains('/')) { ... }
    return "$tenant/${FileRef.PREFIX}${rest.removePrefix("$tenant/")}"
    // "fields/ten-a/quality_control/..." → "ten-a/fields/quality_control/..."
}
```

**Mengapa blok ini ditulis begini?**
- Nilai di sel **selalu** `fields/{tenantId}/...` (kontrak `FileRef.isValid` yang divalidasi klien/domain) — tapi layout di bucket **tenant-first** `{tenantId}/fields/...` supaya sweep orphan per tenant cukup `mc rm --recursive local/wemade-files/ten-a/fields/`. Dua keputusan berbeda; mencampurnya berarti salah satunya pecah diam-diam.
- `require` di dalam `runCatching` (di `put`/`downloadUrl`) = key yang tak sah menghasilkan `Result.failure`, bukan objek yang ditulis ke key lain. **Gagal keras lebih murah daripada data salah alamat.**

### Blok B: Klien S3 Lazy + Path-Style

```kotlin
private val s3: S3Client by lazy { ... S3Configuration.builder().pathStyleAccessEnabled(true).build() ... }
```

**Mengapa?**
- `by lazy` = server tetap hidup tanpa MinIO; klien (dan koneksinya) baru dibuat saat request FILE pertama. Tanpa storage, `isConfigured == false` sudah menolak lebih dulu dengan 503.
- MinIO menolak virtual-hosted style (`http://{bucket}.localhost:9000` gagal DNS). `pathStyleAccessEnabled(true)` memaksa `http://endpoint/{bucket}` — pelajaran mahal dari fitur PO (lihat `teaching-minio-upload-path-style-fix.md`).

### Blok C: Gerbang RBAC Paling Awal (Kontrak 7)

```kotlin
post("/upload") {
    val tenant = call.requireTenant() ?: return@post
    val module = BusinessModules.fromCode(call.parameters["moduleCode"])
    if (module == null) { call.respond(HttpStatusCode.Forbidden, ...); return@post }
    val decision = call.moduleDecision(module, tenant, roleRepository, moduleAssignmentRepository)
    if (!call.requireModuleAccess(module, decision, AccessLevel.OPERATE)) return@post
    ...
    val bytes = call.receive<ByteArray>()   // ← body dibaca PALING AKHIR
```

**Mengapa blok ini ditulis begini?**
- Attacker tanpa wewenang tidak boleh membuat server menghabiskan resource membaca 10 MB body. RBAC dihitung dari token + matrix jabatan — tidak butuh body sama sekali.
- Modul tak dikenal (`BusinessModules.fromCode` → null) = keputusan RBAC **tidak bisa dihitung** = 403, bukan 404. Mengirim 404 membocorkan peta modul; 403 tidak.
- `moduleDecision` mendelegasikan ke `callerDecisions` — jalur perhitungan yang SAMA dengan menu `/me/access` klien. Server dan menu tidak mungkin berbeda pendapat.
- Setelah RBAC: `tenant.pack.module(module) == null` → 404 (pola `LayananChangeRequestRoutes`: yang lolos RBAC pun tetap harus tenant yang pack-nya memuat modul).

### Blok D: Urutan Tolakan Setelah RBAC

```kotlin
if (!objectStorage.isConfigured) { rejectStorageUnavailable(); return@post }      // 503, menyebut env kurang
if (fileName == null) { ... 400 }                                                  // query wajib
if (contentType !in ALLOWED_FIELD_FILE_MIME_TYPES) { ... 415 }                    // dari query, sebelum body!
val bytes = call.receive<ByteArray>()
if (bytes.isEmpty()) { ... 400 }
if (bytes.size > MAX_FIELD_FILE_BYTES) { ... 413 }
```

**Mengapa?**
- 503 paling awal setelah RBAC: lebih jujur memberi tahu "fiturnya belum terpasang" daripada memvalidasi isi yang pasti ditolak juga. Pesannya **menyebut nama env** (`S3_ENDPOINT/S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET_FILES`) — dev baru tidak perlu menebak.
- 415 dicek dari query parameter, **sebelum** body dibaca — request salah tipe tidak perlu ditransfer byte-nya.
- Allowlist tertutup (fail-closed), bukan denylist: yang belum terpikirkan otomatis ditolak. Perluasan = tambah satu string + satu tes, di SATU tempat.

### Blok E: Ref Disusun Server, Selalu

```kotlin
val ref = runCatching {
    FileRef.build(tenant.tenantId.value, module.value, recordId, fieldKey, fileName)
}.getOrElse { call.respond(HttpStatusCode.BadRequest, ...); return@post }
```

**Mengapa?**
- `fileName` dari klien adalah **input tidak tepercaya**. `FileRef.build` membuang pemisah path, `..`, dan karakter kontrol — tes hostile `..%2F..%2Fetc%2Fpasswd.png` membuktikan ref yang lahir tetap `FileRef.isValid` dan berakhir `passwd.png`.
- Klien tidak pernah mengirim key; ia mengirim nama berkas untuk **ditampilkan**, server yang menamatkannya. Kalau klien bisa menyusun key, ia bisa menimpa berkas tenant lain.

### Blok F: Varian CRM = Pola yang Sama, Gerbang yang Sama, Record yang Nyata

Unduh CRM membaca ref dari **nilai lead yang tersimpan**:

```kotlin
val ref = lead.customAttributes
    .rawCell(CustomFieldId(fieldId))?.string("v")?.takeIf { FileRef.isValid(it) }
```

**Mengapa?**
- Sel custom field CRM = sel ter-tag `{"t":"...","v":"..."}` (lihat `CustomAttributes`). Ref tinggal `v`-nya; bentuknya wajib lolos `FileRef.isValid` lagi — data lama yang rusak dijawab 404, bukan dipercaya buta.
- Gerbang CRM memakai `crmDecision`/`requireCrmAccess` (pola `CrmRoutes`) **plus** jangkauan data PIC (`crmOwnerReach` + `requireReachableOwner`) — staf ber-scope "Data Sendiri" tidak boleh mengunduh lampiran lead milik sales lain.

### Blok G: Wiring & Kepemilikan Route

```kotlin
// DomainRouteWiring: default adapter, override dari test
private val objectStorage = objectStorageOverride ?: S3ObjectStorage()

// RouteOwnership: entri SETELAH prefix modul khusus tenant yang lebih spesifik
listOf("/api/tenant/modules" to RouteOwner.Platform("unggah/unduh berkas tipe field FILE (TRD-FIELD-002) — gerbang modul induk per path"))
```

**Mengapa?**
- `RouteOwnershipTest` menggagalkan route `/api/tenant/**` tanpa pemilik — ini fitur pendaftaran, bukan formalitas. Entri diletakkan **setelah** concat `TenantPackContributions` supaya prefix lebih spesifik (`/api/tenant/modules/layanan_change_request`) menang lebih dulu.
- Prefix `/api/tenant/crm` sudah tercatat milik `CRM_SALES` — varian CRM otomatis punya pemilik.
- Default `S3ObjectStorage()` di wiring, override lewat `Application.module(...)` — pola injeksi test yang sama dengan `poFileStorageOverride`.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Body raw + metadata via query** (pola PO) | Multipart plugin | Satu request, tanpa plugin, klien cukup `setBody(bytes)`; sudah teruji di DealRoutes | Multipart menambah permukaan parsing + dependensi plugin baru |
| **Port `ObjectStorage` baru** | Reuse `PoFileStorage` langsung | Domain field tidak boleh bergantung domain deal (strangler); jalur deal bebas berubah sendiri | Ketergantungan silang domain = refactoring deal merobohkan field |
| **Presigned GET 15 menit** | Proxy byte lewat server / URL permanen | Server tidak jadi bottleneck aliran byte; URL kedaluwarsa = akses hilang sendiri | URL permanen = kebocoran permanen; proxy = server melayani byte selamanya |
| **Bucket terpisah `S3_BUCKET_FILES`** | Satu bucket bersama PO | Isolasi sweep/retensi per kebutuhan; biaya nol (R3 TRD) | Sweep orphan PO ikut menghapus field (atau sebaliknya) |
| **Gate dinamis per `moduleCode` + varian CRM** | Endpoint per modul (copy-paste) | Satu kontrak untuk semua modul; gerbang tetap fail-closed (modul tak dikenal = 403) | N file route kembar yang meleset satu gerbang |
| **Fake `ObjectStorage` in-memory di tes** | MinIO nyata di CI | Cepat, deterministik; integrasi MinIO tetap opsional (ops) | CI bergantung Docker = lambat & rapuh |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls)

1. **Jebakan 1: `/**` di dalam teks KDoc.**
   - *Kenapa bahaya*: Komentar blok Kotlin **bersarang**. Menulis `` `{tenantId}/fields/**` `` di dalam KDoc membuka komentar dalam — doc ditutup "dua kali" dan file gagal kompilasi dengan `Unclosed comment` di akhir file, jauh dari baris penyebabnya.
   - *Solusi elegan kita*: tulis `prefiks {tenantId}/fields/` tanpa `**`, atau escape.

2. **Jebakan 2: Param konstruktor biasa dipakai di method.**
   - *Kenapa bahaya*: Parameter konstruktor tanpa `val` hanya hidup di initializer — memakainya di `registerIn()` = `Unresolved reference`, padahal "jelas-jelas ada di konstruktor".
   - *Solusi elegan kita*: ikuti pola file itu — param biasa di-*assign* ke `private val` (lihat `fieldFileRecordRows`).

3. **Jebakan 3: Mengecek 415/413 dengan membaca body lebih dulu.**
   - *Kenapa bahaya*: server menerima 10 MB sampah hanya untuk dibuang; lebih buruk, gerbang RBAC ikut "turun" ke bawah validasi (melanggar Kontrak 7).
   - *Solusi elegan kita*: `contentType` divalidasi dari query sebelum `receive`, RBAC sebelum semua itu.

4. **Jebakan 4: Mempercayai `fileName` atau ref yang tersimpan.**
   - *Kenapa bahaya*: path traversal (`../../`) dan data lama yang rusak.
   - *Solusi elegan kita*: hanya `FileRef.build` server yang menyusun key; ref yang DIBACA pun wajib lolos `FileRef.isValid` lagi — gagal = 404, bukan dipercaya.

5. **Jebakan 5: Menambah route ke file besar yang sudah ada.**
   - *Kenapa bahaya*: ratchet §14 — `DealRoutes.kt` 719 baris; menambah = utang bertambah dan review "sekali duduk" mustahil.
   - *Solusi elegan kita*: `FieldFileRoutes.kt` baru; konstanta (`MAX_FIELD_FILE_BYTES`, allowlist) ikut pindah ke sana supaya perluasan = satu file.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Strategi: **route test dengan `testApplication` + fake storage + repositori in-memory** (tanpa Postgres, tanpa MinIO — CI tetap cepat dan deterministik). Lihat `server/src/test/kotlin/com/eventverse/app/routes/FieldFileRoutesTest.kt` (23 tes).

Kasus kunci dan kenapa ia "bermakna":

- `unggah tanpa OPERATE ditolak 403 sebelum body dibaca` — body **sampah tanpa `fileName`** dikirim: bila handler membaca body dulu, jawabannya 400. Jadi 403 = bukti gerbang paling awal (Kontrak 7).
- `modul tak dikenal ditolak 403` — fail-closed: modul di luar registri = RBAC tak terhitung.
- `storage belum dikonfigurasi ditolak 503` — env S3 kosong dim simulasi `configured = false`; pesan wajib menyebut env yang kurang.
- `berkas melebihi 10 MB ditolak 413` / `tipe konten di luar allowlist ditolak 415` / `body kosong / fileName kosong ditolak 400` — satu tes per kode status, isi lain dibuat valid.
- `unggah sukses menghasilkan ref FileRef sah` — ref dicek `FileRef.isValid`, diawali `fields/{tenant}/quality_control/rec-1/lampiran-`, dan fake storage benar-benar menerima byte + content-type di key itu.
- `nama berkas hostile disanitasi dalam ref` — `..%2F..%2Fetc%2Fpasswd.png` masuk, ref keluar tetap sah dan berakhir `passwd.png`.
- `unduh sukses memberi URL presigned` / `unduh field bukan ref sah ditolak 404` / `unduh CRM lead tak ada ditolak 404` — unduh membaca ref dari record, data rusak = 404 (bukan dipercaya), dan URL presigned tiruan memuat ref.

Bukti di mesin pengembang:

```
./gradlew :server:compileKotlin :server:test
→ BUILD SUCCESSFUL; 119 kelas tes, 638 tes, 0 gagal, 0 error
   (termasuk 23 tes baru FieldFileRoutesTest dan RouteOwnershipTest yang menjaga pendaftaran)
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: Tambahkan `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (`.xlsx`) ke `ALLOWED_FIELD_FILE_MIME_TYPES` + satu tes 415→201. Rasakan desain "perluasan = satu perubahan + tes" yang dijanjikan TRD.
- [ ] **Tantangan 2**: Daftarkan modul hasil handoff di `fieldFileRecordRows` (pola injeksi test lewat `Application.module`) lalu tulis tes unduh end-to-end untuk modul itu. Perhatikan: kamu TIDAK menyentuh `FieldFileRoutes` sama sekali — itulah manfaat injeksi map.
- [ ] **Tantangan 3**: Jalankan MinIO (`docker compose up -d minio minio-init`), set env di `.env`, lalu unggah berkas nyata ke endpoint generik dan unduh presigned URL-nya di browser sebelum 15 menit berlalu. Amati layout bucket tenant-first di konsol MinIO (`localhost:9001`).
