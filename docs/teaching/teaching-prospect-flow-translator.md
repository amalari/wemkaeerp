# 🎓 Modul Pembelajaran: Prospect Flow Translator — Narasi Pabrik Jadi Rentang Harga

> **Level Target**: Mid Developer
> **Topik Utama**: Validasi keluaran model, Sealed Hierarchy untuk "tidak tahu", Composable Pipeline
> Assembly, Pembulatan Harga, DDD lintas-package di Kotlin Multiplatform
> **Prasyarat**: Kotlin, Exposed, Ktor, dan
> [teaching-module-development-ledger](teaching-module-development-ledger.md) — lapisan ini berdiri di atasnya.
> **Referensi Task**: Penerjemah alur calon klien (WeMade ERP)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Calon klien menulis di form:

> *"Kami makloon jaket. Kain dari buyer, kami cuma jahit. Ada sablon di dada. QC pakai AQL 2.5."*

Yang harus keluar: rangkaian modul, mana yang sudah kita punya, mana yang harus dibangun, dan
perkiraan harga bulanan.

Ledger sudah bisa menjawab *"berapa lama membangun satu modul"*. Lapisan ini menjawab pertanyaan yang
lebih awal: **modul apa saja yang sebenarnya dia butuhkan?**

### Kenapa ini bukan "tinggal panggil LLM"

Model akan mengembalikan sesuatu yang *terlihat* benar untuk narasi apa pun. Ia akan mengarang kode
archetype yang tidak ada, mengaku menemukan kebutuhan yang tidak pernah disebut klien, dan menebak
model bisnis dari satu kata. Pekerjaan sesungguhnya di lapisan ini **bukan memanggil model, tapi
memeriksa jawabannya** — dan memastikan yang tidak diketahui tetap terlihat sebagai tidak diketahui.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

**Step 0 — Putuskan apa yang terjadi saat kita tidak tahu.**
Sebelum apa pun. Di lapisan ini ada tiga "tidak tahu" yang berbeda: model bisnis tak terdeteksi,
archetype tak dikenal, dan gap yang tidak bisa diestimasi. Ketiganya harus punya representasi yang
**tidak bisa disalahartikan sebagai jawaban**.

**Step 1 — Migrasi** `V15`: leads, translations, price estimates.
**Step 2 — Value object & entity** di `:core`, termasuk pembulatan.
**Step 3 — Validator** — apa yang bisa salah dari sebuah terjemahan.
**Step 4 — Use case**: terjemahkan → petakan coverage → hargai.
**Step 5 — Exposed + repository.**
**Step 6 — Routes**, publik dan admin sebagai dua bentuk respons berbeda.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Prospek bukan tenant, tapi butuh `TenantId`

```kotlin
val placeholderTenantId: TenantId get() = TenantId("prospect-${id.value}".take(64))
```

`CustomTenantPipeline` — mesin perakit alur yang sudah ada — mensyaratkan `TenantId`. Prospek belum
punya. Godaannya: bikin baris di tabel `tenants` dengan status TRIAL.

Jangan. Prospek tidak punya slug, user, pipeline, atau baris untuk dijadikan sasaran RLS, dan
sebagian besar tidak pernah jadi pelanggan. Menaruhnya di `tenants` berarti **setiap query tenant di
seluruh sistem** — entitlement, org chart, billing — ikut memuat pabrik yang tidak ada.

`TenantId` hanya memvalidasi panjang (3–64), tanpa regex, jadi placeholder lolos. Aturannya keras dan
ditulis di KDoc: **pipeline prospek tidak pernah masuk `TenantPipelineRepository`** — ia disimpan
sebagai JSONB di tabelnya sendiri. Ada test integrasi yang menghitung baris `tenants` sebelum dan
sesudah seluruh alur.

### Blok B: Jebakan `fromCode` yang jatuh ke DEFAULT

