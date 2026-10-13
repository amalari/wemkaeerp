# PLAN: Multibahasa Fase 2 — Lokalisasi Data Pack (`LocalizedText`) — Track 0/A/B/C/D

**Tanggal**: 2026-10-13 · **Penulis**: Achmad Jamaludin (dibantu Claude)
**Spesifikasi**: [`TRD-I18N-002`](../../trd/features/TRD-I18N-002-lokalisasi-data-pack.md) · **Prasyarat**: [`PLAN-i18n-phase1-ui-system.md`](PLAN-i18n-phase1-ui-system.md) (Track 0 + C1)
**Status**: Draf — menunggu Fase 1 Track 0

---

## 0. Discovery Note (ringkas)

- **Kebutuhan**: nama modul, fase, slot, tutorial, dan usulan layar tampil dalam bahasa terpilih.
- **Fitur serupa?** Tidak ada `LocalizedText`/terjemahan data. Ada pola yang dipakai ulang: Strangler Fig `TenantStageFlow`, kompatibel-mundur `SlotDefinition.defaultWidget`, codec yang menolak nilai tak dikenal.
- **Jenis**: bukan modul; perubahan bentuk data pack (mesin platform + pack `garment`).
- **Uji Variabilitas**: teks pack beda per pack/industri/admin → **data**, bukan resource XML.
- **Ukuran**: `domain/pack` ±740 string (28 file), `domain/pipeline` ±490 (41), `domain/rbac` ±60 → ±1.300 (kasar, sebagian tumpang tindih dengan label Fase 1).
- **Governance**: tulis pack tetap fail-closed; tes 403 tak berubah.

## 1. Track 0 — Contract (serial, dibekukan dulu)

| # | Pekerjaan | Keluaran |
|---|---|---|
| 0.1 | `LocalizedText` (+`Resolved`), `PackLanguages`, validator kelengkapan | File contract + tes |
| 0.2 | Kontrak codec: bentuk lama → `{id}`, bentuk baru `{id,en}`, tolak kode tak dikenal | Codec test merah→hijau |
| 0.3 | Fixture di `commonTest/resources`: pack lama, pack baru dua bahasa, pack `en` parsial, pack non-default (bordir) | Fixture |
| 0.4 | Keputusan: kolom `pack_languages` (ya/tidak), versi API pack, kebijakan draf terjemah agent | Catatan TRD §4 |
| 0.5 | Inventaris: daftar jalur `String` tampilan per tipe (hasil `grep`, bukan tebakan) → tabel P1–P6 | Lampiran plan |

**Selesai bila**: `:core:jvmTest` hijau untuk codec/fixture; kontrak dibekukan.

## 2. Track A — Core (Strangler Fig, satu tahap per PR)

| Tahap | Tipe | Pekerjaan | Penanda selesai |
|---|---|---|---|
| P1 | `PhaseDefinition` | `displayName`/`subtitle` → `LocalizedText`, jembatan `…Id` | Pembaca bebas jembatan |
| P2 | `SlotDefinition` | `displayName`, `defaultStatuses` | idem |
| P3 | Modul & katalog | `ModuleDefinition`, `OperationalModuleCatalog`, `FoundationModuleCatalog` | idem |
| P4 | `ScreenSuggestion` | `title` + baris contoh; `PackSuggestionMapping` | idem |
| P5 | `RoleHint`, `IndustryStageTemplates` | label dua bahasa | idem |
| P6 | Tutorial & wawancara | `GarmentTutorials`, `GarmentReferenceInterview` | idem; ditandai belum lengkap bila tanpa penerjemah |

Tiap tahap lulus **tes paritas**: tenant konveksi (bahasa id) identik dengan sebelumnya; tes mengiterasi tipe terkait.
Tes juga memakai pack non-default (fixture bordir) sesuai Kontrak 6.

**Selesai bila**: `:core:jvmTest` hijau; `TenantCodeBoundaryTest` hijau; tak ada `else ->` baru.

## 3. Track B — Server & Persistence

