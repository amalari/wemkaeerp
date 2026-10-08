# 🎓 Modul Pembelajaran: Pagar Migrasi Modul Khusus Tenant (J3) — TRD-PLAT-004 Track C

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Arsitektur test-gate (tes yang memblokir), pemindai SQL berbasis regex, multi-tenancy, jalur kepemilikan modul (J0–J3)
> **Prasyarat**: Paham konsep schema per modul (B8, `ModuleSchemaMap`), tahu apa itu pack tenant (`TenantPackContributions`), dan pernah membaca migrasi Flyway
> **Referensi Task**: [`TRD-PLAT-004`](../trd/TRD-PLAT-004-module-ownership-lanes.md) §4.5 keputusan P4 · [`PLAN-module-ownership-lanes.md`](../plannings/PLAN-module-ownership-lanes.md) Track C

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Modul khusus satu tenant (J3, contoh `layanan_change_request`) hidup di schema-nya sendiri. Hari ini pengembangnya satu tim, dua tahun lagi orangnya berganti. Tanpa pagar, migrasi J3 yang butuh data deal akan menulis `REFERENCES crm_sales.crm_leads(id)` — dan begitu merge, modul tenant itu **terkunci permanen** ke schema modul garment: tidak bisa dipromosikan jadi pack bersama (P5), tidak bisa dipindah, tidak bisa dijual ke tenant lain tanpa membawa `crm_sales`. Kopling macam ini selalu lahir dari satu baris SQL yang "kebetulan praktis".
- **Analogi Sederhana**: Unit apartemen (schema J3) boleh tersambung ke meteran listrik utama gedung (`public.tenants`), tapi tidak boleh menyambung kabelnya sendiri ke unit tetangga (`crm_sales`) — sekali nyambung, renovasi unit tetangga merobek unit kamu.
- **Hasil Akhir**: Sebuah **tes arsitektur yang memblokir CI**: migrasi J3 yang merujuk schema modul lain menggagalkan build, dengan pesan yang menjelaskan jalan keluarnya (`moduleReferences`, atau perluasan daftar putih lewat PR).

**Kenapa tes, bukan review manual?** Review lupa; CI tidak. Pola yang sama dengan pagar impor Track B (`TenantCodeBoundaryTest`) dan penjaga B8 (`ModuleSchemaOwnershipTest`) — repo ini konsisten memilih *executable architecture rules*.

---

## 🧭 2. "Start dari Mana?" — Alur Penulisan (Order of Operations)

1. **Langkah 0: Definisikan "siapa yang diawasi" secara mekanis (C1).** Pertanyaan pertama bukan "regexnya apa", tapi: *kapan sebuah file SQL disebut "migrasi J3"?* Jawabannya harus turun dari data yang sudah ada — bukan daftar tulis tangan. Di repo ini: modul J3 = yang terdaftar di `TenantPackContributions` (registri Track B); nama schema = kode modul (kontrak B8). Karena deteksi membaca registri, pack J3 kedua (`klinik_*`) otomatis terjaga tanpa menyentuh pemindai.
2. **Langkah 1: Tetapkan daftar putih dari keputusan, bukan dari kode yang sudah ada.** Daftar putih (`public.tenants`, `public.users`, schema milik sendiri, `moduleReferences`) datang dari TRD-PLAT-004 §4.5. Jangan "menyesuaikan" daftar putih agar migrasi lama lolos — kalau migrasi lama gagal, itu temuan, bukan noise.
3. **Langkah 2: Pilih kata kunci SQL yang diadili (C2).** `REFERENCES` (kopling struktural — FK), `FROM`/`JOIN` (kopling baca), `SET search_path` (jalur pintas membajak resolusi nama). Sengaja **tidak** mengadili `INSERT INTO`/`UPDATE`: pola pendaftaran katalog (V64/V90) memang menulis tabel platform, dan menulis baris bukan kopling schema.
4. **Langkah 3: Tulis pemindai murni** — fungsi `(namaFile, sql) -> List<Violation>` tanpa I/O, tanpa Gradle, tanpa DB, sehingga bisa diuji dengan string inline.
5. **Langkah 4: Tulis tiga bukti (C3)** — lihat §6. Pagar tanpa bukti "punya gigi" hanyalah dekorasi.
6. **Langkah 5: Dokumentasikan batasnya (C4)** di KDoc `ModuleSchemaMap`: pembatasan ini khusus J3; B8 tetap berlaku untuk garment.