```kotlin
// ❌ GarmentBusinessPreset.fromCode(code)  — mengembalikan DEFAULT, bukan null
val match = GarmentBusinessPreset.entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
```

Helper bawaan `fromCode` **jatuh ke `FOB_FULL_PACKAGE`** kalau kodenya tak dikenal. Kalau dipakai di
sini, setiap pabrik yang narasinya tidak jelas akan diam-diam tercatat sebagai eksportir full-package
— lalu dikutip harga untuk membeli kain yang tidak pernah dia beli.

Bandingkan dengan `ModuleArchetype.fromCode`, yang **nullable** dan aman. Dua helper dengan nama sama
di codebase yang sama, perilaku berbeda. Pelajarannya: **baca implementasi helper sebelum memakainya
untuk memvalidasi input yang tidak dipercaya.**

### Blok C: Orang tidak bercerita berurutan

```kotlin
val ordered = requirements.sortedBy { it.archetype.ordinal }
```

Narasi nyata: *"QC-nya pakai AQL... oh iya, kainnya dari buyer."* QC disebut sebelum bahan baku.
Enum `ModuleArchetype` kebetulan dideklarasikan dalam urutan produksi, jadi satu `sortedBy` mengubah
cerita yang melompat-lompat jadi urutan yang bisa dibaca peninjau dari atas ke bawah.

### Blok D: "Tidak tahu" sebagai tipe, bukan null

```kotlin
sealed interface CoverageDecision {
    data class CoveredByCatalog(...) : CoverageDecision
    data class Gap(...) : CoverageDecision
}
```

Alternatifnya `ModuleCatalogEntry?` yang null berarti gap. Dengan sealed, compiler memaksa penanganan
— dan "harus dibangun" membawa biaya, jadi ia tidak boleh bisa dilewatkan diam-diam oleh siapa pun
yang lupa cek null.

Pola yang sama dipakai `EstimationOutcome` di ledger. Konsisten bukan karena rapi, tapi karena kedua
tempat itu sama-sama membawa uang.

### Blok E: Satu gap gagal → tidak ada angka sama sekali

```kotlin
val isPublishable: Boolean
    get() = unpriceableGapCount == 0 && gapLowMonthly != null && gapHighMonthly != null
```

Godaan terbesar di seluruh fitur: tampilkan saja total dari gap yang berhasil dihitung, sebutkan
sisanya "menyusul". Itu **dijamin understate** — kita menjumlahkan sebagian pekerjaan lalu
menyajikannya sebagai keseluruhan, dan klien membaca angka itu sebagai harga.

Konsekuensi yang harus diterima: dengan ledger yang masih kosong, hampir setiap prospek jatuh ke
jalur ini. Fitur ini lahir sebagai **form penangkap lead yang pintar** — tetap menghasilkan pipeline,
gap, dan pertanyaan terbuka — dan rentangnya muncul sendiri seiring ledger terisi. Itu degradasi yang
benar, bukan kegagalan.

### Blok F: Pembulatan yang mengikuti besaran

```kotlin
fun stepFor(amount: MoneyIdr): Long = when {
    amount.amount >= 10_000_000L -> 1_000_000L
    amount.amount >= 2_000_000L  -> 500_000L
    amount.amount >= 500_000L    -> 100_000L
    else                          -> 50_000L
}
```

Dua aturan, dan yang kedua ditemukan lewat test yang gagal:

1. **Selalu membulat keluar** — `floor` untuk batas bawah, `ceil` untuk batas atas. Membulat ke
   terdekat bisa menaruh plafon tampilan **di bawah** harga p90 sebenarnya, lalu angka final yang
   kita kirim melebihi rentang yang sudah dilihat klien.
2. **Langkahnya ikut besaran.** Dengan langkah tetap Rp 500.000, langganan Rp 300.000 membulat ke
   bawah jadi **nol** — rentang "Rp 0 – 500.000" yang terbaca sebagai gratis. Presisi harus mengikuti
   angkanya, seperti cara orang mengutip harga.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan | Alternatif | Kenapa yang ini |
