# 🎓 Modul Pembelajaran: Debug Error 504 "Gagal Memuat Skema Lead" di /crm-sales

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: HTTP 504 Gateway Timeout, Webpack Dev Server Proxy, Ktor Backend, Observability/Debugging Berlapis
> **Prasyarat**: Paham dasar HTTP status code, arsitektur dev environment project ini (frontend Wasm `:3000` → proxy `/api` → Ktor `:8080` → PostgreSQL)
> **Referensi Task**: Investigasi laporan "http://localhost:3000/crm-sales kenapa error gagal memuat skema lead 504?"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah Nyata**: Pengguna membuka `/crm-sales` dan melihat pesan merah:

```text
Gagal memuat skema lead (HTTP 504)
```

Kesalahan paling umum junior developer saat melihat ini adalah **menyalahkan kode aplikasi** —
membuka `CrmApiClient.kt`, `CrmViewModel.kt`, atau `CrmRoutes.kt` mencari bug. Padahal tidak ada
satu baris kode pun yang salah. Kodenya sedang *berbenar* memberi tahu kita bahwa jaringannya
yang putus.

**Analogi Sederhana**: Bayangkan hotel dengan resepsionis (webpack dev server `:3000`). Tamu
(browser) bertanya ke resepsionis, resepsionis menelepon dapur (Ktor `:8080`) — tapi dapurnya
*kosong, tidak ada koki*. Resepsionis menunggu, lalu bilang ke tamu: *"Maaf, dapur tidak
menjawab"* — itulah **504 Gateway Timeout**: *gateway* (perantara) menghubungi *upstream*
(server asal) tapi tidak dapat jawaban tepat waktu.

Bedakan dengan tetangganya:

| Kode | Arti | Siapa yang bermasalah |
|---|---|---|
| `500` | Internal Server Error | Kode backend crash |
| `502` | Bad Gateway | Proxy dapat jawaban **tidak valid** dari upstream |
| `504` | Gateway Timeout | Proxy **tidak dapat jawaban sama sekali** (upstream mati/hang) |

**Hasil Akhir yang Diharapkan**: `/crm-sales` memuat skema lead (kolom inti + kolom kustom
tenant) seperti biasa.

---

## 🧭 2. "Start dari Mana?" — Alur Investigasi (Order of Operations)

Kalau kamu yang menerima laporan ini, **jangan buka editor dulu — buka terminal**. Urutan
penyelidikan yang benar mengikuti jalur paket data dari browser ke database:

1. **Langkah 0: Temukan sumber pesan error di kode** — hanya untuk tahu *siapa yang menangkap*
   errornya, bukan siapa penyebabnya.
2. **Langkah 1: Petakan topologi jaringan dev environment** — siapa mem-proxy ke siapa.
3. **Langkah 2: Periksa listener tiap port** (`lsof`) — sumber kebenaran objektif.
4. **Langkah 3: Uji tiap hop secara terpisah dengan `curl`** — langsung ke upstream, lalu via
   proxy. Bandingkan.
5. **Langkah 4: Perbaiki sesuai temuan** (di kasus ini: nyalakan server).
6. **Langkah 5: Verifikasi ulang semua hop + uji visual di browser.**

### Mental Model: "Debugging = Binary Search di Jalur Paket"

```
Browser ──▶ Webpack :3000 ──▶ Ktor :8080 ──▶ PostgreSQL :5435
   │              │               │               │
 curl :3000     proxy /api     curl :8080      docker ps
```

Uji setiap panah satu per satu. Hop pertama yang gagal adalah lokasi masalahnya.

---

## 🔬 3. Bedah Investigasi Blok per Blok

### Blok A — Sumber pesan: `CrmApiClient.kt`

```kotlin
override suspend fun getSchema(tenantSlug: String): Result<List<LeadFieldDescriptor>> = runCatching {
    val response = httpClient.get(resolveUrl("$LEADS_PATH/schema")) { ... }
    CrmLeadCodec.decodeSchema(response.requireBody("memuat skema lead"))
}
```

