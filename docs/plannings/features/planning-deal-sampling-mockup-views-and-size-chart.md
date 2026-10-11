# Planning: Mockup Tampak Depan & Belakang + Size Chart Deal (Full-Stack)

> **Modul**: CRM Sales — Deal Detail Dialog, Tab *Siklus Sampling*
> **Scope**: (1) Slot foto mockup menjadi 2 upload atas-bawah — *Tampak Depan* & *Tampak Belakang*;
> (2) Tabel *Size Chart* (baris = Bagian/POM dinamis, kolom = ukuran) di atas *Sampling Fee*,
> dengan tombol `+ Tambah Baris Ukuran`.
> **Prinsip**: ikuti §13 — 5 pilar end-to-end (DB → Domain → API → Client → Presentation).

---

## 0. Keputusan Desain (The Why)

| Keputusan | Alasan |
|---|---|
| Tambah kolom `mockup_front_key` / `mockup_back_key` eksplisit, JANGAN menyalahartikan urutan `mockupImageUrls` | Urutan list bukan kontrak — admin bisa upload belakang dulu. Slot *tampak* adalah data domain (menentukan pola depan vs belakang saat produksi), bukan urutan klik. `mockupImageUrls` dipertahankan hanya untuk data lama (read-only legacy). |
| Size chart deal = **jsonb array baris** di `sampling_orders`, bukan tabel anak baru | Bentuknya bebas (baris POM dinamis, nilai bisa kosong), tidak pernah di-join, selalu dibaca utuh per SPK. Precedent: `revision_history` (V39) sudah jsonb di tabel yang sama. Tabel `sampling_size_charts` yang lama milik lembar teknis sampling (bentuk `SizeMeasurement` per-ukuran) — tidak diubah, tidak dicampur. |
| Size chart ikut payload `PUT /sampling-orders/{id}` (autosave) yang sudah ada | UI-nya autosave 800ms seperti fee/catatan; endpoint & gerbang RBAC-nya sudah ada. Endpoint baru = permukaan serangan & duplikasi use case percuma (YAGNI). |
| Upload mockup tetap 1 endpoint, tambah query `view=front|back` | Kontrak upload (metadata via query, bytes raw body, cropper 1:1 di klien) tidak berubah; default `front` agar klien lama tidak rusak. |

---

## 1. Pilar 1 — Database & Persistence (`server/src/main/resources/db/migration/`)

**`V40__sampling_mockup_views_and_deal_size_chart.sql`**

```sql
-- 1a. Slot tampak eksplisit pada knit spec (key object storage / data URL, sama seperti mockup lama)
ALTER TABLE sampling_knit_specs
    ADD COLUMN mockup_front_key TEXT NULL,
    ADD COLUMN mockup_back_key  TEXT NULL;

COMMENT ON COLUMN sampling_knit_specs.mockup_front_key IS
    'Key object storage foto mockup TAMPAK DEPAN; NULL = belum diunggah';
COMMENT ON COLUMN sampling_knit_specs.mockup_back_key IS
    'Key object storage foto mockup TAMPAK BELAKANG; NULL = belum diunggah';

-- 1b. Size chart deal: baris POM dinamis (jsonb, idempotent terhadap data lama = kosong)
ALTER TABLE sampling_orders
    ADD COLUMN deal_size_chart JSONB NOT NULL DEFAULT '[]';

COMMENT ON COLUMN sampling_orders.deal_size_chart IS
    '[{"id":"pom-1","pomLabel":"Lingkar Dada","values":{"ALL SIZE":52,"S":48}}]';

-- RLS mengikuti induknya: kolom baru mewarisi kebijakan tabel — tidak ada policy baru.
```

**Exposed** — `server/.../infrastructure/tables/SamplingTables.kt`:

- `SamplingKnitSpecsTable`: `val mockupFrontKey = text("mockup_front_key").nullable()`,
  `val mockupBackKey = text("mockup_back_key").nullable()`.
- `SamplingOrdersTable`: `val dealSizeChart = jsonbText("deal_size_chart").default("[]")`.
- Mapper repository Postgres: baca-tulis dua kolom slot → `KnitSpec.mockupFrontKey/mockupBackKey`,
  dan `deal_size_chart` → `SamplingOrder.sizeChart` (parse via json, bukan string mentah).


---

## 2. Pilar 2 — Pure Domain (`core/`)

