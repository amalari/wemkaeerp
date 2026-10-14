# PLAN: Builder Console v2 — Navigasi Assisted-Serve (Chat, Live, Handoff, Audit, Setting)

> **Status**: Diusulkan · 2026-10-14 · **Pemilik: Achmad Jamaludin**
> Keluaran turunan: [`TRD-PLAT-014-builder-console-v2-navigation.md`](../../trd/features/TRD-PLAT-014-builder-console-v2-navigation.md)
> Prasyarat baca: [`PLAN-builder-console.md`](PLAN-builder-console.md) (M0–M2 selesai),
> [`PLAN-developer-portal-software-house.md`](PLAN-developer-portal-software-house.md) (terpisah, belum disetujui),
> [`TRD-PLAT-013-pack-theme-customization.md`](../../trd/features/TRD-PLAT-013-pack-theme-customization.md).

## 1. Konteks & posisi

Model kalian **assisted-serve**, bukan Lovable: owner bercerita → platform bangun prototype sesuai
keinginannya → disambungkan ke modul standar → developer yang kalian punya mengeksekusi. Navigasi
Builder hari ini (10 menu, per fitur mesin) tidak menegaskan model itu. v2 merestrukturisasi menjadi
**5 menu per momen pelanggan**: Chat (bicara), Live (lihat: preview + production), Handoff (serahkan ke
developer), Audit (percaya: standar security platform), Setting (atur).

Keputusan user (sesi 2026-10-14):
1. `/builder` langsung ke chat.
2. Live = dua tab: Preview + Production.
3. Handoff = antrean developer (sisi owner; koneksi ke developer yang kalian punya).
4. Audit = keunggulan: memastikan modul standar security-nya platform, bisa dilihat owner.
5. Setting.

## 2. Hasil audit ke kode (2026-10-14)

| Temuan | Bukti | Konsekuensi |
|---|---|---|
| Menu Builder = 10 entri, default `overview` | `BuilderShell.kt:67-96` (`builderMenu()`, `section = "overview"`) | Default berubah ke `chat`; entri lama tidak dihapus, dipetakan (§3) |
| Audit log sudah ada, per tenant | `domain/audit/AuditLogRepository.kt` (`findByTenant(tenantId, limit)`), `PostgresAuditLogRepository`, dipakai di `Application.kt:285,298-306` | Menu Audit = endpoint baca + UI saja, **tanpa infrastruktur baru** |
| Antrian Pembuatan (`BuildRequest`) & brief beku sudah jadi | TRD-PLAT-002 M2 + opsi B (PLAN-developer-portal §1) | Handoff = re-scope pane lama ke tenant sendiri + tautan brief |
| Deploy/rollback, billing manual, pin versi sudah jadi | TRD-PLAT-002 status M2 (plan §8 catatan implementasi) | Tab Production & tab Tagihan mengonsumsi yang ada |
| Portal developer (C1–C6) belum disetujui | `PLAN-developer-portal-software-house.md` status | Handoff **bukan** portal developer; papan lintas tenant tetap superadmin |
| Label menu builder masih string di composable | `BuilderMenuItem.label: String` | Entri baru kirim id+en sebagai data (TRD-I18N-003); entri lama = utang inventaris Track 0 TRD-I18N-003 |

## 3. Peta menu lama → baru (disposisi)

| Menu lama | Nasib | Alasan |
|---|---|---|
| overview | Menempel di header Live:Production (status, deployment aktif) | Kontennya memang status runtime |
| newpack | Aksi di empty-state chat (route tetap hidup) | Ini aksi, bukan tujuan |
| chat | **Menu 1 — default `/builder`** | Pintu masuk percakapan |
| modules, dataflow, prototype | Sub-seksi Live:Preview (re-host, bukan tulis ulang) | Ketiganya cara melihat draf |
| deployments | Live:Production (riwayat + rollback ringkas) | Status dunia runtime |
| buildqueue | **Menu 3 — Handoff** (scope tenant sendiri) | Jendela owner atas serah-terima developer |
| billing | Tab di Setting | Operasional, bukan harian |
| settings | **Menu 5 — Setting** (4 tab: Identitas, Pack & Tema, Tagihan, Kolaborator) | — |
| — | **Menu 2 — Live** (baru), **Menu 4 — Audit** (baru) | — |

Semua route lama tetap valid lewat redirect klien — teaching doc dan deep-link tidak patah.

## 4. Rencana eksekusi (mengikuti Track di TRD-PLAT-014 §5)