`requireBody` adalah generator pesannya:

```kotlin
private suspend fun HttpResponse.requireBody(action: String): String {
    val body = bodyAsText()
    if (!status.isSuccess()) {
        error("Gagal $action (HTTP ${status.value}): $body")
    }
    return body
}
```

**Mental model**: pesan `"Gagal memuat skema lead (HTTP 504)"` hanyalah *gejala*. `CrmApiClient`
tidak tahu (dan tidak perlu tahu) kenapa 504 — dia hanya meneruskan status HTTP apa adanya ke
UI. Inilah desain error handling yang sehat: satu tempat membungkus, satu tempat menampilkan.

### Blok B — Topologi: `app/webApp/webpack.config.d/devServer.js`

```js
config.devServer.proxy = [{
    context: ['/api'],
    target: 'http://localhost:8080',
    changeOrigin: true
}];
```

Semua request `/api/*` dari browser diteruskan webpack ke `:8080`. **Ini kunci pemahamannya**:
browser hanya pernah bicara dengan `:3000`. Ketika `:8080` mati, yang "merasakan" dan
menerjemahkannya jadi 504 adalah proxy webpack, bukan Ktor.

### Blok C — Bukti objektif: `lsof` dan dua `curl`

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN     # → KOSONG. Tidak ada yang listen!
curl http://localhost:8080/api/tenant/crm/leads/schema   # → status 000 (connection refused)
curl http://localhost:3000/api/tenant/crm/leads/schema   # → 504
```

Interpretasinya telak:

- `schema_status=000` = `curl` bahkan tidak bisa membuka koneksi TCP → **upstream mati total**.
- Proxy mengonfirmasi: `Error occurred while trying to proxy` + 504 — proxy *mencoba* tapi
  ditolak koneksi, lalu membalas 504 ke browser.
- PostgreSQL **tetap hidup** (Docker listen di `:5435`) → masalah terisolasi murni di proses Ktor.

### Blok D — Kenapa 504 dan bukan 502 atau ECONNREFUSED di UI?

`http-proxy` (yang dipakai webpack-dev-server) memperlakukan kegagalan koneksi sebagai timeout
upstream: dari sudut pandang proxy, upstream yang tidak menjawab *tepat waktu* = `504`. Jadi
**504 dari dev-proxy hampir selalu berarti "backend tidak jalan atau hang"**, bukan "backend
balik JSON rusak".
---

## 🛠️ 4. Teknologi & Pendekatan ("The Why")

### Kenapa pakai proxy dev server, bukan CORS langsung ke `:8080`?

1. **URL produksi-identik**: di produksi, frontend dan API dilayani dari origin yang sama. Proxy
   membuat perilaku dev sama dengan prod (path relatif `/api/...`, tanpa CORS).
2. **Cookie/token aman**: sesi tidak perlu `SameSite=None` lintas origin.
3. **Konsekuensinya** (yang harus kamu sadari): dev environment punya *dua proses wajib*, dan
   proxy menyembunyikan matinya salah satunya di balik kode 504 yang samar. Karena itulah
   project ini menyediakan `dev.sh` — satu pintu yang menyalakan **keduanya**:

```bash
./dev.sh            # server :8080 + wasm watcher :3000 sekaligus (default)
./dev.sh server     # hanya Ktor backend
./dev.sh wasm       # hanya frontend + hot reload
```

### Kenapa `dev.sh` eksplisit memuat `.env`?

`docker-compose` membaca `.env` sendiri, tapi **JVM tidak** — `System.getenv()` hanya melihat
environment proses. Tanpa `set -a; . .env; set +a`, variabel `DB_PORT=5435` tidak pernah sampai
ke Ktor dan koneksi diam-diam menembak port database yang salah. Kegagalan semacam ini *senyap*
dan sering disalahartikan sebagai bug kode — persis pola "kelihatannya bug, ternyata
environment" seperti kasus 504 ini.

---

## 🪤 5. Jebakan Pemula (Common Pitfalls)

1. **"504 berarti backend lambat, mari naikkan timeout"** — ❌. Di dev, 504 hampir selalu
   berarti upstream *tidak pernah* menjawab karena prosesnya mati. Naikkan timeout hanya
   memperlamanya.
2. **Menyalahkan kode client/server sebelum cek proses** — ✅ selalu mulai dari `lsof`/`curl`
   per hop. Kode yang "gagal memuat" biasanya justru bagian yang paling benar.
3. **Mengira PostgreSQL hidup = sistem siap** — database hidup di `:5435` tidak berarti API
   hidup. Setiap lapisan adalah proses independen.
4. **Restart browser / hard refresh tanpa menjalankan `./gradlew :server:run`** — gejala
   berubah jadi gerbang login, tapi skema lead tetap gagal setelah login. Verifikasi harus
   sampai endpoint-nya merespons.
5. **Menjalankan hanya `./dev.sh wasm`** — dua dev server di project ini memang terpisah;
   lupakan `:8080` adalah jebakan klasik setelah reboot atau ganti terminal.

---

## ✅ 6. Verifikasi & Tantangan Mandiri

### Langkah verifikasi yang dipakai saat investigasi ini

1. `lsof -nP -iTCP:8080 -sTCP:LISTEN` → harus menampilkan proses `java` (Ktor).
2. `./dev.sh server` → tunggu log Hikari `Pool stats (total=10/10)` = pool DB siap.
3. `curl http://localhost:3000/api/tenant/crm/leads/schema -H 'X-Tenant-Slug: ...'`
   → sebelum fix: **504**; sesudah fix: **401 Authentication required** (naik satu tingkat —
   request sudah diproses Ktor, tinggal butuh token login; 401 adalah *perilaku benar* untuk
   caller tanpa sesi).