|---|---|---|
| `KeywordFlowTranslator` | model LLM sungguhan | Tanpa API key, deterministik, dan **gagalnya terlihat**: kebutuhan yang terlewat jadi slot kosong yang dinotice peninjau, bukan jawaban percaya diri yang salah. Endpoint publik tak terautentikasi juga tidak bisa jadi tagihan orang lain. |
| Rentang dari p50/p90 ledger | mekanisme ketidakpastian baru | Ketidakpastiannya sudah diukur di tempat yang benar. Menambah lapisan tebakan di atasnya cuma menggandakan asumsi. |
| `expectedTenantCount` default 1 | asumsi optimis | Rentang harus menyatakan ketidakpastian **jam saja**. Mencampur tebakan reuse membuatnya tak bisa ditafsirkan — dan harga yang turun setelah ditinjau jauh lebih baik daripada yang naik. |
| `BuildEstimator` langsung | `EstimateModuleBuildUseCase` | Use case itu **menyimpan** build record, yang butuh catalog entry. Setiap prospek yang tidak jadi akan meninggalkan modul `PLANNED` dan build spekulatif untuk pekerjaan yang tidak pernah disetujui. |
| Dua fungsi DTO terpisah | satu fungsi + flag `includeCost` | Boolean yang menentukan apakah biaya ikut terkirim cuma berjarak satu argumen salah dari membocorkan rate kita ke endpoint publik. Dua fungsi tidak bisa salah begitu. |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

1. **Jebakan 1: Validasi kompatibilitas port — ditolak dua kali**
   - *Kenapa bahaya*: Predikat yang tampak paling wajar di seluruh fitur ini (`output hulu ==
     input hilir`) melaporkan **katalog kita sendiri** sebagai rusak. Di level spesifikasi modul,
     tidak ada yang memproduksi `CuttingOrderWithFabric` maupun `FinishedGarmentUnit`. Naik ke level
     archetype pun tidak menolong: tiga dari empat sambungan pertama tidak cocok, karena slot hulu
     adalah tahap **perencanaan** yang keluarannya *diturunkan menjadi* dokumen berikutnya, bukan
     dialirkan. Lebih fatal lagi, arsitektur ini memang membolehkan node di-bypass — pabrik CMT yang
     menerima potongan jadi punya `SEWING` tanpa `CUTTING`, dan validator ketat akan menolak pabrik
     yang sepenuhnya normal.
   - *Solusi elegan kita*: `ProposedFlowValidator` memeriksa apa yang benar-benar bisa salah dari
     sebuah **terjemahan** — slot ganda, tidak ada penerimaan pesanan, kebutuhan tanpa kutipan.
     `payloadMatches()` tetap ada tapi ditandai informasional saja. Ada test yang **mengunci temuan
     ini** (`the_declared_archetype_chain_does_not_line_up_end_to_end`), supaya orang berikutnya
     tidak mengulang jalan buntu yang sama.

2. **Jebakan 2: `sourceQuote` dianggap hiasan**
   - *Kenapa bahaya*: Tanpa itu, tidak ada cara membedakan pembacaan sungguhan dari karangan.
   - *Solusi elegan kita*: Setiap kebutuhan menyimpan potongan kalimat yang memicunya. Model yang
     mengarang tidak bisa mengutip kalimat yang mendukungnya, jadi kutipan kosong adalah sinyal
     halusinasi termurah yang tersedia — dan ia otomatis menandai terjemahan untuk ditinjau.

