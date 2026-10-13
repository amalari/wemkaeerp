# PLAN: Judul Menu dari Domain Pack (`menuLabel`) — lanjutan TRD-I18N-002 P3

**Tanggal**: 2026-10-13 · **Spesifikasi induk**: [`TRD-I18N-002`](../../trd/features/TRD-I18N-002-lokalisasi-data-pack.md) · **Status**: Draf — menunggu keputusan §3

## 1. Masalah (terverifikasi di runtime, 2026-10-13)

Menu drawer sudah mengganti **judul seksi** ke Inggris (P3), tetapi **nama item** tetap Indonesia. Item yang punya
layar khusus memakai `AppNavScreen.title` (teks tetap di enum UI), bukan data pack. Dua nama untuk satu modul:

| Layar (`AppNavScreen.title`) | Pack (`ModuleDefinition.displayName`) |
|---|---|
| Bagan Organisasi | Bagan Struktur Organisasi & Karyawan |
| Gudang & Bahan Baku | Bahan Baku & Stok Kain |

Pack tidak bisa dipakai langsung: teks `id` drawer akan berubah (merusak paritas) dan nama pack lebih panjang.
Akibatnya pack non-garment yang memakai modul bersama (mis. `org_chart`) memunculkan judul menu bernada garment.

## 2. Usulan

Tambah **`menuLabelText: LocalizedText?`** (opsional) pada `ModuleDefinition`. Kosong = menu memakai `displayNameText`.
Pack garment mengisinya dengan judul layar yang sekarang (teks `id` identik) + terjemahan `en`.

- Kompatibel mundur: kunci `menuLabel` ditulis hanya bila ada; pack lama terbaca seperti biasa; tanpa migrasi data.
- Jalur validator `modules[i].menuLabel` ikut `localizedTexts()` (klaim `en` tetap dibuktikan mesin).
- `NavMenu`: judul item = `menuLabelText ?: displayNameText` (resolve bahasa, penanda `titleFallback`).
  `AppNavScreen.title` tinggal cadangan untuk layar **non-modul** (login, builder).

## 3a. Keputusan (2026-10-13): Opsi A′ — label per modul **dan per fitur**

Usulan Achmad: tiap layar tambahan modul multi-layar dihubungkan ke **fitur dalam modul** (`ModuleFeature`, kode domain),
bukan ke rute. Spesifikasi lengkap: [`TRD-I18N-003`](../../trd/features/TRD-I18N-003-menu-label-pack-dan-fitur.md).

- Layar utama modul → `ModuleDefinition.menuLabelText` (pack).
- Layar fitur → `ModuleFeature.menuLabelText`; `AppNavScreen.featureCode` menunjuk fitur (induk harus = `businessModule`).
- Layar non-modul (login, builder) → `AppNavScreen.title` (string UI).
- Utang yang diketahui: `ModuleFeatureRegistry` masih di paket mesin dengan konten garment; label fitur ikut di sana sampai registri
  dimigrasi ke pack.
- **Belum terverifikasi**: pemetaan semua layar ke fitur (mis. Packing Bench, Daftar Pengiriman). Track 0 memverifikasinya lebih dulu.

Bagian 3 di bawah dipertahankan sebagai catatan opsi yang dipertimbangkan; Opsi A direvisi menjadi A′ di atas.

## 3. Keputusan yang dibutuhkan: modul multi-layar

3 modul punya >1 layar di menu: `FULFILLMENT` (5 layar), `OPERATOR_EXEC` (2), `INVOICING` (2). Satu `menuLabel`/modul tidak cukup.

| Opsi | Isi | Catatan |
|---|---|---|
| **A (disarankan)** | `menuLabel` hanya untuk modul **satu-layar**; layar ke-2 dst. memakai `AppNavScreen.title` + peta judul `LocalizedText` di UI untuk segelintir layar itu | Kecil; ±5 layar tetap data UI, bukan pack |
| B | `menuLabels: Map<routeKey, LocalizedText>` per modul | Pack tahu rute layar → mencampur data pack dengan routing UI; ditolak |
| C | Pecah modul multi-layar menjadi beberapa modul | Mengubah RBAC/entitlement/kuota; di luar lingkup |

