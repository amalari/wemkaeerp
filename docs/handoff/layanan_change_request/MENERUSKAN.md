# Meneruskan hasil generate — modul `layanan_change_request` (C5)

Untuk developer yang **tidak ikut sesi**. Baca bersama `WIRING.md` dan Discovery Note
`docs/plannings/discovery-PROTO-pilot-change-request.md`.

## Apa yang dihasilkan generator
`HandoffScaffoldGenerator.generateFromSpec(spec, module, versiMigrasi, packExpression)` — dari `PrototypeSpec` satu entitas:

| Berkas | Isi | Milik siapa setelah diterapkan |
|---|---|---|
| `db/migration/V90__…_module.sql` | schema, tabel berkolom nyata, indeks, RLS, grant `wemade_app`, entri katalog `PLANNED` | **tim** — perubahan berikutnya = migrasi **baru** (V91…), jangan menjalankan ulang generator |
| `infrastructure/tables/…Tables.kt` | objek tabel Exposed, cermin migrasi | tim |
| `infrastructure/Postgres…Repository.kt` | `PrototypeRowRepository` per tabel (alias `T`) | tim |
| `routes/…Routes.kt` | CRUD di `/api/tenant/modules/<modul>/<tabel>`, gerbang fail-closed | tim |
| `test/…RoutesGateTest.kt` | 401/403/404 + modul tak dikenal, tanpa Postgres | tim |
| `docs/handoff/<modul>/WIRING.md` | titik pendaftaran manual | dokumen |

## Yang boleh langsung diubah tim
- **UI/UX** layar modul — di luar generator sepenuhnya.
- **Aturan khusus** di route (mis. status "Disetujui" hanya oleh MANAGE): tambahkan di handler, *setelah* `authorized(...)`.
- Nama kolom/tipe → lewat **migrasi baru** + ubah objek tabel + `hydrate`/`save`; ubah juga `SPEC` di route bila aturan validasi berubah.
- Tingkat akses per operasi (VIEW/OPERATE/MANAGE) di handler.

## Yang sebaiknya lewat spec
- Menambah status / field / transisi: ubah **spec** di pack (`LayananPilotPack.entity`) → itu sumber aturan untuk prototype **dan** untuk validasi server (`SPEC` di route perlu disamakan). Jika perubahan bersifat *merusak* (hapus/ganti nama status/field), tulis migrasi data eksplisit — belum ada alat otomatis (F10 ditunda).

## Yang TIDAK boleh
- Mengubah urutan gerbang di `authorized()`: **modul dikenal → RBAC → pack tenant → baru body**. `RouteGateTest` akan merah bila RBAC tertinggal.
- Menjalankan test DB tanpa `DB_NAME` yang menunjuk **database uji** (`DatabaseFactory.init()` menjalankan Flyway).
- Menambah route di luar `/api/tenant/…` tanpa mendaftarkannya di ledger gerbang/kepemilikan.

## Menjalankan
```bash
# generator (core, tanpa DB)
./gradlew :core:jvmTest --tests '*SpecScaffoldGeneratorTest*'
# gerbang route (tanpa Postgres)
./gradlew :server:test --tests '*LayananChangeRequestRoutesGateTest*'
# jalur sukses + isolasi tenant + migrasi dari nol → database uji
docker exec wemade-postgres psql -U postgres -c "CREATE DATABASE wemake_pilot_scratch"
DB_NAME=wemake_pilot_scratch ./gradlew :server:test --tests '*LayananChangeRequestApiIntegrationTest*' \
    --tests '*ModuleSchemaOwnershipTest*' --tests '*RouteGateTest*' --tests '*RouteOwnershipTest*'
```
Menulis ulang scaffold untuk ditinjau (tidak menimpa apa pun):
`PILOT_SCAFFOLD_OUT=/tmp/out PILOT_MIGRATION_VERSION=90 ./gradlew :core:jvmTest --tests '*ScaffoldDumpTool*'`.

## Pendaftaran yang sudah dilakukan untuk pilot (contoh untuk modul berikutnya)
1. `ModuleSchemaMap.byModule` ← `LayananPilotPack.CHANGE_REQUEST to setOf("change_requests")`
2. `RouteOwnership.moduleRoutes` ← `/api/tenant/modules/layanan_change_request`
3. `DomainRouteWiring.registerIn` ← `layananChangeRequestRoutes(Postgres…Repository(), roleRepo, assignmentRepo)`
4. Pack data `layanan` (`LayananPilotPack.pack`) didaftarkan lewat `DomainPackRegistry.register` (test) atau dimuat dari `domain_packs` (runtime tenant).
5. **Belum dilakukan (keputusan bisnis):** backfill `custom_roles.module_permissions` (kunci NAME `LAYANAN_CHANGE_REQUEST`), entitlement/kuota paket.
