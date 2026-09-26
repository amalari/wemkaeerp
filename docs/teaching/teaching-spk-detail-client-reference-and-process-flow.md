# Teaching: Redesain Dialog Detail SPK — Referensi Klien & Alur Proses

> **Level Target**: Junior Developer
> **Referensi Task**: `docs/plannings/planning-spk-detail-client-reference-and-process-flow.md`
> **Modul**: Order Sampling (`app/shared/.../presentation/sampling/`)

## 1. Start dari Mana? (Order of Operations)

Kalau kamu diminta membangun ulang dialog ini dari nol, kerjakan dengan urutan ini:

1. **Cek dulu domainnya** (`core/domain/sampling/SamplingSizeMatrix.kt`, `SamplingOrder.kt`).
   Semua data yang dibutuhkan UI (mockup, size matrix, qty, deadline) sudah ada sebagai field
   domain — UI tidak perlu data baru, hanya merender ulang. Kalau ada data yang ternyata
   belum ada di domain, berhenti dan perbaiki domain dulu (Pilar 1–3), jangan menambalnya
   di Composable.
2. **Bangun komponen kartu referensi** (`ClientSamplingReferenceCard.kt`) secara terisolasi:
   header tag → slot mockup → tabel POM → catatan client. Satu section, satu fungsi privat.
3. **Rakit ulang dialog** (`SamplingSpkDetailDialog.kt`) sebagai *shell*: header, scroll
   content, footer aksi. Shell tidak merender detail; dia hanya menyusun komponen.
4. **Sambungkan gerbang transisi** di `SamplingWorkspaceScreen.kt`: tombol
   "Alur Siap -> Mulai CAM" bukan lagi submit form — dia membuka `StageAdvanceDialog`.
5. **Verifikasi**: kompilasi multi-target + lihat UI-nya dengan mata.

## 2. Bedah Kode Blok per Blok

### Blok A: Filter referensi mockup sebelum dimuat

```kotlin
private fun loadableMockupRef(key: String?): String? =
    key?.takeIf { it.startsWith("http") || it.startsWith("data:") }
```

Mental model: domain menyimpan **storage key** (mis. `mockups/depan_v0.jpg`), bukan URL.
Key mentah tidak bisa di-GET oleh browser — server yang mengubahnya menjadi presigned URL
saat membaca data. Client hanya boleh memuat `https://…` (presigned) atau `data:image/…`
(inline base64 fallback). Tanpa filter ini, `loadMockupBitmap` akan mencoba HTTP GET ke
`mockups/depan_v0.jpg` — gagal, tapi membuang network round-trip percuma.

### Blok B: Zoom preview lewat state minimal

```kotlin
var zoomTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
```

Dua slot mockup butuh satu dialog zoom yang sama. Alih-alih dua `var showFrontZoom` /
`showBackZoom`, kita simpan **pasangan (label, referensi)** dari slot yang diklik.
`zoomTarget?.let { … MockupZoomPreviewDialog(…) }` — null berarti dialog tertutup.
Ini pola "state sebagai sumber kebenaran": UI tidak pernah punya status sendiri.

### Blok C: Tabel POM yang kolomnya adaptif

```kotlin
private fun usedSizeColumns(matrix: List<SizeChartRow>): List<String> =
    STANDARD_SAMPLING_SIZE_COLUMNS
        .filter { col -> matrix.any { it.values[col]?.isNotBlank() == true } }
        .ifEmpty { STANDARD_SAMPLING_SIZE_COLUMNS }
```

Deal ALL SIZE hanya mengisi kolom "ALL SIZE" — menampilkan 7 kolom standar berarti 6 kolom
berisi "-" yang makan lebar. Fungsi murni ini mengembalikan hanya kolom yang benar-benar
dipakai, dengan fallback ke daftar lengkap agar tabel tidak pernah kosong.

### Blok D: Pembeda state lewat warna, bukan bentuk (Kontrak 8)

```kotlin
.then(if (isQty) Modifier.background(WeMadeColors.Primary.copy(alpha = 0.08f)) else Modifier)
```

Baris "Jumlah Sampel (pcs)" disorot dengan wash biru + teks `Primary`. Ketebalan border dan
ukuran cell **tidak berubah** antar baris. Mengganti ketebalan untuk menandai state adalah
anti-pattern yang tercatat di design-system-rules.

### Blok E: Pergeseran tanggung jawab tombol "Alur Siap -> Mulai CAM"

Sebelumnya dialog ini memuat form Program CAM lengkap dan submit-nya langsung memanggil
`ConfirmStageAdvance`. Sekarang:

```kotlin
onStartCam = {
    viewModel.onEvent(SamplingUiEvent.CloseSpkDetailDialog)
    viewModel.onEvent(SamplingUiEvent.OpenStageAdvanceDialog(target, SamplingPipelineStage.CAM_PROGRAMMING))
}
```

