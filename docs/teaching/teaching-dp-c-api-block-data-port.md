# 🎓 Modul Pembelajaran: ApiBlockDataPort — Blok UI Bicara ke Server Lewat Satu Pintu

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Ports & Adapters, Ktor Client, pemetaan error HTTP → error domain, tes dengan `MockEngine`, KMP multi-target
> **Prasyarat**: Tahu `suspend`, `Result<T>`, dan sealed interface di Kotlin
> **Referensi Task**: Jalur C butir C1 — `docs/plannings/parallel2/PLAN-dp-C-api-pilot.md`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: blok kanban/tabel di prototype awalnya hanya mengubah data di memori. Kalau tiap blok memanggil HTTP sendiri, urusan token, header tenant, dan pesan error tersebar di puluhan tempat — dan blok jadi tak bisa dites tanpa server.
- **Analogi**: blok adalah kasir, server adalah gudang. Kasir tidak boleh tahu jalan ke gudang; ia cukup menekan tombol di mesin kasir (`BlockDataPort`). Mesin itu (`ApiBlockDataPort`) yang menelepon gudang, menyebut nama toko (`X-Tenant-Slug`), menunjukkan kartu izin (`Bearer`), dan menerjemahkan jawaban gudang jadi kalimat yang dimengerti kasir.
- **Hasil akhir**: `DataBinding.Api(basePath)` di spec → `apiBlockDataPortFor(binding)` → blok yang sama persis bisa memakai memori (prototype) atau server (pilot).

## 🧭 2. "Start dari Mana?"

1. **Kontrak lebih dulu** (`BlockDataPort`, `PortError` — sudah dibuat Jalur B). Adapter tidak boleh menentukan bentuk kontrak.
2. **Tes dulu**: tulis tes `MockEngine` untuk tiap status HTTP sebelum adapter. Tes itu mengunci *kontrak HTTP* route CRUD.
3. **Adapter tipis**: satu fungsi `call(...)` yang dipakai `load/create/update/delete`.
4. **Pabrik**: `apiBlockDataPortFor(binding)` supaya pemanggil tak perlu tahu kelasnya.
5. **Kompilasi tiga target** (JVM, WasmJS, JS) — kode di `commonMain` wajib bersih dari API JVM.

## 🧱 3. Bedah Kode

### Blok A: Pemetaan status → error domain
```kotlin
400, 409, 422 -> PortError.Validation(pesanServer.take(400) atau "Isian ditolak server.")
401, 402, 403 -> PortError.Forbidden
404           -> PortError.NotFound
else          -> PortError.Unavailable("HTTP $status")
```
- Hanya error *validasi* yang boleh membawa pesan server ke layar: pesan itu memang ditulis untuk pengguna.
- 5xx **tidak** membawa isi respons: isi bisa memuat detail internal. Ada tesnya.
- Pesan dipotong 400 karakter agar respons raksasa tak membanjiri UI.

### Blok B: Pembungkus `call`
- `CancellationException` **dilempar ulang**. Menelannya membuat coroutine yang dibatalkan (layar ditutup) terus berjalan — bug klasik.
- Exception jaringan lain → `Unavailable`, bukan crash.
- Sukses tapi body tak terbaca → `Unavailable`, bukan data kosong yang diam-diam salah.

### Blok C: Validasi konstruksi
`init { require(basePath.isNotBlank()) }` — `basePath` kosong akan menembak ke akar server; lebih baik gagal keras saat dibuat.

## ⚖️ 4. The "Why"

| Pendekatan | Alternatif | Alasan | Risiko alternatif |
|---|---|---|---|
| Port + adapter | Blok memanggil `HttpClient` langsung | Blok tetap bisa dites & dipakai di mode memori | Duplikasi token/header di tiap blok |
| `Result` + `PortError` sealed | Lempar exception HTTP mentah | UI memakai `when` yang dipaksa kompilator | Pesan teknis bocor ke pengguna |
| `MockEngine` | Server asli di tes | Cepat, deterministik, jalan tanpa DB | Tes lambat & flaky |

## ⚠️ 5. Jebakan Pemula

1. **`HttpClient()` default di tes** — tanpa engine ia bisa melempar sebelum `require` kita dijalankan. Tes validasi konstruktor harus memberi `MockEngine` (kasus nyata di tes ini).
2. **Menelan `CancellationException`** — lihat Blok B.
3. **Asersi berbelit** — tes `update` sempat memakai `ifEmpty { ... }` yang membuatnya lulus karena alasan salah; asersi sederhana (`assertEquals(mapOf(...), body.values)`) lebih jujur.
4. **Membocorkan isi 5xx** ke pesan pengguna.

## 🧪 6. Pembuktian

`ApiBlockDataPortTest` (11 tes, `jvmTest`): CRUD sukses, header tenant+token, semua pemetaan status, 5xx tak bocor, body kosong/panjang, jaringan putus, respons rusak, pembatalan tak ditelan, `basePath` kosong ditolak. Lalu kompilasi `compileKotlinJvm/WasmJs/Js` hijau.

## 🏆 7. Tantangan Mandiri

- [ ] Tambah tes: respons 429 (rate limit) → saat ini jatuh ke `Unavailable`. Perlukah `PortError` baru? Alaskan dengan Uji Variabilitas.
- [ ] Rancang penanganan konflik versi (409 saat dua orang mengubah kartu yang sama) tanpa memecah kontrak.
- [ ] Tulis tes yang membuktikan token dibaca *per panggilan*, bukan sekali saat konstruksi.
