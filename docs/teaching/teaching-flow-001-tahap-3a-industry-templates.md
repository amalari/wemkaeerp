# 🎓 Modul Pembelajaran: Template Industri Kedua, Ketiga, Keempat (TRD-FLOW-001 Tahap 3a)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Template sebagai data, uji "jalan penuh" per template, menghormati test lama, snapshot vs rujukan
> **Prasyarat**: [Penutupan Tahap 2](teaching-flow-001-tahap-2g-operator-floor-and-closing.md)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — Tahap 3a

---

## 💡 1. Konsep Dasar

Setelah Tahap 2, menambah industri berarti **menambah data**, bukan kode: tiga daftar
`StageDefinition` baru (potong-jahit, bordir, sablon) dan satu kolom `tenants.industry_template`
yang memilih daftar mana yang disalin saat tenant pertama kali membuka papannya.

Template ini **draf** — urutannya usulan, belum divalidasi orang lantai (TRD §4 poin 3). Karena itu
ia hanya titik awal; tenant akan bisa menyunting salinannya (Tahap 3c).

---

## 🧱 2. Bedah Kode

### Blok A — Kode yang sama untuk pekerjaan yang sama

`QC_FINISHING`, `PENGEMASAN`, `SETRIKA_UAP` dipakai ulang di template lain. Hasilnya gratis:
antrian QC (`entryStage`), tag fase Setrika (`PhaseTaggableStage`), dan kartu SPK mengenali
tahap itu tanpa satu baris kode baru. Kode adalah identitas pekerjaan, bukan identitas template.

### Blok B — Peran yang boleh tidak ada

Sablon tidak punya tahap `SEWING` (tabel archetype menaruh "Sablon Manual" di
`CUSTOM_EXTENSION`). Menulis template itu langsung memunculkan bug laten:

```kotlin
// sebelum — melempar untuk kerangka tanpa SEWING, di SETIAP setoran
if (newFinishedQty >= sampleQuantity && stageCode == assemblyStage)
// sesudah — tanpa meja perakitan, tidak ada setoran perakitan yang memindahkan tahap
if (newFinishedQty >= sampleQuantity && stageCode == firstStageWith(ModuleArchetype.SEWING)?.code)
```

Bug ini tak akan pernah ditemukan dengan test rajut. Ia ditemukan karena test "jalan penuh"
menjalankan setiap template sampai pengemasan.

### Blok C — Test lama menang

Melonggarkan `FlowNodeRef.parse` menjadi "kode valid apa pun" memecahkan test pra-TRD
`flow node ref parse when key unknown should return null`: baris pemetaan untuk tahap yang sudah
dihapus harus dibuang. Test itu **tidak** dilonggarkan; parse-nya yang disempurnakan — kode diterima
bila dikenal template mana pun (`IndustryStageTemplates.knownCodes`). Aturan praktisnya: kalau test
lama gagal karena perubahanmu, anggap test itu benar sampai terbukti sebaliknya.

### Blok D — SPK lama yang tidak pernah beku

`effectiveFrame` semula "beku → bekunya, kalau tidak → kerangka pabrik". SPK sebelum V73 yang sudah
di lantai juga belum beku — padahal dibuat di atas rajut. Kini hanya SPK di **tahap masuk** yang
mengikuti kerangka pabrik; sisanya tetap di kerangka rajut tempat ia dibuat.

---

## ⚠️ 3. Jebakan Pemula

1. **Mengira kolom `industry_template` mengubah tenant yang sudah ada.** Tidak: ia hanya dipakai saat
   provisioning. Tenant yang sudah punya baris `tenant_stage_flows` tetap di kerangkanya.
2. **Menguji template baru hanya secara struktural.** Struktur valid ≠ bisa dijalani. Uji jalan penuh.
3. **Memaksa template rajut lewat uji yang sama.** Gerbang Program CAM (benar) menahannya — itu
   bukan bug untuk "diakali", tapi alasan memisahkan cakupan uji.

---

## 🧪 4. Pembuktian

- `IndustryTemplateWalkTest`: 4 template valid (jangkar sama, ada QC & kemas, ada meja, sisa kerja
  menurun); 3 template baru dijalani SPK Masuk → QC → kemas lewat aturan domain.
- `EffectiveFrameTest` (3): tahap masuk ikut pabrik, SPK lama di lantai tetap rajut, beku pakai bekunya.
- `core` 912, `app:shared` 152 test lulus; test pra-TRD parse **tidak diubah**.
- V74 teraplikasi di dev; semua tenant `KNIT_SWEATER`; `GET /api/tenant/stage-flow` 200.

---

## 🏆 5. Tantangan Mandiri

- [ ] Tahap 3b: `AddStageUseCase` dengan penolakan `STAGE_OCCUPIED` — ingat, hanya SPK yang **belum
      beku** yang terdampak (FR-5b).
- [ ] Rancang parse `FlowNodeRef` yang menerima kerangka tenant, untuk tahap kustom buatan tenant.
