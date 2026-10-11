# Discovery Note — Konsol Platform Superadmin (`app.<base>/admin`) + Act-as Ber-audit

**Tanggal**: 2026-10-01 · **Penulis**: Achmad Jamaludin (dibantu Claude)
Lanjutan [`discovery-M3-login-split.md`](discovery-M3-login-split.md); PLAN-builder-console §3 ("Superadmin").

## 1. Kebutuhan
- Siapa: `PLATFORM_SUPERADMIN` (operator platform), bukan user aplikasi hasil.
- Data: daftar seluruh tenant (milik platform); jejak audit act-as (milik tenant yang dimasuki).
- Kapan: setiap kali superadmin login di `app.` / masuk ke tenant untuk support.

## 2. Fitur serupa
- `AdminRoutes.kt` (`/api/admin/tenants/{slug}`, gate plugin `platformRoutePrefixes`), `recordAudit` (:240).
- Act-as sudah ada di server (`X-Tenant-Slug`, `TenantResolutionPlugin.kt:225`); klien `CompanySwitcherDropdown`
  memakai daftar **hardcode** (`CompanyTenantProfile.ALL`) dan `switchTenant` tanpa server.
- Tiket handoff (`SessionHandoffTicketService`) — diperluas, bukan diduplikasi.
- UI reuse: `BuilderBuildQueuePane`, `DemandLedgerScreen(isSuperadmin)`, `TenantModuleEntitlementDialog`.
- **Belum ada**: endpoint daftar semua tenant; audit act-as.
- Keputusan: **extend**.

## 3. Jenis
Foundation platform (tata kelola platform), **bukan** `BusinessModule`: tidak di-RBAC tenant, tidak di kanvas.

## 4. Uji Variabilitas
| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data |
|---|---|---|---|---|
| Tujuan act-as (`BUILDER` / `APP`) | tidak | tidak | tidak | Kode (path tujuan, bukan enum domain) |
| `AuditAction.PLATFORM_ACT_AS_STARTED` | tidak | tidak | tidak | Kode (status sistem) |

## 5. Core & extend
- `AuditAction` + 1 entri. Tiket handoff + klaim `act_as`.
- Server: `GET /api/admin/tenants` (gate plugin); `/handoff/issue` menerima `actAs=<slug>` khusus superadmin.
- Klien: `PlatformAdminConsole` (route `/admin` di permukaan Platform), sidebar meniru `BuilderShell`.

## 6. I/O & kanvas — tidak ada.

## 7. Governance
| Operasi | Syarat | Ditolak (dites) |
|---|---|---|
| `GET /api/admin/tenants` | superadmin | owner tenant → 403 |
| `/handoff/issue` + `actAs` | superadmin, tenant tujuan ada & dapat diakses; **audit dicatat di tenant tujuan** | owner tenant memakai `actAs` → 403 |
| Tukar tiket act-as | host = tenant tujuan, sekali pakai | host tenant lain → 401 |
| Membuka `/admin` | permukaan Platform + superadmin | user tenant → tidak dirender |

## 8. Ukuran → TRD?
Tanpa migrasi (kolom `action VARCHAR(60)` menampung kode baru), tanpa agregat baru → **TRD tidak perlu**.

## Keputusan (2026-10-01, user)
- Superadmin boleh masuk **Builder dan aplikasi hasil** tenant, asal setiap act-as **tercatat di audit log
  tenant tersebut** (terlihat oleh owner).
- Superadmin login di `app.` mendarat di `/admin`, tidak lagi di aplikasi ERP.