4. Buka `/crm-sales` di browser → render gerbang "Akses Terbatas: Autentikasi Diperlukan"
   (bukan lagi kartu error merah 504). Setelah login, skema lead dimuat normal.

### Tantangan Mandiri

- [ ] **Tantangan 1**: Matikan sengaja container PostgreSQL (`docker compose stop postgres`)
      tapi biarkan Ktor hidup. Buka `/crm-sales` dan amati: apakah tetap 504, atau berubah jadi
      error lain? Dokumentasikan perbedaan gejalanya — ini melatihmu membedakan "proxy tidak
      punya upstream" vs "upstream punya upstream yang mati".
- [ ] **Tantangan 2**: Baca `CrmRoutes.kt` dan temukan route `GET /api/tenant/crm/leads/schema`.
      Telusuri alurnya sampai `GetLeadFormSchemaUseCase` — pahami kenapa skema digabung dari
      `LeadFieldDescriptor.coreFields()` + kolom kustom tenant yang diurutkan `position`.
- [ ] **Tantangan 3**: Tambahkan health check sederhana (`GET /api/public/health` → `200 OK`)
      dan biasakan menembaknya dulu setiap kali ada laporan "aplikasi error" — satu perintah
      yang langsung memisahkan "kode bermasalah" dari "proses mati".

---

## 📌 Ringkasan Kasus

| Aspek | Detail |
|---|---|
| Gejala | `/crm-sales` → "Gagal memuat skema lead (HTTP 504)" |
| Sumber pesan | `CrmApiClient.requireBody` (`app/shared/.../infrastructure/api/CrmApiClient.kt`) |
| Akar penyebab | **Proses Ktor `:8080` tidak berjalan** → webpack proxy gagal koneksi → 504 |
| Perbaikan | Jalankan `./dev.sh server` (atau `./dev.sh` untuk keduanya) |
| Verifikasi | `lsof :8080` terisi; endpoint via proxy balas 401 (butuh login) alih-alih 504; browser render gerbang auth |
| Pelajaran | 504 dari dev-proxy = cek proses upstream dulu, bukan kode |

