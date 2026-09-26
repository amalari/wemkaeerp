# Teaching — Pipeline Kanban Sampling: Drag & Drop Next-Stage-Only, Dialog Tahap Dinamis & Audit Backend

> Kasus: kanban Pipeline Sampling full-width, drag & drop ala Jira dengan aturan
> *hanya boleh maju satu tahap*, lembar kerja dinamis per transisi tahap (CAM → Rajut,
> Rajut → Finishing), dan jejak audit perpindahan tahap yang direkam **server-side**.

---

## 1. Start dari Mana? (Urutan Menulis dari Nol)

Kalau kamu membangun fitur ini dari nol, tulis dalam urutan ini — dari lapisan paling
dalam ke luar, karena setiap lapisan hanya boleh bergantung ke lapisan di bawahnya:

```
1. Migration V46          → kolom jsonb stage_inputs & stage_history
2. Domain (StageWorkInput.kt, SamplingOrder)  → data + ATURAN gerbang
3. Codec (StageWorkInputCodec.kt)             → jsonb ⇄ objek
4. Server (Tables, Repository, Routes)        → persist + audit dari JWT
5. Client API (SamplingApiClient)             → payload stageInputs
6. ViewModel & UiState                        → event dialog
7. UI (DragDropState, Card, Board, Dialog)    → interaksi
```

**Kenapa domain dulu?** Karena gerbang "CAM tidak boleh ke Rajut sebelum lembar lengkap"
adalah *aturan bisnis*, bukan aturan UI. Kalau kamu menaruhnya di ViewModel, maka:
- endpoint API bisa dilewati langsung (curl/Postman) → data kotor masuk DB;
- alur web & mobile (desktop) harus duplikat aturan yang sama dua kali.

---

## 2. Bedah Kode Blok per Blok

### 2.1 Data model — `StageWorkInput.kt`

```kotlin
data class StageInputRow(val label: String, val value: String)
data class StageInputSection(val section: String, val rows: List<StageInputRow>)
data class StageWorkInput(val stage: SamplingPipelineStage, val sections: List<StageInputSection>)
```

**Mental model**: lembar kerja tahap itu seperti tabel Excel longgar — section = tabel,
row = baris label→nilai. Kenapa tidak field fix (`gramasi: Double`, `waktu: Int`)?

1. Tiap tahap butuh kolom berbeda (CAM butuh PROGRAM/RUMUS; Rajut butuh GRAMASI/WAKTU).
   Dengan field fix, kamu menambah kolom = migrasi skema baru.
2. Label adalah data, bukan skema. Operator menulis "P BADAN : 2.94 K" — bebas.

`StageTransitionAudit(fromStage, toStage, actorEmail, actorRole, at)` — note **past tense
semangat log-nya**: ini append-only, tidak pernah diedit.

### 2.2 Gerbang di domain — `SamplingOrder.requireStageGate()`

```kotlin
private fun requireStageGate(target: SamplingPipelineStage) {
    if (pipelineStage != CAM_PROGRAMMING || target != MACHINE_KNITTING) return
    val missing = StageSectionNames.CAM_REQUIRED.filter { camInput?.section(it)?.hasFilledRow != true }
    require(missing.isEmpty()) { "Lembar Program CAM belum lengkap — isi dulu: ..." }
}
```

Poin penting: `require()` melempar `IllegalArgumentException` → route menangkapnya →
HTTP 422. Inilah kenapa **klien tidak bisa menipu**: lewat UI boleh dikunci tombol,
tapi server yang memegang gerbang sesungguhnya.

### 2.3 Audit dari JWT, BUKAN dari body

```kotlin
val caller = call.callerPrincipalOrNull
withInputs.advancePipelineStage(..., actorEmail = caller?.email ?: "unknown", ...)
```

Kalau `actorEmail` diterima dari body request, siapa pun bisa menulis
`{"actorEmail":"bos@pabrik.id"}` dan audit jadi fiksi. **Identitas selalu dibaca server
dari token.** Body hanya boleh membawa *data kerja* (`stageInputs`), bukan *siapa pelakunya*.

### 2.4 Codec dipisah per sub-konsep

`SamplingOrderCodec.kt` sudah 500+ baris (di atas hard limit core = 400). Kontrak
file-size melarang menambah baris ke file yang sudah over. Solusinya: codec baru
`StageWorkInputCodec.kt` (stage inputs + audit) dan `SamplingProgramCodec.kt`
(machine program + yield — hasil cicilan refactor). File induk hanya tinggal 2-3 baris
per field yang didelegasikan.

### 2.5 Drag & drop — mengapa clone pola CRM?

`SamplingDragDropState` + overlay kartu melayang di board (bukan di kolom) meniru
`CrmDragDropState` yang sudah terbukti. Alasannya: kalau kartu melayang dirender di
dalam kolom, `overflow: clip` pada kolom akan memotongnya saat keluar batas. Overlay
di root board + `zIndex` tinggi membuat kartu bebas terbang antar kolom.

Aturan next-only tinggal satu fungsi murni:

```kotlin
fun allowedTargetsFor(order) = when (order.pipelineStage) {
    NEW_INTAKE -> setOf(CAM_PROGRAMMING)
    CAM_PROGRAMMING -> setOf(MACHINE_KNITTING)
    ...
}
```

Fungsi murni = mudah dites, tidak ada kejutan state. Hit-test drop memakai
koordinat *window* (`positionInWindow`), bukan koordinat lokal, supaya tetap benar
meski board di-scroll.

