# 🎓 Modul Pembelajaran: Kontrak Wawancara (InterviewSession) — B0

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: kontrak domain, validator berpath, codec ketat, kompatibilitas mundur, kode vs data
> **Prasyarat**: `DiscoveryDraft`, `DiscoveryDraftValidator`, `DiscoveryDraftCodec`
> **Referensi Task**: `docs/plannings/parallel4/PLAN-iv-B-contract.md` butir B0

---

## 💡 1. Konsep & Masalah
Wawancara menebak divisi → peran → modul → sambungan dari cerita pemilik usaha, lalu meminta konfirmasi.
Tiga tim (UI, agent Koog, server) bekerja paralel; kalau bentuk datanya belum pasti, semuanya kerja ulang.
**Analogi**: B0 adalah *denah bangunan* yang ditandatangani sebelum tukang listrik, pipa, dan interior masuk.

Hasil akhir B0: kode kontrak + satu validator + codec, tanpa mengubah draf lama satu byte pun.

## 🧭 2. Start dari Mana?
1. **Value object** (`DivisionCode`, `RoleKey`): slug ditolak bila rusak, tidak dinormalkan diam-diam.
2. **Enum milik sistem** (`InterviewStep`, `ModuleOrigin`, `Confirmation`, `ItemSource`) — lolos Uji Variabilitas karena mekanik wawancara sama di semua industri. Isinya (nama divisi/modul) = data.
3. **Model** `InterviewSession` & turunannya: murni data, tanpa aturan.
4. **Validator**: semua aturan di satu tempat, galat berpath.
5. **Codec** + kunci opsional `interview` di draf.
6. **Tes** dengan fixture non-garment (klinik, bengkel).

## 🧱 3. Bedah Kode
**Aturan di validator, bukan di `init`.** `DiscoveryDraft.init` melempar; itu cocok untuk invarian struktur, tapi agent AI butuh pesan *berpath* (`$.interview.links[2].moduleId`) untuk mengoreksi diri. Karena itu tautan ke modul tak dikenal dilaporkan `InterviewValidator`, bukan dilempar konstruktor.

**Asal modul diperiksa terhadap kenyataan registri:**
```kotlin
ModuleOrigin.NEW -> if (inShipped) listOf("Modul ... sudah ada di pack bawaan; pakai REUSE_PACK atau EXTEND, bukan NEW")
```
Harga estimasi membedakan asal, jadi asal tidak boleh asal klaim.

**Kompatibel mundur:** `DiscoveryDraft.interview: InterviewSession? = null`; encoder menulis kuncinya hanya bila ada. Tes `menambah wawancara tidak mengubah satu byte pun...` membuktikannya dengan membuang kunci lalu membandingkan string.

**Codec ketat:** kode enum tak dikenal → `DiscoveryDraftDecodeException("$.interview.links[0].origin", ...)`. Dipakai `code` string, bukan `name`, supaya rename konstanta tidak merusak dokumen tersimpan.

## ⚖️ 4. Keputusan & Alasan
| Pilihan | Alternatif | Alasan |
|---|---|---|
| Aturan di validator tunggal | Aturan di `init` | Galat berpath untuk koreksi agent |
| Enum `code` eksplisit | `enum.name` | Kunci tersimpan stabil |
| `confidence` Int 0–100 | Double | Encode/decode stabil, tanpa pembulatan |
| Kemurnian via `VerticalPurity` dulu | Langsung jadi data pack | Data pack = B5; B0 tidak memperbesar lingkup |

## ⚠️ 5. Jebakan
1. **Fallback senyap** pada kode enum asing → data berubah diam-diam. Tolak.
2. **Fixture hanya garment** tidak membuktikan apa pun tentang tenant lain; pakai klinik/bengkel.
3. **`DONE` tapi tautan masih GUESSED** — "terima semua" harus dicatat `SKIPPED`, bukan disamarkan.

## 🧪 6. Pembuktian
`InterviewValidatorTest` (19 tes, satu per aturan dengan path tepat), `InterviewSessionCodecTest` (round-trip, draf lama, penolakan). Jalankan: `./gradlew :core:jvmTest --tests '*Interview*'`.

## 🏆 7. Tantangan
- [ ] Tambahkan aturan: tiap divisi minimal satu peran saat step ≥ G3.
- [ ] Buat fixture katering dan uji round-trip-nya.
- [ ] Rancang `ModuleOrigin` baru `REUSE_SHARED` untuk B6 — apa invarian yang berubah?
