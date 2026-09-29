# Discovery B7: Domain Pack Dinamis per Tenant

## 1. Kebutuhan bisnis

- **Siapa**: superadmin platform menetapkan pack sebuah tenant. Nanti, AI discovery (self-service, wajib login)
  **menghasilkan** pack dari cerita calon klien: modul, alur, dan wireframe. Tim developer lalu mengimplementasikan
  modulnya di atas chassis.
- **Data milik siapa**: pack shipped (garment) milik platform. Pack hasil AI milik **satu tenant**.
- **Kapan berubah**: pack ditetapkan saat onboarding. Pack hasil AI direvisi sampai blueprint dikunci
  (*lock & approve*), setelah itu berubah hanya lewat versi baru.

## 2. Keputusan yang dibalik

B0 Q1 menyatakan "pack milik platform, dikirim per rilis; tenant memilih, tidak menyunting". B7 membaliknya
**sebagian**: pack boleh berasal dari **kode** (shipped, garment) atau dari **data** (baris DB, JSON tervalidasi).
Mesin tidak membedakan keduanya. Invarian `DomainPack.init` tetap satu-satunya validator, jadi pack hasil AI yang
rusak ditolak saat disimpan, bukan saat dipakai.

## 3. Temuan kode

- `soleActivePack` hanya dibaca di **7 titik produksi**: `BusinessModule.kt` (definition, section, entries),
  `ModuleArchetype.kt` (slot, phase), `PortCompatibility.kt`, `GarmentSlots.forModule`, `NavMenu.kt`, dan
  `FactoryFlowUiState.kt`. Seluruh kode lain lewat extension `ModuleId.*`.
- Extension `ModuleId.displayName` dan sejenisnya tidak punya konteks tenant. Kalau setiap pemanggil harus
  mengoper pack, ada ratusan call site yang perlu diubah.

## 4. Rancangan inti

**`ModuleId` unik lintas platform** (bukan per pack). Dengan begitu, *definisi* modul bisa dicari tanpa tenant:
registry menggabungkan semua pack yang dikenal (shipped + DB). Hanya pertanyaan yang memang milik tenant, yaitu
"modul apa saja yang ada" (menu, entitlement "semua modul", kanvas), yang memakai pack tenant.

| Pertanyaan | Hari ini | B7 |
|---|---|---|
| `ModuleId.displayName` / `kind` / `section` … | `soleActivePack` | cari di **semua** pack (id unik) |
| `SlotCode` → fase/port | `soleActivePack` | pack pemilik slot (slot pack DB diberi namespace) |
| Daftar modul (menu, sentinel entitlement, katalog) | `BusinessModules.entries` | `packOf(tenant).modules` |
| Kanvas & `PortCompatibility` | `soleActivePack` | pack tenant |
| Klien | `soleActivePack` | `GET /api/tenant/pack`, didaftarkan saat login |

**Persistensi** (migrasi aditif, V75):
- `tenants.domain_pack VARCHAR NOT NULL DEFAULT 'garment'`. Semua tenant hari ini garment, jadi ini **bukan
  default senyap**: nilainya benar untuk setiap baris.
- `domain_packs(code PK, owner_tenant_id NULL, version, status DRAFT|LOCKED, definition JSONB, created_at)`.
  Pack shipped **tidak** disimpan di tabel ini.
- Resolusi: `code` tenant dicari di pack shipped, lalu di `domain_packs`. Kalau tidak ditemukan, request
  **ditolak** (Kontrak 4), tidak jatuh ke garment.

**Codec**: `DomainPackCodec` (JSON ⇄ `DomainPack`) berupa parser tunggal yang eksplisit. Codec ini juga menjadi
**format keluaran AI** nanti (lihat PDF "Definisi ERP Kustom": JSON schema modul/field/workflow/role).

**Namespace**: id modul dan slot pack DB wajib berprefiks kode pack (`klinik.antrean`). Tabrakan dengan pack lain
ditolak saat disimpan. `storedName = code.uppercase()` tetap unik karena prefiksnya ikut.

## 5. Governance

- Tulis `domain_packs` dan ubah `tenants.domain_pack`: **superadmin platform saja**, fail-closed, dengan test 403
  untuk owner tenant.
- `GET /api/tenant/pack`: anggota tenant. Isinya kosakata (bukan data rahasia), dan tergolong *open by design*
  seperti `me/access`.
- Pindah pack pada tenant yang sudah punya data operasional: **ditolak** di B7.

## 6. Pertanyaan untuk diputuskan

| # | Pertanyaan | Usulan |
|---|---|---|
| Q1 | Pack DB milik satu tenant, atau bisa dipakai beberapa tenant? | Kolom `owner_tenant_id` nullable: hasil AI = milik tenant; superadmin boleh menjadikannya bersama |
| Q2 | Tenant menyimpan *referensi* ke pack, atau *salinan* beku? | Referensi + `version`. Pack `LOCKED` tidak diubah di tempat; revisi = versi baru (Kontrak 5 membeku) |
| Q3 | Modul pack DB tanpa layar khusus? | Layar generik `/m/{code}` (B6g). Implementasi tim developer menambah entri `ModuleScreenRegistry` |
| Q4 | Bukti B7 | Tenant uji `klinik-uji` dengan pack JSON non-garment: menu, `/m/…`, dan gerbang 403 bekerja, snapshot akses garment identik |
| Q5 | AI generator | **Di luar B7.** B7 hanya menyiapkan kontrak (codec + validasi + penyimpanan) yang nanti diisi AI |
