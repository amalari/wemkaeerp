# 🎓 Modul Pembelajaran: Sinkronisasi Pipeline Tenant dengan Katalog Modul

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: DDD, Reconciliation Pattern, Snapshot vs Katalog, Idempotensi, Multi-Tenant Pipeline
> **Prasyarat**: Paham `CustomTenantPipeline`, `OperationalModuleCatalog`, dan alur `GET /api/tenant/pipeline`
> **Referensi Task**: Pertanyaan "kenapa Factory Flow tidak dynamic di-regenerate?"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: Pipeline tenant dibuat **sekali** dari preset (`GetTenantPipelineUseCase` →
  `CustomTenantPipeline.fromPreset`) lalu disimpan. Setelah itu, katalog modul boleh bertambah
  apa saja — tenant lama tidak akan pernah melihatnya. Satu-satunya jalan keluar adalah
  "Reset ke preset", yang **menghapus** nama kustom dan bypass milik tenant.
- **Analogi**: Pipeline tenant itu seperti *denah rumah yang sudah dibangun*. Katalog modul itu
  *brosur developer*. Kalau brosur menambah tipe ruangan baru, rumah yang sudah jadi tidak ikut
  berubah. Yang kita butuhkan bukan merobohkan rumah (reset), tapi **menambah pintu yang masih
  terkunci** — ruangannya kelihatan, tapi belum dipakai sampai pemilik membukanya.
- **Hasil akhir**: setiap `GET /api/tenant/pipeline` men-*top-up* modul katalog yang di-entitle
  tapi belum ada, disisipkan **dalam keadaan bypass** di posisi katalognya. Alur yang sedang
  berjalan dan kuota paket tidak berubah sedikit pun.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — Tentukan siapa sumber kebenaran untuk apa.**
   Katalog = *modul apa yang ada di produk*. Pipeline tersimpan = *keputusan tenant* (nama,
   urutan, bypass, wiring). Sinkronisasi hanya boleh **menambah yang tidak ada**, tidak pernah
   menimpa keputusan tenant. Aturan ini ditulis dulu sebelum satu baris kode pun.
2. **Langkah 1 — Fungsi domain murni** `PipelineCatalogReconciler.reconcile(pipeline, granted)`.
   Tanpa repository, tanpa coroutine — sehingga bisa diuji dengan data biasa.
3. **Langkah 2 — Use case** `SyncTenantPipelineWithCatalogUseCase`: baca → rekonsiliasi →
   simpan **hanya jika berubah**.
4. **Langkah 3 — Route**: `GET /api/tenant/pipeline` memanggil use case sinkronisasi, bukan lagi
   `GetTenantPipelineUseCase` langsung.
5. **Langkah 4 — Tutup celah perilaku**: modul hasil sinkronisasi tidak punya edge. Saat
   diaktifkan, `SetTenantModuleActivationUseCase` harus menyambungkannya ke alur.

Tidak ada perubahan skema DB maupun UI: kanvas sudah merender apa pun yang dikirim server lewat
`TenantPipelineProjector`.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A — Reconciler: identitas sebagai sinyal "tidak ada perubahan"

```kotlin
val missing = catalogOrder.filter { it in grantedModules && it.code !in installedCodes }
if (missing.isEmpty()) return pipeline
```

- Mengembalikan **instance yang sama** memungkinkan use case mengecek `reconciled === pipeline`
  dan melewati penulisan ke DB. GET yang sering dipanggil tetap cuma satu read.
- Filter `grantedModules`: modul yang tidak ada di paket tenant tidak dimunculkan sama sekali.

### Blok B — Posisi sisipan mengikuti urutan katalog

```kotlin
val predecessor = ordered.indexOfLast { node ->
    val nodePosition = node.standardModule?.let(catalogOrder::indexOf) ?: -1
    nodePosition in 0 until position
}
return predecessor + 1
```

- Modul QC baru mendarat **tepat setelah** node terakhir yang mendahuluinya di katalog (mis.
  Operator), bukan di ujung alur.
- Plugin kustom (`standardModule == null`) bernilai -1 sehingga dilangkahi.
- Setelah semua disisipkan, `stepOrderIndex` dinomori ulang 1..n agar tetap rapat.

### Blok C — Masuk dalam keadaan bypass

