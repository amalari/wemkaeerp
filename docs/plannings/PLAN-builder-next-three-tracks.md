# PLAN: Builder — Tiga Jalur Berikutnya (Siklus Build, Deploy/Versi, Narasi → Konfigurasi)

Dibuat 2026-10-08 setelah TRD-FLOW-003, TRD-PLAT-004, dan TRD-PLAT-005 masuk `main`.
**Status: usulan, belum ada TRD dan belum ada kode.** Temuan di §0 dibaca langsung dari kode; yang belum diverifikasi ditandai.
Tiap jalur di bawah memenuhi Gerbang 1 `wemade-feature-workflow`: berukuran sedang/besar → perlu TRD sendiri sebelum kode.

## 0. Temuan terverifikasi (membentuk seluruh plan ini)

| # | Temuan | Sumber |
|---|---|---|
| F1 | `DeployTenantUseCase`: pack **bukan bawaan** ⇒ *seluruh* modul aktif masuk antrean build (`BuildRequest` `QUEUED`) dan deployment `BLOCKED_ON_BUILD`; pack bawaan ⇒ langsung `ACTIVE`, draf dikunci, versi di-pin, trial dimulai | `DeploymentUseCases.kt` |
| F2 | **Tidak ada kode yang memindahkan deployment dari `BLOCKED_ON_BUILD` ke `ACTIVE`** setelah build `SHIPPED`. `POST /api/builder/build-queue/{id}/status` hanya `buildRequests.save(existing.copy(status = status))`: tanpa mesin status (QUEUED→SHIPPED langsung diizinkan), tanpa audit yang terlihat, tanpa efek ke deployment | `BuilderBuildQueueRoutes.kt`, grep `SHIPPED`/`BLOCKED_ON_BUILD` |
| F3 | Modul khusus tenant didaftarkan **saat kompilasi** lewat `TenantPackContributions` (TRD-PLAT-004 Track B). Jadi "SHIPPED" = sebuah rilis kode platform; tidak ada hubungan mesin antara status permintaan dan keberadaan modul di registri | `TenantPackContributions.kt` |
| F4 | **Koreksi atas TRD-PLAT-004 §2/§4.4**: TRD itu menulis mode DATA_DRIVEN "dilayani runtime generik `/m/{code}`". Pencarian rute tidak menemukan runtime generik itu: satu-satunya route modul pack data hanyalah hasil generator (`/api/tenant/modules/layanan_change_request`). Data-driven hari ini = **prototype interaktif**, bukan modul produksi. Pack kustom selalu butuh kode (konsisten dengan F1) | grep route, `LayananChangeRequestRoutes.kt` |
| F5 | Rollback: hanya ke deployment bernomor lebih kecil yang punya `packVersion`; gerbang data bila `blueprintRevision` turun dan tenant punya data operasional, kecuali `force` (wajib audit di pemanggil) | `RollbackDeploymentUseCase` |
| F6 | Tidak ada kode di `domain/discovery`, `domain/builder`, atau `domain/prototype` yang menyentuh `TenantLocationConfig` maupun rute serah terima. `SpecOp` (8 jenis) hanya mengubah entitas/layar prototype | grep |
| F7 | Draf berubah hanya lewat patch chat yang **diterapkan manusia** (`POST /chat/apply`), divalidasi ulang di use case | `BuilderRoutes.kt` |
| F8 | Handoff dan `assign` pack menegakkan pemilik pack (TRD-PLAT-005); **deploy builder tidak memanggil `AssignTenantDomainPackUseCase`**, dan di jalur `BLOCKED_ON_BUILD` tenant tidak di-pin ke versi pack | `DeploymentUseCases.kt` |

**Belum diverifikasi (jawab di Discovery tiap jalur):** bagaimana tenant builder mendapat `tenants.domain_pack` = kode pack kustomnya bila tidak lewat handoff; apakah `BuildRequest.moduleId` selalu sama dengan id modul di pack; apakah ada UI konsol yang memanggil endpoint status; isi `Deployment` state machine penuh (`BuilderDeployment.kt` hanya dibaca sebagian).

## 1. Urutan yang disarankan

```
Jalur 1  Siklus build ↔ registri ↔ deployment   (menutup F2/F3; paling berisiko: modul bisa "SHIPPED" tapi tidak hidup)
   └──► Jalur 2  Deploy, versi, kepemilikan     (menutup F8; bergantung pada Jalur 1 untuk jalur BLOCKED_ON_BUILD)
Jalur 3  Narasi → konfigurasi                    (paling besar dan paling sedikit fondasinya; sink belum ada, lihat F6)
```
Jalur 1 dan 3 tidak saling bergantung dan dapat berjalan paralel di worktree terpisah. Jalur 2 sebaiknya setelah Jalur 1.