### 2a. Mockup views (`SamplingOrderValueObjects.kt`)

```kotlin
/** Slot foto mockup berdasarkan tampak — menentukan pola depan/belakang saat produksi. */
enum class MockupView(val displayName: String) {
    FRONT("Tampak Depan"),
    BACK("Tampak Belakang");
}
```

`KnitSpec` diperluas:

```kotlin
data class KnitSpec(
    /* ...field lama tetap... */
    val mockupImageUrls: List<String> = emptyList(),   // LEGACY: hanya dibaca, tidak ditulis lagi
    val mockupFrontKey: String? = null,
    val mockupBackKey: String? = null
)
```

### 2b. Behavior entity (`SamplingOrder.kt`)

```kotlin
/** Menempelkan foto mockup untuk satu tampak. Menimpa slot yang sama (replace, bukan append). */
fun attachMockup(storageKey: String, view: MockupView, updatedAt: Instant): SamplingOrder {
    val key = storageKey.trim().takeIf { it.isNotEmpty() } ?: return this
    return when (view) {
        MockupView.FRONT -> copy(knitSpec = knitSpec.copy(mockupFrontKey = key), updatedAt = updatedAt)
        MockupView.BACK  -> copy(knitSpec = knitSpec.copy(mockupBackKey = key),  updatedAt = updatedAt)
    }
}

/** Referensi mockup per tampak: slot baru → legacy terakhir sebagai fallback (depan saja). */
fun mockupReference(view: MockupView): String? = when (view) {
    MockupView.FRONT -> knitSpec.mockupFrontKey ?: knitSpec.mockupImageUrls.lastOrNull()
    MockupView.BACK  -> knitSpec.mockupBackKey
}
```

### 2c. Size chart value objects (file gabungan VO `SamplingOrderValueObjects.kt`)

