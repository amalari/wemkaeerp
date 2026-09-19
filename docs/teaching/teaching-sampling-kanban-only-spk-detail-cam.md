# Teaching: Layar Sampling Murni Kanban + Dialog Detail SPK (Persiapan Program CAM)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: View-state simplification (MVI), Rule of Three & ekstraksi komponen bersama,
> "single source of truth" gerbang transisi tahap, dialog sebagai meja kerja (not just form)
> **Prasyarat**: Paham struktur `SamplingUiState`/`SamplingUiEvent`, konsep `requiresStageWorksheet()`,
> dan design system Clay (`.claude/rules/design-system-rules.md`)

---

## 1. Start dari Mana? (Order of Operations)

Task: *"workbench diilangin aja cuma ada kanban; kartu di SPK Baru kalau diklik buka dialog detail
SPK-nya; untuk persiapan tim sampling buat program CAM-nya."*

Urutan mengerjakan dari nol:

1. **Petakan dulu, jangan hapus dulu.** Grep semua pemakai `SamplingViewTab`, `SamplingMobileTab`,
   `SamplingDesktopWorkbench`, `SamplingVendorMonitoringView`, dan komponen kecilnya
   (`TenselityTable`, `FeederSequenceBar`, dst.). Hasilnya: semuanya hanya dipakai oleh Workbench /
   Monitoring Vendor → aman dihapus. `FinishingSetoranDialog` ternyata dipakai layar Finishing →
   hidup, jangan disentuh.
2. **Domain dulu (core/).** `StageWorkInput.kt`: tambahkan `CAM_PROGRAMMING` ke
   `requiresStageWorksheet()`. Satu baris ini yang membuat *semua* jalur (klik tombol, drag-drop)
   otomatis membuka lembar CAM — karena UI membaca fungsi ini, bukan sebaliknya.
3. **Ekstraksi yang berulang.** `CAM_SECTIONS`/`KNITTING_SECTIONS`/`sectionsFor()` tadinya `private`
   di `StageAdvanceDialog.kt`. Karena dialog baru butuh section CAM yang sama, angkat ke file
   bersama `StageWorksheetSpecs.kt` (Aturan Tiga Kali) dan jadikan `DynamicSectionTable` publik.
4. **State & event MVI.** Ganti `activeViewTab`/`activeMobileTab` dengan satu field:
   `spkDetailTarget: SamplingOrder?` + event `OpenSpkDetailDialog`/`CloseSpkDetailDialog`.
5. **Layar.** `SamplingWorkspaceScreen` tinggal toolbar → banner → kanban, plus 4 dialog yang
   di-mount di level layar. Hapus cabang Workbench/Monitoring.
6. **Kanban.** `SamplingPipelineKanbanBoard` menerima callback baru `onOpenSpkDetail`; logika klik
   kartu jadi `when`: NEW_INTAKE → detail dialog; transisi ber-lembar → dialog tahap; lainnya → select.
7. **Hapus dead code** (9 file), **kompilasi 5 target**, **lihat UI-nya dengan mata**.

## 2. Bedah Kode Blok per Blok

### A. Domain — `StageWorkInput.kt`

```kotlin
fun SamplingPipelineStage.requiresStageWorksheet(): Boolean =
    this == SamplingPipelineStage.CAM_PROGRAMMING ||
        this == SamplingPipelineStage.MACHINE_KNITTING ||
        this == SamplingPipelineStage.LINKING_ASSEMBLY
```

Mental model: fungsi ini adalah **kontrak gerbang** yang dibaca dua dunia — UI (untuk memutuskan
"buka dialog atau langsung maju") dan domain (`SamplingOrder.advancePipelineStage` menolak
CAM → Mesin Rajut bila `StageSectionNames.CAM_REQUIRED` belum terisi). Dengan menambah
`CAM_PROGRAMMING` di sini, drag kartu SPK Baru ke kolom Program CAM pun ikut membuka dialog
tanpa mengubah satu baris pun di board.

### B. Ekstraksi — `StageWorksheetSpecs.kt`

`StageSectionSpec` (nama section + hint) dan `stageSectionsFor(targetStage)` pindah ke file
bersama. Perhatikan mapping-nya tidak simetris:

- Target `CAM_PROGRAMMING` **dan** `MACHINE_KNITTING` → `CAM_SECTION_SPECS`.
  Masuk Mesin Rajut memakai lembar CAM yang sama karena gerbangnya memeriksa lembar CAM.
- Target `LINKING_ASSEMBLY` → `KNITTING_SECTION_SPECS` (hasil rajut: gramasi, waktu, size chart).

### C. Dialog Detail SPK — `SamplingSpkDetailDialog.kt`

Struktur: header (SPK + badge stage) → ringkasan read-only (`clayFlat` tile + `SpkDetailRow`) →
lembar CAM dinamis (`DynamicSectionTable` yang sama dengan dialog tahap) → footer dengan gerbang
klien:

```kotlin
val canSubmit = sections.all { it.hasFilledRow }
ClayButton(
    text = "Simpan & Masuk Program CAM",
    enabled = canSubmit && !isSubmitting,
    onClick = { onStartCamProgram(sections) }
)
```

