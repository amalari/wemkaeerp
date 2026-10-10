# 🎓 Modul Pembelajaran: Menyatukan Dua Kosakata Tipe Field (Keputusan D2)

> **Level Target**: Mid Developer
> **Topik Utama**: Refactor lintas modul (core/app/server), registry vs karier parameter, kompatibilitas kode tersimpan tanpa migrasi data, `when` tanpa `else` sebagai pagar pendaftaran
> **Prasyarat**: `field-component-rules.md` Kontrak 1–8; `PLAN-field-component-gaps.md` (C1–C10); `PLAN-unify-field-vocabulary.md`
> **Referensi Task**: keputusan pemilik 2026-10-10 ("satukan aja — CRM belum dipakai user"); premis terverifikasi di DB

---

## 💡 1. Masalah Nyata

Dua kosakata tipe field tumbuh terpisah: `enum FieldType` (prototype — dipakai agent, generator SQL, discovery) dan `sealed interface FieldType` (CRM — dipakai field kustom tenant). Menambah satu tipe berarti mendaftarkannya **dua kali**, dan keduanya mulai menyimpang (`TIME`/`MULTI_SELECT` hanya di prototype; `UserRef` hanya di CRM). Analoginya: dua kantor cabang dengan buku kode barang berbeda — setiap barang baru harus dicatat dua kali, dan cepat atau lambat salah satu buku tertinggal.

Keputusan pemilik: **satukan, CRM ikut kosakata prototype**, karena CRM belum dipakai user nyata (bukti: 1 lead demo, definisi tersimpan hanya `CHECKBOX/SINGLE_SELECT/TEXT`) — penahan lama ("jangan sentuh CRM yang berjalan") gugur.

## 🧭 2. Mulai dari Mana (Order of Operations)

1. **Discovery dulu** (`wemade-feature-discovery`): tulis Discovery Note + verifikasi premis di DB — bukan asumsi.
2. **A0 (kontrak beku, kecil)**: tutup celah agar enum siap menerima CRM — `USER_REF`, `decimals`, dan **parser kode legacy** (`CrmLegacyTypeCode`). Ini WAJIB lebih dulu; tanpa itu migrasi kehilangan tujuan.
3. **Core CRM**: codec → validasi → konversi → use case. Biarkan **kompilator** menuntun: setiap `when` pecah adalah titik pendaftaran (Kontrak 6).
4. **Server CRM**: route + guard (akses param via karier).
5. **UI CRM**: pemeta kontrol, input, dialog.
6. **Test paritas** yang mengiterasi `FieldType.entries` → pagar permanen.

## 🧱 3. Bedah Blok Kunci

### Blok A — Registry vs karier (keputusan desain terpenting)

Klaim awal "`FieldSpec` prototype superset CRM" **salah**: opsi prototype `List<String>` vs CRM `List<SelectOption>` (warna + arsip), CRM punya `maxCount`, `NumberFormat` sendiri. Maka penyatuan dilakukan pada **registry tipe**, bukan muatan:

```kotlin
data class CrmFieldType(
    val kind: FieldType,                    // ← registry tunggal (enum prototype)
    val options: List<SelectOption> = emptyList(),  // kekayaan khas CRM
    val format: NumberFormat = NumberFormat.Plain,
    val decimals: Int? = null,
    val withTime: Boolean = false,
    val maxCount: Int = 1,
    val targetResource: String? = null,
    val maxSelections: Int? = null
) { val code: String get() = CrmLegacyTypeCode.toCode(kind) }
```

Pelajaran: periksa **bentuk data** (bukan hanya nama tipe) sebelum menjanjikan "tinggal ganti tipe". Koreksi catatan rencana saat temuan muncul — dokumen harus jujur.

### Blok B — Kompatibilitas tanpa migrasi data

```kotlin
object CrmLegacyTypeCode {
    fun toFieldType(code: String): FieldType  // SINGLE_SELECT→ENUM, CHECKBOX→BOOL, + nama enum
    fun toCode(type: FieldType): String       // tulisan baru = nama enum
}
```

Baris lama (`field_type = 'SINGLE_SELECT'`) tetap terbaca selamanya; tulisan baru memakai `ENUM`. **Parser yang menyesuaikan, bukan data yang diubah** (Kontrak 4: tanpa fallback senyap, nilai tak dikenal = error). Ini pola strangler paling murah: nol DDL, nol backfill.

### Blok C — Parameter akses setelah migrasi

