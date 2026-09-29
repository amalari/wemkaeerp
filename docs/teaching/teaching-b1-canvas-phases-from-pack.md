# 🎓 Modul Pembelajaran: Kolom Kanvas dari Domain Pack (Jalur B, B1)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Menghapus enum pertama; paritas "tabel emas" setelah enum hilang
> **Prasyarat**: [`teaching-b0-domain-pack.md`](teaching-b0-domain-pack.md)

---

## 💡 1. Apa yang berubah

| Sebelum | Sesudah |
|---|---|
| `enum class PipelineStage` (5 fase konveksi) | `GarmentPhases` — data literal di pack garment |
| `ModuleArchetype.defaultStage` (`when` tulis tangan) | `ModuleArchetype.canvasPhase` → `pack.phaseOfSlot(SlotCode(code))` |
| `PipelineNode.stage: PipelineStage` | `PipelineNode.stage: PhaseDefinition` |
| Kanvas: `PipelineStage.entries.forEach` | Kanvas: `phases` dari `state.pack.orderedPhases` |

Pack baru dengan fase lain (mis. e-learning: Akuisisi → Penyampaian → Penilaian) kini langsung
menjadi kolom kanvas, tanpa menyentuh kode layar.

---

## 🧱 2. Bedah Keputusan

### Blok A — Tipe node = `PhaseDefinition`, bukan `PhaseCode`

Kartu membaca `node.stage.colorHex` dan `node.stage.displayName`. Dengan `PhaseDefinition` sebagai tipe,
nama properti itu tetap sama, sehingga perubahan UI hanya di 3 file. Yang berganti nama cuma
`stepOrder` → `order`.

### Blok B — Paritas setelah enum dihapus: tabel emas

Di B0, paritas **mengiterasi enum**. Setelah enum dihapus, tidak ada lagi yang bisa diiterasi, jadi nilai
enum terakhir **dibekukan** di test (`LEGACY_PIPELINE_STAGES`, `LEGACY_DEFAULT_STAGE`). Mengubah fase
konveksi sekarang berarti mengubah tabel itu dengan sengaja, dan perubahannya terlihat di review.
Iterasi `ModuleArchetype` tetap ada: archetype baru tanpa fase langsung merah.

### Blok C — `soleActivePack`, bukan `default`

Kode mesin (builder, projector) butuh pack, tapi pemilihan pack per tenant baru ada di B7. Namanya dibuat
jujur: `soleActivePack` adalah **satu-satunya vertikal yang dijalankan**, bukan fallback. B7 tinggal
mencari semua pemakaiannya dan menggantinya dengan resolusi pack tenant.

---

## ⚠️ 3. Jebakan

1. **Dev server web yatim.** Menghentikan `gradlew …--continuous` tidak selalu mematikan proses webpack
   anaknya. Port 3000 tetap melayani bundle repo lain, dan screenshot bisa tampak "benar" padahal kodenya
   bukan yang diuji. Cek `lsof -p <pid> -d cwd` sebelum menilai UI.
2. **`sed` BSD tidak menyisipkan baris** dengan pola `0,/…/`, sehingga import gagal ditambahkan diam-diam.
   Kompilasi yang menangkapnya.

---

## 🧪 4. Pembuktian

- `GarmentDomainPackParityTest`: fase = tabel emas; setiap archetype digambar di fase lamanya.
- core 943, app 158, server 231 hijau; JVM, Wasm, JS terkompilasi.
- Visual `wemade-demo` 1440px (bundle repo B): 5 kolom, warna, nomor stepper, dan kartu identik dengan
  sebelum B1; filter per fase berjalan.

## 🧭 5. Berikutnya: B2

Tipe port kanvas (`OperationalModuleSpecification.inputsFor/outputsFor`, `CatalogPortWiring`) membaca
`PortType` dari pack; `PortDataTypeRegistry` menjadi sumber isi pack garment, lalu dihapus.
