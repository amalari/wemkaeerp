# PLAN: Multibahasa Fase 1 — UI, Label Sistem, Server & Agent (Track 0/A/B/C/D)

**Tanggal**: 2026-10-13 · **Penulis**: Achmad Jamaludin (dibantu Claude)
**Spesifikasi**: [`TRD-I18N-001`](../../trd/features/TRD-I18N-001-multibahasa-ui-dan-label-sistem.md) · **Lanjutan**: [`PLAN-i18n-phase2-pack-localization.md`](PLAN-i18n-phase2-pack-localization.md)
**Status**: Draf — menunggu persetujuan TRD

---

## 0. Discovery Note (ringkas)

- **Kebutuhan**: aplikasi bisa dipakai penuh dalam Indonesia + Inggris, dipilih per pengguna; bawaan per tenant.
- **Fitur serupa sudah ada?** Tidak: `stringResource` 0 pemakaian, tidak ada `values-*`. Yang ada hanya format angka/tanggal bebas locale (`IdrFormat`, `NumberFormatting`) — dipakai ulang, diberi parameter. *(Pencarian graphify/`find-similar-feature.sh` belum dijalankan ulang di sesi ini; lakukan sebelum Track 0.)*
- **Jenis**: bukan modul. Fondasi lintas-modul (bukan `BusinessModule`): tidak ada entitlement/kuota/kanvas.
- **Uji Variabilitas**: bahasa antarmuka = konfigurasi (per pengguna/tenant) → data; label sistem = kunci; teks pack → Fase 2.
- **Core & titik extend**: `core/domain/i18n/` (baru), `IdrFormat`, `NumberFormatting`, `BriefRenderer`, enum sistem dengan `label`; `app/shared/presentation/i18n/` (baru); server `users`/`tenants`.
- **Governance**: `PUT /api/me/language` (login), `PUT /api/tenant/language` (MANAGE governance pengaturan tenant), fail-closed + tes 403.

**Keputusan alur terjemahan (2026-10-13)**: terjemahan ditulis **lewat kode** (id + en bersama, ditinjau di PR); belum ada TMS. Format `strings.xml` standar agar bisa dipindah ke Weblate/Tolgee nanti. Rincian di TRD-I18N-001 "Keputusan Alur Terjemahan".

## 1. Titik awal terukur (scan 2026-10-13, perkiraan regex)

421 file `presentation/` · ±2.200 literal kalimat · 83 file dengan `Text("...")` literal · 0 `stringResource` · ±400 label non-pack di `core/` · server ±3 literal pesan · PDF: `SpkCardPdfRenderer`, `PrintLabels`.

## 2. Track 0 — Contract (serial, dibekukan dulu)

| # | Pekerjaan | Keluar |
|---|---|---|
| 0.1 | `LanguageCode` (value class, `ID`/`EN`, parser menolak tak dikenal), `TextKey`, `NumberStyle` di `core/.../domain/i18n/` | File contract + tes |
| 0.2 | **Spike override bahasa runtime** di 5 target (Android, iOS, Desktop, JS, Wasm) dengan Compose Resources | Catatan hasil di TRD §4; keputusan pembungkus `ResourceEnvironment` |
| 0.3 | Konvensi kunci (per peran, `fitur_objek_aksi`), struktur `strings_<fitur>.xml`, daftar kode error server awal | Catatan TRD + fixture di `commonTest/resources` |
| 0.4 | Skrip `scripts/audit-i18n.sh` (hitung literal kalimat di `presentation/`) + garis dasar angka | Ratchet: angka tak boleh naik |

**Status**: ✅ SELESAI (2026-10-13)
- 0.1: `core/src/commonMain/kotlin/com/eventverse/app/domain/i18n/` dibuat, 19 tes hijau
- 0.2: Spike: `AppLanguage` provider + `composeResources/values/strings.xml`
- 0.3: `I18nConventions`, `ServerErrorCodes`, fixture JSON
- 0.4: Baseline: 167 literals (65 files)

**Selesai bila**: `:core:jvmTest` hijau; spike terdokumentasi; kontrak dibekukan (perubahan kembali ke Track 0).

## 3. Track A — Core

| # | Pekerjaan | Keluaran |
|---|---|---|
| A1 | `IdrFormat`/format desimal/`BriefRenderer` menerima `NumberStyle` (bawaan = id, paritas) | Domain + tes |
| A2 | `labelKey: TextKey` pada enum sistem (`CartonStatus`, `ShipmentStatus`, `FlowHealthStatus`, `StageKind`, …); jembatan `label` lama | Enum + tes |
| A3 | Tes paritas yang mengiterasi `entries` (entri tanpa kunci → gagal) | Tes |

