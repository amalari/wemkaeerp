# 🎓 Modul Pembelajaran: Usulan Layar Prototype dari Data Pack (Mock Otomatis `/builder/prototype`)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design (data pack sebagai kosakata vertikal), Compose Multiplatform renderer, Backfill idempoten, Codec ketat (fail-closed)
> **Prasyarat**: Memahami `DomainPack` (B0–B7), `DiscoveryDraft`/`PrototypeScreen`, dan kosakata tertutup `WidgetKind` (baca dulu `teaching-builder-dataflow-port-map.md`)
> **Referensi Task**: PLAN-builder-console — pane Prototype; sesi label Data Flow sebelumnya

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Tab Prototype di `/builder/prototype` merender layar dari **draf kerja tenant**
(`GET /api/builder/draft`). Draf itu di-bootstrap oleh `EnsureTenantWorkingDraftUseCase` — dan
bootstrap lama sengaja mengisi `screens = emptyList()`. Satu-satunya jalan agar layar muncul adalah
agent LLM (`DISCOVERY_AGENT=koog` + `DEEPSEEK_API_KEY`) lewat chat. Akibatnya hampir semua tenant
(mis. `wemade-demo`) melihat kartu kosong *"Draf ini belum punya layar pratinjau…"* — padahal
mesin renderernya (`PrototypeRenderer` + `WidgetRegistry`) sudah matang dan bisa menggambar 7 bentuk
widget.

**Analogi sederhana.** Pack itu kamus vertikal: ia sudah menentukan cara pabrik konveksi *menyebut*
PO, kain, dan surat jalan. Kalau kamus sudah tahu modul apa saja yang aktif, seharusnya kamus itu
juga bisa *mengusulkan* "untuk modul Surat Jalan, layarnya wajar berbentuk dokumen cetak". Yang
tidak boleh: mesin renderer menebak-nebak sendiri istilah garment — itu bocornya data vertikal ke
kode platform.

**Hasil akhir.** Tenant garment yang baru di-bootstrap langsung melihat 9 kartu mock
(Daftar PO & Prospek → tabel daftar, Papan SPK Sampling → kanban, …, Surat Jalan & Packing List → cetak)
tanpa API key apa pun — dan pack klinik kelak mengusulkan layar kliniknya sendiri, karena usulan
layar adalah **data pack**, bukan kode.

---

## 🧭 2. "Start dari Mana?" — Order of Operations

1. **Langkah 0: Uji Variabilitas dulu** (tenant-variability-rules Kontrak 1). Apakah "layar apa yang
   wajar untuk modul X" bisa berbeda antar vertikal? Ya — klinik tidak punya surat jalan. Jadi
   usulan layar = **data**, bukan `when` di renderer.
2. **Langkah 1: Value object di `core/domain/pack`** — `ScreenSuggestion(moduleId, title, widget)`.
   `widget` bertipe enum `WidgetKind` (kosakata sistem, tertutup): compiler yang menjaga, bukan
   regex string.
3. **Langkah 2: Field di `DomainPack` + invarian** — `screenSuggestions: List<ScreenSuggestion> =
   emptyList()` dengan `init` yang menolak modul tak dikenal dan judul kosong. Satu tempat
   validasi untuk konstruksi langsung *dan* hasil dekode codec.
4. **Langkah 3: Codec** — field opsional saat dekode (pack lama tetap hidup), diketatkan saat ada:
   widget di luar `WidgetKind` ditolak dengan path `$.screenSuggestions[0].widget` supaya AI/penyunting
   tahu baris mana yang salah (Kontrak 4: nilai tak dikenal tidak boleh hilang diam-diam).
5. **Langkah 4: Data pack garment** — `GarmentScreenSuggestions`: 9 layar, satu per modul operasional.
6. **Langkah 5: Use case bootstrap** — filter usulan ∩ modul aktif → `PrototypeScreen`, plus backfill
   draf kosong sekali.
7. **Langkah 6: Test** — paritas (setiap modul operasional punya satu layar), codec round-trip,
   perilaku bootstrap/backfill/idempoten.

---

## 🔍 3. Bedah Kode Blok per Blok

### 3.1 `ScreenSuggestion` — kenapa judulnya di pack, widget-nya di enum sistem?

```kotlin
data class ScreenSuggestion(val moduleId: ModuleId, val title: String, val widget: WidgetKind)
```

- **Judul** ("Surat Jalan & Packing List") adalah *kosakata vertikal* — harus ikut pack, sama seperti
  `portLabels`. Kalau judul di-generate kode (`"Layar " + displayName`), dua vertikal dipaksa satu
  gaya bahasa.
