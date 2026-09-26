# 🎓 Modul Pembelajaran: Debugging "Gagal Memuat SPK Sample" di `/sampling-order`

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: JVM Lazy Class Loading, Classpath Stale (Hot Swap Tanpa Restart), Gradle Build Race Condition, Ktor Error Handling, API Debugging dengan curl
> **Prasyarat**: Paham dasar arsitektur modul KMP (core → server → app/shared), tahu cara menjalankan `./dev.sh`
> **Referensi Task**: Debugging session 18 Sep 2026 — halaman `/sampling-order` menampilkan toast "Gagal memuat SPK sample: com/eventverse/app/domain/sampling/usecases/GetSamplingOrderListUseCase$invoke$1"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Halaman Order Sampling (`/sampling-order`) tiba-tiba menampilkan toast merah:

```text
Gagal memuat SPK sample: com/eventverse/app/domain/sampling/usecases/GetSamplingOrderListUseCase$invoke$1
```

Pesannya bukan bahasa manusia — itu **nama class Java internal** yang bocor sampai ke UI.
Developer junior biasanya panik dan mencurigai kode baru yang belum di-commit. Padahal
perubahannya hanya kosmetik (warna badge + komponen timeline).

### Analogi Sederhana

Bayangkan sebuah restoran (server JVM) yang sudah **membuka kunci lemari bahan** (classpath)
sejak pagi. Sore harinya, pemasok (Gradle build) **menukar seluruh isi lemari** tanpa memberi
tahu dapur. Ketika koki butuh satu bahan yang belum pernah dia ambil hari itu (lazy class
loading), dia membuka lemari dengan **peta rak versi pagi** — dan mengambil bahan yang salah
atau tidak menemukannya. Dapur terhenti (`NoClassDefFoundError`), meskipun lemari barunya
sebenarnya lengkap.

### Hasil Akhir yang Diharapkan

- Server Ktor berjalan dengan **classpath konsisten** (jar & class dari build yang sama).
- Endpoint `GET /api/tenant/sampling/orders` kembali `200 OK` dengan 14 SPK.
- Kita punya runbook reproducible untuk mendiagnosis kasus serupa.

---

## 🧭 2. "Start dari Mana?" — Alur Diagnosis (Order of Operations)

Ketika sebuah halaman gagal memuat data, JANGAN langsung baca kode fitur. Ikuti alur ini:

1. **Langkah 0: Pastikan halaman web-nya hidup.**
   `curl -s -o /dev/null -w '%{http_code}' http://localhost:3000/sampling-order` → `200`.
   Kalau 200, berarti Webpack/Compose Web aman. Masalahnya ada di **lapisan data**.

2. **Langkah 1: Temukan sumber pesan error di klien.**
   Grep `"Gagal memuat SPK sample"` → mendarat di
   `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/SamplingViewModel.kt`
   fungsi `load()`. Error itu hanya **pembungkus** dari `SamplingApiClient.getOrders()`.

3. **Langkah 2: Temukan kontrak jaringannya.**
   `SamplingApiClient` memanggil `GET /api/tenant/sampling/orders` dengan
   `Authorization: Bearer <token>` + `X-Tenant-Slug`. Semua kegagalan HTTP ≥ 400 meledak
   lewat `requireBody()` dengan pesan `"Gagal memuat daftar SPK sample (HTTP xxx): <body>"`.
   **Body inilah petunjuk aslinya.**

4. **Langkah 3: Reproduce dengan token asli (bukan tebakan).**
   Server menyediakan endpoint demo yang menerbitkan JWT asli:
   ```bash
   TOKEN=$(curl -s -X POST 'http://localhost:8080/api/public/auth/demo?tenantSlug=wemade-demo&role=TENANT_ADMIN' \
     | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
   curl -s -i -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Slug: wemade-demo" \
     http://localhost:8080/api/tenant/sampling/orders
   ```
   Hasilnya: **HTTP 500** dengan body persis nama class:
   `com/eventverse/app/domain/sampling/usecases/GetSamplingOrderListUseCase$invoke$1`
   → Ini signature klasik **`NoClassDefFoundError`** yang dimapped Ktor ke respons error.

