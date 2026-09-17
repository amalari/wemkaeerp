# 🎓 Modul Pembelajaran: Seed Data Deal WON & SPK Sampling Terhubung (V43)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Flyway Migration, Multi-Tenancy (RLS), Referential Integrity, Idempotent Seed, JSONB Contract Alignment
> **Prasyarat**: Pemahaman dasar SQL PostgreSQL, konsep migrasi database, dan alur modul CRM → Deal → Sampling di WeMade ERP
> **Referensi Task**: Seed data deal yang sudah melewati deal (WON) + SPK untuk modul Sampling

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Modul Sampling (`SPK Sample Rajut`) dirancang untuk **menempel pada Deal CRM** — SPK yang lahir
dari deal yang sudah dimenangkan (`WON`). Tapi database demo kosong: layar kanban sampling
menampilkan kolom kosong, dan tim QA tidak bisa menguji alur "deal WON → SPK → gerbang produksi
massal" tanpa mengetik data palsu satu per satu lewat UI.

### Analogi Sederhana
Bayangkan pabrik yang baru selesai membangun papan produksi (kanban) fisik, tapi belum ada satu
pun lembar SPK tertempel. Seed data = **lembar SPK contoh yang disusun guru produksi sebelum
shift dimulai**, supaya semua pekerja (QA, demo ke klien, developer baru) bisa melihat bagaimana
papan itu *seharusnya* terisi.

### Hasil Akhir
Migrasi `V43__seed_won_deals_and_sampling_spk.sql` yang mengisi:
- 3 kontak pelanggan → 3 deal `WON` → 4 SPK sampling (beda status, beda tahap pipeline, satu jalur makloon vendor),
- lengkap dengan anak-anaknya: knit spec, size chart, program mesin CAM, yield/timing, dan 28 milestone.

---

## 🧭 2. "Start dari Mana?" — Order of Operations

Kalau kamu harus menulis seed seperti ini dari nol, urutannya ditentukan oleh **foreign key**, bukan
selera:

1. **Langkah 0: Baca skema, jangan tebak.** Buka migrasi yang membuat tabelnya
   (`V23`, `V34`, `V35`, `V38`, `V40`, `V41`). Catat kolom `NOT NULL`, default, dan FK.
2. **Langkah 1: Cek data yang SUDAH ada di database dev.** Ini yang paling sering dilompati.
   `SELECT max(spk_number) FROM sampling_orders` → ternyata `SPK-SMP-0001..0004` sudah terpakai,
   dan `nextSpkNumber()` dihitung dari `COUNT(*) + 1`. Maka seed mulai dari **SPK-SMP-0005**.
3. **Langkah 2: Seed master data dulu** (`crm_contacts`) — karena `deals.contact_id` itu `NOT NULL`
   + FK. Kontak dulu, baru deal.
4. **Langkah 3: Seed deal** dengan `stage = 'WON'`.
5. **Langkah 4: Seed SPK** (`sampling_orders`) dengan `deal_id` menunjuk deal di Langkah 3.
6. **Langkah 5: Seed tabel anak** SPK (knit spec → size chart → program mesin → yield → milestone).
7. **Langkah 6: Validasi** — jalankan seluruh file dalam `BEGIN ... ROLLBACK` sebelum di-apply sungguhan.

> 🧠 **Mental model**: seed data adalah *transaksi bisnis yang diulang waktu*. Urutannya harus
> mengikuti urutan kejadian nyata di pabrik: pelanggan dikenal → deal dimenangkan → SPK diterbitkan
> → panel direajut → dst.

---

## 🔬 3. Bedah Kode Blok per Blok

### 3.1 Kontak dulu, deal belakangan (FK constraint)

```sql
INSERT INTO crm_contacts (id, tenant_id, name, brand_name, phone, ...)
VALUES ('con-seed-001', 'ten-demo-001', 'Rina Kusumawati', 'BKG Apparel', ...)
ON CONFLICT (id) DO NOTHING;
```

- **`ON CONFLICT (id) DO NOTHING`** membuat seed *idempoten*: kalau Flyway menjalankan ulang
  (atau developer menjalankan manual dua kali), tidak ada error, tidak ada duplikat.
