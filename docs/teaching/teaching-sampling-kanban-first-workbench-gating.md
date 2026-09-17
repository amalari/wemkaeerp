# Teaching: Kanban-First Landing & Stage-Gated Workbench (Modul Sampling)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Progressive Disclosure, Overview-to-Detail UX Flow, Single Source of Truth untuk Style Mapping, Kotlin Range pada Enum, Compose Multiplatform
> **Prasyarat**: Pemahaman dasar Compose, pola MVI (`UiState`/`UiEvent`), dan design system Claymorphism WeMade

---

## 1. Masalah: Urutan yang Terbalik

Modul Sampling punya dua view: **SPK Workbench** (lembar kerja teknis satu SPK) dan
**Pipeline Kanban** (papan antrian pabrik per tahap). Default view-nya adalah Workbench, dan
SPK yang pertama terpilih sering kali order Draft baru masuk dari CRM — datanya kosong.

Hasilnya, kesan pertama layar adalah **dinding form kosong plus lima tombol aksi** yang belum
boleh ditekan (`Setor Finishing`, `QC Inspeksi`, `Ajukan Revisi`, `ACC PRODUKSI`) untuk SPK yang
bahkan belum masuk program CAM. Ini dua pelanggaran UX sekaligus:

1. **Overview-to-Detail terbalik** — user disodori detail sebelum dia punya peta antrian.
2. **Progressive disclosure dilanggar** — semua aksi ditampilkan untuk semua tahap.

## 2. Perbaikan (Order of Operations dari Nol)

Urutan mengerjakan perubahan ini penting karena ada ketergantungan:

1. **Domain dulu** — pastikan enum `SamplingPipelineStage` punya `order: Int` (sudah ada di
   `SamplingOrderValueObjects.kt`). Semua sortir dan gating bergantung pada field ini; kalau
   belum ada, tambahkan di domain, **bukan** di presentation.
2. **State** (`SamplingUiState.kt`) — ganti default `activeViewTab` ke `PIPELINE_KANBAN` dan
   tambahkan computed property `spkSelectorOrders` yang men-sortir `orders` per
   `pipelineStage.order` lalu `spkNumber.value`.
3. **Helper style bersama** (`SamplingStageStyle.kt`) — angkat mapping `stage -> Color` dari
   `SamplingPipelineKanbanBoard` menjadi `samplingStageTint(stage)`. Ini hasil Aturan Tiga Kali:
   mapping yang sama dibutuhkan dua layar (kanban header + badge workbench), salinan kedua
   harus jadi satu fungsi.
4. **Presentation gating** (`SamplingDesktopWorkbench.kt`) — ganti barisan tombol statis dengan
   `when (order.pipelineStage)`, tambahkan badge tahap, dan `enabled`-kan tombol sidebar.
5. **Verifikasi** — kompilasi 3 target (`Jvm`, `WasmJs`, `Js`), test, lalu lihat dengan mata.

## 3. Bedah Kode Blok per Blok

### Blok A: Computed Property sebagai Tempat Kebijakan Urutan

```kotlin
val spkSelectorOrders: List<SamplingOrder>
    get() = orders.sortedWith(
        compareBy({ it.pipelineStage.order }, { it.spkNumber.value })
    )
```

**Mental model**: UI state bukan cuma "data mentah dari repo"; dia boleh menyimpan *kebijakan
tampilan* yang murni (deterministik, tanpa side effect). Chip selector dulu menampilkan urutan
kedatangan API — `0013, 0004, 0011, ...` — yang menghancurkan mental model antrian. Kebijakan
"stage dulu, nomor SPK kedua" taruhnya di `UiState` (bukan di Composable) supaya bisa dites
tanpa rendering.

### Blok B: `when` pada Enum sebagai Gerbang Aksi

```kotlin
when (order.pipelineStage) {
    SamplingPipelineStage.ACC_APPROVED -> { /* badge GOLDEN SAMPLE LOCKED + Buat Tech Pack */ }
    SamplingPipelineStage.IN_DELIVERY -> { /* Ajukan Revisi + ACC PRODUKSI */ }
    SamplingPipelineStage.FINISHING_QC -> { /* Inspeksi Fisik QC */ }
    SamplingPipelineStage.LINKING_ASSEMBLY -> { /* Setor Finishing / alur makloon */ }
    else -> { /* tahap 1-3: dipandu checklist, tanpa dialog */ }
}
```

