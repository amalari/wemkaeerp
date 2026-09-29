# 🎓 Modul Pembelajaran: Kanvas Factory Flow dari Katalog (TRD-FLOW-002 Fase 2–5)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Port sebagai sumber sambungan, Strangler Fig dengan test paritas, fitur level 2, telemetri jujur
> **Prasyarat**: `teaching-feature-workflow-and-agent-sync.md` (Fase 1), `module-integration-rules.md` §5
> **Referensi**: [`docs/trd/TRD-FLOW-002-catalog-driven-factory-flow.md`](../trd/TRD-FLOW-002-catalog-driven-factory-flow.md)

---

## 💡 1. Konsep Dasar

Kanvas lama adalah **gambar**: node, edge, dan angka ditulis tangan. Kanvas baru adalah **hasil
hitung** dari katalog. Bedanya terasa saat menambah modul: dulu harus menyunting 3 preset (dan
lupa satu), sekarang cukup satu spec — dan lupa port = test merah.

```
OperationalModuleCatalog.all ──► CatalogPortWiring (edge dari port)
            │                              │
            ▼                              ▼
   CatalogPipelineBuilder ◄──── PresetNodeSeeds (teks & angka contoh)
            │
            ▼
   FactoryPipelineSnapshot ──► PipelineTelemetryOverlay ◄── /api/tenant/pipeline/telemetry
            │
            ▼
   Kanvas level 1  ──klik──►  Level 2: ModuleFeatureRegistry + TenantStageFlow + stageWip
```

---

## 🧱 2. Bedah Keputusan

### Blok A — Port, bukan daftar (Fase 2)

```kotlin
// A → B bila keluaran A dipakai B, sebagai aliran atau rujukan
reads(a, b) = flows(a, b) + (a.outputsFor(preset) ∩ b.referenceInputs)
```

Test konsistensi **dulu gagal** di HEAD — QC meminta `FinishedGarmentUnit` yang tidak diproduksi
siapa pun. Itu bukti celahnya nyata, bukan teori. Perbaikannya keputusan bisnis (D1/D2 di TRD),
bukan tambal tipe.

### Blok B — Strangler Fig dengan paritas persis (Fase 3)

Builder baru harus menghasilkan kanvas **identik** dengan preset lama untuk 3 preset × 3 skenario
sebelum pembaca dipindah. Paritas menangkap dua hal halus: skenario CMT yang *mengaktifkan*
Gudang (maka seed modul non-aktif dipertahankan), dan D2C yang lupa port QC di seed.

### Blok C — Fitur ≠ modul (Fase 4)

Washing, storage, surat jalan **tidak** jadi `BusinessModule` (akan memecah kuota & RBAC). Mereka
entri `ModuleFeature` di bawah induk. `RouteOwnershipTest` mengenumerasi semua route yang terpasang;
route `/api/tenant/…` tanpa pemilik = merah. Test ini langsung menemukan `/api/tenant/info` yatim.

### Blok D — Angka yang jujur (Fase 5)

```kotlin
when {
    node.isBypassed -> node                                   // tidak disentuh
    reading == null -> node.copy(isTelemetryEstimate = true)  // seed boleh tampil, tapi berlabel
    else -> node.copy(wipPieces = reading.wipPieces, …)       // nyata
}
```

Angka contoh yang terlihat nyata lebih berbahaya dari angka kosong: klien mengambil keputusan
dari kanvas. Maka setiap angka non-nyata tampil "±N Pcs estimasi", redup.

Provider dijalankan **paralel dengan timeout 2 detik** — satu repository lambat tidak boleh
membekukan kanvas; modul yang gagal hanya jatuh ke "estimasi".

---

## ⚠️ 3. Jebakan yang Benar-benar Terjadi

1. **Tag tambahan di footer kartu** memecah teks "Detail" jadi "Det/ail", lalu chip "4.0h" jadi
   vertikal. Ruang kartu clay sempit (Kontrak 12/13). Solusi: label masuk ke tag WIP yang sudah ada,
   metrik diberi `weight(1f, fill = false)`, teks penting `softWrap = false`. Hanya ketahuan dengan mata.
2. **Continuous build web diam-diam berhenti** — screenshot menampilkan bundle lama. Cek waktu
   terakhir log sebelum menilai UI.
3. **Test server dengan repository Postgres** tanpa DB → `Database.connect()` fatal di coroutine.
   Uji logika di core (overlay, codec, provider dengan repo palsu); route dibuktikan via curl.
4. **Ratchet**: `OperationalModuleContract.kt` (514, sudah di atas hard 400) sempat bertambah 13.
   Dipecah per konsep: agregat `CustomTenantPipeline` ke file sendiri → 347.

---

## 🧪 4. Pembuktian

- `bordir-uji`: API `stageWip={HOOPING:1, THREAD_TRIMMING:1}` = DB (SPK ketiga di Flow Review,
  tahap masuk). Kanvas: "4 Pcs WIP · 5.8h"; level 2 "2. Hooping · 1 SPK".
- Test: `ModuleRegistrationConsistencyTest`, `CatalogPipelineBuilderTest`, `RouteOwnershipTest`,
  `SamplingTelemetryProviderTest` (kerangka bordir), `PipelineTelemetryOverlayTest`.

## 🧭 5. Menambah Telemetri Modul Baru

1. Tulis `class XTelemetryProvider(repo) : ModuleTelemetryProvider` di paket domain modul itu.
2. Daftarkan di `pipelineTelemetryRoutes(providers = …)` di `ServerRouteWiring`.
3. Test dengan tenant non-rajut. Tag "estimasi" di kartu modul itu hilang sendiri.