### 2.6 Dialog dinamis — satu komponen untuk semua section

`DynamicSectionTable` dipakai oleh 7 section (PROGRAM, INSTRUKSI PANAH, RUMUS POLA,
GRAMASI, WAKTU, SIZE CHART, TENSELITY). Ini Aturan Tiga Kali design system: begitu pola
muncul ≥3 kali, wajib diangkat jadi komponen. Komponennya buta domain — menerima
`sectionName: String`, `rows: List<StageInputRow>`, lambda — bukan `SamplingOrder`.

Konfigurasi transisi tinggal deklarasi data:

```kotlin
private val CAM_SECTIONS = listOf(
    StageAdvanceSectionSpec(StageSectionNames.PROGRAM, "mis. DEPAN : BIAN-D"), ...
)
```

Menambah section baru = menambah 1 baris konfigurasi, nol perubahan komponen.

### 2.7 Board full-width — `BoxWithConstraints`

```kotlin
val isWide = maxWidth >= 1100.dp
val columnModifier = if (isWide) Modifier.weight(1f) else Modifier.width(300.dp)
```

≥1100dp → kolom memakai `weight(1f)` (full-width); di bawahnya → fallback 300dp +
`horizontalScroll`. Adaptivitas minimal tanpa menyentuh theme dan tanpa ternary baru.

---

## 3. Teknologi & Pendekatan — "The Why"

| Keputusan | Alternatif yang ditolak | Risiko alternatifnya |
|---|---|---|
| jsonb `stage_inputs` | tabel relational + FK | skema berubah tiap section baru; overkill untuk data read-mostly |
| Gate di domain (`require`) | gate di ViewModel / UI | API bisa dilewati langsung; duplikasi web vs mobile |
| Audit dari `CallerPrincipal` | audit dari body request | identitas dipalsukan dengan mudah |
| Overlay kartu di root board | kartu melayang dalam kolom | kartu ter-clip `overflow` kolom |
| CompositionLocal untuk drag state | param diselipkan ke semua composable | prop-drilling 3 level |
| Extension `resolveGarmentTimeline()` | menaruh di entity | `SamplingOrder.kt` melampaui hard limit 400 baris |

## 4. Jebakan Pemula (Common Pitfalls)

1. **Encode/decode asimetris.** Bug yang tertangkap test: encode menulis `stageInputs`
   sebagai *array* JSON, decode membaca `obj.string(...)` (tipe *string*) → selalu kosong.
   Karena kolom Postgres bertipe `jsonbText` (disimpan sebagai string), kontraknya:
   **jsonb dikirim & dibaca sebagai string di kedua sisi.** Test round-trip codec wajib.
2. **Koordinat hit-test drag.** `positionInWindow()` vs `positionInRoot()` — kalau board
   berada di dalam container ter-offset (top bar, padding), hit-test pakai koordinat root
   akan meleset beberapa puluh pixel. Window coordinate aman di semua layout.
3. **Default parameter di interface + override.** `advanceStage(..., stageInputs: List = emptyList())`
   boleh di interface; implementasi *tidak boleh* mengulang default value. Yang memakai
   default adalah pemanggil lewat tipe interface.
4. **Kolom grup = satu stage perwakilan.** Kolom "Finishing & QC" berisi 2 stage
   (LINKING_ASSEMBLY + FINISHING_QC) tapi drop-nya hanya ke LINKING_ASSEMBLY. Kalau dua
   stage didaftarkan sekaligus, drag bisa "melompat" ke QC.
5. **Ratchet file besar.** `PostgresSamplingOrderRepository.kt` (debt 698 baris) tidak
   boleh bertambah. Setiap penambahan wajib di-offset: extract duplikasi yang ada
   (`sizeMatrixJson`, `parseSizeChartRow`) sehingga file justru menyusut (698 → 692).

## 5. Verifikasi & Tantangan Mandiri

Sudah diverifikasi:
- [x] Kompilasi: core (JVM), server, app/shared (JVM + WasmJS + Android).
      Catatan: `compileKotlinJs` (Skia) dan error di `presentation/qc/**` adalah
      pekerjaan lain yang belum selesai saat verifikasi — bukan perubahan ini.
- [x] Test domain: 14 test `SamplingOrderTest` hijau, termasuk 5 test baru
      (gate menolak, gate lolos + audit, tanpa gerbang, replace input, round-trip codec).
- [x] E2E HTTP: `NEW_INTAKE→CAM` tanpa inputan = 200 + audit; `CAM→Rajut` tanpa
      inputan = **422** + pesan domain; `CAM→Rajut` dengan inputan = 200 + data
      tersimpan + audit memuat email aktor dari JWT.
- [x] Visual: board full-width 5 kolom, dialog lembar kerja terbuka dari tombol
      dengan section kosong (tanpa prefill) dan tombol simpan terkunci sampai semua
      section terisi.

**Tantangan untukmu:**
1. Tambahkan gerbang baru: Rajut → Finishing menolak bila section GRAMASI kosong.
   (Petunjuk: generalisasi `requireStageGate` dengan map `stage → requiredSections`.)
2. Tampilkan `stageHistory` sebagai timeline mini di detail SPK — audit datanya sudah
   ada, tinggal dirender.
3. Tulis test untuk `SamplingDragDropState.allowedTargetsFor` — pastikan TIDAK ada
   jalur loncat 2 tahap dari stage manapun.