```kotlin
/** Satu baris Size Chart deal: satu bagian/POM × nilai per kolom ukuran. */
data class SizeChartRow(
    val id: String,                    // identitas stabil baris (key Compose & edit tanpa kehilangan fokus)
    val pomLabel: String,
    /** Nilai per label kolom ukuran; null = sel kosong. */
    val values: Map<String, Double?> = emptyMap()
) {
    init {
        require(pomLabel.isNotBlank()) { "POM label cannot be blank" }
        require(pomLabel.length <= 60) { "POM label too long" }
    }
}

/** Kontrak kolom ukuran — tetap (fixed); baris yang dinamis, sesuai mockup admin. */
object DealSizeChart {
    val DEFAULT_SIZE_COLUMNS = listOf("ALL SIZE", "S", "M", "L", "XL", "XXL", "XXXL")
    val DEFAULT_ROWS = listOf("Lingkar Dada", "Panjang Baju")

    fun emptyChart(): List<SizeChartRow> = DEFAULT_ROWS.mapIndexed { i, pom ->
        SizeChartRow(id = "pom-${i + 1}", pomLabel = pom, values = emptyMap())
    }
}

---

## 3. Pilar 3 — Backend API & Routing (`server/.../routes/DealRoutes.kt`)

| Endpoint | Perubahan |
|---|---|
| `POST /api/tenant/deals/{id}/sampling-orders/{samplingId}/mockup` | Query baru `view` (`front` default / `back`), divalidasi terhadap `MockView.entries`. Command dikirim dengan `view`. Response tetap `SamplingOrderCodec` + presign **kedua** slot. |
| `PUT /api/tenant/deals/{id}/sampling-orders/{samplingId}` | Body + `sizeChart: [{"id","pomLabel","values":{…}}]` (opsional; `null`/absen = tidak diubah demi kompatibilitas autosave lama). |
| `POST /api/tenant/deals/{id}/sampling-orders` | Idem: `sizeChart` opsional untuk desain baru yang langsung punya chart. |

Helper presign `withResolvedMockups` diperluas: resolve `mockupFrontKey` & `mockupBackKey`
dengan kontrak yang sama (key mentah → presigned URL segar; `data:`/`http` dilewati;
gagal resolve → `null`). RBAC tidak berubah: tetap `requireCrmAccess(OPERATE)` +
`requireReachableOwner` — ini operasi CRUD lembar sampling yang sama.

Validasi server: `pomLabel` ≤ 60 char, ≤ 40 baris, nilai numerik parse-able (atau `null`),
kolom ukuran divalidasi terhadap `DealSizeChart.DEFAULT_SIZE_COLUMNS` (kolom tak dikenal
ditolak — jangan biarkan jsonb jadi tempat sampah).

---

## 4. Pilar 4 — Client–Server Integration (`app/shared/.../infrastructure/api/`)

- **`SamplingOrderCodec`** (`core/.../shared/sampling/`): encode/decode `mockupFrontKey`,
  `mockupBackKey`, `sizeChart` (list `SizeChartRow`). Dekode toleran: field hilang =
  null/kosong (data lama V1–V39 tetap terbaca).
- **`DealRemoteDataSource`**: `UpdateSamplingOrderFromDealRequest` + field
  `sizeChart: List<SizeChartRow>?`; `uploadSamplingMockup(...)` + param `view: MockupView`.
- **`DealApiClient`**: kirim `"sizeChart"` di JSON PUT, dan
  `parameter("view", view.name.lowercase())` di POST mockup.
- **`DealViewModel` / `DealUiEvent`**:
  ```kotlin
  data class UploadSamplingMockup(
      val samplingId: String, val view: MockupView,
      val fileName: String, val mimeType: String, val bytes: ByteArray
  ) : DealUiEvent
  ```
  `SaveSamplingOrder` + `sizeChart: List<SizeChartRow>? = null`.
  `DealUiState` tidak berubah bentuk — `SamplingOrder` baru otomatis membawa chart & slot.

---

## 5. Pilar 5 — Shared Presentation (`app/shared/.../presentation/deal/components/`)

### 5a. Dua slot mockup atas–bawah (menggantikan slot tunggal)

Di kartu ter-expand, kolom kiri menjadi:

```
┌──────────────────────────┐
│  Tampak Depan   [Foto]   │  ← DesignMockupSlot (label + bitmap depan)
├──────────────────────────┤
│  Tampak Belakang [Foto]  │  ← DesignMockupSlot (label + bitmap belakang)
└──────────────────────────┘
```

- `DesignMockupSlot` dapat `label: String` — komponen tetap buta domain
  (String + ImageBitmap + lambda), dipanggil dua kali dengan
  `order.mockupReference(MockupView.FRONT)` / `BACK`.
- State cropper jadi `Map<MockupView, PickedLocalFile?>`; cropper 1:1 yang sama,
  lalu emit `UploadSamplingMockup(view = ...)`.
- Lebar slot ditinjau (150dp → ~130dp) agar dua slot + label muat di setengah lebar

---

## 6. Urutan Pengerjaan (Order of Operations)

1. **Domain (core/)**: `MockupView`, `KnitSpec` 2 slot, `SizeChartRow`, behavior entity,
   perluasan command + unit test murni → `jvmTest core`.
2. **Codec (core/shared/sampling)**: encode/decode 3 field baru + test round-trip.
3. **Migration V40 + Exposed tables + mapper repository Postgres.**
4. **Server routes**: query `view`, body `sizeChart`, helper presign per slot.
   Uji manual via curl (upload front/back, PUT size chart).
5. **Client data source**: `DealRemoteDataSource` + `DealApiClient` + event/ViewModel.
6. **Presentation**: `DesignMockupSlot` dual-slot → `SizeChartTable` → autosave wiring.
7. **Verifikasi**: kompilasi 5 target + uji mata di `http://localhost:3000/crm-sales/deals`
   (upload depan/belakang, tambah baris ukuran, refresh → data persist, mode dev tanpa
   MinIO → foto inline tetap tampil).

## 7. Risiko & Jebakan

- **Data lama tanpa slot**: fallback baca `mockupImageUrls.lastOrNull()` untuk FRONT —
  jangan sampai kartu desain lama "kehilangan" fotonya setelah deploy.
- **Autosave lama menghapus chart baru**: `sizeChart` absen di request HARUS berarti
  "tidak diubah", bukan "kosongkan" — klien lama yang belum tahu field tidak boleh merusak data.
- **Key Compose baris chart**: pakai `SizeChartRow.id` yang stabil, bukan index —
  kalau index, mengetik di baris ke-3 setelah hapus baris ke-1 memindahkan state field.
- **Inline base64 (dev tanpa MinIO)**: dua foto × 2 MB bisa membengkakkan jsonb knit spec —
  batas `MAX_INLINE_MOCKUP_BYTES` dipertahankan; produksi wajib S3/MinIO.
