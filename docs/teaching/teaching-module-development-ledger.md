# 🎓 Modul Pembelajaran: Module Development Ledger — Mencatat Effort agar AI Bisa Mengestimasi Harga

> **Level Target**: Mid Developer
> **Topik Utama**: Data Modelling untuk Machine Learning, Target Leakage, Vector Retrieval (kNN),
> PostgreSQL Generated Column, Strategy Pattern untuk Pricing, DDD di Kotlin Multiplatform
> **Prasyarat**: Kotlin, Exposed, Flyway, Ktor, dan konsep dasar Domain-Driven Design.
> **Referensi Task**: Fondasi estimasi & harga langganan modul (WeMade ERP)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Bayangkan ada calon klien menulis di landing page:

> *"Kami makloon jaket. Kain dari buyer. Ada sablon. QC-nya pakai AQL."*

Kita ingin sistem menjawab: modul apa yang sudah ada, modul apa yang harus dibangun, berapa lama
membangunnya, dan **berapa tambahan harga SaaS per bulan**.

Masalahnya: untuk menjawab "berapa lama", sistem harus tahu berapa lama **pekerjaan serupa di masa
lalu**. Dan sebelum task ini, WeMade ERP tidak menyimpan itu sama sekali:

- Katalog modul hidup sebagai `enum BusinessModule` — tidak bisa tumbuh tanpa deploy.
- Tidak ada satu pun angka uang di seluruh repo. `SubscriptionTier` cuma batas fitur.
- Effort pembuatan modul tidak tercatat di mana pun.

### Kenapa ini bukan sekadar "bikin tabel"

Tabel yang salah bentuk akan **terlihat rapi dan tidak mengajarkan apa pun**. Itu kegagalan yang
paling mahal, karena baru ketahuan setelah setahun mengumpulkan data.

Contoh paling gampang salah: kolom `revision_round_count`. Di data historis, ia akan tampak sebagai
prediktor terkuat — makin banyak revisi, makin lama. Tapi saat permintaan baru masuk, **angkanya
belum ada**. Model yang dilatih memakainya akan terlihat akurat di data lama dan tidak berguna di
dunia nyata.

Itulah yang di machine learning disebut **target leakage**: memakai informasi yang di dunia nyata
baru tersedia *setelah* jawabannya sudah diketahui.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Urutannya bukan selera. Tiap langkah mengunci keputusan yang dipakai langkah berikutnya.

**Step 0 — Putuskan apa yang jadi prediktor dan apa yang jadi hasil.**
Sebelum menulis satu baris SQL pun. Tiap kolom digolongkan: diketahui *sebelum* kerja dimulai
(boleh jadi input model) atau baru diketahui *sesudah* (tidak boleh). Keputusan ini menentukan
bentuk tabelnya, bukan sebaliknya.

**Step 1 — Migrasi SQL** (`V13__create_module_development_ledger.sql`).
Skema dulu, karena kolom generated dan constraint-nya adalah bagian dari logika, bukan detail
penyimpanan.

**Step 2 — Value Objects & Entity di `:core`.**
`MoneyIdr`, `WorkHours`, `SizePoints`, lalu `ModuleBuildRecord`. Domain murni, tanpa Exposed,
tanpa Ktor — bisa diuji tanpa database sama sekali.

**Step 3 — Logika estimasi** (`BuildEstimator`).
Masih domain murni. Ini otaknya, dan ia tidak perlu tahu data datang dari mana.

**Step 4 — Exposed table + repository Postgres.**
Baru sekarang menyentuh infrastruktur.

**Step 5 — Use case**, menjahit domain + repository.

**Step 6 — Routes + DTO**, lapisan paling luar.

> **Mental model**: tulis dari yang paling tidak bergantung apa pun (aturan bisnis) ke yang paling
> bergantung (HTTP). Kalau terbalik, aturan bisnismu akan ikut berbentuk seperti JSON request.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Value Object — kenapa `WorkHours`, bukan `Double`