5. **Langkah 4: Periksa proses yang berjalan vs artefak build.**
   ```bash
   lsof -iTCP:8080 -sTCP:LISTEN        # cari PID JVM server
   ps -p <PID> -o lstart=,command=     # kapan start & classpath apa yang dipakai
   ls -la core/build/libs/core-jvm.jar # kapan jar terakhir ditimpa
   ```
   Temuan: server start **09:21**, tapi `core-jvm.jar` **ditimpa 09:35** oleh build
   `:core:jvmTest` yang berjalan paralel. **Jar di disk sehat** (berisi class yang hilang),
   tapi JVM yang hidup memegang **jar index versi lama**.

6. **Langkah 5: Restart server dengan environment yang benar.**
   ```bash
   lsof -ti :8080 | xargs kill
   cd /Volumes/amalari/Projects/wemade
   (set -a; . ./.env; set +a; nohup ./gradlew :server:run > /tmp/wemade-server.log 2>&1 &)
   ```
   `set -a; . ./.env; set +a` itu **wajib** — Gradle/JVM tidak membaca `.env` sendiri.

---

## 🔬 3. Bedah Akar Masalah Blok per Blok

### 3.1 Mengapa errornya berupa nama class, bukan pesan manusia?

```kotlin
// server/.../routes/SamplingRoutes.kt
private suspend fun ApplicationCall.respondFailure(status: HttpStatusCode, error: Throwable) {
    respond(status, error.message ?: "Unknown error")
}
```

`NoClassDefFoundError` adalah `Throwable` yang **pesan-nya adalah nama class yang gagal
dimuat**. Kode route kita meneruskan `error.message` mentah ke client. Junior sering mengira
ini "bug di frontend" karena pesannya muncul di toast — padahal itu data mentah dari backend.

**Pelajaran kecil**: `respondFailure` boleh diperhalus agar linkage error
(`NoClassDefFoundError`, `NoSuchMethodError`) di-map ke pesan
"Server binary tidak konsisten — restart server", bukan nama class. Tapi jangan menyembunyikan
`error.message` sepenuhnya: pesan mentah itu justru yang menyelamatkan diagnosis hari ini.

### 3.2 Mengapa JVM bisa rusak padahal jar-nya selesai dibuild?

JVM **tidak membaca ulang seluruh jar setiap kali butuh class**. Saat class pertama kali
dibutuhkan, JVM membaca **central directory index** jar (offset byte per entri) lalu menghafalnya.
Ketika Gradle menimpa file jar di belakang punggung JVM:

- Class yang **sudah dimuat** → masih versi lama, hidup di memory.
- Class yang **belum dimuat** → dicari lewat offset index lama di file baru → byte-nya
  tidak cocok / entri hilang → `NoClassDefFoundError` atau `ClassFormatError`.

Kasus kita: `GetSamplingOrderListUseCase$invoke$1` (class lambda hasil kompilasi fungsi
`invoke`, dibuat otomatis compiler Kotlin) belum pernah dipakai sejak server start — sampai
kamu membuka halaman sampling. Boom.

### 3.3 Mengapa ini mudah terjadi di repo KMP ini?

Classpath server yang kita lihat dari `ps -p <PID>`:

```text
-cp .../server/build/classes/kotlin/main:.../core/build/libs/core-jvm.jar:...
```

Server mengonsumsi **core sebagai jar hasil build**, bukan source. Jadi dua proses Gradle
yang berjalan bersamaan (`:server:run` + `:core:jvmTest`) bisa saling menimpa artefak.
Ini trade-off yang wajar untuk kecepatan dev — tapi konsekuensinya: **setiap kali core
berubah, server wajib di-restart**, bukan watch-nya saja.

### 3.4 Mengapa perubahan yang belum di-commit BUKAN tersangka?

Perubahan working tree saat kejadian hanya:

- `DealDetailDialog.kt` — warna badge & komponen `SamplingMonitoringTimeline` (presentation).
- `SamplingOrder.kt` / `SamplingOrderValueObjects.kt` — ter-recompile ke jar baru (itulah
  pemicu jar ditimpa), tapi **secara semantik tidak ada yang salah**; jar di disk valid.

Membedakan "kode saya yang salah" vs "lingkungan yang basi" adalah skill inti debugging:
kode yang salah biasanya menghasilkan **HTTP 4xx + stack trace bisnis**; lingkungan yang
basi menghasilkan **HTTP 500 + nama class/linkage error**.

   Tanpa itu, `DB_PORT` jatuh ke default 5432 (PostgreSQL proyek lain) dan kita menukar
   satu bug dengan bug lain.

7. **Langkah 6: Verifikasi end-to-end.**
   Ulangi curl dari Langkah 3 → `200 OK`, 14 SPK. Lalu verifikasi lewat **proxy Webpack**
   `http://localhost:3000/api/tenant/sampling/orders` → juga `200` (jalur yang sama dengan
   yang dipakai browser). Selesai.

