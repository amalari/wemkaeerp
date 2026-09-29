# 🎓 Modul Pembelajaran: Tipe Port dari Domain Pack (Jalur B, B2)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Memindahkan *kepemilikan* kosakata tanpa migrasi data
> **Prasyarat**: [`teaching-b1-canvas-phases-from-pack.md`](teaching-b1-canvas-phases-from-pack.md)

---

## 💡 1. Apa yang berubah

| Sebelum | Sesudah |
|---|---|
| `object PortDataTypeRegistry` (konstanta + `KNOWN_TYPED_LABELS` + `isTyped`) | `GarmentPortTypes` (data pack garment) + `DomainPack.isWired(label)` |
| `PortCompatibility` bertanya ke registry global | bertanya ke `DomainPackRegistry.soleActivePack` |
| `PortDataTypeRegistryTest` | `CatalogPortVocabularyTest` (juga memeriksa `referenceInputs`) |

## 🧱 2. Bedah Keputusan

### Blok A — B2 bukan migrasi data

Tipe port **sudah string** di semua tempat: spec katalog, JSON pipeline, payload kontrak. Yang enum-like
hanyalah *siapa yang tahu daftar lengkapnya*. B2 memindahkan pengetahuan itu dari objek global ke pack,
sehingga pack e-learning bisa punya `Enrollment`/`Submission` tanpa bercampur dengan `CutPiecesBundle`.

### Blok B — Field spec tetap `String` (sengaja)

`upstreamPrerequisites: List<String>` tidak diubah menjadi `List<PortType>` di B2. Nilainya sudah divalidasi
terhadap pack oleh `ModuleRegistrationConsistencyTest` dan `CatalogPortVocabularyTest`. Mengubah tipenya
menyentuh seluruh katalog, dan lebih murah dikerjakan sekaligus di B3, ketika spec disentuh untuk `SlotCode`.

### Blok C — Tabel emas lagi

`LEGACY_WIRED_PORTS` membekukan 11 label registry terakhir. Menambah port konveksi sekarang = menambah ke
`GarmentPortTypes.wired` **dan** memperbarui tabel itu, dengan sengaja.

## 🧪 3. Pembuktian

- core 943, app 158, server 231 hijau; JVM, Wasm, JS terkompilasi.
- `grep PortDataTypeRegistry` hanya tersisa di KDoc.
- Tanpa perubahan UI/builder → tidak ada cek visual baru (B1 sudah membuktikan kanvas identik).

## 🧭 4. Berikutnya: B3

`ModuleArchetype` → `SlotCode` dari pack. Spec katalog menyatakan `slot: SlotCode` dan port bertipe `PortType`.
Archetype sudah tersimpan sebagai string code di JSON pipeline, jadi tidak perlu migrasi. Ini tahap
terbesar sejauh ini (63 file).
