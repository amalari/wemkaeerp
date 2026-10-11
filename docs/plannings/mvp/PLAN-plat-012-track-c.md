# PLAN: TRD-PLAT-012 Track C — Penyatuan Bypass `TENANT_ADMIN` (Q3) + UX 403 (Q2)

**Tanggal**: 2026-10-11 · **Penulis**: Achmad Jamaludin · **Jalur**: B (repo ini)
**Spesifikasi**: [`TRD-PLAT-012`](../../trd/mvp/TRD-PLAT-012-factory-flow-gates-fail-closed.md) §Pertanyaan Terbuka Q2/Q3 dan §Deployment Track C.
**Prasyarat**: Track A selesai (2026-10-10, ter-merge); Track B selesai sebagian (probe generik per prefix + tes karakterisasi `null`).

---

## 1. Konteks

Dua jalur perhitungan wewenang memberi pendapat berbeda untuk **token `TENANT_ADMIN` (owner pabrik)
yang ber-divisi tetapi tanpa jabatan** (`customRoleId == null`, `departmentId != null`):

| Jalur | Lokasi | `isOwnerOrSuperAdmin` | Hasil untuk kasus tersebut |
|---|---|---|---|
| `callerDecisions` (satu jalur untuk menu klien, `/me/access`, gerbang modul generik) | `ModuleAccessGuard.kt:99` | `(superadmin \|\| TENANT_ADMIN) && role == null` → **true** | `OWNER_BYPASS` → MANAGE |
| `factoryFlowDecision` (gerbang Factory Flow: pipeline, stage-flow, locations, telemetry) | `FactoryFlowAccessGuard.kt:101-102` | `superadmin && role == null` saja → **false** | dinilai matriks; tanpa jabatan = NONE → 403 |

Akibatnya owner yang sama melihat menu/modul diizinkan tetapi gerbang Factory Flow menolak — keputusan
wewenang tak tunggal, melanggar kontrak "satu jalur perhitungan" di KDoc `callerDecisions`.

**Q3 (dikerjakan sekarang)**: seragamkan dengan mengganti `factoryFlowDecision` agar mendelegasi ke
`callerDecisions` — persis rekomendasi TRD. Guard lain (`OrgChartAccessGuard`, `CrmAccessGuard`) belum
dipindahkan; itu refactor terpisah (KDoc `ModuleAccessGuard` sudah mencatatnya).

**Q2 (direncanakan, belum dikerjakan)**: lembutkan UX 403 Factory Flow — pesan yang menjelaskan cara
meminta akses (kepada siapa) alih-alih pesan mentah. Butuh rancangan konten + cek visual; sengaja
dipisah agar Q3 (perilaku keamanan) tidak tercampur dengan keputusan UX.

## 2. Q3 — Ruang lingkup

### Perubahan perilaku (satu-satunya)
Token `TENANT_ADMIN` ber-divisi tanpa jabatan: dari dinilai matriks (NONE) menjadi `OWNER_BYPASS`
(MANAGE), selaras dengan `callerDecisions`. Kasus lain tidak berubah:

- Tanpa principal / tanpa jabatan **dan** tanpa divisi → tetap lewat `moduleDecision` (kini via `callerDecisions`);
- superadmin → tetap MANAGE;
- staf berjabatan (VIEW/NONE/MANAGE) → tetap dinilai matriks;
- repositori wewenang tidak terpasang → tetap `null` (guard meloloskan — karakterisasi `null` dipertahankan).

### Langkah
1. **Tes paritas merah dulu** — `FactoryFlowTenantAdminParityTest`: token `TENANT_ADMIN` +
   `departmentId` + `customRoleId = null` dipakai memanggil `factoryFlowDecision` dan `callerDecisions`
   lewat probe route; keduanya wajib sama dan MANAGE. Pra-perbaikan: factory = NONE → merah.
2. **Refactor** — `factoryFlowDecision` menjadi delegasi:
   `callerDecisions(tenant, roleRepo, assignmentRepo, listOf(GarmentModules.FACTORY_FLOW))[FACTORY_FLOW]`;
   hapus blok pembangunan persona duplikat di `FactoryFlowAccessGuard`; rapikan impor; perbarui KDoc
   (hapus rujukan nomor baris basi).
3. Tes hijau + seluruh `FactoryFlowAccessApiTest` + tes `*Access*` terkait tetap hijau.

### Definition of Done Q3
- [ ] Tes paritas ada dan **gagal sebelum perbaikan** (bukti merah terekam di riwayat kerja)
- [ ] `factoryFlowDecision` mendelegasi ke `callerDecisions`; tidak ada lagi dua blok pembangunan persona
- [ ] `FactoryFlowAccessApiTest` (semua kasus AC1–AC3 + isolasi tenant + karakterisasi `null`) hijau
- [ ] Tes gerbang terkait lain (`MyAccessRoutes`, `HelpRoutes`, modul generik) hijau
- [ ] KDoc diperbarui; `wc -l` `FactoryFlowAccessGuard` tidak bertambah
- [ ] TRD-PLAT-012: status Q3 → selesai, catatan Q4 tetap (dibiarkan sesuai keputusan)