## 4. Rincian pekerjaan (Strangler Fig, satu PR)

| # | Pekerjaan | File |
|---|---|---|
| 1 | Field `menuLabelText` + invarian | `ModuleDefinition.kt` |
| 2 | Codec (tulis bila ada, baca opsional, tolak bahasa tak dikenal) | `DomainPackCodec.kt` |
| 3 | Jalur validator | `PackTranslations.kt` |
| 4 | Versi id-saja & penyuntingan teks id | `PackWithoutTranslations.kt`, `PackIdTextEditing.kt` |
| 5 | Identitas pack garment mengabaikan `menuLabel` (draf lama tetap sah) | `ShippedPackIdentity.kt` |
| 6 | Isi `menuLabel` garment (id = judul layar sekarang) + `en` | `GarmentModules.kt`, `GarmentEnglishNames.kt` |
| 7 | `NavMenu` memakai `menuLabelText` | `NavMenu.kt` |
| 8 | Tes: paritas — **semua judul menu `id` identik** sebelum/sesudah (iterasi `AppNavScreen`); `en` lengkap; codec round-trip + kunci lama; pack tanpa `menuLabel` jatuh ke `displayName`; pack non-garment | `PackLocalizationP3P5Test`, `NavMenuTest` |

## 5. Estimasi & risiko

**Sedang**: ±10 file produksi, ±5 file tes, tanpa migrasi DB, tanpa perubahan server selain serialisasi pack.

| Risiko | Mitigasi |
|---|---|
| Teks `id` menu berubah diam-diam | Tes paritas iterasi seluruh `AppNavScreen` |
| Draf garment tersimpan ditolak | Langkah 5 + tes identitas |
| `GarmentModules.kt` melewati batas ukuran | Cek `wc -l` sebelum/sesudah (Ratchet) |

## 5a. Penambahan (2026-10-13): aturan & penandaan tampil/tidak

- Aturan baku sudah ditulis: `module-integration-rules.md` §5.7 (+ DoD), `CLAUDE.md` §15 butir 9, skill `wemade-feature-workflow`.
  `scripts/sync-agent-config.sh` sudah dijalankan.
- Penandaan **eksplisit** `MenuPlacement` (`Shown` | `Hidden(alasan)`), parameter wajib pada `AppNavScreen` dan `ModuleFeature`
  (menggantikan `isNavMenuItem = true` default). Rincian: TRD-I18N-003 F6 + Lampiran A.
- Track 0 menambah: inventaris layar & fitur tampil/tersembunyi + alasannya (TRD-I18N-003 Lampiran A).

**Status Track 0 (2026-10-13): SELESAI & dibekukan.** Koreksi: 9 layar (bukan 10) tersembunyi lewat flag lama, + LOGIN; `FULFILLMENT` hanya satu baris menu; hanya `OPERATOR_EXEC` punya dua baris menu tampil (Telusur ↔ `traceability`). Tinggal Track A (codec, validator, identitas, isi garment), B (server), C (render fallback), D (visual).

## 6. Definition of Done
- [ ] Tes paritas judul menu `id` hijau; `en` lengkap untuk modul satu-layar
- [ ] Codec: kunci lama terbaca, bahasa tak dikenal ditolak
- [ ] Validator melaporkan `menuLabel` yang kurang `en`
- [ ] Kompilasi 5 target; cek visual drawer id ↔ en (persona Owner; pack non-garment bila tenant uji tersedia)
- [ ] Setiap `AppNavScreen` & `ModuleFeature` bertanda `MenuPlacement` (kompilator); `Hidden` beralasan; daftar menu `Shown` identik dengan sebelumnya
- [ ] Inventaris tampil/tersembunyi dihasilkan/dikunci tes
- [ ] Teaching doc di `docs/teaching/`