---

## 🛠️ 4. Technology & Approach ("The Why")

| Keputusan | Alternatif | Mengapa dipilih | Risiko jika pakai cara lain |
|---|---|---|---|
| Reproduce via `curl` + `POST /api/public/auth/demo` | Klik halaman di browser berulang kali | Reproducible, cepat, dan menampilkan **body error asli** yang dibungkus toast klien | Toast UI memotong pesan; browser menambah lapisan cache yang menyesatkan |
| `ps -p <PID> -o lstart=,command=` | Menebak "pasti butuh rebuild" | Menunjukkan **waktu start** vs **waktu jar ditimpa** — bukti forensik, bukan firasat | Rebuild tanpa restart memperbaiki gejala tapi bug kambuh tiap core berubah |
| Restart dengan `set -a; . ./.env; set +a` | `./gradlew :server:run` polos | `dev.sh` sudah mendokumentasikan jebakan ini: JVM tidak membaca `.env`, koneksi diam-diam ke port DB yang salah | Gagal `HikariPool$PoolInitializationException` yang *kelihatannya* bug kode |
| Verifikasi via proxy `:3000` juga | Berhenti di `:8080` | Browser Wasm memanggil relative path → lewat Webpack proxy. Jalur yang diverifikasi harus **jalur yang dipakai user** | 8080 sehat tapi konfigurasi proxy rusak → user tetap komplain |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Jebakan 1: Menyalahkan kode yang belum di-commit.**
   Pesan error mengandung nama package domain → mata langsung ke `git diff`. Padahal
   diff-nya hanya warna & UI. Pelajaran: **reproduce dulu, curiga kemudian.**

2. **Jebakan 2: Menimpa artefak build di belakang JVM yang hidup.**
   Menjalankan `:core:jvmTest` (atau task build lain yang menyentuh `core-jvm.jar`) sementara
   `:server:run` hidup = bolak-balik `NoClassDefFoundError` yang "sembuh sendiri" lalu kambuh.
   Aturan praktis: **ubah core → restart server.**

3. **Jebakan 3: Restart tanpa `.env`.**
   Gejalanya berpindah: dari "gagal memuat SPK" menjadi "gagal koneksi database". Karena
   keduanya muncul sebagai toast/500, junior bisa mengira perbaikannya malah merusak.

4. **Jebakan 4: Berhenti memverifikasi di 8080.**
   Endpoint sehat di 8080 belum berarti halaman sembuh — browser melalui proxy Webpack di
   3000. Verifikasi harus menembus jalur yang sama dengan pengguna.

---

## ✅ 6. Verifikasi & Tantangan Mandiri

### Verifikasi yang sudah dijalankan

```bash
# 1. Endpoint langsung — 200 OK, 14 SPK
TOKEN=$(curl -s -X POST 'http://localhost:8080/api/public/auth/demo?tenantSlug=wemade-demo&role=TENANT_ADMIN' ...)
curl -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Slug: wemade-demo" \
  http://localhost:8080/api/tenant/sampling/orders   # → HTTP 200, SPK-SMP-0001..0014

# 2. Jalur browser (proxy Webpack) — 200 OK
curl -H "..." http://localhost:3000/api/tenant/sampling/orders  # → HTTP 200

# 3. Server log bersih
grep 'Responding at' /tmp/wemade-server.log  # → Responding at http://0.0.0.0:8080
```

### Tantangan Mandiri

- [ ] **Tantangan 1**: Perbaiki `respondFailure` di `SamplingRoutes.kt` agar linkage error
      (`NoClassDefFoundError`, `NoSuchMethodError`) direspons sebagai
      `"Server binary tidak konsisten — restart :server:run"` dengan log lengkap di stdout,
      tanpa menyembunyikan pesan untuk `Exception` biasa. (Hint: `when (error)`.)
- [ ] **Tantangan 2**: Tambahkan pengecekan ke `dev.sh` mode `all`: sebelum `:server:run`,
      jika port 8080 hidup, tampilkan peringatan eksplisit bahwa jar core bisa basi selama
      build paralel berjalan.
- [ ] **Tantangan 3**: Tulis satu-liner `curl` gabungan (login demo + panggil endpoint
      tenant apa pun) dan simpan sebagai `tools/api-probe.sh` dengan argumen path — ini
      akan menyelamatkanmu berkali-kali di masa depan.

