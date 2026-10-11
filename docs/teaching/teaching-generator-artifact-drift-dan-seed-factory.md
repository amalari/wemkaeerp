# 🎓 Modul Pembelajaran: Kode Ter-generate Itu Bayangan — Ia Harus Ikut Berubah

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: kode ter-generate vs sumber spesifikasi, tes drift, batas penekanan (brace/ekspresi-bodied), disiplin regenerasi, migrasi aditif
> **Prasyarat**: dasar Kotlin (expression body), Exposed DSL, Flyway, membaca pesan tes
> **Referensi Task**: TRD-FIELD-004 (Q4) dan regresi commit A1–A7 (`3987c73b`); perbaikan `b5d38ae5`, `a5997028`

---

## 💡 1. Masalah di Dunia Nyata

Bayangkan **cetakan kue** (generator) dan **kue yang sudah dipanggang** (artifact yang di-commit di repo). Suatu hari seseorang mengubah bentuk cetakan. Kalau kue lama tidak dipanggang ulang, di atas meja tersaji dua bentuk berbeda — dan pelanggan menerima yang mana pun yang kebetulan diambil.

Di repo ini ada tiga "kue" yang dicetak: `*Tables.kt`, `*Routes.kt`, `*Postgres*Repository.kt` untuk modul pilot `layanan_change_request`. Cetakannya `HandoffScaffoldGenerator` (+ `SpecPostgresWriter`). Yang sudah terjadi:

1. **Commit Q4 (`7377e1b6`)** menambah kolom `owner_id` ke cetakan — tapi kue lamanya tidak dipanggang ulang.
2. Lebih buruk: commit itu **menambahkan satu `}` berlebih** ke cetakan repository, sehingga kode hasil cetakan **bukan Kotlin yang valid**. Tak ada yang sadar, karena tak ada tes yang membandingkan teks repository hasil generator.
3. **Commit A1–A7 (`3987c73b`)** mengubah preset (`PipelinePresetFactory`) — tetapi seed yang ditanam di migrasi `V10` tidak diregenerasi.

Ketiganya bermuara pada `main` yang merah di dua tes penjaga yang justru dibuat untuk mencegah hal ini.

## 🧭 2. Start dari Mana Membacanya

1. **Baca pesan tesnya utuh**, jangan potong. `org.junit.ComparisonFailure` menandai bedanya dengan `[...]`: bagian dalam kurung siku itulah satu-satunya perbedaan. Di kasus ini `expected:<...r[T.ownerId])[...]}>` vs `was:<...r[T.ownerId])[...]>` → generator punya satu `}` lebih.
2. **Tanyakan: siapa sumber kebenaran?** Di repo ini: **generator**. Artifact dan seed adalah turunan. Jadi jangan "menambal kue lama" tanpa memeriksa cetakannya.
3. **Baru** tentukan arah perbaikan, dan tulis alasannya (migrasi aditif? edit migrasi lama?).

## 🧱 3. Bedah Tiga Perbaikan

### Blok A — Brace berlebih: expression body vs block body

```kotlin
// SALAH (Q4): hydrate adalah expression body (`= PrototypeRow(`), jadi menambah `}` di sini
// memancarkan brace penutup ekstra dan kode hasil generate tidak bisa dikompilasi.
appendLine("        r[$tbl.ownerId]")
appendLine("    )")
appendLine("}")
appendLine("}")   // ← berlebih

// BENAR
appendLine("        r[$tbl.ownerId]")
appendLine("    )")
appendLine("}")
```

**Mental model**: `.appendLine("…")` adalah **teks yang akan dibaca mesin itu juga**. Setiap `}`, `,`, dan tipe yang kamu tulis di situ akan harfiah menjadi kode. Jadi saat mengubah generator, kamu sedang menulis kode-dari-kode — periksa pasangan brace/kurung seperti memeriksa kode biasa.

### Blok B — Menyelaraskan artifact yang sudah dipanggang

```kotlin
// Tables: posisi & urutan SAMA dengan generator (kanonikalisasi tes mengabaikan spasi, TIDAK mengabaikan urutan)
val tenantId = varchar("tenant_id", 64).references(TenantsTable.id)
val ownerId = varchar("owner_id", 64).nullable()

// Repository: tulis saat insert, baca saat hydrate
it[T.ownerId] = row.owner
...
r[T.ownerId]
```

