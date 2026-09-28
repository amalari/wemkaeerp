# 🎓 Modul Pembelajaran: Pemisahan Alur Sampling vs Produksi Masal (Tag Fase Cuci & Setrika)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Value Object, rute turunan (derived routing), snapshot konfigurasi, Flyway + RLS, Compose design system
> **Prasyarat**: Paham `SamplingPipelineStage`, `TenantOptionalProcess`, dan panel Penentuan Alur
> **Referensi Task**: Plan `buat-planning-pemisahan-alur-declarative-lynx`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata**: di pabrik, sampel sering **tidak dicuci**, cukup disetrika untuk difoto buyer.
Washing baru dikerjakan saat produksi masal (batch di mesin cuci). Tapi sebelumnya rute kartu
sampling adalah enum kaku: `nextStage = entries[ordinal + 1]`. Akibatnya setiap SPK sampel
**wajib** lewat antrian meja Cuci. Operator Cuci lalu melihat kartu yang tidak akan pernah
datang, dan kartunya "nyangkut" sampai ada yang iseng menekan "Selesai".

**Analogi**: bayangkan rute bus kota. Kerangka haltenya sama untuk semua bus, tapi **bus ekspres
melewati halte tertentu tanpa berhenti**. Kita tidak membangun jalan baru; kita cukup menandai
halte mana yang dilewati oleh bus jenis apa.

**Hasil akhir**: di Penentuan Alur, pill **Cuci & Softener** dan **Setrika Uap** memiliki toggle fase kompak terintegrasi `[✓ Sampling]` dan `[✓ Produksi]`.
- **Tata letak sejajar (horizontal baseline)**: semua pill berukuran tinggi sama dan sejajar, tidak ada tag menggantung yang merusak ritme garis timeline.
- **Logika simetris (adil)**: nama tahap **TIDAK DICORET** selama masih aktif di salah satu fase (misal aktif di Produksi saja). Coretan (strikethrough) dan outline putus-putus hanya muncul jika dinonaktifkan di kedua fase (`Dilewati Total`).
- **Konsistensi visual seluruh simpul (termasuk proses opsional)**: `PlacedProcessChip` (Bordir, Sablon, Laundry) memakai bentuk `ClayShapes.Chip` dan outline yang sama persis dengan `StagePill` sehingga tinggi seluruh alur sejajar lurus tanpa ada badge oval yang timpang.
- **Tombol hapus bersih**: tombol remove memakai `IconClose` vektor kanvas yang menyatu rapi di dalam kartu.

---

## 🧭 2. "Start dari Mana?" — Urutan Penulisan

1. **Langkah 0 — domain dulu (`core`)**. Pertanyaan intinya, "kartu ini diserahkan ke meja
   mana?", adalah aturan bisnis. Kalau dijawab di UI atau route, jawabannya akan berbeda di
   tiga tempat.
2. **Langkah 1 — Value Object `StagePhaseTags`** di `domain/process/FlowPhaseTags.kt`.
3. **Langkah 2 — `SamplingRoute`**, satu-satunya sumber urutan tahap sampling.
4. **Langkah 3 — alihkan semua transisi** di `SamplingOrder` agar memakai rute
   (`advancePipelineStage`, setoran linking penuh, makloon kembali, rework).
5. **Langkah 4 — use case**. `AdvanceSamplingStageUseCase` membekukan tag template saat SPK
   masuk Program CAM.
6. **Langkah 5 — server**. Tulis migrasi V71, repository, codec, lalu endpoint.
7. **Langkah 6 — presentation**. Buat `ClayToggleTag` (designsystem) dan
   `PhaseTaggedStagePill` (fitur), lalu buat drag target dan tombol "Selesai" meja mengikuti rute.

---

## 🧱 3. Bedah Kode

### Blok A — Value Object dengan default aman

```kotlin
data class StagePhaseTags(val phases: Map<PhaseTaggableStage, Set<FlowPhase>> = emptyMap()) {
    fun phasesOf(stage: PhaseTaggableStage) = phases[stage] ?: ALL_PHASES
    val skippedSamplingStages: Set<SamplingPipelineStage> get() = ...
    fun productionActiveStations(line) : Set<WorkStationCode> = ...
}
```

Kenapa ditulis begini:
- **Tahap yang tidak tercantum = kedua fase.** Melompati tahap yang seharusnya dikerjakan jauh
  lebih mahal daripada mengerjakan tahap yang ternyata tidak perlu.
- **Hanya Cuci & Setrika yang bisa dipilah** (`PhaseTaggableStage`). Memberi pilihan pada Rajut
  atau QC hanya membuka peluang salah klik.
- **`productionActiveStations` sengaja berbentuk `Set<WorkStationCode>`**, persis parameter
  `WorkStationCatalog.nextAfter(activeStations)`. Itulah titik warisnya nanti.

### Blok B — Rute sebagai turunan, bukan tabel

```kotlin
data class SamplingRoute(val skipped: Set<SamplingPipelineStage>) {
    fun nextAfter(stage) = entries.firstOrNull { it.order > stage.order && it !in skipped }
}
```

`SamplingDragDropState` dulu punya tabel `when` hardcode per tahap. Sekarang isinya cukup
`setOfNotNull(route.nextAfter(stage))`. Satu fungsi dipakai oleh drag kanban, tombol meja, dan
transisi otomatis, jadi ketiganya tidak mungkin berbeda pendapat.