```kotlin
return when (def.type.kind) { … }   // bukan when (def.type)
```
Semua `when` berganti subjek dari tipe ke `kind`; parameter diakses lewat `def.type.withTime` dsb. Invarian pindah ke `init` karier (RELATION wajib `targetResource`, `maxCount ≥ 1`, `decimals 0..6`) — satu tempat, dipakai codec + UI + validasi.

### Blok D — Tipe yang "diwarisi gratis"

CRM kini otomatis memahami `TIME` (validasi `TimeFieldValues`, kontrol `ClayTimePicker`) dan `MULTI_SELECT` (validasi `MultiSelectValues` dengan **id opsi**, kontrol `ClayMultiChoiceChips`) — tanpa kode pendaftaran baru. Inilah payoff penyatuan: tambah tipe sekali, semua konsumen ikut.

## ⚖️ 4. The "Why"

| Keputusan | Alternatif | Mengapa ini | Risiko alternatif |
|---|---|---|---|
| Registry enum + karier `CrmFieldType` | Paksa CRM pakai `FieldSpec` mentah | Kosakata discovery sengaja tipis; memaksa kekayaan CRM masuk = mengotori kosakata agent | Codec discovery ikut berubah risiko regresi besar |
| Parser legacy di satu tempat | Migrasi data `field_type` | Nol DDL, nol downtime, reversibel | Backfill salah = data rusak permanen |
| `when (kind)` tanpa `else` | `else` fallback teks | Tipe baru gagal kompilasi = terdaftar paksa | Tipe diam-diam dipalsukan jadi teks (Kontrak 8) |
| `decimals` null = tak dibatasi | Selalu default 0 | Sesuai semantik `FieldSpec`; baris lama selalu menulis kunci ini | Round-trip pecah bila encode/decode tak sepakat (lihat jebakan 2) |

## ⚠️ 5. Jebakan Pemula

1. **Mengganti nama tipe tanpa memeriksa *pemakaian konstruktor*.** Sealed `FieldType.Text` → enum `FieldType.TEXT` bukan sekadar rename; setiap konstruktor varian menjadi `CrmFieldType(FieldType.X, …)`. Biarkan kompilator mendaftar lokasinya.
2. **`decimals` null → ditulis `Null` tapi dibaca `?: 0`.** Round-trip test langsung menangkapnya. Aturan: **absen kunci = null**, jangan menulis `Null` untuk nilai opsional.
3. **Menganggap satu tipe = satu kontrol.** `DATE` dengan `withTime` sudah punya dua kontrol; setelah migrasi `TIME`/`MULTI_SELECT` menambah dua lagi. Pemeta kontrol adalah `when` tanpa `else` — pagarnya test paritas.
4. **Menambah tipe ke UI tanpa katalog agent.** Kontrak 1 — namun di sini arahnya terbalik: registry tunggal berarti UI/katalog/SQL bergerak bersama.
5. **Menyentuh dokumen/dokumentasi yang kini basi.** KDoc yang menyebut `FieldType.SingleSelect` (sudah tak ada) = tanda rujukan mati; grep ulang setelah migrasi.

## 🧪 6. Cara Membuktikan

- `CrmFieldTypeParityTest`: `samples_allKinds_coverEveryEnumEntry` (11 kind wajib ada sampel), round-trip config & definisi, **kode legacy terbaca** (`SINGLE_SELECT`/`CHECKBOX`), validasi sel per tipe (termasuk TIME `9:30` ditolak, MULTI_SELECT id hantu ditolak), konversi (ganti `withTime` LOSSY).
- `LeadFieldControlParityTest`: tiap kode punya kontrol; `TIME→TIME_PICKER`, `MULTI_SELECT→MULTI_CHOICE`; subset input dialog eksplisit.
- Gerbang: `:core:jvmTest`, `:app:shared` (JVM/WasmJs/JS) + `:app:shared:jvmTest`, `:server:test` — semua hijau; `grep customfield.FieldType` = nol.

## 🏆 7. Tantangan Mandiri

- [ ] Tawarkan editor opsi berwarna (SingleSelect/MULTI_SELECT) di `AddCustomFieldDialog` — mengapa sengaja ditunda sampai sekarang?
- [ ] Rancang penyatuan muatan parameter (§2a rencana): satu `NumberFormat`, satu `SelectOption`, `maxCount` masuk `FieldSpec` — uji dulu dengan Uji Variabilitas.
- [ ] Jelaskan apa yang terjadi pada baris tersimpan `field_type='SINGLE_SELECT'` setelah migrasi, dan mengapa **tidak** perlu backfill.
- [ ] Cek visual CRM: buat field `Jam` baru lewat dialog, isi di form lead, lihat di inspektur — buktikan kontrol waktu benar-benar terpakai.