---

## 🧩 3. Anatomi Kode — Blok per Blok

Semua kode pagar ada di **sumber tes** (`server/src/test/kotlin/com/eventverse/app/infrastructure/`):

### 3.1 Deteksi J3 (`isJ3Migration`) — "siapa yang diadili?"

```kotlin
val j3Schemas: Set<String> =
    TenantPackContributions.all.flatMap { c -> c.tables.keys.map { it.value } }.toSet()
```

Mental model: `TenantPackContributions` adalah **daftar tamu**; pemindai tidak menyimpan daftar kedua. Dua pola menandai file sebagai migrasi J3: (1) `CREATE SCHEMA [IF NOT EXISTS] <j3>` atau `ALTER SCHEMA/TABLE <j3>`, (2) sebutan terkualifikasi `<j3>.<tabel>`. Komentar SQL dibuang dulu supaya catatan prosa tidak salah tangkap.

### 3.2 Pengadilan (`scan`) — tiga kata kunci

- `REFERENCES` **terkualifikasi maupun tidak**: `tenants(id)` tanpa schema resolve lewat `search_path` ke `public.tenants` — diangkat eksplisit jadi `public.<tabel>` lalu dicek daftar putih. FK = kopling tahan lama, maka yang tak-terkualifikasi pun diadili.
- `FROM`/`JOIN` **hanya yang terkualifikasi** (`crm_sales.deals`). Yang tak-terkualifikasi dilewati dengan alasan tertulis: lewat `search_path` bawaan hanya bisa mendarat di `public` (isinya sudah dijaga B8), sekaligus menghindari positif palsu `FROM generate_series(...)`.
- `SET search_path`: setiap schema di daftarnya wajib diizinkan — menutup celah "biarkan `FROM` tak-terkualifikasi, tapi bajak `search_path`-nya".

### 3.3 `stripComments` — detail yang sering salah

Komentar diganti **spasi** (bukan dipotong) dan baris baru dipertahankan, sehingga nomor baris asli bisa dihitung untuk pesan pelanggaran: `V90__....sql:31: [REFERENCES] crm_sales.crm_leads — …`. String literal `'…'`/`"…"` dihormati agar `--` di dalam string tidak memicu mode komentar.

### 3.4 Sumber kebenaran daftar putih

`moduleReferences` diambil dari `c.pack.moduleReferences` per kontribusi (B6). Hari ini kosong; begitu pack J3 merujuk modul bersama, schema modul itu otomatis boleh dirujuk — tanpa mengubah pemindai.

---

## 🏗️ 4. Teknologi & Keputusan — "Kenapa begini?"

