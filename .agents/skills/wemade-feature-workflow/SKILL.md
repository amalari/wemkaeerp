---
name: wemade-feature-workflow
description: Alur kerja wajib bergerbang untuk membangun fitur atau modul WeMade ERP dari Discovery Note sampai dokumentasi - ukuran/TRD, domain dengan tenant kedua, pendaftaran per jenis modul (operasional/governance/foundation/fitur), persistensi, API & governance fail-closed, UI dari data, verifikasi (5 target, test segar, audit variabilitas, konsistensi pendaftaran, cek visual), teaching doc. Aktif setelah `wemade-feature-discovery`, atau saat user minta implementasi fitur/modul baru. Mengorkestrasi skill yang sudah ada (ddd-kotlin-multiplatform, compose-design-system, trd-generator, teaching).
---

# WeMade Feature Workflow

Setiap gerbang punya **output wajib**. Jangan lanjut ke gerbang berikutnya sebelum outputnya ada.
Aturan yang dirujuk: `.claude/rules/{tenant-variability,module-integration,design-system,file-size}-rules.md`.

| # | Gerbang | Kerjakan | Output wajib |
|---|---|---|---|
| 0 | **Discovery** | Skill `wemade-feature-discovery` | Discovery Note |
| 1 | **Ukuran** | >1 agregat baru, ada migrasi, atau >1 lapisan besar → `trd-generator`; kalau tidak, rencana singkat | TRD atau rencana |
| 2 | **Domain dulu** | Skill `ddd-kotlin-multiplatform`; kontrak modul (module-integration-rules §3); aturan pakai peran, bukan nama | Test domain, **termasuk fixture template non-default** |
| 3 | **Pendaftaran** | Ikuti module-integration-rules §5 sesuai jenis (5.1–5.4); port I/O §5.5 | Semua titik pendaftaran disentuh; test konsistensi pendaftaran hijau |
| 4 | **Persistensi** | Migrasi **aditif**; dekode ketat (tidak fallback senyap); snapshot beku bila dokumen butuh | Migrasi dicoba di Postgres dev dalam `BEGIN … ROLLBACK` |
| 5 | **API & governance** | Matriks operasi × level akses; gate modul (induk); tulis = fail-closed | Test **peran tidak berwenang = 403** + peran berwenang = 200 |
| 6 | **UI** | Skill `compose-design-system`; UI dari data, bukan `when(enum)`; tidak ada literal warna | Layar di-host di modul yang benar; cabang `App.kt` memeriksa `accessDecisions` |
| 7 | **Verifikasi** | Lihat daftar di bawah | Bukti tertulis di pesan akhir |
| 8 | **Dokumentasi** | Skill `teaching` (CLAUDE.md §12); update TRD; bila rule/skill berubah → `scripts/sync-agent-config.sh` | Tautan doc |

## Gerbang 7 — Verifikasi (semua wajib)

1. Kompilasi 5 target:
   `./gradlew :core:compileKotlinJvm :core:compileKotlinJs :core:compileKotlinWasmJs :app:shared:compileKotlinJvm :app:shared:compileKotlinJs :app:shared:compileKotlinWasmJs :server:compileKotlin :server:compileTestKotlin`
2. Test `core`, `app:shared`, dan test server yang relevan — **hasilnya segar** (lihat resep).
3. `scripts/audit-variability.sh` — tidak ada temuan baru yang tak dijelaskan.
4. File size: `git diff --name-only main...HEAD -- '*.kt' | xargs wc -l | sort -rn | head`.
5. Cek visual: tenant rajut (`wemade-demo`, superadmin) **dan** tenant uji non-rajut (`bordir-uji`),
   termasuk `/factory-flow` bila modul/fitur harus tampil di kanvas.
6. Laporkan apa yang **tidak** diverifikasi, jangan diklaim.

Resep lengkap: [`references/verification-recipes.md`](references/verification-recipes.md).

## Anti-pola yang menggagalkan gerbang

- Enum domain baru tanpa Uji Variabilitas → kembali ke Gerbang 0.
- Test hanya dengan data rajut → Gerbang 2 belum lulus.
- Route fitur yang hanya mengecek tenant → Gerbang 5 belum lulus.
- "Test lulus" dari build 2 detik tanpa cek kesegaran → Gerbang 7 belum lulus.
- Mengubah test lama agar hijau tanpa menjelaskan kenapa perilakunya memang berubah.
