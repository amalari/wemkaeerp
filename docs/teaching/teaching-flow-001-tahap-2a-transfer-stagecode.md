# 🎓 Modul Pembelajaran: Memindahkan Paket `transfer` ke `StageCode` (TRD-FLOW-001 Tahap 2a)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Strangler Fig, jembatan di tepi paket, overload + `@JvmName`, mempertahankan perilaku parser
> **Prasyarat**: [Tahap 1](teaching-flow-001-tahap-1-stage-flow-foundation.md)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — Tahap 2, paket pertama

---

## 💡 1. Konsep Dasar

Paket `transfer` menentukan **ke mana barang berpindah** antar gedung. Ia merujuk tahap lewat
`FlowNodeRef.Stage`, yang dulu membungkus enum rajut — artinya tahap "Bordir Mesin" milik
perusahaan bordir tidak mungkin diberi lokasi. Setelah perubahan ini, `transfer` bicara dalam
`StageCode`, sehingga tahap apa pun bisa menjadi simpul serah terima.

**Analogi**: mengganti pipa di satu lantai gedung tanpa mematikan air di lantai lain. Sambungan
adaptor dipasang di ujung pipa lantai itu; lantai lain tetap memakai ukuran lama sampai
gilirannya tiba.

---

## 🧭 2. "Start dari Mana?"

1. **Petakan jejaknya dulu** (`grep`): hanya 3 file di `transfer`, tapi `FlowNodeRef.Stage`
   dipanggil dari `sampling`, `app/shared`, dan ±20 baris test.
2. **Tentukan apa yang *tidak boleh* berubah**: key tersimpan `STAGE:<code>`, kesetaraan objek,
   dan perilaku `parse` terhadap nama yang sudah dihapus.
3. **Ubah bagian dalam, pasang jembatan di tepi**, lalu buktikan test lama hijau **tanpa
   menyentuh satu assertion pun**.

---

## 🧱 3. Bedah Kode

### Blok A — Konstruktor sekunder sebagai adaptor

```kotlin
data class Stage(val code: StageCode) : FlowNodeRef {
    constructor(stage: SamplingPipelineStage) : this(stage.toStageCode())
    ...
}
```

- Semua pemanggil lama (`FlowNodeRef.Stage(SamplingPipelineStage.X)`) tetap terkompilasi.
- Karena konstruktor sekunder mendelegasikan ke primer, `Stage(enum) == Stage(code)` — kunci map
  lokasi yang dibuat dengan cara lama dan baru saling cocok. Ini dibuktikan
  `stage_fromEnumAndFromCode_shouldBeEqualWithSameKey`.

### Blok B — Overload dengan `@JvmName`

```kotlin
fun resolveNodes(stages: List<StageCode>, ...)

@JvmName("resolveNodesFromLegacyStages")
fun resolveNodes(stages: List<SamplingPipelineStage>, ...) = resolveNodes(stages.map { it.toStageCode() }, ...)
```

- Di JVM, `List<StageCode>` dan `List<SamplingPipelineStage>` terhapus menjadi `List` yang sama
  (*type erasure*) → dua fungsi bertanda tangan identik. `@JvmName` memberi nama biner berbeda.
  Di `commonMain` wajib `import kotlin.jvm.JvmName`.
- Overload lama cuma **menerjemahkan lalu mendelegasikan** — tidak ada logika ganda yang bisa
  menyimpang. Test `resolveNodes_legacyOverload_shouldMatchCodeOverload` menjaganya.

### Blok C — Parser yang sengaja *tidak* dilonggarkan

```kotlin
KIND_STAGE -> SamplingPipelineStage.entries.firstOrNull { it.name == value }
    ?.let { Stage(it.toStageCode()) }
```

- Godaan terbesar: menggantinya dengan `StageCode.parseOrNull(value)`. Kelihatan lebih "baru",
  tapi diam-diam **mengubah perilaku**: baris pemetaan lokasi berisi nama tahap yang sudah
  dihapus (atau alias `FINISHING_QC`) yang dulu dibuang, kini akan diterima sebagai simpul
  hantu. Pelonggaran ditunda ke Tahap 3, saat parser bisa memvalidasi terhadap kerangka tenant.

---

## ⚖️ 4. The "Why"

| Pendekatan | Alternatif | Kenapa ini |
|---|---|---|
| Jembatan di tepi paket | Ubah semua pemanggil sekaligus | Satu PR = satu paket; review & rollback kecil |
| `displayName` fallback ke kode | Wajibkan nama di konstruktor | Nama di konstruktor ikut `equals` → objek hasil `parse` ≠ objek bentukan tangan |
| Test lama tak disentuh | Perbarui test ke `StageCode` | Test lama adalah bukti paritas; mengubahnya menghapus buktinya |

---

## ⚠️ 5. Jebakan Pemula

1. **Memasukkan `displayName` ke properti data class** — merusak kesetaraan kunci map lokasi.
2. **Lupa `@JvmName`** — error "platform declaration clash" hanya muncul di JVM.
3. **"Merapikan" parser** — lihat Blok C; perubahan perilaku yang tidak tertangkap test lama.

---

## 🧪 6. Pembuktian

- Seluruh 127 kelas test `core` lulus **tanpa perubahan assertion**.
- Test baru `FlowNodeRefStageCodeTest` (5): kesetaraan enum↔kode, fallback nama, parser tetap
  ketat, simpul untuk kode di luar enum + proses berjangkar, paritas dua overload.
- Kompilasi `core` JVM/JS/wasm, `server` main+test, `app:shared` JVM/JS/wasm — hijau.

---

## 🏆 7. Tantangan Mandiri

- [ ] Paket berikut: `domain/process` — `TenantOptionalProcess.samplingAnchorAfter` masih enum.
      Rancang migrasinya supaya kolom tersimpan tidak berubah.
- [ ] Setelah semua pemanggil pindah, hapus konstruktor sekunder & overload lama; pastikan
      kompilator yang menunjukkan sisa pemanggilnya.