- **Presign dua slot**: resolve `mockupFrontKey`/`mockupBackKey` di SEMUA titik yang memanggil
  `withResolvedMockups` (GET list, GET detail, POST mockup) — slot bolong di satu endpoint =
  foto hilang misterius di UI.
- **Grid 2 kolom tetap**: kartu ter-expand tetap setengah lebar (perubahan sebelumnya);
  chart 8 kolom harus dicek di width sempit — kolom ALL SIZE boleh disingkat "ALL" via
  header compact, isi sel tetap utuh.

## 8. Definition of Done

- [ ] Unit test domain lulus (attach mockup per view, fallback legacy, validasi size chart).
- [ ] Codec round-trip test (encode → decode identik; data lama tanpa field baru terbaca).
- [ ] Kompilasi 5 target hijau:
      `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs
      :app:shared:compileKotlinJs :app:shared:assembleAndroidMain :app:shared:jvmTest`.
- [ ] Manual E2E: upload depan & belakang → refresh → kedua foto tampil; tambah/hapus baris
      ukuran → refresh → chart persist; fee tetap autosave di bawah chart; migration V40
      jalan bersih di Postgres lokal.
- [ ] Dokumen teaching dibuat di `docs/teaching/` setelah implementasi selesai.

  grid 2 kolom (Kontrak 12 — densitas ditinjau, bukan diasumsikan).

### 5b. `SizeChartTable` — disisipkan SEBELUM field *Sampling Fee*

```
Size Chart
┌──────────────┬──────┬───┬───┬───┬────┬─────┬──────┐
│ Bagian / POM │ALL SZ│ S │ M │ L │ XL │ XXL │ XXXL │
├──────────────┼──────┼───┼───┼───┼────┼─────┼──────┤
│ Lingkar Dada │  52  │48 │50 │52 │ 54 │ 56  │  58  │
│ Panjang Baju │  70  │66 │68 │70 │ 72 │ 74  │  76  │
└──────────────┴──────┴───┴───┴───┴────┴─────┴──────┘
        [ + Tambah Baris Ukuran ]
```

- Komponen baru `SizeChartTable.kt` di package yang sama (colocation per fitur):
  - Header kolom dari `DealSizeChart.DEFAULT_SIZE_COLUMNS` (`ClayTag`, `ClayShapes.Chip`).
  - Baris = `SizeChartRow`: kolom pertama `ClayTextField` (nama POM), sisanya
    `ClayTextField` numeric; `Modifier.weight(1f)` pada kolom nilai — tanpa literal lebar.
  - Tombol `+ Tambah Baris Ukuran` (`ClayButton` Secondary + `IconPlus`) → append
    `SizeChartRow(id = baru, pomLabel = "")`, fokus ke kolom nama.
  - Hapus baris: `ClayActionSurface` kecil dengan ikon vektor `ClayIcons` (bukan emoji).
  - Autosave: state chart di-hoist ke `SamplingDesignCard`, debounce 800ms + guard
    `detailTouched` yang sama dengan fee, dikirim via `SaveSamplingOrder(sizeChart = rows)`.
- Nol literal `Color(0xFF…)` — semua via `WeMadeColors` / katalog Clay.

```

`SamplingOrder`:

```kotlin
val sizeChart: List<SizeChartRow> = emptyList()   // kosong → UI render DealSizeChart.emptyChart()

fun updateSizeChart(rows: List<SizeChartRow>, updatedAt: Instant): SamplingOrder =
    copy(sizeChart = rows, updatedAt = updatedAt)
```

> **Bukan** `SizeMeasurement` — itu tetap milik lembar teknis sampling (finished/raw knit)
> dan TIDAK disentuh agar modul Sampling tidak pecah.

### 2d. Use case (perluasan command, bukan use case baru)

- `AttachSamplingMockupCommand` + `val view: MockupView = MockupView.FRONT` →
  `order.attachMockup(storageKey, view, now)`.
- Command simpan lembar sampling dari deal (`SaveSamplingOrderFromDeal…`) + field
  `sizeChart: List<SizeChartRow>?` → jika non-null, `order.updateSizeChart(sizeChart, now)`.

Unit test domain (murni, tanpa framework):
- `attach mockup when view is back should not touch front slot`
- `mockupReference front when no slot but legacy exists should return latest legacy`
- `update size chart when row label blank should throw exception`
- `update size chart should preserve row order`