```kotlin
@JvmInline
value class WorkHours(val hours: Double) {
    init { require(hours >= 0.0) { "WorkHours cannot be negative: $hours" } }
    fun costAt(hourlyRate: MoneyIdr): MoneyIdr = hourlyRate * hours
}
```

Yang penting justru **apa yang tidak ada**: tidak ada konstruktor atau accessor berbasis hari, di
seluruh package.

Alasannya konkret dan mahal. Hari kerja nyata di proyek ini **± 4 jam terfokus, bukan 8**. Begitu
satu lapisan menyimpan "3 hari" dan lapisan lain mengonversinya dengan asumsi 8 jam/hari, setiap
angka membengkak 2×. Karena `harga = jam × rate × margin`, kesalahan itu **ikut tertagih selama 24
bulan**. Tidak ada test yang menangkapnya, karena secara aritmetika semuanya benar.

Maka `lead_time_days` tetap ada — tapi ia **janji tanggal ke klien**, bukan ukuran effort, dan
keduanya tidak pernah saling dikonversi. Task 12 jam bisa punya lead time 3 minggu karena menunggu
jawaban buyer.

Konsekuensi yang gampang terlewat:

> `blended_hourly_rate_idr` harus dihitung dari **jam produktif nyata**, bukan 160 jam nominal.
> Biaya Rp 20 jt/bulan dengan 80 jam produktif = rate Rp 250.000. Memakai Rp 125.000 sambil
> mencatat jam jujur berarti **tiap penawaran cuma memulihkan separuh biaya** — dan kebocorannya
> tidak muncul di baris mana pun.

Begitu juga `MoneyIdr(val amount: Long)`: `Long`, bukan `Double`, karena angka ini dikalikan
persentase lalu ditagih bulanan bertahun-tahun. Drift floating point di sini bukan keanehan
pembulatan, tapi selisih di invoice.

### Blok B: Tabel — kolom yang dibekukan dan kolom yang dihitung database

```sql
estimate_variance_percent NUMERIC(8,2) GENERATED ALWAYS AS (
    CASE WHEN estimated_hours IS NULL OR estimated_hours = 0 OR actual_hours IS NULL
         THEN NULL
         ELSE ROUND((actual_hours - estimated_hours) / estimated_hours * 100, 2) END
) STORED
```

Varians dihitung **oleh PostgreSQL**, bukan aplikasi. Bukan karena lebih cepat, tapi karena ia jadi
mustahil lupa dihitung dan mustahil melenceng dari inputnya. Konsekuensinya di Exposed: kolomnya
dideklarasikan agar bisa dibaca, tapi **tidak pernah di-assign** saat insert/update — kalau
di-assign, PostgreSQL menolak seluruh statement.

Lalu aturan yang paling penting di seluruh skema:

```kotlin
fun withEstimate(...): ModuleBuildRecord {
    check(!isEstimated) { "Build ${id.value} is already estimated; estimates are frozen once written" }
    ...
}
```

**Estimasi ditulis sekali dan tidak pernah ditimpa.** Jarak antara estimasi dan aktual adalah
satu-satunya hal yang diajarkan sebuah build yang sudah selesai. Menimpanya menghasilkan tabel rapi
yang sudah melupakan seluruh pelajaran di dalamnya.

Hal yang sama berlaku untuk `feature_vector`: dibekukan pada `estimated_at`, **tidak dikoreksi**
walau ternyata salah. Salah-kiranya itulah datanya. Temuan belakangan masuk ke kolom terpisah,
`discovered_scope_delta`.

### Blok C: Estimator — yang dipinjam dari tetangga adalah *kecepatan*, bukan jam

Ini inti konseptual seluruh fitur.

```kotlin
val productivity: Double?  // pada ModuleBuildRecord
    get() {
        val hours = actualHours ?: return null
        if (!effortSource.isTrainingGrade) return null
        if (sizePoints.isZero) return null
        return hours.hours / sizePoints.value
    }
```

