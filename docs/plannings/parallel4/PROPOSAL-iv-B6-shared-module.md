# USULAN B6 — Modul Bersama: `ModuleReference` + adaptor port

**Status:** usulan, **menunggu keputusan** · **Tanggal:** 2026-10-07 · **Penulis:** Agent B · **Induk:** [PLAN-iv-B-contract](PLAN-iv-B-contract.md) butir B6, plan induk §3

Tidak ada kode produksi yang berubah. Dua berkas tes mengunci usulan ini:
- `SharedModuleInvariantsCharacterizationTest` — perilaku **sekarang** (hijau, 4 tes).
- `SharedModuleReferenceProposalTest` — aturan **usulan**, ditulis sebagai fungsi murni di berkas tes itu sendiri (hijau, 6 tes). Belum menyentuh `DiscoveryDraftValidator` maupun `DomainPackRegistry`.

## 1. Fakta hari ini (terbukti tes karakterisasi)
1. **Id platform tidak bisa direbut.** Modul yang sudah ada di pack lain wajib berdefinisi identik; modul baru wajib berawalan `<kode pack>_`.
2. **Modul tata kelola/fondasi sudah bisa dipakai pack lain** lewat salinan identik (`org_chart` di fixture klinik).
3. **Modul operasional platform tidak bisa dipakai bersih.** Menyalinnya sah di registri, tetapi pack penyalin terpaksa membawa **slot, fase kanvas, dan port garment** (`TechPackAndYieldData`, `CostingCalculationResult`) — kosakata garment mencemari pack katering.
4. **Wawancara menolaknya dua kali:** modul tak ada di `pack.modules`, dan `REUSE_PLATFORM` hanya untuk modul non-operasional.

Jadi hambatannya bukan "id dilarang" semata, melainkan **kosakata port/slot yang ikut terseret**.

## 2. Usulan
`DomainPack.moduleReferences: List<ModuleReference>` (opsional, kosong bawaan, kunci JSON ditulis hanya bila ada — pack lama identik).
```kotlin
data class ModuleReference(val platformModuleId: ModuleId, val portMapping: Map<PortType, PortType>) // port pack → port slot platform
```
Aturan (R), semuanya galat **berpath** `$.pack.moduleReferences[i]…`:
- **R1** hanya modul **operasional bawaan platform** (punya slot) yang bisa dirujuk; id tak terdaftar atau modul tanpa slot ditolak (tata kelola tetap lewat salinan identik).
- **R2** `portMapping` **lengkap**: semua port masuk dan keluar slot modul terpetakan.
- **R3** kunci pemetaan harus port milik pack; nilainya harus port milik slot modul; satu-satu (tanpa dua port ke satu port).
- **R4** modul tidak boleh **sekaligus** didefinisikan di `pack.modules` dan dirujuk; tidak boleh dirujuk dua kali.
- **R5** pack bawaan platform (garment) **tidak boleh** punya rujukan.
- **R6** modul rujukan dihitung sebagai modul pack untuk wawancara (`effectiveModuleIds`); asalnya `REUSE_PLATFORM`.

## 3. Dua invarian yang disebut plan — apa yang berubah
| Invarian | Usulan |
|---|---|
| "Pack bawaan identik" | **Tidak dilonggarkan.** R5 mempertegasnya; garment tidak punya rujukan, tes paritas B1 tak tersentuh. |
| "Id platform dilarang" | **Tidak dilonggarkan untuk definisi.** Larangan merebut/menulis ulang id tetap. Yang baru: id platform **boleh dirujuk** (bukan didefinisikan) lewat daftar terpisah. |

Yang berubah di kode bila disetujui: `DomainPack` (+1 field), `DomainPackCodec`, `DomainPackRegistry.violations` (menerima rujukan, memeriksa R1–R5), `InterviewValidator` (modul pack = `pack.modules` + rujukan; `REUSE_PLATFORM` sah untuk rujukan), dan ringkasan server.

## 4. Risiko dan dampak
- **Konsumen `pack.modules` banyak** (kanvas, menu, RBAC, blueprint). Usulan: rujukan **tidak** masuk `pack.modules`; hanya `effectiveModuleIds` dipakai wawancara dan validator draf lebih dulu. Memperluas ke konsumen lain = keputusan terpisah.
- **Estimasi harga:** asal `REUSE_PLATFORM` lebih murah daripada `NEW`; bobotnya perlu keputusan produk.
- **Adaptor lossy:** `PortAdapterDescriptor` (dari/ke, `isLossy`) sudah ada untuk jalur garment. Usulan ini memakai pemetaan satu-satu **tanpa** konversi data; konversi bernilai (mis. agregasi) di luar lingkup.

## 5. Yang perlu diputuskan
1. **Modul "keuangan" yang dimaksud plan tidak ada sebagai modul operasional.** `invoicing` adalah modul **fondasi tanpa slot dan port**, sehingga R1 menolaknya sebagai rujukan. Tes memakai `costing_hpp` sebagai pengganti. Mana yang dimaksud: (a) rujukan hanya untuk modul berslot (usulan sekarang), (b) jadikan `invoicing`/keuangan modul operasional berslot dulu, atau (c) izinkan rujukan juga untuk modul fondasi?
2. **Apakah `costing_hpp` pantas jadi modul bersama?** Namanya "HPP" masuk kosakata cadangan garment; modul bersama yang dipakai pack lain perlu nama dan deskripsi netral.
3. **Cakupan konsumen:** cukup wawancara + validator draf dulu (usulan), atau langsung kanvas/RBAC?
4. Aturan rules `module-integration-rules.md` §5.1 menyebut `PortDataTypeRegistry`, yang **sudah tidak ada** (port kini data pack). Dokumen rules perlu diperbarui oleh koordinator.