- **`tenant_id` wajib eksplisit** — semua tabel operasional di proyek ini multi-tenant.

### 3.2 Deal yang "sudah melewati deal"

```sql
INSERT INTO deals (..., stage, estimated_value_idr, expected_close_date, ...)
VALUES ('deal-seed-001', ..., 'WON', 87500000, CURRENT_DATE - 5, ...);
```

- Enum stage di domain: `OPEN | PO_RECEIVED | IN_PRODUCTION | WON | LOST`
  (`DealValueObjects.kt`). Nilai di SQL harus **persis** nama enum-nya (kapital), karena
  `DealStage.fromCode` yang melakukan pemetaan.
- Tanggal dipakai **relatif** (`CURRENT_DATE - 5`), bukan literal `2026-09-01`. Kenapa?
  Supaya data demo tidak "menua" — deal selalu terlihat baru ditutup pekan lalu berapa pun
  lama repo ini dijalankan.

### 3.3 SPK dengan nomor yang tidak bentrok

Invarian yang harus dijaga:
```sql
CONSTRAINT uq_sampling_order_spk UNIQUE (tenant_id, spk_number)
```
plus logika server:
```kotlin
// PostgresSamplingOrderRepository.nextSpkNumber()
val index = count + 1                       // COUNT(*) dari sampling_orders tenant itu
SpkNumber("SPK-SMP-$padded")                // → "SPK-SMP-0005"
```
Karena dev DB sudah punya 4 baris, seed menempati `0005..0008`; baris berikutnya dari aplikasi
jadi `0009`. Kalau seed memakai `0001`, aplikasi akan **gagal INSERT** dengan unique violation
begitu user membuat SPK pertamanya — bug yang muncul berminggu-minggu kemudian.

### 3.4 JSONB harus cocok dengan decoder — kontrak dua arah

Ini jantung task ini. Setiap kolom JSONB punya pasangan encoder/decoder di
`PostgresSamplingOrderRepository`. Contoh `size_matrix`:

```sql
'[
  {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"2", ...}},
  {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"56", ...}}
]'::jsonb
```

Decoder-nya membaca `id`, `pomName`, `values` — nama kunci yang salah **tidak error**, ia
`getOrDefault(defaultSamplingSizeMatrix())`: data rusak diam-diam jadi data default. Karena itu
kita membaca decoder dulu (baris `parseRevisionHistory`, `loadOrderDetails`) sebelum menulis satu
baris JSON pun. Tiga format yang wajib disalin persis:

| Kolom | Bentuk | Sumber format |
|---|---|---|
| `feeder_instructions` | `[{"feederNumber":1,"name":"RIB","ply":"1 PLAY","color":"HITAM"}]` | `FeederEntry` decode |
| `tenselity_entries` | `[{"parameter":"1 BS POLY","body":"","sleeve":"","collar":""}]` | `TenselityEntry` decode |
| `revision_history` | `[{"revision":1,"notes":"...","at":"2026-09-13T10:00:00Z"}]` | `RevisionFeedback` decode — kuncinya **`notes`**, bukan `note`! |

Perhatikan juga `at` harus ISO-8601 yang bisa `Instant.parse` — di SQL kita membangunnya dengan
`TO_CHAR(..., 'YYYY-MM-DD"T"HH24:MI:SS"Z"')`.

### 3.5 Milestone harus selaras dengan pipeline_stage

28 baris milestone ditanam supaya *ceritanya masuk akal*: SPK berstatus `MACHINE_KNITTING`
hanya menyelesaikan `PROGRAM`; SPK `ACC_APPROVED` menyelesaikan ketujuh tahap. UI milestone
tracker membaca tabel ini — kalau tidak sinkron dengan `pipeline_stage`, demo terlihat berbohong.

---

## 🏗️ 4. Technology & Approach — "The Why"