## 3. Q2 — Rencana (dikerjakan kemudian)

1. Rancang konten pesan 403 Factory Flow: modul apa yang dibutuhkan, level apa, dan ke siapa meminta
   (owner/superadmin tenant? kepala divisi?) — samakan pola dengan pesan 403 modul lain bila ada.
2. Sisi server: `requireFactoryFlowAccess` memancarkan pesan baru (mungkin struktur JSON kecil
   `{required, current, module}` kalau klien sudah siap; bila belum, teks tetap + info cara meminta).
3. Cek visual layar Factory Flow saat 403 (login superadmin demo, lalu token peran NONE).
4. DoD: pesam menjelaskan tindakan yang bisa diambil pengguna; cek mata di tenant uji non-rajut.

## 4. Risiko & mitigasi

| Risiko | Mitigasi |
|---|---|
| Owner bypass berarti owner ber-divisi tanpa jabatan kini bisa menulis topologi lokasi | Itu *maksud* bypass: owner tidak bisa mengunci dirinya sendiri (alasan sama di `AccessDecisionEngine:152-160`); perilaku kini selaras dengan menu & modul lain |
| Perubahan menyentuh gerbang tulis topologi lokasi (menggerakkan gerbang tahap) | Tes paritas + AC1–AC3 memastikan hanya kasus owner yang berubah; staf tanpa bypass tak terpengaruh |
| `factoryFlowDecision` dipakai 5 route (pipeline, stage-flow, locations, telemetry) | Semuanya lewat satu fungsi — satu titik ubah; probe generik Track B menjaga prefix |

## 5. Status

### Q3 (2026-10-11) — SELESAI
Tes paritas `FactoryFlowTenantAdminParityTest` ditulis dan **merah** sesuai prediksi
(`factory=NONE` vs `caller=MANAGE` — pesan asert: "dapat factory=NONE expected:<MANAGE>
but was:<NONE>"), lalu hijau setelah `factoryFlowDecision` didelegasikan penuh ke
`callerDecisions` (blok pembangunan persona duplikat di `FactoryFlowAccessGuard` dihapus;
file 138 → 108 baris). Kontrol `FactoryFlowAccessApiTest` hijau sebelum dan sesudah (7/0);
Dokumen pengajaran: [`teaching-plat-012-q3-tenant-admin-bypass-parity.md`](../../teaching/teaching-plat-012-q3-tenant-admin-bypass-parity.md).
tes `MyAccess`/`Help`/`RouteOwnership`/stage-flow hijau. Suite server penuh: **800 tes,
2 gagal — keduanya di luar perubahan ini** (diverifikasi lewat worktree bersih `origin/main`):
`SeedTopologyConsistencyTest` gagal hanya di folder utama karena perubahan **belum terkomit**
sesi lain (`PipelinePresetFactory.kt` dsb.); di `origin/main` hijau 3/0.
`GeneratedRouteMatchesPackSpecTest` sudah gagal di `origin/main` (drift `owner_id` FIELD-004 Q4
vs Track C1 — temuan tersendiri untuk sesi FIELD-004). Parity test sendiri hijau di suite penuh.
Perilaku berubah hanya untuk token `TENANT_ADMIN` ber-divisi tanpa jabatan
(NONE → MANAGE); kasus lain (tanpa principal, superadmin, staf berjabatan, repo wewenang
tak terpasang → `null`) tidak berubah — sesuai §2.

### B5 TRD-PLAT-005 (dikerjakan bersamaan, 2026-10-11)
DB produksi belum ada (deployment OCI belum dibangun), jadi B5 dikerjakan sebagian: kueri pemeriksaan
disusun dan dijalankan read-only ke DB dev (`wemake_erp_scratch_verify2`, migrasi V99):

```sql
-- a) tenant yang memakai pack BERPEMILIK milik tenant lain (akan ditolak assign ulang pasca-rilis)
SELECT t.slug, t.domain_pack, dp.owner_tenant_id
FROM tenants t JOIN domain_packs dp ON dp.code = t.domain_pack
WHERE dp.owner_tenant_id IS NOT NULL AND dp.owner_tenant_id <> t.id;
-- b) inventaris pack data + jumlah pemakai
SELECT dp.code, dp.status, dp.owner_tenant_id,
       (SELECT count(*) FROM tenants t WHERE t.domain_pack = dp.code) AS jumlah_tenant
FROM domain_packs dp ORDER BY dp.code;
-- c) tenant yang mereferensikan pack yang tidak ada (bukan shipped 'garment' / tidak di domain_packs)
SELECT t.slug, t.domain_pack FROM tenants t
WHERE t.domain_pack <> 'garment'
  AND NOT EXISTS (SELECT 1 FROM domain_packs dp WHERE dp.code = t.domain_pack);
```

Hasil dev: (a) **0 baris**, (b) `layanan` LOCKED bersama dipakai 3 tenant (sesuai skenario handoff),
49 pack `klinikbp*` LOCKED tanpa pemilik tanpa pemakai, (c) **0 baris**. **Sisa B5**: jalankan ketiga
kueri yang sama di DB produksi OCI sebelum rilis pertama, tempel hasil di PR rilis.
