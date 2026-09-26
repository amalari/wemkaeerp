# 🎓 Modul Pembelajaran: Meja Operator per Bagian — Kanban Antrian / Sedang Dikerjakan / Selesai

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: DDD (entity + extension function domain), audit trail sebagai sumber kebenaran, rework loop (Kontrak 5), Ktor route per sub-agregat, Exposed, Compose kanban, Aturan Tiga Kali, Aturan Ratchet
> **Prasyarat**: Paham `SamplingOrder`, `SamplingPipelineStage`, dan alur `POST /{id}/stage`
> **Referensi Task**: Lantai Produksi `/operator-exec` — pecah meja "Finishing & Linking" menjadi satu meja per bagian

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Dulu Lantai Produksi hanya punya dua meja: *Rajut* dan *Finishing & Linking*. Meja kedua menampung lima bagian sekaligus (Linking, Cuci, Setrika, QC, Kemas), sehingga:
- Tukang setrika harus mencari kartunya di antara kartu linking dan QC.
- Sistem tidak bisa menjawab **"sampelnya sekarang sedang di tangan siapa?"** — yang tercatat hanya *tahap*, bukan *orang*.
- Kalau QC menemukan jahitan lepas, tidak ada jalan mengirim SPK **mundur** ke Linking. Orang akhirnya bilang lewat WhatsApp, dan sistemnya jadi bohong.

**Analogi.** Bayangkan dapur restoran dengan papan tiket per stasiun (grill, goreng, plating). Tiap stasiun punya tiga jepitan: *tiket masuk*, *sedang dimasak*, *sudah diantar*. Kalau plating menemukan steak gosong, tiketnya dijepit balik ke **grill**, ditulisi merah "gosong — ulang", dan ditaruh paling depan.

**Hasil akhir.**
- Enam meja: **Rajut | Linking | Cuci | Setrika | QC | Kemas**. Daftarnya dibaca dari rentang tahap, bukan ditulis tangan.
- Tiap meja punya tiga kolom:
  - **Antrian** → tombol *Mulai*.
  - **Sedang Dikerjakan** → tombol *Kembalikan*, *Rework*, dan *Selesai* dengan aksi khas bagian itu.
  - **Selesai (hari ini)**.
- Tombol **Riwayat** membaca semua serah terima dari `stageHistory` yang sudah ada. Tidak ada tabel baru.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — Kontrak domain dulu (`core`).** Pertanyaan pertamanya bukan "tabelnya apa", tapi **"kartu ini ada di kolom mana, dan siapa yang berhak memindahkannya?"**. Jawabannya aturan murni. Kalau aturannya diletakkan di Composable, test tidak bisa menjangkaunya.
2. **Langkah 1 — Value object.** `StageWorkClaim(stage, operatorName, actorEmail, startedAt)` dan `OperatorDeskColumn`. Lalu tambah dua field opsional di `StageTransitionAudit`: `reason` dan `liability`.
3. **Langkah 2 — Aturan entity.**
   - Buat `startStageWork`, `releaseStageWork`, `sendBackForRework`, `deskColumn`, dan `handoffsFrom` di file baru `SamplingStageWork.kt`.
   - Buat helper `movedTo()` di `SamplingOrder`.
4. **Langkah 3 — Repository.** Tidak ada operasi baru, karena `findById` + `save` sudah cukup.
5. **Langkah 4 — Use case.** `StartSamplingStageWorkUseCase`, `ReleaseSamplingStageWorkUseCase`, `SendBackSamplingReworkUseCase`. Satu operasi bisnis per use case.
6. **Langkah 5 — Infrastruktur.**
   - Migrasi `V65__sampling_active_work.sql`.
   - Kolom `activeWork` di `SamplingOrdersTable`.
   - Codec `encodeClaim` / `decodeClaim`.
   - Route `SamplingStageWorkRoutes.kt`.