- **Widget** (`PRINT`) adalah *kosakata sistem*: renderer harus bisa menggambar setiap kind di semua
  vertikal, jadi ia enum tertutup. Mengetiknya sebagai enum berarti dekode codec tinggal
  `s.enum("widget", WidgetKind.entries)` — nilai aneh otomatis ditolak.

### 3.2 Invarian `DomainPack.init` — satu gerbang, dua pintu masuk

```kotlin
requireUnique("usulan layar", screenSuggestions.map { it.moduleId.value })
screenSuggestions.forEach { s ->
    require(s.moduleId.value in moduleIds) { "Usulan layar menunjuk modul tak dikenal …" }
}
```

Ini pola yang sama dengan `portLabels`: codec *tidak* menduplikasi aturan — ia membungkus galat
konstruksi dengan path JSON (`Reader.build`). Encode selalu menulis field; dekode memaafkan
ketiadaannya (`objectsOrNull`). **Jebakan yang dihindari**: fallback senyap ke kosakata pack lain
bila field hilang — kosong berarti "belum mengusulkan", bukan "pakai punya garment".

### 3.3 Backfill idempoten di `EnsureTenantWorkingDraftUseCase`

```kotlin
drafts.findByTenant(tenantId)?.let { existing ->
    if (existing.draft.screens.isNotEmpty()) return existing   // draf berlayar → biarkan
    val screens = backfillScreensFor(existing) ?: return existing   // pack tanpa usulan → biarkan
    return runCatching { drafts.save(existing.copy(…)) }.getOrNull() ?: existing
}
```

Kontrak idempoten lama ("tenant yang sudah punya draf tidak pernah disentuh") membuat fitur ini
tidak pernah terlihat oleh tenant yang draft-nya sudah ada — termasuk `wemade-demo`. Karena itu
kontraknya diperhalus jadi tiga kasus: **(a)** draf punya layar → tidak disentuh; **(b)** draf
kosong → backfill **sekali** (setelah itu kasus a); **(c)** pack tanpa usulan → tetap kosong, jujur.

**Jebakan yang benar-benar terjadi saat implementasi**: backfill pertama memakai
`existing.draft.pack` — dan itu *snapshot* JSON di `ops.discovery_drafts` yang ditulis codec lama,
**tanpa** field `screenSuggestions`. Akibatnya `screenCount` tetap 0 walau pack garment terkini
sudah mengusulkan 9 layar. Solusinya: sumber usulan = pack **registry terkini**
(`DomainPackRegistry.find`), tapi gerbangnya tetap snapshot draf — hanya usulan yang modulnya ada
di dokumen yang lolos, dan field `pack` draf **tidak pernah diganti**. Pelajaran umumnya: dokumen
yang membeku (Kontrak 5) tidak ikut tumbuh saat kosakata pack bertambah; pembaca harus sengaja
memilih sumber mana yang "hidup".

### 3.4 Penanda asal layar: `screenId = "default-<moduleId>"`

Mock dari data pack dan layar buatan agent dibedakan dari id-nya. Murah, tetap string polos
(`PrototypeScreen` tidak diubah), dan test bisa menguncinya.

---

## ⚖️ 4. Keputusan Arsitektural & Trade-off

| Keputusan | Alternatif yang ditolak | Kenapa |
|---|---|---|
| Usulan layar = data pack | Fixture mock di UI klien | Melanggar netralitas platform: kode mesin tidak boleh menyebut konsep satu industri (Jalur B, aturan 4) |
| Backfill draf kosong | Minta tenant hapus draf manual / migrasi SQL | Draf adalah dokumen JSONB; backfill di use case = satu tempat, idempoten, dan tervalogis oleh test |
| `widget` enum di value object | `String` + parser | Enum menutup kosakata di compile-time; parser string adalah tempat bug "fallback senyap" lahir |
| Field opsional di codec | Menaikkan `schemaVersion` pack | Pack lama valid; tidak ada transformasi yang dibutuhkan — kosong punya arti jelas |

---

## 🧪 5. Verifikasi yang Menuntun (Tests First)

- `GarmentScreenSuggestionsTest` — paritas: setiap modul operasional garment punya tepat satu
  usulan; semua usulan menunjuk modul operasional pack sendiri; judul kalimat manusiawi (bukan kode
  ber-underscore).
- `DomainPackCodecTest.screenSuggestions_optionalField_roundTrips_andIsRejectedWhenInvalid` —
  pack lama tanpa field → kosong; round-trip identik; widget `MAGIC` → 400 berpath;
  moduleId hantu → ditolak invarian `$`.
