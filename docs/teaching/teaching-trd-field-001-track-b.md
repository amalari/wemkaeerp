# 🎓 Modul Pembelajaran: TRD-FIELD-001 Track B — Server Rujukan Tipe Field `RELATION`

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Port/Adapter (`RelationTargetResolver`), logical foreign key vs FK lintas schema, gerbang RBAC fail-closed berlapis, RLS schema-aware, ratchet ukuran file.
> **Prasyarat**: Paham struktur DDD repo ini (`core` = domain, `server` = infrastruktur & route), sudah membaca [TRD-FIELD-001](../trd/TRD-FIELD-001-relation.md) dan [PLAN-field-component-gaps](../plannings/PLAN-field-component-gaps.md) §2.
> **Referensi Task**: TRD-FIELD-001 (C7, Irisan 4a) — Track B; **A0 sudah merge** (`FieldSpec.target`, `FieldType.Relation`, port `RelationTargetResolver` di `core`).

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata**: Satu dokumen sering menunjuk dokumen lain — SPK merujuk PO, lead merujuk record modul lain. Cara "database baku" adalah `FOREIGN KEY`/`JOIN` lintas tabel. Tapi di Jalur B, **schema = modul**, dan modul bisa *dipromosikan* dengan menyalin + memberi prefiks baru (TRD-PLAT-004 P5). FK database **pasti patah** saat itu. Pagar J3/P4 bahkan melarang modul khusus tenant mengunci diri ke schema modul lain.

**Keputusan TRD (K1/K2)**: rujukan = **logical FK**. Yang tersimpan hanyalah **id baris target (string)**. Tidak ada `REFERENCES`, tidak ada `JOIN` lintas schema. Karena DB tidak bisa menjaga integritasnya, penjagaannya dipindah ke **saat tulis**, fail-closed, lewat port domain `RelationTargetResolver`.

**Analogi**: Kartu perpustakaan menulis "ISBN 978-…" alih-alih menyalin seluruh isi buku. Kalau buku dipindah rak (schema di-*rename*), kartunya tetap sah; petugas (resolver) yang memeriksa buku itu benar-benar ada, bukan raknya.

**Hasil akhir (Track B)**:
1. Implementasi server `RelationTargetResolver` (`RegistryRelationTargetResolver`) — target harus modul **operasional** yang dapat diresolusi pack (sendiri atau bersama/R1).
2. Route generik `GET /api/tenant/relation-options` — gerbang berlapis, `LIMIT 20`.
3. Migrasi additive `custom_field_relation_links` di schema `crm_sales` + RLS.
4. Validasi tulis CRM (400 bila target tak ditemukan) + katalog/prompt agent.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — Baca kontrak A0, jangan mengarang.** A0 sudah mengunci: `RelationTargetResolver.exists(tenantId, targetResource, targetRecordId)`, `FieldType.Relation`, `CustomFieldValidationError.TargetNotFound`. Track B hanya **mengisi** port itu + melayani endpoint. Definisi persisnya ada di TRD §4.3, bukan di kepala kita.
2. **Langkah 1 — Salin pola yang terbukti.** `FieldFileRoutes.kt` (Track B `FILE`) sudah menyelesaikan masalah serupa: route generik per `moduleCode`, gerbang `moduleDecision` → `requireModuleAccess` → `resolveModule`. Kita meniru urutannya persis; yang baru hanyalah *sumber baris* target.
3. **Langkah 2 — Ukur file besar sebelum menyentuh.** `DealRoutes.kt` = 719 baris (> hard limit server 500). Ratchet §14: dilarang menambah baris ke sana → **semua route baru di file baru** (`RelationRoutes.kt`). `CrmRoutes.kt` 480 → hanya +9 baris (tetap < 500).
4. **Langkah 3 — Tentukan "sumber baris target".** Tidak ada registry baris generik di server; yang ada `Map<String, PrototypeRowRepository>` (`fieldFileRecordRows`) + repositori CRM. Kita angkat abstraksi kecil `RelationTargetSource` + `RelationTargetRegistry` (server-side), dipakai **dua tempat**: route (opsi) dan resolver (validasi tulis).
5. **Langkah 4 — Gerbang dulu, isi kemudian.** urutan tolakan TIDAK boleh diubah: tenant → modul dikenal (403) → RBAC target (403) → pack tenant (404) → **baru** baca parameter/query.
6. **Langkah 5 — Migrasi additive + RLS, daftarkan skema.** Tabel baru di schema modul pemegang; `ModuleSchemaMap` wajib diperbarui atau `ModuleSchemaOwnershipTest` merah.
7. **Langkah 6 — Tes yang membuktikan kontrak.** 403 dua arah (pemegang vs target), 403 sebelum parameter dibaca, 404, LIMIT 20, dan migrasi lolos pagar J3.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: `RelationTargetSource` & Registri — satu abstraksi, dua pemakai