7. **Langkah 6 — Client.**
   - Tiga method di `SamplingRemoteDataSource`.
   - Event baru di `SamplingUiState.kt`.
   - Delegasi `SamplingStageWorkActions`.
8. **Langkah 7 — UI.**
   - Papan murni `buildOperatorDeskBoard`.
   - `ClayKanbanColumn` di design system.
   - Kartu, meja, tiga dialog (Rework, Riwayat, QC).
   - Layar induk `OperatorFloorWorkspaceScreen`.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Satu jalan pindah tahap — `movedTo()`

```kotlin
internal fun movedTo(target: SamplingPipelineStage, audit: StageTransitionAudit): SamplingOrder = copy(
    pipelineStage = target,
    activeWork = null,
    stageHistory = stageHistory + audit,
    updatedAt = audit.at
)
```

**Mengapa begini?**
- Sebelumnya `addFinishingDeposit` dan `completeQcInspection` mengganti `pipelineStage` **tanpa menulis audit**. Akibatnya kolom *Selesai* di meja Linking dan QC akan selalu kosong, padahal barangnya sudah pindah. Kolom *Selesai* dan dialog *Riwayat* hanya bisa jujur kalau **setiap** perpindahan tercatat.
- Melepas klaim (`activeWork = null`) di titik yang sama mencegah keadaan aneh "sedang dikerjakan di tahap yang sudah lewat".

### Blok B: Klaim yang otomatis kedaluwarsa

```kotlin
val SamplingOrder.currentWork: StageWorkClaim?
    get() = activeWork?.takeIf { it.stage == pipelineStage }
```

**Mengapa begini?**
- Masih ada jalur lama yang mengubah tahap tanpa lewat `movedTo()`, yaitu `assignMakloonVendor` dan `recordVendorReturn`.
- Daripada menambal semua jalur itu, klaim menyimpan tahapnya sendiri. Begitu tahap SPK berbeda, klaim itu tidak berlaku lagi, jadi kebenarannya dijaga oleh datanya sendiri.

### Blok C: Rework = entri audit, bukan daftar baru

```kotlin
data class StageTransitionAudit(
    …,
    val reason: String? = null,
    val liability: DefectLiability? = null
) { val isRework: Boolean get() = liability != null }

fun SamplingOrder.sendBackForRework(target, reason, liability, …): SamplingOrder {
    require(target in pipelineStage.reworkTargets) { "Rework hanya bisa dikirim ke meja sebelum …" }
    require(reason.isNotBlank()) { "Alasan rework wajib diisi" }
    return movedTo(target, StageTransitionAudit(…, reason = reason.trim(), liability = liability))
}
```

**Mengapa begini?**
- "SPK ini lewat mana saja" adalah **satu garis waktu**. Kalau disimpan dua daftar (`stageHistory` + `reworkHistory`), pembaca harus menjahit ulang urutannya, dan kita juga butuh kolom DB tambahan.
- `DefectLiability` (pabrik / kain buyer / supplier) wajib dipilih. Ini memenuhi Kontrak 5 di `module-integration-rules.md`, supaya biaya rework tidak salah dibebankan ke penjahit.
- `handoffsFrom(stage)` membuang entri rework, karena barang yang dikirim balik **belum selesai**.

### Blok D: Migrasi — `text`, bukan `jsonb`

```sql
ALTER TABLE sampling_orders
    ADD COLUMN IF NOT EXISTS active_work text NOT NULL DEFAULT '';
```

**Mengapa begini?** Keadaan yang paling sering justru "tidak ada klaim". String kosong bukan JSON yang sah untuk `jsonb`, jadi pilihannya adalah kolom nullable dengan penanganan khusus, atau `text` yang di-decode secara toleran oleh `decodeClaim`. Yang kedua lebih sederhana.

### Blok E: Aturan Ratchet di repository 723 baris

```kotlin
private fun SamplingOrdersTable.writeOrderColumns(row: UpdateBuilder<*>, order: SamplingOrder) {
    row[clientName] = order.clientName
    …
    row[activeWork] = StageWorkInputCodec.encodeClaim(order.activeWork)
}
```

