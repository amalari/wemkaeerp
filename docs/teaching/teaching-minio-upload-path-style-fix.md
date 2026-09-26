# 🎓 Modul Pembelajaran: Fix Upload Mockup Gagal — File Masuk ke MinIO

> **Level Target**: Junior Developer  
> **Topik Utama**: AWS SDK v2 Path-Style Access, MinIO, 12-Factor Config (.env), Fallback Inline Base64  
> **Prasyarat**: Paham alur upload mockup (client PUT raw body → `DealRoutes` → `PoFileStorage`), dan docker-compose MinIO  
> **Referensi Task**: "fixing gagal mengunggah file, harusnya bisa masuk ke minio"

---

## 💡 1. Konsep Dasar & Masalah

**Gejala**: Upload foto mockup di Deal Detail selalu gagal; file tidak pernah muncul di MinIO.

**Akar masalah — dua lapis**:

1. **`.env` tidak punya variabel `S3_*`**. `S3PoFileStorage.isConfigured` membaca
   `System.getenv()` → `false` → route upload jatuh ke mode *inline base64* dengan limit
   **2 MB**. Foto hasil cropper (PNG 1024px) hampir selalu > 2 MB → ditolak
   `"Ukuran foto melebihi 2 MB. Konfigurasikan S3/MinIO…"`. MinIO-nya sendiri sehat —
   yang tidak sehat adalah konfigurasi proses server.
2. **Bug laten virtual-hosted style**. AWS SDK v2 secara default membangun URL
   `http://{bucket}.endpoint` (virtual-hosted). Untuk endpoint `http://localhost:9000`,
   request diteruskan ke `http://wemade-po.localhost:9000` → gagal DNS. MinIO hanya
   mengerti **path-style**: `http://endpoint/{bucket}`. Tanpa
   `pathStyleAccessEnabled(true)`, begitu env diisi upload tetap gagal — hanya beda pesan
   errornya (S3 `403`/`UnknownHostException`, bukan lagi limit ukuran).

**Analogi**: Masalah 1 = kartu aksesnya belum dibuat. Masalah 2 = alamat gedungnya
salah tulis. Mengisi `.env` saja tidak cukup; dua-duanya harus dibetulkan.

---

## 🧭 2. Start dari Mana (Order of Operations Debugging)

1. **Cek infrastruktur dulu, baru kode**: `docker ps` (MinIO healthy?), `curl
   http://localhost:9000/minio/health/live` → 200.
2. **Cek konfigurasi proses**: `grep S3_ .env` — kosong = server jalan tanpa storage.
3. **Baca jalur kode keputusan**: `DealRoutes.kt` line ~502
   (`poFileStorage?.takeIf { it.isConfigured }`) → inilah percabangan inline vs MinIO.
4. **Buktikan kredensial & bucket secara terpisah** dari kode aplikasi:
   ```bash
   docker exec wemade-minio sh -c \
     "mc alias set local http://localhost:9000 wemademinio wemademinio-secret && \
      mc cp /etc/hostname local/wemade-po/_probe/p.txt && mc ls local/wemade-po/_probe/"
   ```
   Kalau ini berhasil tapi upload dari app gagal, masalahnya di adapter SDK — bukan di MinIO.
5. **Baru perbaiki kode** (`S3PoFileStorage`) dan konfigurasi (`.env`).

---

## 🔬 3. Bedah Perbaikan

### A. Path-Style Access (`S3PoFileStorage.kt`)

```kotlin
private val pathStyle: S3Configuration =
    S3Configuration.builder().pathStyleAccessEnabled(true).build()

private val s3: S3Client by lazy {
    val builder = S3Client.builder().region(Region.of(region))
    if (!endpoint.isNullOrBlank()) builder.endpointOverride(URI.create(endpoint))
    builder.serviceConfiguration(pathStyle)   // ← kunci perbaikannya
    ...
}
```

Diterapkan ke **dua** client: `S3Client` (put) dan `S3Presigner` (download URL). Lupa
presigner = upload sukses tapi foto tidak mau tampil — bug dua tahap yang lebih membingungkan
daripada gagal total. Path-style tetap valid untuk AWS S3 asli, jadi aman untuk production.

### B. Konfigurasi 12-Factor (`.env` / `.env.example`)

```bash
S3_ENDPOINT=http://localhost:9000
S3_REGION=us-east-1
S3_ACCESS_KEY=wemademinio
S3_SECRET_KEY=wemademinio-secret
S3_BUCKET_PO=wemade-po
```

Kredensial **wajib sama** dengan `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD` di
`docker-compose.yml`. `dev.sh` mengeksekusi `set -a; . .env; set +a` sehingga variabel sampai
ke `System.getenv()` — JVM tidak membaca `.env` sendiri.

---

## ⚠️ 4. Jebakan Pemula

1. **Restart server setelah edit `.env`.** `System.getenv()` dibaca sekali saat proses
   menyala; hot-reload kode tidak memuat ulang environment. Selalu matikan `dev.sh` dan
   jalankan ulang.
2. **Anggap "MinIO jalan" = "upload jalan".** Container sehat ≠ kredensial server cocok ≠
   bucket ada. Tiga hal berbeda, cek bertingkat.
3. **Lupa presigner path-style** — download URL dibuat virtual-hosted dan gagal diam-diam
   (`downloadUrl(key).getOrNull()` di `withResolvedMockups` kembali `null` → slot foto kosong
   tanpa error terlihat).
4. **Menulis kredensial MinIO baru di `.env`** tanpa mengubah docker-compose (atau
   sebaliknya) → `403 InvalidAccessKeyId` yang pesannya tidak menunjuk ke ketidakcocokan ini.

---

## ✅ 5. Verifikasi Mandiri

1. `./gradlew :server:compileKotlin` — lulus.
2. Probe tulis MinIO via `mc` (perintah di §2 langkah 4) — lulus: file ter-upload dan
   ter-hapus di bucket `wemade-po`.
3. **Uji end-to-end oleh kamu**: restart `./dev.sh`, buka Deal Detail → Siklus Sampling →
   upload foto mockup > 2 MB → harus sukses, dan objek muncul di
   `mc ls local/wemade-po/sampling-mockups/` (atau konsol MinIO di `http://localhost:9001`).
4. Tampilkan foto di kartu (presigned URL) — memastikan presigner juga path-style.

## 🏆 6. Tantangan Mandiri

- [ ] Pindahkan kredensial MinIO ke satu sumber: isi `S3_ACCESS_KEY`/`S3_SECRET_KEY` di `.env`
      dan buat `docker-compose.yml` memakai substitusi yang sama tanpa default ganda.
- [ ] Tambahkan startup log di `Application.kt`: `"PO storage: S3 (bucket=…)"` vs
      `"PO storage: inline fallback (max 2 MB)"` supaya mode storage terlihat saat server nyala.
- [ ] Auto-create bucket dari server saat `isConfigured` tapi bucket belum ada
      (`headBucket` → `createBucket`), menggantikan sidecar `minio-init`.