`server/.../relation/RelationTargetRegistry.kt`

```kotlin
interface RelationTargetSource {
    suspend fun options(tenantId: TenantId, query: String, limit: Int): List<RelationOption>
    suspend fun exists(tenantId: TenantId, recordId: String): Boolean
}
```

**Mental model**: route butuh **daftar** (untuk combobox), resolver butuh **keberadaan** (untuk validasi tulis). Keduanya fakta tentang *modul target yang sama*, jadi wajar satu abstraksi. Konsekuensinya: route dan validasi tulis **mustahil** berbeda pendapat soal modul mana yang boleh jadi target — keduanya membaca `RelationTargetRegistry` sama.

```kotlin
fun default(crmLeads: CrmLeadRepository, recordRows: Map<String, PrototypeRowRepository>) {
    val sources = HashMap<String, RelationTargetSource>()
    recordRows.forEach { (code, rows) -> sources[code] = PrototypeRowRelationSource(rows) }
    sources["crm_sales"] = CrmLeadRelationSource(crmLeads)
    ...
}
```

**Jebakan yang dihindari**: `recordRows.mapValues { … } + mapOf(…)` mengandalkan inferensi *least-upper-bound* dua tipe nilai; hasilnya bisa `Map<String, Any>`. `HashMap<String, RelationTargetSource>` eksplisit menutup celah itu.

### Blok B: `RegistryRelationTargetResolver` — fail-closed, tanpa fallback

```kotlin
override suspend fun exists(tenantId, targetResource, targetRecordId): Boolean {
    if (targetRecordId.isBlank()) return false
    val module = resolvableTarget(targetResource) ?: return false
    return registry.sourceFor(module.value)?.exists(tenantId, targetRecordId) ?: false
}

private fun resolvableTarget(targetResource: String): ModuleId? {
    val module = BusinessModules.fromCode(targetResource) ?: return null
    if (!module.isOperational) return null
    val known = DomainPackRegistry.moduleDefinition(module) != null || ModuleReferenceRules.offered(module) != null
    return module.takeIf { known }
}
```

Tiga penolakan yang semuanya **kembali `false`**, bukan melempar atau jatuh ke modul lain:
1. `targetRecordId` kosong;
2. `targetResource` bukan modul dikenal → `BusinessModules.fromCode` null;
3. modul **governance/foundation** → `!isOperational` (R1: modul itu diakses lewat **salinan identik**, bukan rujukan);
4. modul operasional tapi belum punya sumber baris → `sourceFor(...) == null` (Kontrak 4: nilai tak dikenal ditolak, bukan fallback).

`ModuleReferenceRules.offered` menambahkan jalur **modul bersama** (R1): modul yang ditawarkan `sharedModules` pack bawaan boleh dirujuk tanpa jadi milik pack tenant.

### Blok C: Route `relation-options` — urutan gerbang adalah kontrak

`server/.../routes/RelationRoutes.kt`

