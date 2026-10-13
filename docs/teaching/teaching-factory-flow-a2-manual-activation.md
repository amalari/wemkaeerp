# 🎓 Modul Pembelajaran: Aktivasi Manual Node Factory Flow (PLAN A2)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Preset sebagai saran (bukan keadaan), entitlement & kuota paket, UI sebagai cermin gerbang server, i18n label klien, cek visual yang jujur
> **Prasyarat**: Tahu `CustomTenantPipeline` / `CustomPipelineNode`, konsep entitlement paket (`TenantModuleEntitlement`), dasar Compose + StateFlow
> **Referensi Task**: [`PLAN-factory-flow-a2-entitlement-activation.md`](../plannings/mvp/PLAN-factory-flow-a2-entitlement-activation.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: sebelum A2, tenant baru langsung "punya" modul sesuai preset (FOB/CMT/D2C). Padahal modul itu dijual per paket dan dihitung kuotanya. Tenant bisa tampak menjalankan modul yang tidak ia bayar, dan "aktif" berarti "ikut preset", bukan "dipilih tenant".
- **Analogi**: preset itu *menu paket hemat di restoran* — saran susunan hidangan. Pelanggan baru duduk dengan meja kosong, lalu memesan satu per satu. Pelayan (server) menolak pesanan yang tidak ada di paketnya; papan menu (UI) cukup tidak menampilkan tombol "pesan" untuk hidangan yang pasti ditolak.
- **Hasil akhir**: tenant baru melihat semua node berstatus **Nonaktif** + tombol **Aktivasi**; mengaktifkan hanya bisa bila modul termasuk paket dan kuota belum penuh; pabrik yang sudah berjalan (seed demo) tetap utuh lewat `withPresetActivation`.

---

## 🧭 2. "Start dari Mana?" — Order of Operations

1. **Kontrak dulu (T0)**: tulis keputusan di plan — semua node preset mulai bypassed, entitlement berlaku untuk node preset, reconciler menghormati status yang ada. Tanpa ini tiga orang akan menafsirkan "aktif" tiga cara.
2. **Core `CustomTenantPipeline.fromPreset`**: `isBypassed = true`. Mulai dari domain karena semua lapisan lain membaca dari sini.
3. **Jembatan untuk yang sudah berjalan**: `withPresetActivation(preset)` — terapkan saran preset untuk tenant yang memang sudah beroperasi (seed demo, "terapkan saran").
4. **Penegakan di server**: `SetTenantModuleActivationUseCase` → `SaveTenantPipelineUseCase` → `entitlement.validate`. Ini penegak sebenarnya (400 bila di luar paket/kuota).
5. **Cermin di UI**: fungsi murni `canActivateFromCanvas` + badge/tombol di `PipelineNodeCard`.
6. **Label dwibahasa** dari resource, bukan literal.
7. **Cek visual** — dan di sinilah bug terbesar ditemukan (§5).

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Preset hanya saran
```kotlin
// CustomTenantPipeline.fromPreset
isBypassed = true,  // Preset nodes mulai sebagai bypassed; tenant harus aktivasi manual

// PresetActivation.kt
fun CustomTenantPipeline.withPresetActivation(preset: Blueprint): CustomTenantPipeline {
    val suggested = PipelinePresetFactory.createSnapshot(preset).nodes.associate { it.id to it.isBypassed }
    return copy(nodes = nodes.map { it.copy(isBypassed = suggested[it.nodeId] ?: false) })
}
```
**Mengapa begini?**
- `fromPreset` jujur: ia *membuat struktur*, tidak memutuskan apa yang dijalankan. Status "jalan" milik tenant.
- `withPresetActivation` dipisah (bukan flag di `fromPreset`) supaya jalur tenant baru dan jalur "pabrik berjalan" tidak bercampur. Fungsi ini aman bila node tak ada di snapshot (`?: false`).

### Blok B: Server sebagai penegak
```kotlin
// SaveTenantPipelineUseCase
if (pipeline.activeNodes.isEmpty()) {
    val previous = pipelineRepository.findByTenantId(pipeline.tenantId)
    require(previous == null || previous.activeNodes.isEmpty()) { "Pipeline must keep at least one active module" }
}
entitlement?.validate(pipeline)  // kuota & grant paket
```
**Mengapa begini?**
- "Belum ada modul aktif" kini keadaan awal yang sah, jadi aturan lama "minimal satu aktif" diubah menjadi: *dilarang mematikan modul aktif terakhir* (dari ≥1 ke 0), bukan *dilarang 0*.
- `validate` mengecek **semua** pelanggaran sekaligus, jadi operator melihat gambaran penuh.

### Blok C: UI hanya cermin
```kotlin
fun TenantModuleCatalogSnapshot.canActivateFromCanvas(moduleId: String): Boolean =
    modules.firstOrNull { it.moduleId == moduleId }
        ?.let { it.isGrantedByPlan && !hasReachedPlanLimit } ?: false
```
**Mengapa begini?**
- Fungsi murni → bisa diuji tanpa Compose (`NodeActivationGateTest`). Repo belum punya infrastruktur Compose UI test, jadi logika keputusan sengaja dipisah dari gambar.
- Komentar KDoc menegaskan: **server tetap penegak**. UI yang menyembunyikan tombol bukan keamanan — hanya kesopanan.

### Blok D: Kanvas tidak boleh kosong
```kotlin
val hidesBypassedNodes: Boolean get() = hideBypassedNodes && snapshot.nodes.any { !it.isBypassed }
```
**Mengapa begini?** Lihat jebakan #1.

### Blok E: Label dwibahasa
```kotlin
@Composable
fun nodeInactiveLabel(language: LanguageCode): String =
    if (language == LanguageCode.EN) stringResource(Res.string.factory_flow_node_inactive_en)
    else stringResource(Res.string.factory_flow_node_inactive_id)
```
Mengikuti pola `i18n_pack_text_missing_id/_en`: bahasa dipilih dari **bahasa aplikasi**, bukan locale sistem, agar deterministik.

---

## ⚖️ 4. Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Alasan | Risiko alternatif |
|---|---|---|---|
| Preset → semua bypassed | Preset mengaktifkan sesuai sarannya | Aktif = pilihan tenant yang bisa ditagih | Tenant "memakai" modul di luar paket |
| Penegakan di server, UI cermin | Cek hanya di UI | Klien tak pernah dipercaya | Siapa pun bisa `POST` langsung |
| Gerbang tombol = fungsi murni | Logika di composable | Bisa diuji tanpa UI test | Bug tombol baru ketahuan lewat mata |
| Resource `_id/_en` | String literal | Aturan menu/i18n proyek | Teks Indonesia di UI berbahasa Inggris |

---

## ⚠️ 5. Jebakan Pemula

1. **Default "sembunyikan bypassed" + keadaan awal "semua bypassed" = kanvas kosong.**
   *Ditemukan lewat cek visual*: tenant baru dari preset CMT melihat "Tidak ada modul yang cocok dengan pencarian", tombol Aktivasi tak terjangkau. Dua keputusan masing-masing benar, gabungannya rusak. *Solusi*: `hidesBypassedNodes` menampilkan node bypassed bila tak ada satu pun yang aktif.
2. **Badge ganda.** "Nonaktif" ditambahkan di samping pil kesehatan "Di-bypass" → redundan dan memotong judul kartu. *Solusi*: node bypassed cukup satu penanda.
3. **Mengira UI yang menyembunyikan tombol = aman.** Tes server membuktikan 400 untuk aktivasi di luar kuota; tes UI hanya membuktikan cermin.
4. **Asumsi "tenant baru" pada data seed.** Tes kuota pertama saya gagal karena seed demo sudah punya 7 modul aktif > batas STARTER (5). Periksa keadaan awal data, jangan menebaknya.
5. **State persisten antar-run saat cek visual.** Skrip yang sama dijalankan dua kali menghasilkan hasil berbeda karena toggle sebelumnya tersimpan di DB. Setiap skenario harus tahu keadaan awalnya (atau pakai DB scratch baru).
6. **Dev server Wasm tidak membangun ulang otomatis** — gambar yang Anda lihat bisa versi lama. Restart server web sebelum menilai perubahan.
7. **`sed -i` di macOS** meminta argumen ekstensi; skrip yang gagal diam-diam membuat Anda melihat screenshot lama. Pakai Python/`perl` untuk edit portabel.

---

## 🧪 6. Bagaimana Membuktikan Kodingan Bekerja?

- **Core**: `PresetActivationStateTest` — semua preset mulai tanpa modul aktif; `withPresetActivation` pada preset non-default (CMT); reconciler tidak menimpa aktivasi manual.
- **Server (fail-closed)**: `PipelineModuleApiTest.postActivation_beyondPlanQuota_…` — semua aktivasi di atas kuota → 400 dan modul aktif tidak berubah.
- **Presentasi**: `NodeActivationGateTest` (paket/kuota/tak dikenal), `FactoryFlowBypassVisibilityTest` (3 preset; kanvas tak kosong).
- **Visual** (Chromium headless, skrip Playwright mandiri karena profil MCP terkunci): badge+tombol, EN ("Inactive"/"Activate"), kuota penuh (tombol tersembunyi), tenant baru starter CMT.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tambahkan alasan di UI saat tombol disembunyikan karena kuota penuh ("Naikkan paket" vs "Kuota habis") tanpa mengimpor domain ke `designsystem/`.
- [ ] **Tantangan 2**: Pasang Compose UI test di `app/shared` (jvmTest) dan buat tes badge + tombol tergambar. Pertimbangkan dampak dependensi ke 5 target.
- [ ] **Tantangan 3**: Jalankan `./gradlew :app:shared:assembleAndroidMain` di mesin ber-SDK — satu-satunya butir A2 yang belum terverifikasi (ditunda atas keputusan pemilik).

---

*Catatan proses*: A2 tidak punya baris `module_build_records`, jadi pencatatan effort ke ledger dilewati.