Mental model: dialog detail = **meja referensi**; `StageAdvanceDialog` = **gerbang transisi
tahap**. Semua transisi yang menuntut lembar kerja (`requiresStageWorksheet()`) kini lewat
pintu yang sama — satu sumber kebenaran untuk validasi dan audit aktor.

## 3. Technology & Approach (The Why)

- **`rememberMockupBitmap` dipakai ulang dari package `deal`**, bukan ditulis ulang. Aturan
  Tiga Kali: loader data-URI/HTTP sudah ada dan teruji; menduplikasinya berarti dua tempat
  untuk memperbaiki bug yang sama.
- **`clayFlat` untuk slot mockup & tabel**, `ClayCard` untuk dialog zoom — bukan
  `Modifier.shadow()` atau `Card` Material. Hard shadow tanpa blur adalah identitas visual;
  `shadow()` menghasilkan blur yang berlawanan dengan bahasa clay.
- **Nol literal `Color(0xFF…)`** — semua warna dari `WeMadeColors.*` + turunan `.copy(alpha=)`.
- **Ikon dari `ClayIcons.kt`** (`IconPackage`, `IconRuler`, `IconCalendarGrid`, `IconImage`,
  `IconClose`) — emoji/Unicode glyph merender tofu (`▯`) di Wasm.
- **Tombol memakai ASCII `->`** alih-alih `→` Unicode demi keamanan rendering lintas 5 target.

## 4. Jebakan Pemula (Common Pitfalls)

1. **Menaruh form CAM kembali ke dialog referensi.** God dialog = tanggung jawab ganda =
   file membengkak lagi. Input teknis milik gerbang tahap.
2. **Memuat storage key mentah sebagai URL** (Blok A) — broken image yang tidak paham
   kenapa, karena error-nya hanya `null` dari `loadMockupBitmap`.
3. **`weight(1f)` di dalam `horizontalScroll`** — constraint infinite membuat weight error.
   Tabel di sini sengaja memakai lebar cell tetap (46dp/104dp) karena container-nya
   `weight(1f)` di Row induk, bukan scrollable.
4. **Lupa `weight(1f, fill = false)` + `maxLines` + `overflow`** pada teks yang berdampingan
   dengan badge (Kontrak 13) — teks panjang memecah satu huruf per baris.
5. **Mengira kompilasi JVM cukup.** WasmJS/JS punya aturan font & Skia sendiri; kompile
   kelima target, lalu lihat UI-nya dengan mata.

## 5. Verifikasi & Tantangan Mandiri

Sudah dijalankan dan hijau:

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:jvmTest :server:compileKotlin
```

> Catatan: `:app:shared:assembleAndroidMain` saat ini gagal **pra-ada** di `MockupCropDialog.kt`
> (API Skia JVM-only di commonMain) — gagal juga sebelum perubahan ini. Bukan utang task ini.

Audit design system (nol hit):

```bash
grep -n "Color(0xFF\|RoundedCornerShape\|Modifier.shadow()" <file yang disentuh>
```

Tantangan mandiri:
1. Tambahkan tag `Rev N` di header kartu referensi jika `order.revisionCount > 0`.
2. Buat slot mockup menampilkan indikator loading beneran (bukan teks "Memuat…").
3. Bonus arsitektur: ekstrak `SizeChartTable` ke komponen bersama jika ada ≥3 pemakai
   (Aturan Tiga Kali) — cek dulu `ConfirmSpkDialog` dan `DealDetailDialog` sebagai kandidat.

## 6. Lampiran: Penyelesaian Pilar 3 (Presign URL di SamplingRoutes)

Audit lanjutan menemukan satu gap: endpoint `GET /api/tenant/sampling/orders` (list) dan
`GET /api/tenant/sampling/orders/{id}` (detail) masih mengembalikan **storage key mentah** —
UI baru kita memfilter `http`/`data:` sehingga key mentah hanya berujung placeholder
"Belum ada foto", bukan gambar. Perbaikannya:

1. **Ekstraksi helper bersama** — `withResolvedMockups` dipindah dari `DealRoutes.kt`
   (di sana `private`) ke `routes/SamplingMockupResolution.kt` sebagai `internal`.
   Efek samping positif: `DealRoutes.kt` menyusut 677 -> 647 baris (cicilan Ratchet).
2. **Dependensi disuntik** — `samplingRoutes(..., poFileStorage: PoFileStorage?)`, diteruskan
   dari `operationalModuleRoutes` (ServerRouteWiring.kt) hingga `Application.kt`.
3. **Penerapan** — kedua endpoint GET meng-encode `withResolvedMockups(order, poFileStorage)`.

Mental model: DB menyimpan *apa* yang disimpan (key yang stabil & deterministik); URL *cara
mengambilnya* (presigned, kedaluwarsa) di-resolve fresh saat read. Prinsipnya sama dengan
menyimpan path file vs menyalin link download ber-expiry ke database.

Ratchet `Application.kt` (698 baris, di atas hard limit) tetap dijaga: +1 baris parameter
Di-offset dengan menghapus 1 baris kosong ganda, total tetap 698.