| Keputusan | Alternatif yang ditolak | Alasan |
|---|---|---|
| Regex di atas file `.sql` | Parser AST SQL (dependensi baru) | Hanya 3 kata kunci yang diadili; regex + buang-komentar cukup dan transparan. Batasnya ditulis eksplisit di KDoc |
| Tes Gradle (`:server:test`) | Skrip audit shell | Skrip "melapor"; **tes memblokir CI** (risiko #5 di PLAN). Pola sama dengan B4 |
| Pemindai di sumber tes | Objek di `src/main` | Bukan perilaku runtime; di tes ia bebas mengimpor registri, dan pagar impor B4 mengecualikan tes dengan alasan tertulis |
| Lokasi file dari akar repo (walk-up ke `settings.gradle.kts`) | `getResource("/db/migration")` | Mengikuti pola `TenantCodeBoundaryTest`; tahan terhadap layout working-directory Gradle |

---

## ⚠️ 5. Jebakan Umum (yang hampir kita alami)

1. **Komentar blok Kotlin bersarang!** Berbeda dari Java, pembuka komentar blok di dalam KDoc membuka komentar *bersarang*; KDoc lalu tidak tertutup oleh penutup miliknya → `Unclosed comment` di akhir file. KDoc `stripComments` sempat memuat literal pembuka komentar dan build gagal. Solusi: sebut nama karakternya ("pembuka garis-miring-bintang"), jangan tulis langsung.
2. **Interpolasi `$user` di string Kotlin**: `"$user"` adalah template Kotlin — untuk teks dolar-user milik `SET search_path`, tulis `"\$user"`.
3. **Positif palsu terhadap fungsi SQL**: `FROM generate_series(...)` bukan tabel. Karena itu `FROM` tak-terkualifikasi dilewati sebagai kebijakan, bukan dikecualikan satu-satu.
4. **Fixture jangan pernah di `db/migration` milik main**: Flyway menjalankan `classpath:db/migration`; fixture pelanggar ditaruh di `db/migration-fixture/` milik test resources supaya tidak pernah dieksekusi ke database.

---

## 🧪 6. Testing — Tiga Bukti Pagar (C3)

`J3MigrationFenceTest` membuktikan tiga hal, dan **ketiganya wajib** — kalau salah satu hilang, pagar bisa kosong secara diam-diam (regex tidak pernah match, atau match ke semesta kosong):

1. **Kasus nyata lolos**: semua file `db/migration/*.sql` yang terdeteksi J3 (termasuk V90) menghasilkan nol pelanggaran. Ada asersi anti-kosong: V90 *harus* terdeteksi sebagai J3 — kalau deteksi C1 rusak, tes gagal, bukan diam.
2. **Fixture pelanggar gagal**: `db/migration-fixture/V901__j3_fixture_referencing_crm_sales.sql` (di luar lokasi Flyway) memuat tiga bentuk pelanggaran — `REFERENCES crm_sales.crm_leads`, `JOIN crm_sales.deals`, dan `SET search_path = crm_sales, public` — dan tes menuntut **ketiganya** tertangkap satu per satu.
3. **Yang patuh tidak dituduh**: migrasi J3 inline yang hanya menyentuh `tenants`/`users`/schema sendiri + `INSERT INTO module_catalog_entries` + `SET search_path` berisi `"$user"`, `public`, schema sendiri harus lolos — membuktikan daftar putih bekerja, bukan pagar yang menolak semuanya.

Bonus: migrasi garment yang ber-FK lintas schema (sifat B8) **tetap lolos** — menegaskan pembatasan P4 khusus J3.

**Bukti gigi pada file sungguhan** (dilakukan saat implementasi, pola yang sama dengan Track B): sisipkan `REFERENCES crm_sales.crm_leads(id)` ke V90 asli → tes **gagal** dengan pesan berisi `file:baris` → pulihkan V90 → hijau lagi. Mutasi sengaja, dibuktikan, lalu dibatalkan.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan pack J3 kedua (`klinik`) ke `TenantPackContributions` dengan satu modul dan satu migrasi fixture yang sah — pemindai harus menjaganya **tanpa satu baris pun diubah**. Lalu buat migrasi `klinik_*` yang merujuk `layanan_change_request` — perhatikan saat ini ia lolos (satu ranah tenant); rancang perketatan per-kontribusi, dan tulis tesnya lebih dulu.
- [ ] **Tantangan 2**: Seseorang menulis `SELECT ... FROM crm_sales.deals` di dalam blok `DO $$ ... $$` PL/pgSQL pada migrasi J3. Apakah pagar menangkapnya? (Jawab lewat eksperimen, lalu putuskan: jalan keluar yang sah, atau lubang yang dicatat di KDoc.)
- [ ] **Tantangan 3**: Perluas daftar putih `public` agar `module_catalog_entries` boleh dirujuk migrasi J3 lewat prosedur yang benar: keputusan dicatat di PR, ubah konstanta `publicWhitelist`, tambah satu baris di catatan risiko PLAN. Rasakan kenapa daftar putihnya sengaja kecil.
