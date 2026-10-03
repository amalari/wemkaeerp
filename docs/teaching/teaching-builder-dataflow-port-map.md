# Teaching — Builder Data Flow: Peta Sambungan Port Antar Modul

> Slug: `teaching-builder-dataflow-port-map` · Tanggal: 2026-10-03
> Lingkup: `app/shared/.../presentation/discovery/DataFlowPane.kt`, `DataFlowModel.kt`,
> `core/.../domain/pack/DomainPack.kt`, `GarmentDomainPack.kt`, `core/.../shared/pack/DomainPackCodec.kt`,
> `commonTest/.../DataFlowModelTest.kt`, `CatalogPortVocabularyTest.kt`

## 1. Masalah

Pane **Data Flow** (`/builder/dataflow`) semula hanya menampilkan kartu statis per modul:
`"Masuk: X • Keluar: Y"`. Ia tidak menjawab pertanyaan yang justru paling dicari dari sebuah
peta aliran: *input modul ini datang dari modul mana, dan outputnya dipakai siapa?*

## 2. Keputusan Kunci

### 2.1 Sumber kebenaran sambungan = kontrak domain, bukan slot pack

Ada dua kandidat sumber port di repo ini:

| Sumber | Isi | Masalah bila dipakai |
|---|---|---|
| Slot pack (`slotInput`/`slotOutput` di draf) | Port default per **slot** | Rantainya bolong: `ProductionOrderDraft` tidak dikonsumsi slot mana pun, `CuttingOrderWithFabric` tidak dihasilkan slot mana pun |
| `OperationalModuleCatalog` (core) | `upstreamPrerequisites` / `downstreamHandoffs` / `referenceInputs` per **modul** | — |

Kita memilih katalog karena inilah kontrak yang sama yang dipakai **kanvas Factory Flow**
menyambung node (`keluar A ∩ masuk B`). Akibatnya peta Data Flow konsisten dengan kanvas.
Pelajaran menarik: rantai katalog ternyata **tertutup penuh** — `crm_sales` memang tanpa port
masuk (titik masuk alur), dan `tech_pack_bom` ikut memancarkan `MaterialRequisition` yang
dikonsumsi `inventory` (fakta yang tidak terlihat di slot pack sama sekali).

**Test pertama kami gagal justru karena asumsi awal memakai port slot pack** — bukan karena
kode salah, melainkan karena fixture mengasumsikan port dari sumber yang berbeda. Test lalu
diperbaiki mengikuti kontrak domain. Pelajarannya: pastikan dulu *sumber kebenaran mana* yang
dipakai sebelum menulis ekspektasi test.

### 2.2 Sambungan dihitung di klien, bukan di server

Draf (`GET /api/builder/draft`) sudah berisi daftar modul aktif; id-nya bisa diselesaikan ke
spesifikasi katalog lewat `OperationalModuleCatalog.specificationForCode()`. Karena itu nol
perubahan server/API. Modul yang **tidak ada di katalog** (plugin kustom) jatuh ke fallback
`slotInput`/`slotOutput` draf.

### 2.3 Label port = kosakata domain pack, bukan terjemahan di UI

Kode port (`ProductionOrderDraft`, `VerifiedMaterialStock`) adalah **identitas tersimpan** — ia
tidak boleh diubah demi tampilan. Tapi pengguna Builder tidak seharusnya membaca nama kelas.
Solusinya mengikuti pola `GarmentVocabulary` yang sudah ada:

- `DomainPack` mendapat field `portLabels: Map<String, String>` + accessor `portLabel(code)`
  (fallback = kode itu sendiri, jadi pack lama/pack pihak ketiga tanpa label tidak pernah crash).
  `init` memaksa **setiap kunci label harus terdaftar di `portTypes`** — label untuk port yang
  tidak ada = fail cepat saat konstruksi pack, bukan bug visual diam-diam.
- Label hidup di **pack**, bukan di Composable: UI (`DataFlowPane`) buta domain, menerima
  `String` lewat `PortHandoff.payloadLabel` / `ModuleDataFlow.slotLabel`. Ini Kontrak 6 design
  system — komponen bersama tidak boleh tahu `DomainPack`.
- Codec menyerialisasi `portLabels` sebagai field **opsional** → pack JSON lama tetap ter-decode
  (backward compatible), dan unknown key tetap divalidasi `DomainPack.init` bersama konstruksi
  langsung (satu set aturan, dua pintu masuk).
- Test gerbang: `CatalogPortVocabularyTest.allWiredAndSlotPorts_mustHaveHumanLabel` mengunci
  bahwa **setiap** port wired + port slot punya label — tipe port baru tanpa label = test merah,
  jadi daftar label tidak pernah basi.