- `EnsureTenantWorkingDraftUseCaseTest` — bootstrap baru → 9 layar (FOB aktif penuh);
  draf berlayar → `assertSame` (tak tersentuh); draf kosong → backfill sekali lalu idempoten
  (`updatedAt` tidak berubah di pemanggilan kedua).
- Kompilasi 5 target: JVM, WasmJs, JS, server hijau; Android tetap terbatas environment mesin ini
  (SDK tidak terpasang — batas pra-eksisting, bukan regresi).

---

## 🏆 6. Tantangan Mandiri

- [ ] Buat `KlinikScreenSuggestions` untuk pack klinik (`docs/packs/klinik-uji.pack.json`): layar
      antrean pasien wajar widget apa? Pastikan test paritas klinik hijau tanpa menyentuh renderer.
- [ ] Tambahan opsi "hapus mock" di pane Prototype: mock `default-*` boleh dibuang per layar, tapi
      layar `agent-*` tidak — petakan aturannya ke use case mana?
- [ ] Pikirkan: jika tenant menonaktifkan modul lewat draf (patch blueprint), kapan mock layarnya
      seharusnya ikut hilang? Cari titik eksekusi yang benar (patch use case vs render time).

---

## 🔄 7. v2 (2026-10-03): Isi Layar Nyata — "kok bukan kayak aplikasi?"

Feedback prospek setelah v1 tayang: mock-nya benar *struktur*nya tapi tidak terlihat seperti
aplikasi — `Kolom / Baru`, `… contoh 1`, tabel yang hanya menggambar baris pertama. V2 menjawabnya
tanpa mengubah satu konsep pun dari v1: **isi layar = data pack** dikejar sampai ke level kalimat.

### Apa yang berubah

| Lapis | Sebelum (v1) | Sesudah (v2) |
|---|---|---|
| `ScreenSuggestion` | `moduleId, title, widget` | + `sampleRows: List<Map<String,String>>` (default kosong, invarian anti-baris-kosong) |
| `GarmentScreenSuggestions` | 9 judul + watak widget | + isi garment nyata: `PO-2026-0312`, `PT Sinar Jaya`, `1.200 pcs kemeja PDH`, `Rp 38.500/pcs` — dipindah ke filenya sendiri (`GarmentScreenSuggestions.kt`) |
| `WidgetRegistry` | selalu generik | usulan pack dipakai **apa adanya** bila widget layar cocok; widget lain tetap generik |
| Renderer | KANBAN = 3 kartu datar; TABLE = baris pertama saja | KANBAN = kolom (`Kolom`) berisi tumpukan kartu (judul tebal + detail redup); TABLE = header + semua baris; PRINT/FORM/DASHBOARD/CHECKLIST tetap kontrak lama |
| `BuilderPrototypePane` | tumpukan semua layar | filter modul (`Semua Modul` + chip per modul) + badge `x dari n layar` |
| Server `summaryObj` | rows dari snapshot draf | rows dari **pack registri hidup** (`DomainPackRegistry.find(code) ?: snapshot`) |

### Pelajaran berulang: snapshot beku, kedua kalinya

Draf `wemade-demo` menyimpan snapshot pack **sebelum** `sampleRows` ada. Backfill layar (v1) sudah
belajar memilih registri hidup sebagai sumber usulan; v2 menghadapi jebakan yang sama di tempat
lain: **render time**. Kalau `summaryObj` tetap membaca snapshot, draf lama selamanya menggambar
baris generik meski pack baru kaya — dan tidak ada test yang menangkapnya, karena draf baru
(selalu dipakai test) tidak pernah beku. Aturannya sekarang tegas: *kosakata tampilan yang baru
ditambahkan ke pack dibaca dari registri hidup; snapshot draf hanya jadi gerbang modul & data
kontrak.* Dokumen beku tidak pernah ditimpa, tapi ia juga tidak boleh jadi batu sandungan kosakata.

### Kontrak bentuk baris (supaya pack lain tidak menebak)

`sampleRows` dikonsumsi per widget (kontrak lengkap di KDoc `ScreenSuggestion`):
KANBAN berkunci `Kolom`; FORM satu baris berkunci `Simpan` untuk tombol; CHECKLIST berkunci
`Selesai` = `ya`/`tidak`; TABLE = baris-baris berkunci identik (kunci pertama = header);
DASHBOARD = satu pasang label→angka per baris; PRINT = pasangan label→isi dokumen.

### Verifikasi

- `GarmentScreenSuggestionsTest.sampleRows_richEnoughToLookLikeTheApp_shapedPerWidget` — semua
  usulan punya baris, nol kata "contoh", bentuk sesuai widget (kolom ≥2, tabel ≥3×3 konsisten).