Naif: *"cari 3 build termirip, rata-ratakan jamnya"*. Itu salah, karena mengabaikan ukuran. Build
B-012 cuma 26 jam — tapi ia seperempat ukurannya. Merata-ratakan jam mentah akan menyeret estimasi
turun ke pekerjaan kecil yang kebetulan mirip.

Yang stabil lintas ukuran adalah **jam per poin**:

```
size_points  = Σ (count × bobot)
estimasi     = size_points_baru × median(productivity tetangga) × clarity_multiplier
```

Dengan contoh nyata dari QC (foto cacat + berita acara PDF): 60 poin × median 1.26 × 1.15 = **87 jam**.

Perhatikan juga `isTrainingGrade`. Hanya baris `LOGGED` yang boleh menyumbang rasio. Baris
`RECONSTRUCTED` (jam dari ingatan) dan `IMPORTED` (tanpa jam) ditolak — alasannya di Jebakan 3.

### Blok D: Dua nilai, bukan satu — p50 dan p90

```kotlin
val p50Hours = WorkHours(sizePoints.value * median * clarityMultiplier)
val p90Hours = WorkHours(sizePoints.value * p90 * clarityMultiplier)
```

Harga disusun dari **p90**, bukan median. Alasannya bukan kehati-hatian abstrak: harga bulanan
dikunci sekali untuk 24 bulan, dan **kita** yang menanggung kalau meleset. Menghargai dari median
berarti separuh proyek rugi secara sistematis — bukan kadang-kadang, tapi secara desain.

Percentile-nya diinterpolasi, bukan nearest-rank. Dengan 3 tetangga, nearest-rank p90 sama saja
dengan nilai maksimum — artinya seluruh margin keamanan jadi sandera satu build yang kebetulan
paling kacau.

### Blok E: Gerbang penolakan — "tidak tahu" harus mustahil disalahartikan

```kotlin
sealed interface EstimationOutcome {
    data class Estimated(...) : EstimationOutcome
    data class InsufficientEvidence(val reason: String, ...) : EstimationOutcome
}
```

Kalau tetangga terdekat similarity-nya di bawah 0.65, estimator **tidak mengembalikan jam sama
sekali**. Bukan angka rendah dengan peringatan — tidak ada angka.

Kenapa `sealed interface` dan bukan `WorkHours?` yang nullable: dengan sealed, "kami tidak tahu"
adalah *state yang wajib ditangani compiler*, bukan null yang bisa diselipkan lewat `?: 0.0` oleh
orang yang sedang buru-buru.

Estimasi yang diekstrapolasi dari tetangga berjarak 0.52 akan **salah dan terdengar yakin** — lalu
harga salahnya terkunci 24 bulan.

### Blok F: Rumus harga sebagai Strategy

```kotlin
monthly = (buildCost / expectedTenantCount / amortizationMonths) × (1 + margin)
        + (buildCost / expectedTenantCount) × maintenancePercent
        + monthlyInfraCost      // per tenant, TIDAK dibagi
```

Dua pembagian yang gampang salah, dan dua-duanya berbiaya nyata:

- **Maintenance dibagi jumlah tenant, infra tidak.** Memelihara modul itu satu usaha yang dipakai
  bersama; storage dan bandwidth yang dikonsumsinya terjadi per pabrik. Menyamakan keduanya berarti
  salah satunya pasti keliru.
- **Margin hanya berlaku pada porsi amortisasi.** Maintenance dan infra itu biaya pass-through;
  menambahkan margin di atasnya membuat harga tidak bisa dijelaskan ke klien.