**Mengapa begini?**
- `PostgresSamplingOrderRepository.kt` sudah di atas hard limit server (500 baris). Kontrak Ratchet melarangnya bertambah panjang.
- Blok `insert` dan `update` isinya 30 baris kembar. Keduanya disatukan jadi satu fungsi, dan filenya turun **723 → 701** walaupun ada kolom baru.
- Bonusnya: kolom baru sekarang cukup ditulis di **satu** tempat. Dulu risikonya lupa menulis di salah satu blok.
- Trik yang sama dipakai di `SamplingOrder.kt` (402 → 386 baris): `requireStageGate` dipindah ke `SamplingStageGate.kt`.

### Blok F: Route per sub-agregat

```kotlin
fun Route.samplingStageWorkRoutes(repository: SamplingOrderRepository) {
    route("/api/tenant/sampling/orders/{id}") {
        post("/work/start") { … }
        post("/work/release") { … }
        post("/rework") { … }
    }
}
```

**Mengapa begini?**
- `SamplingRoutes.kt` sudah 415 baris, di atas soft limit server.
- Aktor dibaca dari JWT (`callerPrincipalOrNull`), bukan dari body request, supaya identitasnya tidak bisa dipalsukan klien.
- `require` di domain menghasilkan `IllegalArgumentException`, yang diterjemahkan menjadi **422** berikut pesannya. Pesan itu muncul di `ClayStatusBanner`.

### Blok G: Papan murni + aksi "Selesai" per bagian

```kotlin
fun buildOperatorDeskBoard(orders, stage, today, timeZone): OperatorDeskBoard
fun SamplingPipelineStage.finishAction(): DeskFinishAction? = when {
    this == MACHINE_KNITTING -> Worksheet(LINKING_ASSEMBLY)   // lembar turun mesin
    this == LINKING_ASSEMBLY -> Deposit                        // setoran, pindah sendiri saat genap
    this == QC_FINISHING    -> QcInspection                    // form QC yang sama dengan modul QC
    isOperatorDesk          -> nextStage?.let(::Handoff)       // cuci / setrika / kemas
    else -> null
}
```

**Mengapa begini?**
- Tiap bagian mencatat hal yang berbeda saat selesai: rajut mengisi gramasi, linking mencatat setoran, QC mengisi berita acara. Satu tombol "Selesai" generik akan membuang data itu.
- `sealed interface` membuat `when` di layar induk wajib menangani semua kasus.
- Papan disusun oleh fungsi murni, jadi urutan (rework di puncak, lalu FIFO) dan filter "hari ini" diuji tanpa merender apa pun.

### Blok H: `ClayKanbanColumn` — Aturan Tiga Kali

Kanban Sampling dan Kanban CRM masing-masing punya kolom sendiri. Meja operator adalah pemakaian **ketiga**, jadi kolomnya diangkat ke `designsystem/`. Komponen ini **buta domain**: menerima `title`, `count`, `tint`, dan slot `LazyListScope`, tidak pernah `SamplingOrder`.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa Ini | Risiko Alternatif |
|---|---|---|---|
| Klaim `activeWork` di order | Tabel `work_sessions` terpisah | Satu klaim per SPK sudah cukup; ikut tersimpan dalam satu `save` | Dua sumber kebenaran, dan harus transaksi lintas tabel |
| Rework di `stageHistory` | Kolom `rework_history` | Satu garis waktu, tanpa kolom baru | Urutan harus dijahit manual, migrasi bertambah |
| Riwayat dibaca dari audit | Tabel riwayat meja | Datanya sudah ada | Data ganda yang bisa saling menyimpang |
| Extension function di file terpisah | Method di dalam `SamplingOrder` | Entity sudah di ambang hard limit | Melanggar Aturan Ratchet |
| Meja = tampilan di satu modul `OPERATOR_EXEC` | Satu `BusinessModule` per meja | Tidak merembet ke RBAC, entitlement, dan seed tenant | Enam entri RBAC baru untuk satu layar |
| Delegasi `SamplingStageWorkActions` | Tambah semua handler ke ViewModel | ViewModel sudah 451 baris | God ViewModel |