```kotlin
CustomPipelineNode(..., isBypassed = true)
```

- `TenantModuleEntitlement.permits` menganggap node bypass **tidak memakan lisensi**. Karena itu
  sinkronisasi aman dilakukan tanpa validasi kuota, dan tidak pernah membuat tenant PRO
  tiba-tiba "melebihi 9 modul".

### Blok D — Wiring saat modul diaktifkan

```kotlin
private fun wireIfIsolated(pipeline: CustomTenantPipeline, nodeId: String): CustomTenantPipeline {
    val isWired = pipeline.edges.any {
        !it.isFeedbackReworkLoop && (it.fromNodeId == nodeId || it.toNodeId == nodeId)
    }
    if (isWired) return pipeline
    ...
}
```

- Dulu penyambungan otomatis hanya terjadi di `installModule` (modul yang belum ada sama
  sekali). Modul hasil sinkronisasi *sudah ada* tapi tanpa edge, jadi jalurnya lain dan ia
  akan tetap terisolasi. Kedua jalur sekarang memakai helper yang sama.
- Node yang **sudah** di-wiring tenant dibiarkan persis apa adanya.

---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

| Pendekatan | Alternatif | Kenapa ini | Risiko alternatif |
|---|---|---|---|
| Rekonsiliasi saat baca (GET) | Migrasi DB setiap katalog bertambah | Otomatis untuk semua tenant, tanpa migrasi per rilis | Developer lupa menulis migrasi → tenant lama tertinggal lagi |
| Sisipkan dalam keadaan bypass | Sisipkan aktif | Alur produksi & kuota tidak berubah diam-diam | Tenant mendadak ditolak simpan karena kuota terlampaui |
| Tulis hanya jika berubah | Selalu `save()` | Idempoten, GET murah | Write amplification di endpoint yang paling sering dipanggil |
| Fungsi domain murni + use case tipis | Logika di route | Bisa diuji tanpa Ktor/DB | Logika bisnis bocor ke lapisan API (melanggar CLAUDE.md §2) |

---

## ⚠️ 5. Jebakan Pemula

1. **Memakai "Reset ke preset" sebagai sinkronisasi.** Nama kustom, bypass, dan parameter
   formula tenant ikut terhapus. Rekonsiliasi hanya boleh *menambah*.
2. **Menghapus node yang tidak ada di katalog.** Node itu bisa saja plugin kustom tenant.
   Reconciler sengaja tidak pernah menghapus.
3. **Selalu menyimpan setiap GET.** Tanpa cek identitas, tiap buka kanvas memicu write.
4. **Lupa wiring.** Node yang muncul tapi tidak tersambung akan terlihat aktif padahal tidak
   menerima alur apa pun.

---

## 🧪 6. Pembuktian

`core/src/commonTest/.../pipeline/PipelineCatalogSyncTest.kt` (6 test, semua hijau):

- `reconcile_whenNothingMissing_shouldReturnSameInstance` → `assertSame`
- `..._shouldInsertItBypassedInCatalogPosition` → QC tepat setelah Operator, bypass, nomor rapat
- `..._shouldKeepTenantRenamesAndActiveCount` → nama "Lini Jahit A" tetap, jumlah aktif & edge sama
- `reconcile_whenModuleNotGranted_shouldNotInsertIt`
- `sync_whenModuleMissing_shouldPersistOnce` → panggilan kedua identik (idempoten)
- `activate_syncedBypassedModule_shouldWireItFromPrecedingActiveNode`

Jalankan: `./gradlew :core:jvmTest --tests '*PipelineCatalogSync*'`

---

## 🏆 7. Tantangan Mandiri

- [ ] Tandai node yang modulnya sudah **dihapus** dari katalog sebagai *deprecated* (badge di
      kanvas) tanpa menghapusnya.
- [ ] Daftarkan fitur yang belum menjadi modul (Washing, Transfer/Custody, Traceability) ke
      `BusinessModule` + `OperationalModuleCatalog` dengan archetype yang tepat, lalu buktikan
      tenant lama otomatis mendapatkannya lewat sinkronisasi ini.
- [ ] Tambahkan test di mana tenant sudah menyisipkan plugin kustom di tengah alur, dan pastikan
      posisi sisipan tetap benar.