`expectedTenantCount` bisa mengubah harga **3×** untuk build yang sama — Rp 1.084.000 kalau
eksklusif, Rp 361.000 kalau disebar ke 4 tenant. Karena itu ia kolom yang tersimpan, bukan
keputusan di kepala.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan | Alternatif | Kenapa yang ini |
|---|---|---|
| Embedding di kolom `JSONB` | pgvector | Dengan puluhan–ratusan baris, cosine di Kotlin makan mikrodetik. Memasang extension sekarang = mengurusnya di dev, CI, dan produksi demi masalah yang belum ada. Pindah ke `vector(N)` nanti cuma satu migrasi, datanya sudah ada. |
| `LexicalEmbeddingProvider` (overlap kata) | model embedding sungguhan | Tanpa API key, tanpa biaya per request, deterministik untuk test. Dan karena gerbang similarity mengubah tetangga buruk jadi *penolakan*, kelemahannya gagal dengan aman. Namanya sengaja jujur: ia tidak tahu "foto cacat" = "gambar reject". |
| Tabel platform-global tanpa RLS | semua tabel ber-RLS | Biaya build itu data kita, bukan data pabrik. Tidak adanya kolom `tenant_id` membuat angka cost **secara struktural** tidak bisa bocor ke tenant — bukan dijaga pengecekan izin yang bisa lupa ditulis. |
| Bobot sizing di tabel bervers | konstanta Kotlin | Bobotnya mulai dari tebakan dan harus dikalibrasi regresi nanti. Versi membuat `size_points` lama tetap terbaca dengan bobot yang menghasilkannya. |
| Codec JSON tulis tangan | kotlinx-serialization | Proyek ini tidak punya dependensi itu; menambahkannya menarik compiler plugin ke 5 target KMP demi satu package. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Memakai kolom hasil sebagai prediktor (target leakage)**
   - *Kenapa bahaya*: `rework_hours` dan `revision_round_count` akan tampak sebagai prediktor
     terbaik di data historis. Modelmu akan skor tinggi di evaluasi dan tidak berguna di produksi,
     karena saat permintaan baru datang angka-angka itu belum ada.
   - *Solusi elegan kita*: Kolom dipisah tiga blok fisik (fitur / estimasi / aktual), dan blok fitur
     dibekukan pada `estimated_at`.

2. **Jebakan 2: Menyimpan effort dalam hari**
   - *Kenapa bahaya*: 4 jam/hari vs asumsi 8 jam/hari = seluruh harga 2× lipat, tertagih 24 bulan.
   - *Solusi elegan kita*: Tidak ada jalur berbasis hari di mana pun — tidak di value object, tidak
     di DTO, tidak di skill `teaching`. Ada test khusus yang memverifikasi `{"days": 3}` ditolak.

3. **Jebakan 3: Merekonstruksi jam dari ingatan untuk task biasa**
   - *Kenapa bahaya*: Tebakan "kelihatannya besar, ya 3 hari" sebenarnya fungsi dari ukuran yang
     terlihat — dan `size_points` juga dihitung dari ukuran. Keduanya mengukur hal yang sama, jadi
     `productivity` keluar nyaris konstan **secara konstruksi**. Model lalu "belajar" bahwa jam
     sebanding dengan ukuran, yang persis hipotesis yang sedang diuji.
   - *Solusi elegan kita*: `effort_source` (`LOGGED` / `RECONSTRUCTED` / `IMPORTED`), dan hanya
     `LOGGED` yang boleh menyumbang rasio. Backfill hanya mengisi sisi fitur.

4. **Jebakan 4: Mengira `git log` menyimpan durasi**
   - *Kenapa bahaya*: Commit mencatat kapan kerjaan **mendarat**, bukan kapan dimulai. Di repo ini
     33 commit mendarat di 7 hari kerja — 12 di antaranya di satu hari yang sama.
   - *Solusi elegan kita*: `startedAt` ditulis saat build record **dibuat**, bukan saat ditutup.
     Masalahnya hilang ke depan; untuk masa lalu memang tidak bisa dipulihkan, dan skrip backfill
     mengakuinya alih-alih menebak.

5. **Jebakan 5: Rate per jam dibaca dari tabel master saat menghitung ulang**
   - *Kenapa bahaya*: Menaikkan rate tahun depan akan diam-diam mengubah berapa biaya build tahun
     lalu — padahal harga sudah terlanjur dikutip dari angka itu.
   - *Solusi elegan kita*: `hourly_rate_idr` disimpan **di setiap baris effort**, dan
     `total_build_cost_idr` didenormalisasi ke build record.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Tiga lapis, masing-masing membuktikan hal yang tidak bisa dibuktikan lapis lain.