---

## Jalur 1 — Siklus Build ↔ Registri ↔ Deployment

**Tujuan**: status `SHIPPED` bermakna dan aman; deployment berpindah ke `ACTIVE` lewat jalur yang dapat diaudit, dan hanya bila modulnya benar-benar ada.

**Perilaku yang diusulkan**
1. Mesin status `BuildRequestStatus` eksplisit (mis. `QUEUED→QUOTED→APPROVED→IN_PROGRESS→SHIPPED`, `REJECTED` dari tahap mana pun sebelum `SHIPPED`; `SUPERSEDED` tetap milik sistem). Lompatan ilegal ditolak 409.
2. `SHIPPED` mensyaratkan modulnya **terdaftar di `TenantPackContributions`** (id modul ada di salah satu kontribusi). Bila tidak, ditolak dengan pesan jelas.
3. **Go-live eksplisit**: endpoint/usecase `ActivateBlockedDeployment` (superadmin) yang hanya berhasil bila *semua* build request deployment itu `SHIPPED`; ia mengunci draf, men-pin versi pack, memulai trial (perilaku yang sama dengan jalur `ACTIVE` pada F1), dan mencatat audit. Rekomendasi: manual, bukan otomatis saat status terakhir `SHIPPED` — ada langkah verifikasi manusia di antara rilis kode dan go-live.
4. Setiap perubahan status tercatat audit (aktor, dari, ke).

| Track | Isi | Direktori |
|---|---|---|
| **A** | Mesin status + `ActivateBlockedDeployment` + tes domain (termasuk modul tenant non-`layanan` sebagai tenant kedua) | `core/domain/builder` |
| **B** | Endpoint status memakai mesin; endpoint go-live; cek registri; audit; tes HTTP 409/403/200 | `server` |
| **C** | Panel antrean di konsol builder: tampilkan transisi sah, tombol go-live, alasan penolakan | `app/shared/presentation/builder` |

**Keputusan yang diminta**: (J1-1) go-live manual vs otomatis; (J1-2) apakah `SHIPPED` untuk modul yang belum terdaftar ditolak (rekomendasi: ya).
**Risiko**: data lama dengan status yang melompat (QUEUED→SHIPPED) — mesin hanya menegakkan transisi baru, tidak menulis ulang yang lama; deployment `BLOCKED_ON_BUILD` yang sudah ada di produksi perlu kueri pemeriksaan sebelum rilis.
**Selesai bila**: tes domain dan HTTP hijau; satu modul tenant kedua (non-`layanan`) lolos siklus penuh di test; kueri produksi dijalankan.

---

## Jalur 2 — Deploy, Versi Pack, dan Kepemilikan

**Tujuan**: deploy builder tunduk pada aturan kepemilikan pack dan pin versi yang sama seperti handoff.

**Perilaku yang diusulkan**
1. Deploy memeriksa pemilik pack seperti `AssignTenantDomainPackUseCase` (pack berpemilik tenant lain ⇒ ditolak; pack bersama/milik sendiri ⇒ lolos). *Prasyarat Discovery*: pastikan bagaimana `tenants.domain_pack` tenant builder diatur (belum diverifikasi).
2. Jalur `BLOCKED_ON_BUILD` mencatat versi pack yang akan dipin agar saat go-live (Jalur 1) tenant tidak berpindah ke versi yang berbeda dari yang dibrief.
3. Rollback tidak boleh menurunkan tenant ke deployment yang packnya kini berpemilik tenant lain atau sudah tidak ada; gerbang data yang ada (F5) dipertahankan.
4. Pelaporan: ringkasan "pack, versi, pemilik, status" di konsol untuk tenant yang dipilih.

| Track | Isi |
|---|---|
| **A** | Pemeriksaan pemilik + pin versi di `DeployTenantUseCase`/`RollbackDeploymentUseCase`, tes dengan dua tenant dan pack berpemilik |
| **B** | Respons deploy/rollback membawa alasan penolakan; tes HTTP (409 pemilik, 403 non-superadmin); audit konsisten |

