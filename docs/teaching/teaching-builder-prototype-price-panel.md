# 🎓 Modul Pembelajaran: Panel "Paket & Harga" di Prototype (TRD-PLAT-003 / rencana pasar)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Reuse mesin harga, what-if berbasis subset, endpoint baca-saja, fail-closed
> **Prasyarat**: `PriceDiscoveryDraftUseCase`, [seed garment ekspor](teaching-builder-garment-export-seed.md)

## 1. Masalah
Model bisnis: klien melihat prototype dan **langsung tahu harga**. Mesin harga sudah ada (wizard memakainya), tapi tidak bisa dijawab "berapa kalau modul X dilepas?", tidak punya rincian per modul, dan endpoint-nya (`/api/discovery/drafts/{id}/price`) hanya untuk *pemilik* draf — draf kerja tenant dibuka lewat gerbang builder.

## 2. Start dari mana
1. **Use case** `PriceDiscoveryDraftUseCase`: parameter `onlyModuleIds` (what-if) + `lines` per modul (`ModuleLine`: langganan atau rentang bulanan modul baru). Id di luar draf, atau himpunan kosong, **ditolak** — validasi *sebelum* menyentuh katalog.
2. **Endpoint baca-saja** `GET /api/builder/draft/price?modules=a,b` (`BuilderPriceRoutes.kt`): gerbang builder yang sama dengan `GET /api/builder/draft`, hanya membaca draf yang sudah ada (404 bila belum; tidak pernah membuat draf).
3. Encoding JSON dipisah ke `DiscoveryPriceJson.kt` — sekaligus menurunkan `DiscoveryRoutes.kt` (ratchet).
4. Klien: `BuilderApiClient.draftPrice`, `DraftPriceUi`, `PrototypePricePanel`; `BuilderPrototypePane` menyimpan himpunan modul terpilih dan memfilter layar + harga dengan himpunan yang sama.

## 3. Keputusan penting
- **Satu rumus harga.** Panel memakai mesin yang sama dengan wizard, bukan rumus kedua.
- **What-if lewat subset**, bukan salinan draf: server menghitung ulang untuk modul terpilih; klien tak menghitung uang.
- **Tolak, jangan abaikan**: modul asing menghasilkan 400 — harga yang diam-diam mengabaikan pilihan menyesatkan.
- **Baca-saja & fail-closed**: peran tak berwenang 403, tanpa kredensial 401, tanpa draf 404 dan *tidak* membuat draf (dites).
- **Kata "estimasi, bukan penawaran mengikat"** ditulis di panel supaya tak dibaca sebagai janji.

## 4. Jebakan
1. Memakai endpoint discovery untuk draf tenant → 403 "Draf ini bukan milik Anda" (ditemukan saat uji mata).
2. Bootstrap draf di dalam endpoint harga → efek samping pada GET dan test menggantung ke Postgres.
3. Validasi setelah memanggil katalog → test jalur tolak ikut menyentuh DB.
4. Server dev tidak otomatis mendaftarkan rute baru; mulai ulang sebelum uji mata.

## 5. Verifikasi
- Core: test subset, rincian per modul, id asing & himpunan kosong ditolak.
- Server (`BuilderDraftBootstrapTest`, 9 test): 403 peran tak berwenang tanpa membuat draf, 401 tanpa kredensial, 404 tanpa draf, 400 modul asing.
- Mata: 9 modul = Rp 3.400.000/bulan; melepas Jadwal Mesin → Rp 2.850.000, layar 8 dari 9, papan hilang.
- **Belum**: jalur sukses di tingkat route (butuh Postgres katalog); lebar ~1280dp; Android (CI).

## 6. Batasan yang diketahui
- Dasbor yang menghitung dari modul yang dilepas memakai **nilai cadangan statis** (mis. "2 PO"), bukan 0 — perlu keputusan produk.
- Semua modul sampel sudah di katalog, jadi biaya bangun selalu Rp 0; jalur "modul baru" baru terlihat pada tenant/pack yang punya modul di luar katalog.

## 7. Tantangan
- [ ] Panel margin/harga untuk tim internal (superadmin) memakai `marginPercent`.
- [ ] Aturan harga berbasis blok: layar dari blok standar murah, `CUSTOM_EXTENSION` mahal.
- [ ] Test route jalur sukses dengan katalog in-memory.
