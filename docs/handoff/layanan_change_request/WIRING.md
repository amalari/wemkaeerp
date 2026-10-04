# Pendaftaran modul `layanan_change_request` (kandidat — terapkan dan tinjau manual)

1. **`ModuleSchemaMap.byModule`** (server/.../infrastructure/ModuleSchemaMap.kt) — dijaga `ModuleSchemaOwnershipTest`:
   `ModuleId("layanan_change_request") to setOf("change_requests"),`
2. **`RouteOwnership.moduleRoutes`** (server/.../routes/RouteOwnership.kt) — dijaga `RouteOwnershipTest`:
   `"/api/tenant/modules/layanan_change_request" to RouteOwner.Module(ModuleId("layanan_change_request")),`
3. **`DomainRouteWiring.registerIn`** (server/.../routes/DomainRouteWiring.kt):
   `layananChangeRequestRoutes(PostgresLayananChangeRequestRepository(), roleRepo, assignmentRepo)`
4. **Pack**: modul harus ada di pack tenant (pack data berprefiks `layanan_`, didaftarkan lewat `DomainPackRegistry.register`).
5. **Wewenang jabatan** (`custom_roles.module_permissions`, kunci NAME `CHANGE_REQUEST`→ lihat migrasi) dan entitlement/kuota: keputusan bisnis.
6. Jalankan: `ModuleSchemaOwnershipTest`, `RouteOwnershipTest`, `RouteGateTest` (butuh Postgres) dan test gerbang yang ikut digenerate.
