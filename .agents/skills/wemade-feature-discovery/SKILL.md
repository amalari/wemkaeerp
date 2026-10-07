---
name: wemade-feature-discovery
description: Langkah PERTAMA sebelum menulis kode fitur atau modul apa pun di WeMade ERP. Menjawab berurutan - kebutuhan bisnisnya apa, fitur serupa sudah ada belum (dan di mana), ini modul operasional / governance / foundation / fitur dalam modul, apakah konsepnya bervariasi per tenant (kode vs data), core-nya di mana dan extend dari mana, input/output port dan posisinya di kanvas Factory Flow, serta governance (gate modul, level akses, scope, entitlement). Aktif saat user minta "buat fitur", "tambah modul", "fitur baru", "extend modul", atau sebelum `wemade-feature-workflow`. Menghasilkan Discovery Note.
---

# WeMade Feature Discovery

Tujuan: keputusan mahal diambil **sebelum** kode ditulis, ketika mengubahnya masih murah.
Refactor TRD-FLOW-001 (±70 file) terjadi karena langkah ini tidak ada.

Jangan menulis kode produksi selama skill ini berjalan. Hasil akhirnya satu **Discovery Note**
(template: [`references/discovery-note-template.md`](references/discovery-note-template.md)) yang
menjadi input `wemade-feature-workflow`.

---

## Langkah 1 — Kebutuhan dalam bahasa bisnis

Tulis 3 baris:
- **Siapa** yang memakai (peran: sales, PPIC, operator meja X, QC, admin pabrik, superadmin)?
- **Data milik siapa** (tenant, dokumen/SPK, operator, pabrik bersama)?
- **Kapan berubah** (sekali saat onboarding, per desain, per SPK, per shift)?

## Langkah 2 — Fitur serupa sudah ada?

```bash
scripts/find-similar-feature.sh <kata-kunci> [kata-kunci lain…]
# contoh: scripts/find-similar-feature.sh washing cuci batch
```

Skrip mencari di paket domain `core`, `BusinessModule`, `AppNavScreen`, route server, migrasi,
teaching doc, dan TRD. Putuskan satu:

| Hasil | Tindakan |
|---|---|
| **Sudah ada** | Extend fitur itu. Baca teaching doc-nya dulu. Jangan membuat paralel. |
| **Mirip** | Tiru polanya (repository, codec, route, layar). Sebutkan file contohnya di Note. |
| **Baru** | Lanjut, tapi tetap tiru pola terdekat dari `references/codebase-map.md`. |

## Langkah 3 — Jenisnya apa?

Ikuti [`module-integration-rules.md` §5](../../rules/module-integration-rules.md):

- **Modul operasional** — dijual, di-RBAC, dihitung kuota, **node di kanvas**. Jarang: hanya bila ia
  mengisi slot `ModuleArchetype` yang berbeda dan klien ingin membeli/mematikannya terpisah.
- **Governance** — layar tata kelola (org chart, RBAC, factory flow). Tidak di kanvas.
- **Foundation** — data induk/referensi (master data, vendor). Tidak di kanvas.
- **Fitur dalam modul** — paling sering. Punya modul induk, mewarisi RBAC & entitlement-nya.

Ragu antara modul dan fitur → **fitur**. Menurunkan modul jadi fitur jauh lebih mahal daripada
menaikkan fitur jadi modul.

## Langkah 4 — Uji Variabilitas

Untuk setiap konsep baru (status, tahap, jenis, urutan, label, warna, tarif), jawab tiga pertanyaan
[`tenant-variability-rules.md` Kontrak 1](../../rules/tenant-variability-rules.md). Isi tabel:

| Konsep | Beda per tenant? | Per industri? | Admin mengubah? | → Kode / Data | Template & titik beku |
|---|---|---|---|---|---|

## Langkah 5 — Core di mana, extend dari mana

Baca [`references/codebase-map.md`](references/codebase-map.md). Tulis:
- **Core** (agregat/entitas yang memiliki aturannya) dan paketnya.
- **Titik extend** yang dipakai (katalog, template, registry, pola repository) — satu baris per titik.
- **Contoh nyata** yang ditiru (file:baris).
- Yang **tidak** boleh disentuh (file di tabel utang file-size, file hasil generate).

## Langkah 6 — Input/Output & kanvas Factory Flow

- **Port masuk** (tipe data apa, dari modul/fitur mana) dan **port keluar** (ke mana).
  Tipe wajib ada di kosakata pack (`DomainPack.portTypes`, dan `wiredPortTypes` bila menyambung; `core/.../domain/pack/`).
- **Posisi di kanvas**: node modul (level 1), atau item di bawah node modul induk (level 2: tahap,
  proses, stasiun, alat).
- **Telemetri** yang dihasilkan (`wipPieces`, `cycleTimeHours`, `healthStatus` — Kontrak 6), atau
  tulis "belum ada, tampil estimasi".

## Langkah 7 — Governance

- **Gate**: modul mana yang menjaga route & layar (modul sendiri atau modul induk).
- **Level akses per operasi**: tabel operasi × VIEW/OPERATE/MANAGE; tulis = fail-closed (Kontrak 7).
- **`ScopeCapability`**: `GLOBAL_ONLY` atau `HIERARCHICAL` (module-integration-rules Kontrak 8).
- **Entitlement**: ikut modul induk, atau modul baru yang perlu didaftarkan di paket.
- **Peran yang TIDAK boleh** — ini yang nanti dites (harus 403).

## Langkah 8 — Discovery Note

Isi template, taruh di deskripsi PR / awal TRD / pesan ke user, lalu lanjut ke
`wemade-feature-workflow` Gerbang 1.
