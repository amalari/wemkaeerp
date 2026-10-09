# Teaching — Review & Perbaikan Track RELATION (TRD-FIELD-001)

Dokumen ini melanjutkan `teaching-trd-field-001-a0-relation.md` dan tiga teaching doc Track A/B/C.
Setelah ketiga track di-merge ke `main`, sebuah review kode menemukan **sembilan** temuan. Dokumen ini
menjelaskan akar masalah, cara memperbaikinya, dan pola yang harus dibawa ke fitur berikutnya.

## Ringkasan temuan dan perbaikannya

| # | Temuan | Akar masalah | Perbaikan |
|---|---|---|---|
| 1 | `relation-options` membocorkan seluruh lead tenant | Route memakai `requireModuleAccess(..., VIEW)` tetapi **tidak** menerapkan `DataScope` modul target; sumber CRM memanggil `findActive(tenantId, null)` (`null` = tanpa predicate) | Route menghitung `decision.config.sanitizeFor(target).scope`; bila bukan `ALL_TENANT_DATA`, `reachableOwnerIds` diturunkan (pola CRM) dan diteruskan ke sumber |
| 2 | Semua sel tabel/kanban RELATION tampil "Tidak ditemukan (id)" | `relationLabels` dideklarasikan tetapi **tidak pernah diisi**; resolver non-null yang selalu `null` diartikan "target hilang" | Helper `cachedRelationLabel` — cache kosong = belum diresolusi → **id apa adanya**, bukan "hilang" palsu |
| 3 | Form RELATION (mode tanpa kontroler) ikut "Tidak ditemukan" | `{ id -> relation?.labelFor(id) }` memasok lambda non-null meski `relation == null` | Sama: bungkus dengan `cachedRelationLabel` sehingga fallback id tetap hidup |
| 4 | Parameter `entity` divalidasi lalu dibuang | `options(...)` lama tidak menerima `entity` | Kontrak `RelationTargetSource.options(tenantId, entity, reachableOwnerIds, query, limit)` |
| 5 | CRM `targetResource` bentuk `module:entity` selalu 400 saat simpan | Server membaca `targetResource` sebagai kode modul telanjang (`BusinessModules.fromCode`), klien membacanya `module:entity` | Server mem-parse bagian sebelum `:` (dikunci `relationTargetFormatError`); dialog & hint diperbaiki |
| 6 | Lookup opsi menarik seluruh tabel tiap ketikan | `rows.list(tenantId)` + `findActive(tenantId, null)` lalu filter di memori | CRM: `searchActive(...)` menekan predicate + `LIMIT` ke SQL (tanpa agregat). Prototype: seam `PrototypeRowRepository.search(...)` |
| 7 | Parser target UI menyimpang dari aturan core | `resolveRelationTarget` mengimplementasi ulang tata bahasa (`":kain"`, `"a:b:c"` lolos) | Parser memanggil `relationTargetFormatError` lebih dulu; bentuk tak sah → entitas kosong (fail-closed) |
| 8 | Label CRM picker berbeda dari sisa aplikasi | Label dihitung ulang tanpa fallback WhatsApp/`Prospek #` | Memakai `CrmLead.title` (aturan kanonik core) |
| 9 | `RelationDisplay.resolved` tak dibaca produksi | Sisa desain awal | Dihapus |

## Empat pelajaran yang bisa dipakai ulang

### 1. Gerbang RBAC ≠ jangkauan data
`requireModuleAccess(module, VIEW)` hanya menjawab **"boleh lihat modul ini?"**. Ia tidak menjawab
**"boleh lihat record yang mana?"** — itu `DataScope`. Modul hierarkis (CRM) memisahkan keduanya:
level akses di-guard, jangkauan di-`sanitizeFor`+`reachableOwnerIds`. Setiap route baru yang membaca
data modul lain (seperti `relation-options`) wajib melewati **kedua** gerbang. Uji regresi
`RelationTargetResolverTest.opsi CRM menghormati jangkauan pemilik` mengunci ini.

### 2. `null` yang ambigu adalah bug
`findActive(tenantId, null)` berarti "semua tenant"; `labelFor(id)` mengembalikan `null` yang berarti
"belum diketahui" — dua `null` dengan arti berbeda. Ketika API mengembalikan `null` untuk dua makna,
pemanggil **pasti** salah menafsirkan salah satunya. Solusi: pisahkan sinyalnya (`reachableOwnerIds`
eksplisit; `cachedRelationLabel` menormalkan "belum diketahui" → id).

### 3. Satu aturan, satu implementasi
Bentuk target (`entityId` / `moduleId:entityId`) hidup di tiga tempat: core (`relationTargetFormatError`),
UI (`resolveRelationTarget`), dan server (`resolvableTarget`). Sebelum perbaikan ketiganya berbeda
pendapat — akibatnya nilai yang bisa dipilih di picker selalu 400 saat disimpan. Setelah perbaikan,
ketiganya memanggil aturan core yang sama. **Jika sebuah grammar/whitelist muncul dua kali, ekstrak
sebelum pemakaian ketiga (Aturan Tiga Kali, tapi untuk logika).**

### 4. Baca-cepat bukan alasan untuk meninggalkan SQL
`list().filter().take(20)` secara fungsional benar, tetapi memuat seluruh tabel. Untuk jalur yang
dipicu **tiap ketikan**, predicate + `LIMIT` harus ada di SQL. Untuk repositori ter-generate
(`SpecPostgresWriter`) yang belum bisa diubah, sediakan **seam** di antarmuka
(`PrototypeRowRepository.search`) sehingga push-down bisa ditambahkan tanpa memecah implementasi.

## Sisa yang diketahui (belum dikerjakan)
- `PrototypeRowRepository.search` default masih memfilter `list()` di memori; implementasi
  Postgres ter-generate perlu override push-down (perubahan `SpecPostgresWriter` + snapshot test) —
  dicatat sebagai tindak lanjut, bukan di PR review ini.
- FR-3 "tidak ditemukan" belum bisa otoritatif: jalur baca lokal hanya punya cache, bukan pemeriksaan
  keberadaan target. Bila diinginkan, tambahkan endpoint meta/lookup yang mengembalikan status per id.

## Verifikasi
`./gradlew :core:jvmTest :server:test --tests '*Relation*' :app:shared:compileKotlinJvm
:app:shared:compileKotlinJs :app:shared:compileKotlinWasmJs :app:shared:jvmTest` → hijau.
