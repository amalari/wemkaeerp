# 🎓 Modul Pembelajaran: Penegakan Kepemilikan Pack — TRD-PLAT-005

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Invarian di use case, fail-closed, kompensasi saat langkah kedua gagal, audit, menguji lewat HTTP
> **Prasyarat**: Tahu apa itu Domain Pack, `StoredDomainPack.ownerTenantId`, dan alur handoff discovery → tenant
> **Referensi Task**: [`TRD-PLAT-005`](../trd/TRD-PLAT-005-pack-ownership-enforcement.md) · [`PLAN-pack-ownership-enforcement.md`](../plannings/PLAN-pack-ownership-enforcement.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Tabel `domain_packs` punya kolom `owner_tenant_id`, tapi tidak ada yang memeriksanya. Pack "milik" tenant A secara teknis bisa dipasang pada tenant B lewat `PUT /api/admin/tenants/{slug}/domain-pack`. Kolom yang hanya disimpan dan ditampilkan memberi rasa aman palsu.
- **Analogi Sederhana**: Kunci rumah dengan nama pemilik di gantungannya, tapi pintunya tidak pernah mengecek nama itu.
- **Hasil Akhir**: Pack berpemilik hanya bisa dipasang pada pemiliknya. Namun **reuse itu sah** — pack adalah kosakata dan daftar modul, bukan data tenant (`garment` sendiri dipakai semua tenant). Maka handoff identik oleh tenant lain **melepas** pack menjadi bersama (`owner = null`), secara eksplisit, dengan jejak audit.

---

## 🧭 2. "Start dari Mana?"

1. **Langkah 0: Temukan titik pemasangan.** Hanya `AssignTenantDomainPackUseCase` yang menulis `tenants.domain_pack` — di sanalah aturannya hidup, bukan di route atau UI.
2. **Langkah 1: Tulis tes kegagalan dulu** (tenant lain ditolak). Tes pertama lulus pada kode lama = kita belum menemukan masalahnya.
3. **Langkah 2: Tegakkan di use case**, sebelum jalan pintas "pack sudah sama".
4. **Langkah 3: Jalankan seluruh tes → temukan tes lama yang bertabrakan.** Tes handoff kedua gagal. Itu bukan gangguan, itu **temuan desain**: reuse memang disengaja.
5. **Langkah 4: Putuskan, jangan tambal.** Opsi: tolak reuse / jadikan bersama / fork. Dipilih "bersama" (fork dibatalkan: pack bukan data rahasia).
6. **Langkah 5: Kompensasi + audit + API + tes HTTP.**

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Penegakan
```kotlin
val owner = packRepository.findLatest(code)?.ownerTenantId
require(owner == null || owner == tenantId) { "Pack ${code.value} tidak tersedia untuk tenant ini." }
if (tenant.domainPack == code) return@runCatching tenant   // jalan pintas SETELAH pengecekan
```
- Dicek **sebelum** jalan pintas: kalau urutannya dibalik, tenant yang sudah (salah) berada di pack itu lolos diam-diam.
- Pesan galat **tidak memuat pemilik** — jangan membocorkan tenant lain.
- Pemilik dibaca dari versi **tertinggi**, supaya revisi draf tidak melepaskan kepemilikan.

### Blok B: Pelepasan di handoff + kompensasi
```kotlin
if (latest.ownerTenantId != null && latest.ownerTenantId != tenant.id) {
    domainPackRepository.save(latest.copy(ownerTenantId = null)); packBecameShared = true
}
val assigned = AssignTenantDomainPackUseCase(...)(tenant.id, pack.code)
    .onFailure { if (packBecameShared) latest?.let { domainPackRepository.save(it) } }
    .getOrThrow()
```
- Urutan memaksa pelepasan **sebelum** `assign` (assign menolak pack berpemilik lain). Konsekuensinya: kalau `assign` gagal (mis. tenant sudah punya data operasional), pack A sudah terlanjur bersama. Maka `onFailure` **mengembalikan** pemilik. Ini kompensasi — bukan transaksi, jadi ada celah kecil (pembatalan coroutine tepat di saat itu).

### Blok C: Audit tanpa membocorkan
```kotlin
if (it.packBecameShared) runCatching { call.recordAudit(…, AuditAction.TENANT_DOMAIN_PACK_SHARED, "Handoff tenant '${slug}' memakai ulang pack '${code}' …") }
```
- `runCatching`: kegagalan menulis audit tidak boleh membuat handoff yang sudah berhasil tampak gagal.
- Ringkasan menyebut tenant yang memicu, **bukan pemilik lama**.

---

## ⚖️ 4. The "Why"

| Pendekatan | Alternatif | Mengapa | Risiko Alternatif |
|---|---|---|---|
| Penegakan di use case pemasangan | Di resolusi pack tiap request | `ResolveDomainPackUseCase` hanya menerima kode pack (tanpa tenant) dan registry global per proses | Tenant sandbox pratinjau memakai kode pack tenant lain → akan rusak (D3, ditunda) |
| Reuse → bersama (eksplisit) | Fork per tenant | Pack bukan data rahasia; fork menulis ulang kode, prefiks modul, schema, RBAC (23 file menyebut `ModuleId`) | Pekerjaan besar tanpa kebutuhan bisnis |
| `packBecameShared` di respons + audit | Diam-diam | Perubahan kepemilikan harus terlihat | Pemilik kehilangan eksklusivitas tanpa jejak |

---

## ⚠️ 5. Jebakan Pemula

1. **Dokumen yang salah menulis perilaku.** TRD semula menulis "revisi tanpa `ownerSlug` = bersama". Tes menunjukkan sebaliknya: `SaveDomainPackDraftUseCase` memakai `ownerTenantId ?: latest?.ownerTenantId` (mempertahankan pemilik). *Solusi*: tulis tes untuk perilaku yang kau klaim, lalu koreksi dokumennya.
2. **Mengubah tes lama agar hijau.** Tes handoff kedua gagal karena benar-benar berbenturan. Dihentikan, ditanyakan, baru diubah dengan keputusan sadar.
3. **Fake repository menyembunyikan jalur Postgres.** Semua tes memakai in-memory; jalur `save(ownerTenantId = null)` ke database sungguhan belum teruji (dicatat sebagai batas).
4. **Lupa data lama.** Aturan baru bisa menolak pemasangan ulang tenant yang *sudah* melanggar. Sebelum rilis, **jalankan kueri pemeriksaan di produksi** (B5).
5. **Kompensasi bukan transaksi.** Selalu tuliskan celahnya.

---

## 🧪 6. Bagaimana Membuktikan?

- Core: `AssignTenantDomainPackOwnerTest` (6): pemilik diterima, tenant lain ditolak (pesan tanpa pemilik), pack bersama, revisi tidak melepas, pasang ulang oleh bukan pemilik gagal, garment tak terpengaruh. `DiscoveryHandoffUseCasesTest`: reuse → bersama, pemilik sendiri tidak, tenant ketiga tanpa pelepasan baru, assign gagal mengembalikan pemilik.
- Server (HTTP): `DomainPackApiTest` 409 / 403 / 200 dan semantik revisi; `DiscoveryApiTest` handoff tiga tenant (false → true+audit → false).
- Bukti segar saat merge: `:core:jvmTest` 1617/0, `:app:shared:jvmTest` 264/0, tes server terarah 15/0.

---

## 🏆 7. Tantangan Mandiri

- [ ] Rancang penegakan pada **request-time** (D3) yang tetap mengizinkan sandbox pratinjau. Apa yang harus dibedakan?
- [ ] Buat jalur untuk **mengosongkan** pemilik lewat API superadmin; apa audit dan tes 403 yang diperlukan?
- [ ] Tulis tes integrasi Postgres untuk `findLatest` + `save(ownerTenantId = null)`.