| # | Pekerjaan | Keluaran |
|---|---|---|
| B1 | Pembaca/penulis pack memakai codec baru; (opsional) kolom `pack_languages` | Repository + integrasi scratch |
| B2 | Endpoint pack mengembalikan `LocalizedText`; versi API tercatat | Route + tes |
| B3 | Tes: 403 tulis pack oleh peran tak berwenang; RLS; pack lama terbaca; dokumen beku tak berubah | Tes hijau segar |

**Selesai bila**: `:server:test` terarah hijau segar; `J3MigrationFenceTest` hijau.

## 4. Track C — Client

| # | Pekerjaan | Keluaran |
|---|---|---|
| C1 | Pembungkus resolve di package fitur + lencana "Belum diterjemahkan" (`ClayBadge(text, tint)`) | Komponen |
| C2 | Kanvas, menu, matriks RBAC, dialog entitlement, layar usulan membaca `LocalizedText` | Wiring per tahap |
| C3 | Tutorial/Help dua bahasa | Layar |
| C4 | Tes ViewModel dengan bahasa non-default dan pack parsial | Tes |

**Selesai bila**: kompilasi JVM/JS/Wasm/Android hijau; nol literal warna/bentuk.

## 5. Track D — Visual Check

1. `wemade-demo` (login "Demo Mode: Masuk Cepat (Superadmin Apps)") dan `bordir-uji`, ~1280dp.
2. id ↔ en: nama fase/slot/modul di kanvas dan menu berubah; lencana muncul pada pack `en` parsial.
3. Periksa kolom swimlane 324dp dengan nama Inggris yang lebih panjang.
4. Dokumen beku tetap menampilkan teks lama.

## 6. Terjemahan konten (paralel, non-coding)

| Konten | Pelaku | Catatan |
|---|---|---|
| Nama fase/slot/modul/status (±ratusan butir pendek) | Developer + review Product | Istilah baku dijaga dalam glosarium singkat |
| Tutorial & wawancara referensi | Penerjemah manusia | Draf mesin hanya usulan; ditandai sampai disetujui |
| Contoh isi layar (`ScreenSuggestion`) | Product | Bukan terjemahan harfiah; sesuaikan konteks pembaca Inggris |

## 7. Urutan & dependensi

```
Fase 1 Track 0 (LanguageCode) ─► Track 0 (I18N-002) ─► A: P1→P2→P3→P4→P5→P6
                                              ├► B (paralel setelah P1)
                                              └► C (paralel, per tahap) ─► D
```

## 8. Risiko & mitigasi

| Risiko | Mitigasi |
|---|---|
| Pack tersimpan/dokumen beku rusak | Codec baca-bentuk-lama + tes paritas; tak ada migrasi data |
| Fallback senyap menyembunyikan terjemahan kurang | `Resolved` + lencana + validator klaim bahasa |
| File pack besar membengkak | Ratchet; pecah per tema/bahasa, bukan `Part2` |
| Terjemahan tutorial terlambat | P6 terpisah; pack tetap sah dengan lencana |
| Tenant/agent menulis pack satu bahasa | `id` wajib, bahasa lain opsional; draf terjemah perlu persetujuan |

## 9. Definition of Done

- [ ] Paritas konveksi hijau di tiap tahap P1–P6
- [ ] Validator: pack klaim `en` lengkap; selisih menggagalkan tes
- [ ] Codec: bentuk lama terbaca; kode bahasa tak dikenal ditolak
- [ ] `resolve` ber-lencana saat kembali ke `id`; tak ada fallback senyap
- [ ] Tes dengan pack non-default dan tenant `bordir-uji`
- [ ] 403 tulis pack teruji; dokumen beku tak berubah
- [ ] Tak ada pembacaan jembatan tersisa saat tahap ditutup; tak ada `else ->` baru
- [ ] Kompilasi 5 target; `audit-variability.sh` tak menambah temuan; cek visual dua tenant × dua bahasa
- [ ] Teaching doc di `docs/teaching/`; `graphify update .`

## 10. Estimasi

±3–4 person-week (tanpa waktu penerjemah). Model + codec + validator ±1 minggu · P1–P5 ±1,5–2 minggu · P6 + wiring tutorial ±1 minggu.