| Fase | Isi | Selesai bila |
|---|---|---|
| **T0 Kontrak (serial)** | Bekukan tabel peta §3, kontrak `GET /api/builder/audit`, label id+en 5 menu, definisi badge (handoff: jumlah request aktif; live: titik status deployment) | Tabel TRD FR-1-2 dikunci; fixture redirect di `commonTest` disepakati |
| **T1 Server** | `BuilderAuditRoutes` + wiring `ServerRouteWiring`; test gate 403 (tanpa izin / tenant lain / keputusan tak terhitung); cek indeks tenant di tabel audit (migrasi additif hanya bila belum ada) | AC-4 hijau; tidak ada rute baru di `Application.kt` |
| **T2 Klien — shell & Live** | `builderMenu()` 5 entri + redirect route lama; default `/builder` → chat; `BuilderLivePane` (tab Preview re-host + `BuilderProductionPane`) | AC-1, AC-2 hijau |
| **T3 Klien — Handoff, Audit, Setting** | `BuilderHandoffPane` (scope tenant + brief + audit baca), `BuilderAuditPane` + ViewModel (fake di test) + kartu Postur Security, `BuilderSettingPane` (4 tab, hosting pane lama) | AC-3, AC-5 hijau; badge tampil benar |
| **T4 Verifikasi** | Kompilasi 5 target, `scripts/audit-variability.sh`, cek visual dua tenant (`wemade-demo` superadmin + `bordir-uji`) lebar ~1280dp, deep-link lama, audit file-size (`BuilderShell.kt` & pane ≤600) | Checklist DoD design-system & file-size lolos; teaching doc dibuat |

Urutan: T0 → T1 ∥ T2 → T3 → T4. T2 dan T3 bisa paralel setelah T0 bila dikerjakan dua sesi
(shell+Live vs tiga pane lain) — batasnya `BuilderShell.kt` hanya disentuh T2.

## 5. Risiko

| Risiko | Mitigasi |
|---|---|
| Deep-link lama patah (teaching doc, refleks user) | Test yang mengiterasi tabel peta §3; redirect klien, bukan 404 |
| Pane lama disalin, bukan re-host (utang file-size + Aturan Tiga Kali) | Re-host sebagai komposisi; audit `wc -l` per pane di T4 |
| Audit bocor lintas tenant — klaim security gugur | `findByTenant` dari sesi; test 403 tenant lain; superadmin hanya via act-as |
| Scope meliar ke portal developer (C1–C6) | Non-goal eksplisit TRD; Handoff hanya baca milik sendiri |
| Tema (TRD-PLAT-013) belum terpasang saat T3 | Tab Pack & Tema tampil "belum tersedia" jujur, jangan dipalsukan |

## 6. Verifikasi & penutupan

- AC lengkap di TRD-PLAT-014 §5; setiap fase menyalin AC-nya ke checklist di bawah saat mulai dikerjakan.
- Setelah selesai: centang fase, ubah **Status** di kepala dokumen ini, perbarui
  [`docs/plannings/mvp/TASKS-SUMMARY.md`](../mvp/TASKS-SUMMARY.md), buat teaching doc, `graphify update .`.

### Checklist Definition of Done

- [ ] T0: tabel peta route + kontrak audit endpoint dibekukan
- [ ] T1: `GET /api/builder/audit` + test gate 403 × 3 kasus hijau
- [ ] T2: `/builder` default chat; Live Preview+Production; deep-link lama teruji
- [ ] T3: Handoff (scope tenant + brief + audit baca), Audit pane + Postur Security, Setting 4 tab
- [ ] T4: kompilasi 5 target; audit-variability tanpa temuan baru; cek visual 2 tenant dengan mata
- [ ] `BuilderShell.kt` & semua pane baru ≤600 baris (catat `wc -l` sebelum/sesudah)
- [ ] Label menu baru dua bahasa sebagai data; nol literal warna baru di luar `WeMadeTheme.kt`
- [ ] Teaching doc + TASKS-SUMMARY diperbarui

## 7. Keputusan yang masih terbuka

1. Badge Handoff: apakah `SUPERSEDED` dihitung "aktif"? (Usul: tidak — hanya QUEUED/QUOTED/APPROVED/IN_PROGRESS.)
2. Kartu Postur Security: apakah perlu data "modul dengan RBAC terdaftar vs total" yang belum tersedia
   sebagai agregat? (Usul: T0 memutuskan turunan dari `module_catalog_entries` atau ditunda ke v2.1.)
3. Apakah tab Preview perlu sub-tab per pane lama (Modules|Data Flow|Prototype) atau satu gulir
   berurutan? (Usul: sub-tab, karena ketiganya punya interaksinya sendiri.)