- `WidgetRegistryTest` baru — baris pack garment dipakai persis; widget beda → generik, bukan
  salah bentuk. Test klinik lama membuktikan pack tanpa sampleRows tak berubah.
- `DomainPackCodecTest.screenSuggestions_sampleRows_optional_...` — round-trip; pack lama tanpa
  field → kosong; nilai non-string → 400 berpath `$.screenSuggestions[0].sampleRows[0].Kartu`.
- API: `GET /api/builder/draft` (wemade-demo, draf lama beku) kini mengirim 9 layar dengan baris
  kaya — fallback registri hidup terbukti bekerja tanpa menulis ulang draf.
- Kompilasi JVM/WasmJs/JS/server hijau + `:core:jvmTest`, `:app:shared:jvmTest` hijau; Android
  tetap terbatas environment (SDK tidak ada — pra-eksisting).

## 📱 8. v2.1 (2026-10-03): Bingkai Perangkat & Proyeksi `default-*` — "kok ga kaya prototype apps?"

**Keluhan**: isi layar sudah nyata, tapi pratinjau membentang penuh ~1700px — terlihat seperti
dokumen, bukan aplikasi. Dan layar "Daftar PO & Prospek" dirender sebagai satu form entry.

**Dua perbaikan**:

1. **Bingkai perangkat** (renderer): setiap layar kini digambar sebagai frame berlebar tetap
   `ClayPaneWidth.PrototypeDevice` (360dp, token baru di `ClayTokens.kt`), disusun berjajar
   `ClayFlowRow` — di lebar 1280dp terlihat ±3 mockup berdampingan. `PrototypeRenderer` dipakai
   4 call site (Builder prototype, wizard Discovery, Prototype Studio, Chat result), semuanya
   ikut tanpa perubahan signature.
2. **"Daftar" berarti daftar** (data pack): usulan CRM berubah FORM → TABLE berisi 3 PO nyata
   (PT Sinar Jaya, CV Amanah, PT Cahaya Tekstil). Widget = watak layar; form entry bukan watak
   layar daftar.

### Pelajaran ketiga: widget ikut beku, bukan cuma baris

V2 memproyeksikan **baris** dari registri hidup, tapi **widget** tetap dibaca dari snapshot —
sehingga begitu pack mengubah watak layar (FORM → TABLE), `sampleRowsFor` melihat widget tidak
cocok dan diam-diam jatuh ke baris generik. Proyeksi kini utuh: `WidgetRegistry.screenFor`
mengambil alih **judul + widget** layar `default-*` dari pack hidup; layar kustom (agent LLM /
suntingan user) tetap beku. Rumus yang sama tiga kali terbukti: *snapshot beku untuk yang
milik user, registri hidup untuk yang milik pack*.

### Verifikasi v2.1

- `WidgetRegistryTest.layar default diproyeksikan...` — default-* diambil alih pack; modul
  asing & layar kustom tidak.
- API: draf beku `wemade-demo` kini mengirim `Daftar PO & Prospek | TABLE` + 3 baris PO.
- Uji visual menunggu hard-refresh `/builder/prototype` (webpack 3001 hot-reload).

### Perbaikan lanjutan v2.1.1: sel tabel satu baris (Kontrak 13 di frame 360dp)

Hasil cek mata pertama: frame sudah benar (3 mockup per baris di 1280px), tapi isi tabel CRM
pecah per kata — "PT Sinar Jaya" jadi 3 baris, "28-Mar-2026" patah dua. Penyebabnya aritmetika:
360dp ÷ 5 kolom `weight(1f)` ≈ 60dp/kolom, dan sel lama boleh `maxLines = 2`, sehingga Nunito
ber-x-height besar melipat setiap kata. Perbaikan mengikuti Kontrak 13 design system: sel tabel
mockup dibuat seperti baris tabel aplikasi nyata — `maxLines = 1` + `softWrap = false` +
`weight(1f, fill = false)` + `Ellipsis`. Teks yang tidak muat menyusut dengan tanda potong,
tidak pernah patah kata.

Pelajaran sampingnya soal *tooling*: continuous build Gradle di volume eksternal `/Volumes/…`
bisa diam-diam berhenti bereaksi terhadap perubahan file (watch service gagal senyap), bahkan
tertahan lock dari proses Gradle client lain yang statusnya `STOPPED`. Gejalanya bukan error —
hanya "halaman tidak pernah berubah". Diagnosisnya: bandingkan `stat` mtime file sumber vs
`build/kotlin-webpack/**/developmentExecutable/webApp.js`; obatnya bunuh proses beku, lalu
nyalakan ulang frontend. Karena webpack dev server menyajikan dari memori, mtime file di disk
bukan bukti stale — satu-satunya ujujan yang sah adalah memuat halamannya.