3. **Jebakan 3: Data hilang antar-lapisan tanpa ada yang error**
   - *Kenapa bahaya*: `RawCapabilityRequirement.features` sempat tidak dibawa ke
     `CapabilityRequirement`. Tidak ada exception, tidak ada warning — setiap gap cuma *kebetulan*
     berskor nol poin dan estimator menolak semuanya. Gejalanya terlihat persis seperti "ledger masih
     kosong", yang memang benar saat itu.
   - *Solusi elegan kita*: Test yang menyediakan riwayat lengkap lalu menuntut rentang **muncul**.
     Test yang hanya memeriksa "tanpa riwayat → ditolak" akan lulus dengan bug ini masih di tempat.

4. **Jebakan 4: Mencocokkan slot wildcard lewat archetype**
   - *Kenapa bahaya*: "Sablon manual" dan "laundry kimia" sama-sama jatuh di `CUSTOM_EXTENSION`.
     Mencocokkan lewat archetype akan menyatakan kebutuhan laundry sudah tercakup oleh plugin sablon.
   - *Solusi elegan kita*: Slot wildcard hanya tercakup kalau penerjemah menyebut `moduleId` spesifik
     yang benar-benar ada di katalog.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

**Unit test domain (43 test)** — murni. Menguji pembulatan, gerbang publikasi, dan validator:

```kotlin
@Test
fun a_normal_cmt_flow_should_produce_no_warnings() {
    // Jahit tanpa potong: bengkel yang menerima panel jadi. Validasi port ketat akan
    // menolak pabrik yang sepenuhnya biasa ini.
}
```

**Integration test Postgres (8 test)** — yang hanya bisa dibuktikan di server nyata: kolom JSONB,
constraint `chk_prospect_range_ordered`, dan **jumlah baris `tenants` tidak berubah** sepanjang alur.

**API test end-to-end (13 test)** — lewat HTTP, termasuk tiga properti yang lebih penting dari happy
path: narasi CMT tidak jadi FOB, ledger kosong menghasilkan `"available": false` (bukan Rp 0), dan
respons publik dipindai untuk `hourlyRate`, `buildCost`, `marginPercent`, `sizePoints`.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: `KeywordFlowTranslator` tidak paham negasi — "kami **tidak** potong sendiri"
      tetap memicu slot CUTTING. Perbaiki, lalu jelaskan kenapa memperbaikinya dengan daftar kata
      negasi akan rapuh, dan apa yang sebenarnya dibutuhkan.
- [ ] **Tantangan 2**: Ganti dengan LLM sungguhan. Sebelum menulis kode, tulis dulu satu paragraf:
      apa yang harus ada di endpoint publik itu **sebelum** panggilan berbayar pertama dikirim?
- [ ] **Tantangan 3**: Alur konversi prospek → tenant. Kolom `converted_tenant_id` sudah ada.
      Pertanyaan sulitnya: pipeline usulan yang tersimpan sebagai JSON harus jadi apa, dan harga yang
      pernah dikutip harus diapakan?

---

## 📌 Ringkasan File

| Lapisan | File |
|---|---|
| Migrasi | [V15__create_prospect_flow_tables.sql](../../server/src/main/resources/db/migration/V15__create_prospect_flow_tables.sql) |
| Domain | [core/.../domain/prospect/](../../core/src/commonMain/kotlin/com/eventverse/app/domain/prospect/) |
| Validator | [ProposedFlowValidator.kt](../../core/src/commonMain/kotlin/com/eventverse/app/domain/prospect/ProposedFlowValidator.kt) |
| Pembulatan | [ProspectPriceRange.kt](../../core/src/commonMain/kotlin/com/eventverse/app/domain/prospect/ProspectPriceRange.kt) |
| Penerjemah | [KeywordFlowTranslator.kt](../../server/src/main/kotlin/com/eventverse/app/infrastructure/KeywordFlowTranslator.kt) |
| API | [ProspectRoutes.kt](../../server/src/main/kotlin/com/eventverse/app/routes/ProspectRoutes.kt) · [ProspectDto.kt](../../server/src/main/kotlin/com/eventverse/app/routes/dto/ProspectDto.kt) |