**Unit test domain (`:core`, 71 test)** — murni, tanpa DB. Menguji aritmetika dan aturan:

```kotlin
@Test
fun a_small_lookalike_should_not_drag_the_estimate_down() {
    // B-012 cuma 26 jam, tapi seperempat ukurannya. Meminjam jam mentah akan menarik
    // estimasi ke 26; meminjam rasionya tidak.
    val estimated = assertIs<EstimationOutcome.Estimated>(outcome)
    assertTrue(estimated.p50Hours.hours > 60.0)
}
```

**Integration test Postgres (12 test)** — tiga hal yang **hanya** bisa dibuktikan di server nyata:
kolom generated (kalau Exposed sampai mencoba menulisnya, PostgreSQL menolak — tidak ada unit test
yang tahu), kolom JSONB (ditolak kalau driver mengirim `varchar`), dan isolasi RLS antar tenant.

**API test end-to-end (9 test)** — alur penuh lewat HTTP: permintaan tenant → estimasi → catat jam
→ tutup → harga → tagihan. Plus dua properti yang lebih penting dari happy path:

```kotlin
@Test
fun tenantFacingResponses_shouldNeverExposeOurCost() {
    listOf("actualHours", "hourlyRate", "totalBuildCost", "buildCostIdr", "marginPercent")
        .forEach { assertFalse(body.contains(it), "leaked '$it' to tenant") }
}
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Setelah ada 30 build dengan jam `LOGGED`, tulis regresi linear
      `actual_hours ~ counts` untuk mencari bobot `size_points` yang sebenarnya. Simpan hasilnya
      sebagai `weights_version = "v2"` — **jangan ubah v1**, dan pikirkan kenapa.
- [ ] **Tantangan 2**: `LexicalEmbeddingProvider` tidak tahu "foto cacat" ≈ "gambar reject".
      Ganti dengan model embedding sungguhan. Petunjuk: kamu hanya perlu mengimplementasikan
      `EmbeddingProvider`, dan `embedding_model` sudah tercatat di tiap baris — apa yang terjadi
      pada baris lama, dan kenapa itu justru yang kita mau?
- [ ] **Tantangan 3**: Bangun `tenant_module_subscriptions` dengan `locked_monthly_price_idr`.
      Jelaskan dulu, dalam satu paragraf, apa yang rusak kalau kita menagih tanpa tabel itu.

---

## 📌 Ringkasan File

| Lapisan | File |
|---|---|
| Migrasi | [V13__create_module_development_ledger.sql](../../server/src/main/resources/db/migration/V13__create_module_development_ledger.sql), [V14__seed_module_catalog_and_sizing_weights.sql](../../server/src/main/resources/db/migration/V14__seed_module_catalog_and_sizing_weights.sql) |
| Domain | [core/.../domain/moduledev/](../../core/src/commonMain/kotlin/com/eventverse/app/domain/moduledev/) |
| Estimator | [BuildEstimator.kt](../../core/src/commonMain/kotlin/com/eventverse/app/domain/moduledev/BuildEstimator.kt) |
| Harga | [PricingFormula.kt](../../core/src/commonMain/kotlin/com/eventverse/app/domain/moduledev/PricingFormula.kt) |
| Infrastruktur | [PostgresModuleDevRepositories.kt](../../server/src/main/kotlin/com/eventverse/app/infrastructure/PostgresModuleDevRepositories.kt), [ModuleDevTables.kt](../../server/src/main/kotlin/com/eventverse/app/infrastructure/tables/ModuleDevTables.kt) |
| API | [ModuleDevRoutes.kt](../../server/src/main/kotlin/com/eventverse/app/routes/ModuleDevRoutes.kt) |
| Backfill | [tools/backfill_module_ledger.py](../../tools/backfill_module_ledger.py) |