**Kenapa `when` penuh, bukan `if`?** Compiler Kotlin memaksa exhaustive. Kalau nanti ada tahap
baru di enum, kompilasi gagal di sini — pengingat bahwa gerbang aksi harus diputuskan, bukan
diam-diam lewat ke cabang `else`. Semantik cabangnya sengaja disamakan dengan tombol pada kartu
Kanban (`SamplingPipelineKanbanBoard`), sehingga kedua permukaan tidak pernah menawarkan aksi
yang berbeda untuk tahap yang sama.

### Blok C: Range pada Enum untuk Gating Halus

```kotlin
enabled = order.pipelineStage in
    SamplingPipelineStage.LINKING_ASSEMBLY..SamplingPipelineStage.FINISHING_QC
```

Enum Kotlin adalah `Comparable`, jadi `..` menghasilkan `ClosedRange`. Tombol `+ Setor` tetap
*terlihat* (layout stabil, user tahu kemampuan itu ada) tapi *nonaktif* di luar tahapnya —
beda dengan header yang tombolnya dihilangkan total. Kapan pakai yang mana? **Hilangkan** kalau
aksi itu ilegal dan membingungkan; **disable** kalau aksinya relevan konteks tapi timing-nya
belum.

### Blok D: Satu Sumber Warna Tahap

```kotlin
// dulu: when(stage) { ... } disalin di KanbanBoard; sekarang:
val headerTint = samplingStageTint(stage)
```

Warna adalah pembeda state (Kontrak 8 design system). Kalau kanban bilang tahap "Rajut Turun
Mesin" berwarna amber tapi badge workbench bilang ungu, user kehilangan bahasa visual. Satu
fungsi `samplingStageTint` di package `components` (bukan di `designsystem/` — dia tahu domain
`SamplingPipelineStage`, jadi ilegal masuk komponen bersama yang buta fitur, Kontrak 6).

## 4. Jebakan Pemula (Common Pitfalls)

| Jebakan | Konsekuensi | Yang benar |
|---|---|---|
| Men-sortir di dalam Composable (`remember(orders) { orders.sorted... }`) | Kebijakan tersebar, tak bisa dites unit | Computed property di `UiState` |
| Menambah mapping warna tahap baru di kedua layar | Kedua layar drift; perubahan warna = cari 2+ file | `samplingStageTint()` tunggal |
| Mengganti default tab dengan mengirim `SelectViewTab` dari init ViewModel | Ada dua sumber kebenaran state awal | Default value di `SamplingUiState` |
| Menghilangkan SEMUA tombol tahap lanjut sampai ke sidebar | Layout "loncat" saat tombol muncul-hilang | Header = hilang, sidebar = `enabled` |
| Asal lulus kompilasi JVM | Perbedaan target hanya ketahuan di Wasm/JS | Kompilasi minimal 3 target |

## 5. Verifikasi & Tantangan Mandiri

1. `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs`
2. `./gradlew :app:shared:jvmTest`
3. Buka `http://localhost:3000/sampling-order` (login demo): landing harus Kanban; klik kartu
   SPK-SMP-0005 → Workbench terbuka dengan badge "Rajut Turun Mesin", header tanpa tombol aksi
   tahap lanjut, tombol `+ Setor`/`QC` pudar.
4. **Tantangan**: tambahkan satu tahap baru di `SamplingPipelineStage` (mis. `WASHING`) dan
   amati kompilasi memaksa kamu memutuskan warna (`samplingStageTint`) serta cabang gerbang
   aksi di Workbench — itulah desain yang "menolak dilupakan".

---

## Next Related Tasks

- Mode "Isian Sampling Belum Lengkap (n)": filter Workbench yang hanya menampilkan SPK dengan
  field sampling wajib yang kosong (program CAM, tenselity, ukuran rajut mentah).
- Auto-select SPK pertama berdasarkan `spkSelectorOrders.firstOrNull()` agar fallback
  `selectedOrder` konsisten dengan urutan pipeline.

