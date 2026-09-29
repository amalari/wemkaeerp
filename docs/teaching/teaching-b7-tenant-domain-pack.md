# 🎓 Modul Pembelajaran: Domain Pack per Tenant (Jalur B, B7)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Vertikal sebagai data. Satu server melayani konveksi dan klinik tanpa satu baris kode "klinik"
> **Prasyarat**: `teaching-b6-business-module-as-data.md`, `tenant-variability-rules.md` Kontrak 4–5

---

## 💡 1. Masalah

Sampai B6, seluruh platform memakai **satu** pack (`soleActivePack`, garment). Target produk adalah calon klien yang
menjalani *discovery*, lalu AI menyusun modul dan alurnya. Syaratnya, setiap tenant harus bisa berjalan di atas vertikal
**miliknya sendiri**, dan vertikal itu harus bisa disimpan sebagai **data**, bukan kode.

## 🧱 2. Tiga keputusan inti

### A. Identitas modul global, daftar modul milik tenant

Extension seperti `ModuleId.displayName` dipakai di ratusan tempat tanpa konteks tenant. Kalau setiap pemanggil harus
mengoper pack, ada ratusan call site yang berubah. Solusinya: **`ModuleId` unik di seluruh platform**.

- Definisi modul dicari di **semua** pack yang dikenal (`DomainPackRegistry.moduleDefinition`).
- Id yang dipakai beberapa pack (modul platform `org_chart`, `dynamic_rbac`) wajib **identik** definisinya.
- Modul/slot **baru** di pack data wajib berprefiks `<kode pack>_` (`klinik_antrean`), jadi tidak bisa merebut id pack lain.
- Hanya pertanyaan yang memang milik tenant yang memakai pack tenant: modul apa saja (menu, entitlement "semua",
  preset jabatan), fase kanvas, dan port (`PortCompatibility.validate(pipeline, pack)`).

### B. Satu codec, ketat

`DomainPackCodec` = satu-satunya jalan JSON ⇄ `DomainPack`. Field hilang, enum asing, atau warna rusak **ditolak**
dengan path (`$.modules[1].kind`), supaya pesan galat bisa dikembalikan ke AI/penyunting. Invarian struktur tetap milik
`DomainPack.init`. Pack rusak gagal **saat disimpan**, bukan saat tenant membukanya.

### C. Registry sebagai cache, bukan loader startup

```kotlin
class ResolveDomainPackUseCase(repo) {
    suspend operator fun invoke(code) = DomainPackRegistry.find(code) ?: registryWrite.withLock {
        DomainPackRegistry.find(code) ?: repo.findEffective(code)?.pack?.takeIf { runCatching { register(it) }.isSuccess }
    }
}
```

Plugin tenant memanggilnya per request. Pack bawaan dijawab tanpa I/O, dan pack data dimuat dari DB **sekali**. Kode yang
tak dikenal → **409**, tidak pernah jatuh ke garment (Kontrak 4).

## 🗄️ 3. Persistensi (V75)

- `tenants.domain_pack DEFAULT 'garment'` adalah nilai **benar** untuk setiap baris lama, bukan fallback baca.
- `domain_packs(code, version, status DRAFT|LOCKED, owner_tenant_id, definition JSONB)`.
- Versi: draf diganti di tempat. Setelah `LOCKED`, revisi menjadi versi baru, sementara tenant tetap di versi terkunci
  (Kontrak 5, membeku).

## 🌐 4. API

| Endpoint | Siapa | Catatan |
|---|---|---|
| `GET /api/tenant/pack` | anggota tenant | *open by design*: kosakata, bukan data tenant |
| `PUT /api/admin/domain-packs/{code}` | superadmin | simpan draf, 400 bila tak sah |
| `POST /api/admin/domain-packs/{code}/lock` | superadmin | |
| `PUT /api/admin/tenants/{slug}/domain-pack` | superadmin | ditolak bila tenant punya deal/SPK; tercatat di audit |

## ⚠️ 5. Jebakan yang benar-benar terjadi

1. **Urutan di klien.** Kunci modul di `/me/access` dan entitlement didekode lewat parser yang hanya mengenal pack
   terdaftar. Pack tenant **wajib** dimuat sebelum `/me/access`. Kalau tidak, modul klinik dibuang diam-diam dan
   menunya hanya berisi modul platform.
2. **`runBlocking` saat route dipasang** membuat seluruh suite server menggantung di dalam `testApplication`. Loader
   startup diganti resolusi lazy.
3. **Fungsi extension tidak bisa dipanggil lewat nama lengkap package** (`com.x.routes.foo(...)` di dalam `routing {}`).
   Harus di-import.
4. **macOS tidak punya `timeout`.** Pipeline `timeout 900 ./gradlew … | grep` "lulus" tanpa menjalankan Gradle. Selalu
   periksa jumlah & umur file hasil test.
5. **Dua repo, satu port.** Web repo A dan B sama-sama :3000, jadi yang tampil di browser ternyata bundle repo A.
   Repo B kini default **3001 (web) / 8081 (API)**, bisa diubah lewat `WEMADE_WEB_PORT`, `WEMADE_API_PORT`, dan `PORT`.
6. **Login demo ke tenant tanpa pengguna** jatuh ke owner demo (`tenant_id = ten-demo-001`), jadi server menjawab pack
   wemade-demo. Itu perilaku login demo, bukan bug pack. Buat pengguna tenant dulu.

## 🧪 6. Pembuktian

- core 969, app 162, server 244 hijau. Snapshot akses 690 keputusan garment identik.
- `DomainPackApiTest`: simpan → kunci → tetapkan → tenant berjalan di pack klinik; owner 403, pack rusak 400, pack
  hilang 409.
- Server nyata: `klinik-uji` dari [`docs/packs/klinik-uji.pack.json`](../packs/klinik-uji.pack.json). Menu = Sistem &
  Struktur, **Layanan Pasien**, **Keuangan Klinik**; `/m/klinik_antrean` terbuka dengan breadcrumb "Antrean Pasien";
  0 error console. wemade-demo tetap garment.

## 🧭 7. Sisa

- Layar generik masih berbahasa konveksi ("Setujui SPK", "pabrik"). Kosakata aksi harus ikut menjadi data pack.
- Bagan Organisasi tenant baru menampilkan karyawan contoh garment (utang lama, fallback sample).
- Generator AI (discovery → JSON pack) memakai `DomainPackCodec` sebagai kontrak keluarannya.
