# 🎓 Modul Pembelajaran: Domain Pack — Kosakata Vertikal Platform (Jalur B, B0)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Mengangkat enum menjadi data tanpa mengubah perilaku; paritas "dibangun dari enum"
> **Prasyarat**: `teaching-flow-001-*` (Strangler Fig), `teaching-catalog-driven-factory-flow.md`
> **Referensi**: [`docs/plannings/discovery-B0-domain-pack.md`](../plannings/discovery-B0-domain-pack.md)

---

## 💡 1. Konsep Dasar

Kanvas Factory Flow adalah **jaringan antrian**: node = modul, panah = port bertipe, kolom = fase. Pola itu
berlaku untuk bisnis apa pun. Yang khusus konveksi hanya **kosakatanya** — dan kosakata itu dulu enum.

**Domain Pack** memindahkan kosakata itu ke data:

```
DomainPack (garment, elearning…)        ← platform, dikirim per rilis
 ├─ phases  : PhaseCode  (kolom kanvas)  ← dulu enum PipelineStage
 ├─ slots   : SlotCode   (slot modul)    ← dulu enum ModuleArchetype
 └─ ports   : PortType                   ← dulu konstanta PortDataTypeRegistry
     └─ IndustryTemplate → TenantStageFlow → beku per SPK   (sudah ada, TRD-FLOW-001)
```

---

## 🧱 2. Bedah Keputusan

### Blok A — Pack garment *dibangun dari* enum, bukan disalin

```kotlin
phases = PipelineStage.entries.map { s -> PhaseDefinition(PhaseCode(s.name), s.stepOrder, …) }
```

Menyalin tangan membuat dua sumber kebenaran yang bisa melenceng diam-diam. Membangun dari enum membuat
keduanya **identik secara konstruksi**; test paritas tinggal menjaga bahwa pemetaannya lengkap. Saat semua
pembaca enum sudah pindah (B1–B3), barulah isi pack ditulis sebagai data literal dan enum dihapus.

### Blok B — Kode lama dipertahankan persis

`SlotCode("order_ingestion")` = `ModuleArchetype.ORDER_INGESTION.code` — kode yang **sudah tersimpan** di JSON
pipeline tenant. Nama baru yang "lebih rapi" berarti migrasi data tanpa manfaat.

### Blok C — Kosakata port ≠ port wiring

Paritas menemukan 4 port default archetype (`CommercialInquiry`, `CuttingOrderWithFabric`,
`FinishedGarmentUnit`, `AnyOperationalPayload`) yang **tidak** ada di registry wiring. Menolak atau
menambahkannya ke wiring sama-sama mengubah perilaku. Maka pack punya dua himpunan:
`portTypes` (seluruh kosakata) dan `wiredPortTypes` (yang dipakai menyambung modul) — jujur terhadap data lama.

### Blok D — Tanpa fallback

`DomainPackRegistry.find(DomainPackCode("elearning"))` mengembalikan `null`, bukan garment. Fallback senyap
adalah bug TRD-FLOW-001 (SPK bordir terbaca `NEW_INTAKE`) — di level vertikal akibatnya lebih parah.

---

## ⚠️ 3. Jebakan

1. **Menguji invariant memakai pack garment** hanya membuktikan garment valid. Invariant diuji dengan
   fixture **e-learning** — itulah bukti bentuknya tidak mengasumsikan konveksi.
2. **Memindah pembaca di B0.** B0 hanya menambah di samping; semua test lama hijau **tanpa diubah** adalah
   syarat selesai. Pembaca pertama dipindah di B1 (kolom kanvas).
3. **Memasukkan pack ke DB.** Pack tanpa modul yang dibangun tidak berguna; ia dikirim bersama software.

---

## 🧪 4. Pembuktian

- `GarmentDomainPackParityTest` — iterasi `PipelineStage`, `ModuleArchetype`, registry port.
- `DomainPackInvariantTest` — fixture e-learning; fase/port tak dikenal, kode ganda, kode cacat ditolak.
- core 942 (932 + 10), app 158, server 231 — hijau; JVM, Wasm, JS terkompilasi.

## 🧭 5. Berikutnya: B1

Kanvas membaca kolom dari `pack.orderedPhases` dan fase node dari `pack.slot(archetype).phase`, bukan dari
`PipelineStage`. Setelah itu `PipelineStage` (11 file) bisa dihapus.
