# Teaching — Discovery Fase A: narasi → draf pack + blueprint (A1–A7, A9)

> Plan: [`docs/plannings/PLAN-discovery-blueprint-prototype-studio.md`](../plannings/PLAN-discovery-blueprint-prototype-studio.md) §2 · Status: A0 (config), A1–A7 + A9 selesai (A8 Koog menyusul di branch terpisah) · 2026-09-30

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

## A7 — pratinjau tanpa kode (tanpa menyentuh registry LOCKED)

```
POST   /api/discovery/drafts/{id}/preview?ttlMinutes=60  → {sandboxSlug, packCode, expiresAt}
DELETE /api/discovery/drafts/{id}/preview                → akhiri sesi lebih awal
```

- `DiscoveryPreviewRegistry` (core): ledger sesi dalam memori, **terkait waktu** (TTL default 120 menit,
  purge setiap kali ledger disentuh) dan **satu sesi per kode pack**. `start` memakai
  `DomainPackRegistry.register` — itu menulis peta *loaded* (pack data B7), **bukan** daftar `shipped`;
  test membuktikan `GarmentDomainPack.pack` identik sebelum/sesudah sesi.
- `StartDiscoveryPreviewUseCase` membuat tenant sandbox `sandbox-<kode>` (slug ≤30, idempoten — pakai
  ulang tenant yang sama; slug yang dipakai pack lain → ditolak). Klien tidak perlu perubahan apa pun:
  menu & `/m` sudah membaca `GET /api/tenant/pack` (jalur data B7).
- Setelah sesi berakhir/kedaluwarsa, pack dilepas → tenant sandbox ditolak **fail-closed 409** oleh
  mekanisme B7 FR-4 ("vertikal tidak dikenal"), tidak pernah jatuh ke garment. Dites di `DiscoveryApiTest`.

## A9 — evals narasi emas

`server/src/test/.../DiscoveryEvalsTest.kt`: 4 narasi emas (klinik, bengkel, katering, garment CMT) digrade
dengan grader yang sama dengan produksi — `DiscoveryDraftValidator` + cakupan modul/kode blueprint yang
diharapkan. Format log `evals | <kasus> | PASS|FAIL | …` dipakai juga agent Koog nanti, jadi regresi
prompt/model ketahuan sebelum ganti model. Baseline deterministik: **4/4 PASS**.

## Pelajaran saat implementasi

- **Ktor: handler di root vs child route.** Kebingungan awal (404 kosong) terjadi karena pembungkus
  `route("/api/discovery/drafts") { ... }` hilang saat penyuntingan, sehingga `post {}` terdaftar di root.
  Diagnosisnya berlapis: `println` di fungsi muncul, di handler tidak → masalah *registrasi*, bukan *matching*.
- **Superadmin tanpa `X-Tenant-Slug` selalu 404** untuk path non-`/api/admin`: `TenantResolutionPlugin`
  menuntut konteks tenant untuk semua path non-publik. Rute platform per-user seperti ini tetap
  memakai act-as (`asSuperadminActingAs`) — jangan tambahkan bypass baru.

## Utang & langkah berikutnya

- A8 (KoogDiscoveryAgent + loop koreksi diri) — branch terpisah karena dependensi `ai.koog:koog-agents`
  vs `kotlinx-datetime` 0.6.2; kill-switch & interface sudah siap.
- Fase B (estimasi → lock → handoff), C (renderer/Studio), D (wizard/PDF), E (operasi produk).

## Bukti verifikasi

- Kompilasi 5 target hijau (`core` Jvm/Js/WasmJs, `app:shared` Jvm/Js/WasmJs, `server` main+test).
- `:core:jvmTest --rerun-tasks`: **989 test, 0 gagal** (termasuk 20 test discovery: draf, validator,
  use case, registry pratinjau — dengan fixture non-garment, Kontrak 6).
- `:server:test`: `DiscoveryApiTest` (3, termasuk siklus pratinjau end-to-end), `DiscoveryEvalsTest`
  (4/4 PASS), `DomainPackApiTest` (2), `ProspectApiTest` (13) — semua hijau.
- `audit-variability.sh`: 2 temuan akhir (route mutasi `preview`) — sudah fail-closed: login wajib,
  gerbang pemilik (test 403), TTL dibatasi 1..480 menit.
- `Application.kt` **697 baris** ≤ 698 (Aturan Ratchet §14; blok `prospectRoutes`/`moduleDevRoutes`
  diringkas sebagai kompensasi).