---

## ⚠️ 5. Jebakan Pemula

1. **Mengubah `pipelineStage` lewat `copy()` langsung.**
   - *Bahaya*: tidak ada audit, jadi kolom *Selesai* dan *Riwayat* diam-diam kosong.
   - *Solusi*: selalu lewat `movedTo()`, atau `advancePipelineStage()` yang memanggilnya.
2. **Menganggap `activeWork != null` berarti sedang dikerjakan.**
   - *Bahaya*: klaim basi dari tahap sebelumnya ikut terhitung.
   - *Solusi*: pakai `currentWork` / `deskColumn()`.
3. **Menghitung rework sebagai "selesai".**
   - *Bahaya*: QC terlihat produktif padahal barangnya balik.
   - *Solusi*: `handoffsFrom` menyaring `!isRework` dan `toStage.order > stage.order`.
4. **Membaca `Clock.System.now()` di badan Composable.**
   - *Bahaya*: dihitung ulang setiap rekomposisi.
   - *Solusi*: `remember(state.orders) { … }`.
5. **Menambah baris ke file yang sudah melanggar batas ukuran.**
   - *Bahaya*: melanggar Ratchet.
   - *Solusi*: cari duplikasi (insert/update kembar) atau pindahkan satu konsep ke file sendiri. Catat `wc -l` sebelum dan sesudah.

---

## 🧪 6. Bagaimana Membuktikan Kodingan Kita Bekerja?

- **Domain** (`core/.../SamplingStageWorkTest.kt`, 12 test): mulai/kembalikan klaim, tolak klaim ganda, klaim basi, setoran genap tercatat sebagai serah terima, rework hanya boleh mundur dan wajib beralasan, round-trip codec, dan payload lama tanpa `activeWork`.
- **Presentation** (`app/shared/.../OperatorDeskBoardTest.kt`, 4 test): pemisahan kolom per tahap, kartu rework di puncak, *Selesai* hanya hari ini sementara riwayat utuh, SPK makloon tidak muncul di meja Linking.

```kotlin
@Test
fun `sendBackForRework to earlier desk should land in its queue with reason`() {
    val reworked = order(QC_FINISHING).startStageWork("QC Ani", "", now)
        .sendBackForRework(LINKING_ASSEMBLY, "Jahitan bahu lepas", FACTORY_WORKMANSHIP, "ani@x.id", "QC", later)
    assertEquals(OperatorDeskColumn.QUEUE, reworked.deskColumn(LINKING_ASSEMBLY))
    assertEquals(1, reworked.reworkCount)
    assertTrue(reworked.handoffsFrom(QC_FINISHING).isEmpty())
}
```

- **Manual**: restart server (supaya migrasi V65 jalan), lalu buka `/operator-exec`.
  - Rajut: *Mulai*, lalu *Turun Mesin, Isi Lembar*, dan kartu muncul di Antrian Linking.
  - QC: *Rework* ke Linking, dan kartu muncul paling atas di Antrian Linking berbadge merah.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Salurkan `assignMakloonVendor` dan `recordVendorReturn` lewat `movedTo()`, supaya perpindahan makloon ikut tercatat di Riwayat.
- [ ] **Tantangan 2**: Tampilkan "lama dikerjakan" (sekarang − `startedAt`) di kartu, dan beri warna `Warning` bila lewat target menit dari lembar CAM. Ini bentuk telemetri `cycleTimeHours` (Kontrak 6).
- [ ] **Tantangan 3**: Migrasikan `CrmKanbanColumn` untuk memakai `ClayKanbanColumn`. Slot drag-and-drop perlu diangkat sebagai parameter opsional.
