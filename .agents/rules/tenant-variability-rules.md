# WeMade ERP — Aturan Variabilitas Tenant (Kode vs Data)

Status sejajar dengan [`module-integration-rules.md`](module-integration-rules.md),
[`design-system-rules.md`](design-system-rules.md), dan [`file-size-rules.md`](file-size-rules.md).
Aturan ini menjawab satu pertanyaan: **sebuah konsep boleh tinggal di kode (enum/`when`), atau wajib
menjadi data per tenant?**

Lahir dari TRD-FLOW-001: kerangka tahap sampling ditulis sebagai enum rajut, lalu harus dibongkar
lewat ±70 file dan 7 PR ketika tenant bordir/potong-jahit/sablon masuk. Semua itu bisa dihindari
dengan satu pertanyaan di hari pertama.

---

## Kontrak 1 — Uji Variabilitas sebelum `enum class` / `when`

Sebelum menulis `enum class` atau `when` untuk **konsep domain**, jawab:

1. Apakah nilainya bisa **berbeda antar tenant**?
2. Apakah bisa **berbeda antar industri** (rajut, potong-jahit, bordir, sablon)?
3. Apakah admin pabrik **mungkin ingin mengubahnya** (nama, urutan, menambah)?

Satu jawaban "ya" → konsep itu **data**: template bawaan + salinan per tenant.
Enum hanya untuk konsep yang dimiliki **sistem**: status teknis, jenis leg transfer, peran platform,
`StageKind`, `StageTrait`.

```kotlin
// ❌ Kerangka tahap sebagai enum — tenant bordir dipaksa lewat "Rajut Turun Mesin"
enum class SamplingPipelineStage { NEW_INTAKE, CAM_PROGRAMMING, MACHINE_KNITTING, … }

// ✅ Kerangka tahap sebagai data per tenant
data class TenantStageFlow(val tenantId: TenantId, val template: IndustryTemplateCode, val stages: List<StageDefinition>)
```

## Kontrak 2 — Tangga keputusan: taruh di anak tangga yang tepat

| Anak tangga | Artinya | Contoh | Tempat |
|---|---|---|---|
| Modul | Dijual, di-RBAC, dihitung kuota | QC, Fulfillment | `BusinessModule` + `OperationalModuleCatalog` |
| Tahap | Urutan kerja satu dokumen | Digitizing, Hooping | `TenantStageFlow` / `IndustryStageTemplates` |
| Proses opsional | Sisipan per desain | Bordir di SPK rajut | `TenantProcessCatalog` |
| Stasiun | Meja di lini produksi massal | Obras, Steam | `WorkStationCatalog` |
| Konfigurasi | Pilihan per tenant | Tag fase Cuci/Setrika | tabel JSONB per tenant (pola V71) |

Proses **bukan** modul (keputusan 2026-09-28): menjadikan setiap proses modul memecah kuota & RBAC.

## Kontrak 3 — Peran, bukan nama

Aturan domain mencari **peran** (`ModuleArchetype`, `StageTrait`), bukan kode khas satu industri.

```kotlin
// ❌ hanya benar untuk rajut
if (stageCode == LINKING_ASSEMBLY) …
// ✅ benar untuk semua template
if (stageCode == firstStageWith(ModuleArchetype.SEWING)?.code) …
```

Peran boleh **tidak ada** (sablon tidak punya `SEWING`). Kode yang mencari peran wajib aman bila
hasilnya `null` — itu bug nyata yang ditemukan di TRD-FLOW-001 Tahap 3a.

## Kontrak 4 — Kunci tersimpan = value object string, parser tunggal, tolak bukan fallback

Kolom DB dan key JSON memakai value object (`StageCode`), bukan `enum.name`. Satu parser, dan nilai
tak dikenal **ditolak** (atau dicocokkan ke kerangka dokumen), **tidak** jatuh diam-diam ke default.

> Fallback senyap = data berubah. Contoh nyata: SPK bordir dibaca ulang sebagai `NEW_INTAKE`.

## Kontrak 5 — Template disalin, dokumen membeku

- Template bawaan (`IndustryStageTemplates`) → **disalin** ke tenant saat pertama dibutuhkan.
- Dokumen (SPK) **membekukan** salinan saat mulai dikerjakan (`frozenStageFlow`, V73).
- Konsekuensinya wajib ditulis di KDoc: mengubah template bawaan **tidak** sampai ke tenant lama;
  mengubah kerangka tenant **tidak** mengubah dokumen yang sudah beku.

## Kontrak 6 — Tenant kedua wajib di test

Setiap fitur yang membaca konsep variabel punya test dengan **template non-default** (fixture
bordir/sablon), dan bila menyentuh UI, dicek mata di tenant uji non-rajut (`bordir-uji`).
Test yang hanya memakai data rajut tidak membuktikan apa pun tentang tenant lain.

## Kontrak 7 — Menulis wajib fail-closed

Endpoint mutasi menolak bila keputusan RBAC tidak bisa dihitung (`mayEditWithoutDecision` di
`TenantStageFlowRoutes.kt`). Test wajib mencakup **peran yang tidak berwenang** (harus 403), bukan
hanya pengguna yang berwenang.

## Kontrak 8 — Konsep yang terlanjur enum: Strangler Fig

1. Bangun struktur data baru di samping enum; template bawaan **identik** dengan enum.
2. Test paritas yang **mengiterasi enum** (entri baru tanpa padanan → test gagal).
3. Jembatan (`toStageCode()`, konstruktor sekunder), pindahkan pembaca satu paket per PR.
4. Kriteria selesai: pemindai "pembacaan jembatan yang bisa melempar" kosong di semua lapisan.
5. Baru setelah itu aktifkan variasi kedua (template lain).

---

## Checklist Definition of Done

- [ ] Setiap `enum class` domain baru lolos Uji Variabilitas (tulis alasannya di KDoc)
- [ ] Tidak ada `when (enumDomain)` baru di `presentation/**` untuk konsep variabel
- [ ] Aturan domain memakai peran/trait, aman bila peran tidak ada
- [ ] Kunci tersimpan berupa value object; parser tidak fallback senyap
- [ ] Template vs salinan vs beku dijelaskan di KDoc
- [ ] Ada test dengan template non-default; cek visual di tenant uji non-rajut
- [ ] Endpoint tulis fail-closed + test peran tidak berwenang
- [ ] `scripts/audit-variability.sh` tidak menambah temuan baru

## Pelajaran TRD-FLOW-001 (bacaan)

`docs/teaching/teaching-flow-001-*.md` (Tahap 1 s.d. 3c) dan
[`docs/trd/TRD-FLOW-001-industry-stage-templates.md`](../../docs/trd/TRD-FLOW-001-industry-stage-templates.md).
