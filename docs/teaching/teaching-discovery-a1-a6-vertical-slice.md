# Teaching — Discovery Fase A: narasi → draf pack + blueprint (A1–A6)

> Plan: [`docs/plannings/PLAN-discovery-blueprint-prototype-studio.md`](../plannings/PLAN-discovery-blueprint-prototype-studio.md) §2 · Status: A0 (config), A1–A6 selesai · 2026-09-30

## Apa yang dibangun

Irisan vertikal pertama funnel discovery (Jalur B): calon klien **ber-login**, mengirim narasi bisnisnya,
dan mendapat **draf yang membungkus kontrak yang sudah ada** — `DomainPack` (B7) + `Blueprint` (B4) +
deskriptor layar (Fase C, masih kosong). Tidak ada DSL baru (keputusan T1).

```
POST /api/discovery/drafts  {"narrative": "...", "industryHint"?}
  → DiscoveryAgent.draft()            (interface domain; implementasi di server)
  → DiscoveryDraftValidator           (galat berpath; draf gagal tidak pernah tersimpan)
  → ops.discovery_drafts (V78)        status DRAFT, pemilik = caller
GET  /api/discovery/drafts            milik pemanggil (superadmin: semua, via act-as)
GET  /api/discovery/drafts/{id}       pemilik atau superadmin, selain itu 403
PUT  /api/discovery/drafts/{id}       dokumen DiscoveryDraftCodec; hanya DRAFT; LOCKED → 409
POST /api/discovery/drafts/{id}/lock  beku selamanya (Kontrak 5)
```

## Keputusan penting & alasannya

1. **Draf = bungkus, bukan DSL** (T1): `DiscoveryDraft(pack, blueprint, screens)`. Invarian lintas-bagian
   (blueprint hanya menyebut modul pack-nya) ditegakkan di `DiscoveryDraft.init` *dan* dilaporkan
   `DiscoveryDraftValidator` dengan path — dua lapis, karena AI output tidak tepercaya.
2. **Validator tidak menduplikasi aturan identitas**: ia memanggil `DomainPackRegistry.violations()` lalu
   hanya *menerjemahkan pesan ke path* (`$.pack.modules[i].id`). Satu sumber kebenaran aturan, satu penerjemah lokasi.
3. **Pack bawaan tak bisa ditulis ulang**: dokumen berkode `garment` sah hanya bila identik dengan pack
   yang dikirim platform; kalau berbeda → galat `$.pack`. Narasi konveksi pun memakai pack bawaan apa adanya.
4. **Agent deterministik = fallback + baseline evals** (A3/A9, D4): `DeterministicDiscoveryAgent` memetakan
   kata kunci → (a) starter garment FOB/CMT/D2C, atau (b) pack baru berprefiks `<kode>_` (modul & slot
   selalu berprefiks → tak bisa merebut id platform, risiko plan §7). `DiscoveryAgents.fromEnv()` adalah
   kill-switch: Koog (A8) tinggal mengganti satu cabang `when`.
5. **Kepemilikan dua lapis** (T12): route memeriksa pemilik (→ 403), use case memeriksa lagi
   (`NotOwnerException`) supaya pemanggil non-HTTP tidak bisa melewatinya. LOCKED ditolak dengan
   `LockedException` → 409.
6. **V78 di schema `ops`** tanpa grant `wemade_app` (pola V16): data prospek & harga tidak pernah
   terlihat koneksi tenant-scoped. `owner_user_id` FK `users` wajib (T12).

## Pengecekan visual/mode tidak berlaku di fase ini

Fase A belum menyentuh UI — layar wizard adalah Fase D. Uji lengkap lewat test API (401/403/409/201).

## Pelajaran saat implementasi

- **Ktor: handler di root vs child route.** Kebingungan awal (404 kosong) terjadi karena pembungkus
  `route("/api/discovery/drafts") { ... }` hilang saat penyuntingan, sehingga `post {}` terdaftar di root.
  Diagnosisnya berlapis: `println` di fungsi muncul, di handler tidak → masalah *registrasi*, bukan *matching*.
- **Superadmin tanpa `X-Tenant-Slug` selalu 404** untuk path non-`/api/admin`: `TenantResolutionPlugin`
  menuntut konteks tenant untuk semua path non-publik. Rute platform per-user seperti ini tetap
  memakai act-as (`asSuperadminActingAs`) — jangan tambahkan bypass baru.

## Utang & langkah berikutnya

- A7 (pratinjau sandbox), A8 (KoogDiscoveryAgent + loop koreksi diri), A9 (evals) — Fase B/C/D menyusul.
- `audit-variability.sh`: 3 temuan, semuanya dijawab — (1) `DiscoveryDraftStatus` adalah enum **platform**
  (siklus hidup dokumen, seperti `DomainPackStatus`), bukan kosakata vertikal; (2–3) route `put`/`lock`
  sudah fail-closed: login wajib, gerbang pemilik, status check, test 403/409.

## Bukti verifikasi

- Kompilasi 5 target hijau (`core` Jvm/Js/WasmJs, `app:shared` Jvm/Js/WasmJs, `server` main+test).
- `:core:jvmTest --rerun-tasks`: **981 test, 0 gagal** (termasuk 12 test discovery baru dengan fixture
  non-garment — Kontrak 6).
- `:server:test`: `DiscoveryApiTest` (2), `DomainPackApiTest` (2), `ProspectApiTest` (13) — semua hijau.
- `Application.kt` tetap **698 baris** (Aturan Ratchet §14 dipenuhi; blok `prospectRoutes`/`moduleDevRoutes`
  diringkas sebagai kompensasi).
