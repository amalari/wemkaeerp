# Discovery Note — M2 Builder (Deploy + rollback dengan gerbang data + Antrian Pembuatan)

**Tanggal**: 2026-09-30 · **Penulis**: Achmad Jamaludin (dibantu Claude) · **Jalur**: B
**Rencana induk**: [`PLAN-builder-console.md`](PLAN-builder-console.md) fase M2 · TRD:
[`TRD-PLAT-002-builder.md`](../trd/TRD-PLAT-002-builder.md) FR-M2-1 s.d. FR-M2-7.
**Lingkup turns ini**: FR-M2-1/2/3 (deploy, status, gerbang data + rollback), FR-M2-4 domain
(`BuildRequest` + migrasi V83), pecah route per agregat, audit activate/rollback, pane Deployments.
FR-M2-5 (billing manual), FR-M2-6 (F1 penuh ≤600), FR-M2-7 (flag daftar publik), Docker/Caddy
(prasyarat non-kode: akses DNS wildcard) menyusul turn berikutnya.

## 1. Kebutuhan
- **Siapa**: pemilik project menekan Deploy; superadmin mengelola Antrian Pembuatan & rollback paksa.
- **Data**: deployment & build request milik **tenant** (schema `builder`, RLS); pack version milik tenant.
- **Berubah**: saat deploy (jarang, aksi sadar) — append-only, tidak ada baris deployment yang dihapus.

## 2. Fitur serupa
- Status & event deployment, invarian "tepat satu aktif", repo konflik → **sudah ada** dari M0
  (`BuilderDeployment.kt`) — ini **extend**, bukan baru.
- "Butuh kode": tidak ada penanda di `ModuleDefinition`. **Aturan v1 jujur**: draf dengan pack yang
  **bukan pack shipped** (`DomainPackRegistry.shipped`) → seluruh modul aktifnya butuh kode (pack
  kustom belum punya implementasi platform); pack shipped → langsung ACTIVE (paritas garment, konsisten
  keputusan V81 "pack shipped bukan baris domain_packs").

## 3. Jenis
Governance-type platform screen (lanjutan M0/M1) — gate `MANAGE_BUILDER`; rollback pula `TENANT_ADMIN`.

## 4. Uji Variabilitas
| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data |
|---|---|---|---|---|
| Status deployment & BuildRequest | tidak (sistem) | tidak | tidak | **kode** |
| Isi versi pack yang dipin | **ya** | ya | ya (deploy) | **data** |
| Alasan BuildRequest | **ya** | ya | tidak | data |

## 5. Core & extend
- **Core**: `core/domain/builder/` — `BuildRequest.kt` baru, `DeploymentUseCases.kt`
  (`DeployTenantUseCase`, `RollbackDeploymentUseCase`).
- **Extend**: `TenantRepository.save` (pin `domainPackVersion`, TRIAL→ACTIVE),
  `TenantOperationalDataProbe` (gerbang data, sudah ada), `AuditAction` (+2 entri platform),
  `BuilderRoutes` (komposisi route per agregat, `Application.kt` tidak bertambah).
- **V1 limitation gerbang data (jujur)**: probe saat ini tenant-wide, dan blueprint lama tidak
  disimpan per versi (draf diganti di tempat). Gerbang v1: rollback yang **menurunkan
  `blueprintRevision`** ditolak bila tenant punya data operasional — kecuali `force` (aksi
  "Arsipkan modul" eksplisit, tetap ter-audit). Pelacakan data per modul = pekerjaan lanjut.

## 6. I/O & kanvas
- Tidak ada node kanvas baru. Deploy = kunci versi + aktifkan; rollback = pin N−1 append-only.
- Port: tidak ada; telemetri: tidak ada (status deployment tampil di pane).

## 7. Governance
| Operasi | Level | Ditolak 403/409 (dites) |
|---|---|---|
| Deploy | `MANAGE_BUILDER` | tanpa izin; draf tidak sah (400); agent vs host |
| Rollback | `MANAGE_BUILDER` | gerbang data → **409** + pesan modul; `force` tetap ter-audit |
| Antrian (list superadmin) | turn ini domain+persistensi saja; route superadmin menyusul |
