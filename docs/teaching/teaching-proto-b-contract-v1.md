# 🎓 Modul Pembelajaran: Kontrak v1 Prototype (B0) — Membuka Jalan untuk Tiga Agent Paralel

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Contract-first, kompatibilitas mundur, sealed interface sebagai kosakata tertutup, kerangka yang jujur
> **Referensi**: [PLAN-proto-B-spec-capture](../plannings/parallel/PLAN-proto-B-spec-capture.md) butir B0, [plan induk](../plannings/PLAN-prototype-mvp-parallel-agents.md) §3

## 1. Masalah
Tiga agent (UI, domain, handoff) bekerja bersamaan. Kalau bentuk data belum disepakati, A dan C menebak lalu bentrok saat digabung. B0 menerbitkan **bentuk** data sebagai kode yang dites, sebelum isinya dikerjakan.

## 2. Yang diterbitkan
| Kontrak | Wujud | Status |
|---|---|---|
| Field wajib | `FieldSpec.required` (default `false`) | **Berfungsi**: `Create` dan `SetField` menolak kosong (`'Judul' wajib diisi`) |
| Form | `FormConfig`, `ScreenSpec.form` (widget `FORM` ⇔ `form != null`) | **Divalidasi** konstruktor; belum ada blok UI (A2) |
| Hapus | `PrototypeAction.Delete` | **Berfungsi** (hanya baris itu hilang; baris/entitas tak ada = galat siap-tampil) |
| Operasi spec | `SpecOp` (5 jenis, sealed) + `SpecOpCodec` | Kodek **berfungsi**; `SpecOpApplier.apply` = **kerangka** (B3) |
| Giliran | `SpecOpApplier.applyAll` — satu per satu, maks 5, log semuanya | **Berfungsi** dan tidak berubah di B3 |
| Log & brief | `CaptureEntry`, `RequirementsBrief`, `BriefRenderer`, `BriefCodec` | Bentuk **tetap**; isi `BriefRenderer` kerangka (B4) |
| Pengusul | `SpecOpProposer`, `DeterministicSpecOpProposer` | Port tetap; implementasi kerangka (B5) |
| Contoh | `PrototypeContractSamples` (tiket servis, **non-garment**) | Untuk test A dan C |

## 3. Keputusan penting
- **Kerangka berkata jujur**: bagian yang belum ada mengembalikan `Result.failure` berpesan, bukan diam atau pura-pura sukses.
- **Sealed = kosakata tertutup**: operasi di luar lima jenis tidak bisa dibentuk, jadi LLM tidak bisa menyelundupkan "operasi bebas".
- **Kompatibel mundur**: spec lama tanpa `form`/`required` tetap terbaca (diuji dengan JSON lama).
- **Sampel di `commonMain`** (bukan `commonTest`) supaya modul `app` dan `server` bisa memakainya; ditandai bukan data demo.
- **Waktu dari pemanggil** (`at`): domain tidak memanggil jam, hasil tetap deterministik.
- **Log tidak disimpan di server**: klien mengirimnya saat meminta brief.

## 4. Jebakan
1. Menaruh `required` pada `FieldSpec` tanpa menegakkannya di reducer — kontrak hanya hiasan.
2. Menguji kerangka dengan menegaskan teks "belum didukung" — test pecah saat B3 selesai. Test hanya mengunci yang berlaku seterusnya (batas 5, urutan, log).
3. Kodek yang "menebak" jenis operasi tak dikenal — selalu tolak dengan pesan.

## 5. Verifikasi
- `PrototypeContractTest` (16 test): round-trip spec form/required, JSON lama, validasi form, required/Delete, kodek 5 operasi + entri log, batas 5 operasi, pengusul kerangka, brief deterministik.
- Seluruh suite `core` hijau; kompilasi JVM/WasmJS/JS, server, dan `:app:shared:jvmTest` lolos; audit variabilitas 0 temuan.
- **Belum**: Android (CI); tidak ada UI yang diuji karena B0 hanya model.

## 6. Tantangan
- [ ] Implementasikan `AddEnumOption` (B3) dengan menambah opsi ke `FieldSpec.options` **dan** `KanbanConfig.columns`.
- [ ] Golden test Markdown untuk brief berisi modul nyata (B4).