### Blok C — Snapshot saat alur dikunci

```kotlin
fun SamplingOrder.freezePhaseTags(tenantDefault: StagePhaseTags) =
    if (stagePhaseTags != null) this else copy(stagePhaseTags = tenantDefault.normalized)
```

Tag template pabrik dibekukan ke order saat SPK masuk **Program CAM**. Ada dua alasan:
1. Admin yang mengubah template besok **tidak boleh me-rute ulang** kartu yang sudah di lantai.
2. Transisi otomatis di dalam agregat (misalnya setoran linking penuh) tidak punya akses
   repository. Setelah dibekukan, `order.samplingRoute` sudah cukup.

Sebelum dibekukan, tampilan dan penurunan leg memakai `order.routeWith(template)` atau
`effectivePhaseTags(template)`.

### Blok D — Leg perpindahan barang ikut rute

```kotlin
if (stage !in skipped) add(FlowNodeRef.Stage(stage))
processes.filter { it.samplingAnchorAfter == stage }.forEach { add(...) }
```

Tahap yang dilompati **tidak menjadi simpul**, jadi leg Surat Jalan ke gedung Cuci ikut hilang,
dan gerbang `requireLegReceived` tidak memblokir. Proses yang berjangkar di Cuci (misalnya
Laundry) **tetap** disisipkan, karena jangkar adalah posisi, bukan syarat.

### Blok E — Migrasi V71 + RLS

- Tabel `tenant_stage_phase_tags(tenant_id PK, tags JSONB)` dengan policy isolasi tenant.
- Kolom `sampling_orders.stage_phase_tags JSONB NULL`. Nilai `NULL` berarti "warisi template",
  sedangkan `{}` berarti "punya sendiri, isinya default". Keduanya sengaja dibedakan.

### Blok F — UI

- `ClayToggleTag` (designsystem, buta domain): satu komponen untuk dua keadaan, `[Label ×]` dan
  `[+ Label]`, supaya posisinya tidak melompat saat di-×.
- `PhaseTaggedStagePill` (fitur): pill diredupkan dan dicoret tanpa nomor urut, dengan
  keterangan "Hanya di Produksi".
- Tombol **Template Alur Pabrik** di header Order Sampling membuka `TenantFlowTemplateDialog`.

---

## ⚖️ 4. The "Why"

| Pendekatan | Alternatif | Kenapa ini | Risiko alternatif |
|---|---|---|---|
| Tag fase pada tahap wajib | Hapus tahap dari enum per tenant | Kerangka kustodi tetap satu; tahap cukup ditandai "dilompati" | Enum per tenant = parser, codec, dan migrasi pecah |
| Snapshot di CAM | Selalu baca template live | Kartu in-flight stabil | Ubah template = kartu di lantai berpindah meja diam-diam |
| Endpoint `PUT /flow/phase-tags` terpisah | Menumpang di `PUT /flow` | Toggle tag tidak ikut membekukan daftar proses jadi "kustom" | Klik satu tag membuat desain tidak lagi ikut template proses |
| JSONB ringkas (`normalized`) | Kolom bool per tahap | Tahap baru bisa ditambah tanpa migrasi | Migrasi setiap kali tahap taggable bertambah |

---

## ⚠️ 5. Jebakan Pemula

1. **Memakai `stage.nextStage` untuk menyerahkan kartu.** Itu urutan kerangka, bukan rute
   desain. Pakailah `order.samplingRoute.nextAfter(stage)`.
2. **Memakai `order.samplingRoute` sebelum CAM.** Tag belum dibekukan, jadi hasilnya rute penuh.
   Untuk tampilan dan leg sebelum CAM, pakai `routeWith(template)`.
3. **Menghapus proses yang berjangkar di tahap yang dilompati.** Laundry setelah Cuci tetap
   dikerjakan.
4. **PATCH reposition mengosongkan field yang tidak dikirim.** Bug ini ikut diperbaiki:
   menggeser chip di alur sampling dulu diam-diam menghapus `stationAnchorAfter` proses di lini
   produksi. Sekarang field yang absen berarti tidak diubah.

---

## 🧪 6. Pembuktian

`core/src/commonTest/.../sampling/SamplingPhaseRouteTest.kt` berisi 18 test:
- rute Linking → Setrika bila Cuci di-×, dan Linking → QC bila keduanya di-×;
- advance ke tahap yang dilompati ditolak;
- setoran penuh dan makloon kembali melompat dengan benar;
- rework tidak bisa dikirim ke meja yang dilompati;
- pembekuan template saat masuk CAM, dan tag milik desain menang atas template;
- `resolveNodes` membuang tahap tapi menyimpan prosesnya;
- round-trip codec, dengan `null` dan `{}` tetap berbeda;
- `WorkStationCatalog.nextAfter` melompati WASHING bila tag Produksi di-×.

---

## 🏆 7. Tantangan Mandiri

- [ ] Tautkan `BulkWorkOrder` ke desain sampling, lalu pakai `productionActiveStations()` untuk
      routing WorkCard produksi (endpoint pindah stasiun).
- [ ] Tampilkan tag fase juga pada chip proses opsional (Bordir/Sablon), diturunkan dari
      `samplingAnchorAfter` / `stationAnchorAfter`.
- [ ] Label tombol "Selesai, Serahkan ke …" di header meja masih generik per meja. Buat labelnya
      mengikuti kartu yang dipilih.