**Rantai label sampai ke klien (jarak dari chat builder):** label ikut **dokumen draf**, bukan
kode. `DiscoveryDraftCodec` sudah menyertakan pack utuh (format keluaran AI), dan `summaryObj`
di `DiscoveryRoutes` kini menambahkan `portLabels` + `slotLabels` ke ringkasan yang diparse
klien. Prioritas `buildDataFlowMap`: **payload draf → registry → kode** — jadi draf pra-handoff
(pack hasil chat builder yang belum di-assign ke tenant, belum terdaftar di registry klien)
sudah berlabel juga. Fallback terakhir tetap kode mentah, bukan crash (Kontrak 4).

Pembagian peran di header kartu modul: badge kanan = **label ramah** (`Penerimaan Pesanan / PO /
Sales Ingestion`), subtitle redup di bawah nama = **kode slot** (`order_ingestion`) sebagai
identitas teknis. Label yang sama dua kali itu redundan; kode yang sama dua kali juga —
masing-masing tampil tepat sekali, di peran yang tepat.

## 3. Cara Kerja

`buildDataFlowMap(draft)` (murni, tanpa Compose):

1. Ambil modul `kind == OPERATIONAL` yang aktif — governance/foundation bukan stasiun aliran.
2. Petakan port tiap modul: katalog → fallback slot.
3. Untuk tiap tipe port: *penghasil* = modul aktif yang mengeluarkan tipe itu (self dikecualikan),
   *pemakai* = modul aktif yang menerima tipe itu (masuk atau rujukan).
4. Klasifikasikan serah-terima:
   - `from == null` → **input eksternal** (tidak dihasilkan modul aktif mana pun);
   - `to == null` → **keluaran akhir**;
   - `from == to` → **pemakaian internal** (modul memakai ulang outputnya sendiri);
   - `isReference` → masukan rujukan (dibaca, tidak dialirkan — QC membaca tech pack).

UI (`DataFlowPane`) hanya menggambar: badge ringkasan → kartu "Peta Sambungan Port"
(`[sumber] →(payload)→ [tujuan]`) → kartu per modul dengan baris `Masuk … dari …` /
`Keluar … ke …`.

## 4. Jebakan yang Ditemui

- **Port slot ≠ port kontrak modul.** Sampel kelas: slot `cutting` mengklaim masuk
  `CuttingOrderWithFabric`, padahal kontrak modul `production_mrp` masuknya
  `CostingCalculationResult + VerifiedMaterialStock`. Jangan campur dua sumber dalam satu peta.
- **`CostingHppModule.inputsFor(parameters)`** menyesuaikan port berdasar perilaku costing
  (makloon tidak menjumlahkan stok ke HPP). Pane ini memakai `upstreamPrerequisites` bawaan
  karena parameter blueprint belum ada di payload draf — catatan untuk pengembangan berikutnya
  bila peta per-tenant-makloon dibutuhkan.
- Kontrak 13 design system: elemen yang boleh menyusut diberi `weight(1f, fill = false)` +
  `maxLines` + `overflow`, terbukti perlu agar badge nama modul panjang tidak memecah satu
  huruf per baris di lebar sempit.

## 5. Verifikasi

- `DataFlowModelTest` (8 kasus): rantai lengkap, rujukan QC, ujung alur, ringkasan hitungan,
  eksklusi governance, fallback modul kustom (+kunci label `AnyOperationalPayload`), dan
  **draf pra-handoff berlabel dari payload sendiri** (pack `klinik_uji` tak terdaftar) — hijau
  via `:app:shared:jvmTest`. `DiscoveryApiTest` hijau setelah `summaryObj` menambah
  `portLabels`/`slotLabels`.
- `CatalogPortVocabularyTest` (3 kasus): semua handoff katalog wired di pack, label slot
  `crm_sales`, dan gerbang "setiap port wired/slot wajib punya label" — hijau via `:core:jvmTest`.
- Kompilasi JVM + WasmJs + JS + jvmTest hijau. (Target Android tidak bisa dijalankan di mesin
  ini: tidak ada Android SDK / `ANDROID_HOME` — keterbatasan environment, bukan perubahan.)
- Dicek mata di `http://localhost:3001/builder/dataflow` pada lebar penuh dan 1280px: peta
  sambungan, baris Masuk/Keluar, dan header kartu semuanya menampilkan label Indonesia
  (`Draf Pesanan Produksi (PO)`, `Stok Bahan Terverifikasi`, `Unit Lolos QC (Tergrade)`), tanpa
  teks pecah satu huruf per baris.

## 6. Kapan Mencocokkan Pola Ini

Pane read-only yang menjawab "*dari mana, ke mana*" dari **kontrak port domain** yang sudah
ada — tanpa endpoint baru — adalah pola murah yang bisa ditiru untuk tampilan dependensi lain
(mis. peta antar tahap di Factory Flow, atau peta referensi antar modul foundation).