| Keputusan | Alternatif yang ditolak | Risiko jika pakai cara lain |
|---|---|---|
| **Flyway migration** untuk seed | Seed via endpoint admin / script manual | Migrasi ter-versioning, ter-apply konsisten di semua environment. Script manual selalu "lupa dijalankan di mesin B". |
| **`ON CONFLICT DO NOTHING`** | `INSERT` polos / `TRUNCATE` + insert ulang | Seed yang idempoten aman dijalankan ulang; `TRUNCATE` di produksi adalah bencana. |
| **Tanggal relatif** (`CURRENT_DATE - n`) | Tanggal literal | Data demo menua dan akhirnya menyesatkan (semua deadline lewat → UI penuh badge "terlambat"). |
| **Nomor SPK mulai `COUNT+1`** | Nomor hardcoded bagus apapun kondisinya | Bentrok unique constraint + logika `nextSpkNumber` server. |
| **Validasi `BEGIN...ROLLBACK` dulu** | Langsung apply | Kesalahan sintaks / FK tidak meninggalkan setengah data di DB dev. |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Menulis seed tanpa melihat isi DB dev.** Data uji (`SPK-SMP-0001..0004`, deal "Test") sudah
   ada. Selalu `SELECT` dulu.
2. **Kunci JSONB typo** (`note` vs `notes`). Decoder berbasis `runCatching` → tidak error,
   hanya data hilang diam-diam. Ini kelas bug yang paling mahal dideteksi.
3. **Lupa urutan FK.** Insert deal sebelum kontak = gagal dengan foreign key violation.
4. **Melanggar multi-tenancy**: satu baris pun tanpa `tenant_id` yang benar akan tak terlihat
   oleh RLS (`app.current_tenant_id`), atau lebih buruk, bocor ke tenant lain.
5. **Mengedit migrasi lama** (misal menyuntik seed ke `V23`). Flyway menyimpan checksum —
   migrasi ter-apply yang diubah akan membuat *seluruh* pipeline migrasi gagal validasi.
   Selalu buat nomor baru (`V43`).

---

## ✅ 6. Verifikasi & Cara Menguji

1. **Dry-run transaksional** (tidak meninggalkan jejak):
   ```bash
   cat V43__seed_won_deals_and_sampling_spk.sql | \
     docker exec -i wemade-postgres psql -U postgres -d wemade_erp \
       -v ON_ERROR_STOP=1 -c "BEGIN;" -f - -c "ROLLBACK;"
   ```
   Setiap statement harus mencetak `INSERT 0 n` lalu `ROLLBACK`.
2. **Apply sungguhan**, lalu cek relasinya dengan JOIN:
   ```sql
   SELECT d.stage, s.spk_number, s.status, s.pipeline_stage
   FROM deals d JOIN sampling_orders s ON s.deal_id = d.id
   ORDER BY s.spk_number;
   ```
3. **Uji dari "mata aplikasi" (RLS)** — jalankan sebagai role aplikasi dengan konteks tenant:
   ```bash
   psql -U wemade_app -d wemade_erp \
     -c "SET app.current_tenant_id='ten-demo-001';
         SELECT spk_number FROM sampling_orders WHERE deal_id LIKE 'deal-seed-%';"
   ```
   Kalau baris terlihat di sini, `GET /api/tenant/sampling/orders` dari modul Sampling akan
   menampilkannya (endpoint membaca lewat repository dengan konteks tenant yang sama).
4. **Buka UI** modul Sampling (Wasm/Desktop) → kanban harus mengisi kolom
   Program CAM / Rajut / Linking / Finishing & QC / ACC.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tambahkan satu SPK baru pada `deal-seed-002` dengan status `DRAFT` dan
      pipeline `NEW_INTAKE`. Pastikan nomornya tidak bentrok dengan `nextSpkNumber()`.
- [ ] **Tantangan 2**: Tanam satu baris `sampling_qc_inspections` (skema V41) untuk
      `smp-seed-0008` dengan hasil `REWORK` dan satu pengukuran POM yang melebihi toleransi.
      Cek format JSONB di `measured_pom_values` sebelum menulis!
- [ ] **Tantangan 3**: Buktikan seed ini idempoten — jalankan file dua kali berturut-turut lalu
      pastikan `SELECT count(*) FROM sampling_orders` tidak berubah.