**Selesai bila**: `:core:jvmTest` hijau; format id identik byte-per-byte dengan sebelum.

## 4. Track B — Server

| # | Pekerjaan | Keluaran |
|---|---|---|
| B1 | Migrasi `V103`: `users.preferred_language`, `tenants.default_language` (aditif, CHECK id/en) | Migrasi teruji Flyway |
| B2 | `PUT /api/me/language`, `PUT /api/tenant/language`; fail-closed | Route + tes |
| B3 | Respons error ber-`code` stabil di rute yang dimigrasi (mulai Fulfillment) | Kode + tes |
| B4 | PDF/cetak menerima `LanguageCode`; tabel label cetak id/en | Renderer + tes |
| B5 | Prompt Koog menerima bahasa keluaran; evaluasi deterministik | Prompt + tes |
| B6 | Tes: 403 peran tak berwenang, 400 bahasa tak dikenal, RLS tak berubah | Tes hijau segar |

**Selesai bila**: `:server:test` terarah hijau; `J3MigrationFenceTest` hijau.

## 5. Track C — Client

| # | Pekerjaan | Keluaran |
|---|---|---|
| C1 | `LanguageProvider`/`LocalAppLanguage`, penyimpan lokal, `TextKeyResolver` | Infrastruktur + tes |
| C2 | Pemilih bahasa: layar login + pengaturan profil (Clay, token, tanpa literal warna) | UI |
| C3 | Migrasi string per fitur, **urutan**: (1) shell/navigasi/login, (2) Fulfillment (baru, kecil), (3) Factory Flow, (4) RBAC/Org Chart, (5) CRM/Deal, (6) sisanya | Satu PR per fitur |
| C4 | Pemetaan kode error server → teks | Mapping + tes |

**Selesai bila**: tiap fitur: audit literal = 0 pada paketnya; tes paritas kunci hijau; kompilasi JVM/JS/Wasm/Android hijau.
File yang sudah melewati hard limit **tidak boleh membesar** (catat `wc -l` sebelum/sesudah).

## 6. Track D — Visual Check

1. Runtime: server + web (resep di memori *Run app lokal & scratch DB*), login **Demo Mode: Masuk Cepat (Superadmin Apps)**.
2. Tiap fitur yang dimigrasi: id vs en, `wemade-demo` dan `bordir-uji`, ~1280dp.
3. Cari teks terpotong/pecah per huruf (kolom swimlane 324dp, badge, tombol). Perbaiki dengan `weight(1f, fill=false)` + `maxLines`.
4. Ganti bahasa saat layar terbuka: tidak perlu muat ulang.
5. Bersihkan perancah sementara.

## 7. Urutan & dependensi

```
Track 0 ─► A ─┐
        ├► B ─┼─► C (per fitur, bertahap) ─► D (per fitur)
        └──────┘
```
A dan B paralel setelah Track 0; C1–C2 butuh Track 0 + B1–B2; C3 per fitur butuh A2 untuk enum fitur itu.

## 8. Risiko & mitigasi

| Risiko | Mitigasi |
|---|---|
| Override locale runtime tak seragam di Wasm/JS | Spike Track 0 sebelum memigrasi apa pun |
| Teks Inggris lebih panjang merusak Clay | Track D per fitur; tier ukuran font ditinjau |
| Label enum yang ternyata varian per tenant | Uji Variabilitas; bila varian → data (Fase 2), bukan kunci |
| Konflik dengan fitur yang sedang jalan (Fulfillment) | Migrasi per fitur, mulai dari yang baru dan kecil |
| Kunci hilang diam-diam | Tes paritas + audit; tak ada fallback senyap |

## 9. Definition of Done

- [ ] Tes paritas kunci id/en hijau; placeholder identik
- [ ] Audit literal: 0 pada paket yang dimigrasi; angka global tidak naik
- [ ] Enum sistem: semua `entries` punya padanan dua bahasa
- [ ] `LanguageCode.parse("fr")` ditolak; 400 dan 403 teruji
- [ ] Format id identik dengan sebelum; en benar (`1,234.5`)
- [ ] Kompilasi 5 target hijau; `scripts/audit-variability.sh` tak menambah temuan
- [ ] Cek visual dua tenant × dua bahasa
- [ ] File besar tidak bertambah baris
- [ ] Teaching doc di `docs/teaching/`; `graphify update .`; `scripts/sync-agent-config.sh` bila aturan berubah

## 10. Estimasi

±6–7,5 person-week (±3 minggu kalender dengan agent paralel). Fondasi 3–5 hari · UI 2–3 minggu · label core ±1 minggu · server/PDF ±1 minggu · agent 3–5 hari · QA ±1 minggu.