**Keputusan yang diminta**: (J2-1) apakah deploy oleh tenant pemilik pack bersama boleh memprivatkan ulang (rekomendasi: tidak; hanya handoff yang mengubah kepemilikan, sesuai TRD-PLAT-005 D4).
**Risiko**: mengubah deploy bisa menolak tenant yang berjalan hari ini; wajib kueri produksi (padanan B5 TRD-PLAT-005).

---

## Jalur 3 — Narasi → Konfigurasi (lokasi dan rute serah terima)

**Tujuan**: pengguna menceritakan susunan pabriknya ("rajut dan QC di Gedung 1, finishing di Gedung 2, operator antar langsung") dan itu menjadi konfigurasi tenant: `TenantLocationConfig` dan rute serah terima (`HandoverRoute` + mode).

**Mengapa ini jalur terbesar**: sink-nya belum ada (F6). `SpecOp` berpasangan dengan `PrototypeSpec`, sedangkan lokasi dan rute adalah **konfigurasi tenant yang hidup**, bukan spesifikasi prototype. Menumpangkannya ke `SpecOp` akan mencampur dua konsep dengan siklus hidup berbeda (draf beku vs konfigurasi yang berubah).

**Arah yang disarankan** (perlu TRD sendiri; ini hipotesis desain, bukan fakta kode):
1. **Kosakata tertutup baru, terpisah dari `SpecOp`**: operasi konfigurasi (mis. `DefineSite`, `PlaceStageAtSite`, `DefineHandoverRoute`, `SetHandoverMode`) yang **menolak** apa pun di luar kosakata (Kontrak 4: tolak, bukan fallback). Pemetaan ke `HandoverMode`/`HandoverRoute` yang sudah ada; mode di luar enum ditolak sebagai permintaan perubahan.
2. **Usulan, bukan penulisan langsung**: agen hanya mengusulkan; manusia menerapkan (pola F7). Konfigurasi baru ditulis lewat endpoint tulis yang sudah fail-closed (`PUT /route-settings`, `PUT /routes`, lokasi), sehingga RBAC dan validasi yang sama berlaku.
3. **Default satu atap**: tanpa penyebutan gedung, tidak ada operasi lokasi yang dihasilkan (selaras perilaku `TenantLocationConfig` bawaan).
4. Kosakata dibatasi pada **peran dan simpul alur yang ada di kerangka tenant** (`FlowNodeRef`); nama stasiun yang tak dikenal ditolak dengan pesan, bukan ditebak.

| Track | Isi |
|---|---|
| **A** | TRD + kosakata operasi konfigurasi (sealed, tertutup) + penerapnya yang murni + tes dengan tenant rajut dan bordir |
| **B** | Pemancar usulan dari narasi (deterministik dulu, `DeterministicSpecOpProposer` sebagai contoh pola; LLM kemudian) + endpoint "terapkan" yang memanggil endpoint tulis yang ada |
| **C** | Panel usulan di konsol builder: pratinjau diff konfigurasi sebelum diterapkan |

**Keputusan yang diminta**: (J3-1) kosakata baru terpisah dari `SpecOp` (rekomendasi) atau perluasan `SpecOp`; (J3-2) cakupan awal: hanya mode serah terima per rute yang sudah ada, atau juga lokasi/gedung sejak awal (rekomendasi: mode dulu — lebih kecil dan sinkron dengan TRD-FLOW-003).
**Risiko**: salah tafsir narasi mengubah disiplin bukti (DIRECT melonggarkan timbang/foto) — karena itu usulan hanya diterapkan manusia dan perubahan mode ditampilkan eksplisit di pratinjau.
**Prasyarat**: S3 TRD-FLOW-003 (hapus `SackRoute`) sebaiknya selesai lebih dulu, supaya kosakata rute tidak punya dua sumber kebenaran.

---

## 2. Yang sengaja di luar plan ini
- Runtime generik untuk modul pack data (F4: tidak ada hari ini; jadi bukan pekerjaan "kecil" yang bisa diselipkan).
- Marketplace modul, penagihan modul bersama.
- Memecah binary per tenant (TRD-PLAT-004 P3).
- Penegakan pemilik pack pada request-time (TRD-PLAT-005 D3).

## 3. Verifikasi umum tiap jalur
Gerbang 7 `wemade-feature-workflow`: kompilasi 5 target, tes core/app/server segar, `scripts/audit-variability.sh` tanpa temuan baru, tes dengan **tenant kedua** (modul/template non-default), tes 403 untuk peran tak berwenang, cek visual untuk Track C, dan teaching doc (CLAUDE.md §12).
