# PLAN: Deploy Produksi — Hostinger Domain + VPS + Docker + Caddy Wildcard

> **Status**: Aktif — domain `wemakeerp.com` sudah dibeli di Hostinger (2026-10-01); ini adalah
> item terakhir M2 yang selama ini tertahan prasyarat DNS
> ([`PLAN-builder-console.md`](PLAN-builder-console.md) §8, `discovery-M2-builder-deploy.md`).
>
> **Kriteria selesai**: `https://<slug>.wemakeerp.com` hidup dari data tenant sungguhan; tenant
> membuka pane Billing dan mengunduh PDF invoice; server + Postgres berjalan di VPS lewat Docker
> Compose; TLS otomatis oleh Caddy.

---

## 1. Yang SUDAH selesai (kode & infra, per 2026-10-01)

### 1.1 Aplikasi (M0–M2 tuntas di kode, ter-commit)
- [x] **M0** fondasi builder: deployment, chat, blueprint, print ticket (`iss: wemade-erp-print`,
      scope per dokumen), F0–F4. Commit `84aa15b`, `e4254c8`, `2c5ad0f`.
- [x] **M1** kanvas puzzle + reconciler pipeline tenant. Lampiran M1 di
      `docs/teaching/teaching-plat-002-m0-builder-foundation.md`.
- [x] **M2** deploy + rollback dengan gerbang data (append-only, ter-audit), `BuildRequest` + V83,
      antrean superadmin, F1 (`Application.kt` 394 → **299**, lihat `6537f48`), flag
      `WEMADE_PUBLIC_SIGNUP` (bawaan tertutup), tagihan langganan harga-terkunci (V85),
      **FR-M2-5b PDF invoice platform** (watermark status, kolom harga rata kanan, tiket cetak).
- [x] **Test suite 100% hijau**: `server:test` 342 test (termasuk `RouteOwnershipTest` — pemilik
      `/api/tenant/help` didaftarkan di `ad3010f`), `core:jvmTest`, kompilasi JVM/Js/WasmJs.

### 1.2 Infrastruktur di repo
- [x] `docker-compose.yml`: **Postgres 18** (skema `wemade_app` + init RLS) dan **MinIO** (storage
      PO) — sudah siap dipakai di VPS apa adanya.
- [x] Migrasi Flyway otomatis saat server start (V1–V85).
- [x] Resolusi tenant per **subdomain**: `TenantResolutionPlugin` memetakan `<slug>.<host>` →
      tenant; host tanpa subdomain tidak membatasi (cocok untuk root domain = landing/SPA).
- [x] SPA memanggil API di **origin yang sama** (`/api/...`) — pitfall 23 — sehingga Caddy cukup
      satu reverse proxy `/api/*`.

### 1.3 Domain
- [x] Domain `wemakeerp.com` **sudah dibeli** di Hostinger (2026-10-01).

## 2. Yang BELUM selesai (urutan pengerjaan)

### Fase A — DNS di hPanel Hostinger *(butuh: akses login hPanel)*
- [ ] Record `A` `@` → IP publik VPS.
- [ ] Record `A` `*` (wildcard) → IP publik VPS — kunci multi-tenant tanpa record per tenant.
- [ ] Record `CNAME` `www` → `wemakeerp.com`.
- [ ] Verifikasi: `dig +short wemakeerp.com` dan `dig +short bordir.wemakeerp.com` → IP VPS.
      Catatan: bila memakai Cloudflare, wildcard harus **DNS only** (TLS tetap milik Caddy).

### Fase B — VPS *(butuh: VPS disewa, minimal 2 GB RAM; Ubuntu/Debian)*
- [ ] Sewa VPS, catat IP publik → dipakai Fase A.
- [ ] Install Docker + compose plugin (`curl -fsSL https://get.docker.com | sh`).
- [ ] Firewall: buka `22`, **`80`** (issuance sertifikat Caddy), `443`.

### Fase C — Containerisasi aplikasi *(bisa dikerjakan SEKARANG, tanpa menunggu DNS)*
- [ ] `Dockerfile` server: multi-stage `gradle :server:installDist` → image JRE tipis.
- [ ] `Dockerfile`/target build SPA Wasm (`:app:webApp:wasmJsBrowserDistribution`) → served statis.
- [ ] Tambah service `server` + `caddy` ke `docker-compose.yml` (join network Postgres/MinIO).
- [ ] `.env.production.example` + dokumentasi env: `DB_*`, `S3_*`, `PORT`, `TRACE_SCAN_HOST`,
      `WEMADE_BLENDED_HOURLY_RATE_IDR`, `WEMADE_DEFAULT_MARGIN_PERCENT`;
      `WEMADE_PUBLIC_SIGNUP` **tetap tertutup** di produksi.
- [ ] Caddyfile: root domain → SPA statis + proxy `/api/*`; `*.wemakeerp.com` → proxy Ktor.
      TLS per-subdomain via HTTP challenge (port 80) — wildcard DNS ≠ wildcard sertifikat,
      tidak butuh API DNS Hostinger; opsi DNS-01 menyusul bila perlu.

### Fase D — Go-live & verifikasi
- [ ] `docker compose up -d` di VPS; migrasi V1–V85 jalan otomatis; cek log server.
- [ ] `curl -I https://wemakeerp.com` (SPA) dan `https://<slug>.wemakeerp.com/api/health`.
- [ ] E2E mata: login tenant → Deploy → status ACTIVE; pane Billing → **Unduh PDF** →
      `application/pdf`; watermark status benar.
- [ ] Audit env: tidak ada secret default dev yang terbawa (`wemade-app-dev`, `wemademinio`, dsb.).

## 3. Yang TETAP di luar rencana ini (tertunda prasyarat lain)
- **L1 iPaymu** (`PLAN-ipaymu-testing-bridge.md`) — menunggu akun vendor; domain ber-SSL dari
  fase ini justru prasyarat `notifyUrl`-nya.
- **Kirim invoice otomatis** (email/WhatsApp) — unduhan manual dulu; butuh gateway.
- **UI VOID invoice** — domain sudah mengizinkan penomoran ulang, UI menyusul.
- **Satu sertifikat wildcard beneran (DNS-01)** — hanya jika volume subdomain membuat
  issuance per-subdomain terasa berat.

## 4. Pembagian kerja
| # | Pekerjaan | Pihak |
|---|---|---|
| 1 | Sewa VPS, kirim IP publik | Pemilik project |
| 2 | Set DNS (Fase A) | Pemilik project (hPanel) — atau kasih akses ke dev |
| 3 | Fase C seluruhnya (Dockerfile, compose, Caddyfile, env) | Dev — **bisa mulai sekarang** |
| 4 | Fase D verifikasi bersama | Dev + pemilik project |