**Mengapa urutan penting?** Tes drift membandingkan kode setelah **spasi dibuang** (`replace(Regex("\\s+"), "")`). Artinya `val a` lalu `val b` ≠ `val b` lalu `val a`. Selaraskan urutan, jangan hanya isi.

### Blok C — Kolom baru butuh migrasi aditif

`V100` sudah terpasang di semua DB; mengubahnya = mengubah checksum migrasi yang sudah dijalankan. Maka kolom `owner_id` ditambahkan lewat **migrasi baru**:

```sql
-- V101
ALTER TABLE layanan_change_request.change_requests
    ADD COLUMN IF NOT EXISTS owner_id VARCHAR(64);
```

**Prinsip**: migrasi yang sudah dirilis adalah **sejarah**, bukan draf. Perubahan bentuk = migrasi baru.

### Blok D — Regenerasi seed tanpa membahayakan checksum Flyway

Tes `SeedTopologyConsistencyTest` membaca **isi file migrasi `V10`**. Jadi dokumen prosesnya memang: jalankan tool, tempel hasilnya ke `V10`. Itu "melanggar" aturan biasa (migrasi sudah dirilis) **karena** proyek ini memanggil `flyway.repair()` sebelum `migrate()`:

```kotlin
flyway.repair()   // menyetel ulang checksum yang cocok dengan file saat ini
flyway.migrate()
```

Pelajaran: sebelum mengedit migrasi lama, **cari tahunya dulu apakah checksum dijaga**. Di repo ini dijaga-lunak (repair), jadi seed demo boleh diregenerasi di tempat.

## ⚖️ 4. Pilihan & Trade-off

| Pilihan | Alternatif | Mengapa | Risiko alternatif |
|---|---|---|---|
| Artifact diselaraskan ke generator | Ubah generator agar tidak memancarkan `owner_id` untuk modul `GLOBAL_ONLY` | Mengikuti keputusan Q4 yang sudah ditandai selesai; generator tetap sumber kebenaran | Membatalkan keputusan TRD lain secara sepihak |
| Migrasi **baru** (V101) | Menyunting `V100` | `V100` sudah dijalankan di dev/produksi | Checksum/intégritas sejarah rusak |
| Regenerasi seed di `V10` | Migrasi seed baru | Tes penjaga membaca `V10`; `repair()` membuatnya aman | Janji ke pembaca tes menjadi palsu |

## ⚠️ 5. Jebakan Pemula

1. **Menambal artifact, bukan cetakannya.** Drift akan kembali pada perubahan generator berikutnya.
2. **Menganggap "semua tes lulus" dari angka yang disebut commit message.** Commit A1–A7 mengklaim "All 1867 tests pass" — 1867 itu **jumlah tes `core`**, bukan `server`. Klaim tanpa menjalankan suite yang benar.
3. **Menambah/menghapus brace di output generator tanpa membayangkan hasil teksnya.** Generator menulis kode; uji hasil teksnya, bukan hanya kompilasi generator itu sendiri.
4. **Mengedit migrasi yang sudah rilis tanpa memeriksa kebijakan checksum.**
5. **Menganggap perbedaan hanya soal spasi.** Tes drift menghapus spasi, jadi **urutan baris tetap wajib sama**.

## 🧪 6. Cara Membuktikan

- Tes penjaga: `GeneratedRouteMatchesPackSpecTest` (drift artifact) dan `SeedTopologyConsistencyTest` (drift seed) — keduanya **merah sebelum**, hijau sesudah.
- Suite penuh sebagai gerbang: `:core:jvmTest` 1867, `:server:test` 803, `:app:shared:jvmTest` 478 — 0 gagal.
- Untuk seed: tool regenerasi `GenerateSeedTopologyTool` → `server/build/generated-seed/seed_topology.sql`.

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tulis tes yang membandingkan **seluruh teks** keluaran `SpecPostgresWriter.repositoryFile` untuk satu spec contoh, lalu jalankan `kotlinc`-style sanity check bahwa brace seimbang. Ini menutup celah yang membuat `}` berlebih lolos.
- [ ] **Tantangan 2**: Cari generator lain di repo yang mengubah artifact terkomit; daftar mana yang punya tes drift dan mana yang tidak.
- [ ] **Tantangan 3**: Periksa apakah `owner_id` pernah benar-benar ditulis untuk modul `GLOBAL_ONLY` — apakah `row.owner` selalu terisi, atau selalu `null`? Kalau selalu `null`, tulis alasan keputusannya di TRD-FIELD-004-Q4.