```kotlin
get("/api/tenant/relation-options") {
    val tenant = call.tenantContextOrNull ?: return@get /* 404 */
    val target = BusinessModules.fromCode(call.request.queryParameters["module"])
    if (target == null || !target.isOperational) { /* 403 */ return@get }
    val decision = call.moduleDecision(target, tenant, roleRepo, assignmentRepo)
    if (!call.requireModuleAccess(target, decision, AccessLevel.VIEW)) { /* 403 + WARN */ return@get }
    if (tenant.pack.resolveModule(target) == null) { /* 404 */ return@get }
    val entity = call.request.queryParameters["entity"] ?: return@get /* 400 — SETELAH gerbang */
    ...
}
```

**Kenapa gate-nya modul TARGET, bukan modul pemegang** (inti keputusan #2): route ini membuka **jalur baca** ke modul target. Kalau gate-nya modul pemegang, siapa pun yang boleh mengisi field CRM bisa membaca lead/SPK modul lain — celah baca lintas modul. Tes `pemanggil berwenang di pemegang tapi tak berwenang di target tetap 403` mengunci keputusan ini.

**Kenapa `entity` diminta 400 SETELAH gate**: itulah bukti mekanis "403 sebelum parameter dibaca". Tes mengirim request **tanpa** `entity` kepada pemanggil tanpa wewenang dan menuntut **403**, bukan 400 — kalau handler membaca parameter dulu, ia akan 400.

**Kenapa `resolveModule`, bukan `pack.module`**: `resolveModule` mencakup modul sendiri pack **dan** modul bersama yang dirujuk (R1). `pack.module` hanya modul sendiri, sehingga modul bersama yang sah akan salah ditolak 404.

### Blok D: Registri tulis CRM — validasi target sebelum simpan

`server/.../routes/CrmRelationWriteGuard.kt` + 2 baris di `CrmRoutes.kt`

```kotlin
if (relationTargetResolver != null &&
    !call.rejectMissingRelationTargets(tenant.tenantId, customFieldRepository, req.customValues, relationTargetResolver)
) return@post
```

Guard membaca definisi aktif, mengambil tiap field `RELATION` yang **terisi**, dan memanggil `resolver.exists`. Tidak ditemukan → **400** (fail-closed). Sel kosong dilewati (belum diisi itu sah). Penempatan **setelah** gerbang RBAC & jangkauan data, **sebelum** use case menyimpan — supaya data tak sah tak pernah masuk.

> **Catatan jujur (utang yang dicatat, bukan disembunyikan)**: nilai rujukan saat ini disimpan di blob `custom_attributes` JSONB (jalur yang sudah ada). Dual-write baris ke `custom_field_relation_links` **belum** dikerjakan pada v1 — sama pola dengan celah `UserRef` yang sudah didokumentasikan `PostgresCrmLeadRepository`. Tabelnya sudah ada + RLS, sehingga pengisiannya additive di fase berikutnya. Kriteria terima §5.2 ("id tak ditemukan = 400; ditemukan = tersimpan") sudah terpenuhi lewat validasi resolver + simpan blob.

### Blok E: Migrasi `V99` — additive, RLS, tanpa FK lintas schema

```sql
CREATE TABLE IF NOT EXISTS crm_sales.custom_field_relation_links (
    tenant_id ... REFERENCES tenants(id) ON DELETE CASCADE,
    field_id  ... REFERENCES custom_field_definitions(id) ON DELETE CASCADE,
    ...
    target_resource VARCHAR(64) NOT NULL,
    target_record_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (owner_resource, owner_record_id, field_id, ordinal)
);
SELECT apply_tenant_rls_in('crm_sales', 'custom_field_relation_links');
```

**Kenapa `crm_sales` dan bukan tabel link CRM lama**: `custom_field_links` sudah dipakai `UserRef` **dengan FK ke `employees(id)`**; mengubahnya berisiko regresi. Strangler fig (K4): tabel baru, tabel lama tak disentuh.

**Kenapa FK hanya ke `public`**: `tenants` & `custom_field_definitions` adalah tabel platform (public), bukan schema modul. FK ke schema modul lain = kopling tahan lama yang dilarang FR-1 & pagar J3. `target_record_id` hanya string — id, bukan kopling.

**RLS schema-aware**: fungsi `apply_tenant_rls` lama tak bisa menerima `schema.tabel`; `apply_tenant_rls_in` (V76) yang dipakai. Wajib, atau `ModuleSchemaOwnershipTest` menuduh tabel tenant tanpa RLS.

### Blok F: `ModuleSchemaMap` & `RouteOwnership` — daftar yang dipaksa kompilator/tes

- `ModuleSchemaMap.byModule[CRM_SALES] += "custom_field_relation_links"` → `ModuleSchemaOwnershipTest` (B8) hijau.
- `RouteOwnership`: `/api/tenant/relation-options` → `RouteOwner.Platform(...)`. Tanpa entri ini, `RouteOwnershipTest` merah ("route tanpa pemilik").
- `RouteGateTest` (B5): dipanggil anggota tanpa wewenang → route menjawab 403 (`module` kosong pun ditolak) → tidak perlu masuk `RouteGateLedger`.

---

## 🧩 4. Keputusan & Jebakan yang Dihindari

| Keputusan | Alternatif yang ditolak | Alasan |
|---|---|---|
| Logical FK (string) | FK/JOIN lintas schema | promosi modul (salin + prefiks) mematahkan FK; pagar J3 |
| Gate modul **target** | gate modul pemegang | pemegang-only = celah baca lintas modul |
| Tolak saat tulis (resolver) | sweep orphan berkala | sweep tidak fail-closed: nilai tak sah sudah masuk |
| Tabel link baru | ubah `custom_field_links` | FK ke `employees` milik `UserRef`; risiko regresi |
| Route file baru | tambah ke `DealRoutes` (719) | ratchet §14: file > hard limit dilarang tumbuh |
| `null` → tolak | `?: fallback` | Kontrak 4: fallback senyap = data berubah |

---

## ✅ 5. Verifikasi

- `./gradlew :server:test --tests '*Relation*'` → **HIJAU** (17 tes):
  `RelationRoutesTest` (8) — 401/403 target, 403 sebelum parameter, 403 modul tak dikenal, 403 lintas modul, 404 pack, LIMIT 20, filter `q`;
  `RelationTargetResolverTest` (6);
  `RelationMigrationFenceTest` (3).
- Penjaga lintas: `RouteOwnershipTest`, `TenantCodeBoundaryTest`, `J3MigrationFenceTest`, `CrmLeadDraftRoutesTest` — **HIJAU**.
- `:server:compileKotlin` hijau. `DealRoutes.kt` tetap 719.

### Permintaan kontrak ke Track A (bukan ditambal di server)

`Kolaborasi`: `KoogModuleEditor` belum bisa "menerima `target`" karena **`FieldProposal` (core) belum punya field `target`**, dan `ScreenProposalCodec`/`PackSuggestionMapping` belum membacanya. Track B sudah menambahkan **pengetahuan** target + aturan R1 ke `screen_catalog` (`relationTarget`) dan prompt (`KoogDiscoveryPrompt`), tetapi mengalirkan `target` pada usulan/sunting modul **butuh** `FieldProposal.target` + kodek proposal di Track A. Jangan menambal `core/` dari Track B.

---

## 📌 6. Takeaway

1. Ketika DB tak bisa menjaga integritas, pindahkan penjagaan ke **satu titik yang bisa fail-closed** (tulis), lewat port — bukan ke proses sapu berkala.
2. Untuk route yang membuka baca lintas modul, **gate modul yang dibaca**, bukan modul pemanggil; lalu tes 403 **dua arah**.
3. Urutan gerbang adalah API: jangan pernah membaca body/parameter sebelum RBAC menjawab.
4. Fungsi yang sama (`resolver`) dipakai route dan validasi tulis → tidak mungkin menyimpang.
5. Ukur file sebelum menyentuh; file besar milik utang — jangan menumbuhkannya.