Tombol submit **tidak** memanggil API sendiri. Ia memancarkan satu event yang sama dengan jalur
drag-drop: `ConfirmStageAdvance(orderId, CAM_PROGRAMMING, sections)`. Artinya server tetap satu
sumber kebenaran untuk validasi gerbang + audit aktor.

### D. Klik Kartu — `SamplingPipelineKanbanBoard.kt`

```kotlin
onSelectOrder = {
    when {
        order.pipelineStage == SamplingPipelineStage.NEW_INTAKE -> onOpenSpkDetail(order)
        nextStage != null && nextStage.requiresStageWorksheet() ->
            onAdvanceStageRequested(order, nextStage)
        else -> onSelectOrder(order.id)
    }
}
```

Urutan cabang penting: NEW_INTAKE dicek **lebih dulu** — meskipun `CAM_PROGRAMMING` sekarang
`requiresStageWorksheet()`, klik kartu SPK Baru tetap buka *detail*, bukan *worksheet*. Worksheet
CAM dari kartu SPK Baru dibuka lewat tombol "Mulai Program CAM ->" di kartu, atau lewat drag.

## 3. Technology & Approach ("The Why")

- **Mengapa hapus tab, bukan sembunyikan?** State `activeViewTab` + enum + dua workbench adalah
  ~1.800 baris yang hidupnya tergantung satu selector. Menyimpan kode "demi suatu saat" = dead code
  yang menipu pembaca (dan melanggar file-size rules §4: pecah per tanggung jawab, bukan simpan).
- **Mengapa event yang sama untuk klik & drag?** Dua jalur yang berbeda hasilnya = bug klasik
  ("kok drag nanya lembar, klik enggak?"). Satu fungsi `requiresStageWorksheet()` = satu perilaku.
- **Mengapa dialog, bukan pindah view?** Kanban-first: konteks antrian tidak hilang. Dialog adalah
  "meja kerja di atas papan", bukan "ruangan lain".
- **Risiko kalau dibuat cara lain**: jika gerbang CAM hanya ditulis di UI (boolean di layar),
  domain kehilangan jaminan bahwa SPK yang masuk Mesin Rajut punya lembar program — audit trail
  bocor dan pabrik bisa memproduksi tanpa program CAM tercatat.

## 4. Jebakan Pemula (Common Pitfalls)

| Jebakan | Kenapa fatal | Solusi |
|---|---|---|
| Hapus komponen tanpa grep pemakai | `FinishingSetoranDialog` dipakai layar Operator Finishing — ikut terhapus = layar lain rusak | Grep nama komponen di seluruh repo sebelum `rm` |
| Menambah dialog baru dengan copy-paste tabel section | Melanggar Aturan Tiga Kali; perubahan hint/section ganda | Angkat `DynamicSectionTable` + specs ke file bersama |
| Menulis gerbang CAM di layar (`if (stage == CAM) bukaDialog()`) | Drag-drop lewat jalur lain, dialog terlewat | Satu sumber kebenaran: `requiresStageWorksheet()` di core |
| Lupa bahwa hard limit berlaku ke file *setelah* diubah | Menambah 5 baris ke file 600+ tetap pelanggaran | `wc -l` sebelum & sesudah; ratchet |
| Memercayai klik sintetis JS untuk uji Compose Wasm | Renderer tidak menerima untrusted event dengan andal | Verifikasi visual via screenshot + uji klik manual oleh manusia |

## 5. Verifikasi & Tantangan Mandiri

**Cara menguji:**

1. `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs :app:shared:jvmTest :core:jvmTest` → BUILD SUCCESSFUL.
   (Catatan: `assembleAndroidMain` saat ini gagal pra-eksisting di `MockupCropDialog.kt` —
   referensi Skia di commonMain, bukan akibat perubahan ini; sudah diverifikasi gagal juga tanpa diff.)
2. Buka `http://localhost:3000/sampling-order` → toolbar "ORDER SAMPLING", tanpa tab, 5 kolom kanban.
3. Klik kartu di kolom **SPK Baru** → dialog "Detail SPK" muncul dengan ringkasan + lembar Program CAM.
4. Isi ketiga section (PROGRAM, INSTRUKSI PANAH, RUMUS POLA) → tombol "Simpan & Masuk Program CAM"
   aktif → submit → kartu pindah ke kolom Program CAM.
5. Drag kartu SPK Baru ke kolom Program CAM → dialog lembar CAM yang sama muncul.
6. Klik kartu di kolom Program CAM → dialog lembar kerja CAM → Rajut (perilaku lama, tetap).

**Tantangan mandiri:**

- Tambahkan badge "Lembar CAM belum lengkap" pada kartu di kolom Program CAM yang
  `stageInputFor(CAM_PROGRAMMING)`-nya kosong (hint: pola `SamplingKanbanReadOnlyBadges`).
- Sambungkan tombol "Buat Tech Pack" di dialog detail ke `CreateFromSamplingDialog` di modul
  Tech Pack (param `onCreateTechPack` sudah tersedia).
- Bersihkan sisa event VM yang tidak lagi punya pemanggil UI (`OpenVendorDialog`, dst.) beserta
  flow `AssignVendorDialog`-nya — saat ini dibiarkan sebagai API ViewModel.

