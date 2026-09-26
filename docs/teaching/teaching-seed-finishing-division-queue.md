# 🎓 Modul Pembelajaran: Seed Antrean Divisi Finishing (V44)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Seed Data Deterministik, Flyway Migration, Aggregate vs Dokumen Terpisah, Derived State
> **Prasyarat**: SQL dasar, paham alur modul Sampling WeMade, pernah baca [`teaching-seed-won-deals-and-sampling-spk.md`](teaching-seed-won-deals-and-sampling-spk.md)
> **Referensi Task**: Seed data sampling tahap finishing agar menu Divisi Finishing bisa dilihat

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Modul **Catatan Kerja Operator Finishing** (`BusinessModule.OPERATOR_EXEC`) sudah selesai
dikodekan — layar, dialog setoran, kartu progres, semuanya ada. Tapi begitu dibuka, layarnya
menampilkan *"Tidak Ada Antrean Finishing"*.

Bukan bug. Databasenya memang kosong untuk kondisi yang layar itu cari.

- **Masalah Nyata**: fitur yang tidak punya data demo **tidak bisa ditinjau**. Reviewer tidak
  bisa membedakan "layarnya memang kosong" dari "query-nya salah". Bug layout seperti badge
  yang menabrak nama SPK panjang juga tidak akan pernah muncul kalau daftarnya nol baris.
- **Analogi Sederhana**: seperti menguji meja kasir tanpa pernah menaruh barang di atasnya.
  Mesinnya menyala, tapi tidak ada satu pun perilaku nyata yang teruji.
- **Hasil Akhir**: 5 kartu di antrean finishing, masing-masing mewakili satu *state* berbeda —
  belum disetor, dicicil, tuntas — sehingga satu kali buka layar sudah menguji semua cabang UI.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Seed data itu **kode terbalik**: kamu tidak mulai dari domain, kamu mulai dari **layar yang
ingin diisi**, lalu mundur ke tabel.

1. **Langkah 0: Baca saringannya, jangan tebak.**
   Buka [`FinishingOperatorWorkspaceScreen.kt:39-46`](../../app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/finishing/FinishingOperatorWorkspaceScreen.kt#L39-L46).
   Di sanalah kriteria sebenarnya:

   ```kotlin
   (order.pipelineStage == LINKING_ASSEMBLY ||
    order.pipelineStage == FINISHING_QC ||
    order.finishingDeposits.isNotEmpty()) &&
   order.finishingPath == FinishingPath.INTERNAL
   ```

   Tanpa langkah ini kamu akan menyeed 10 SPK cantik yang semuanya `MAKLOON_VENDOR` dan tidak
   satu pun muncul.

2. **Langkah 1: Cek kondisi DB sekarang.**
   `select spk_number, pipeline_stage, finishing_path from sampling_orders;`
   Hasilnya: hanya `SPK-SMP-0008` yang lolos saringan. `SPK-SMP-0007` ada di tahap linking tapi
   jalurnya vendor — itu dipantau admin di tab vendor, bukan disetor operator meja.

3. **Langkah 2: Rancang *state* yang mau diuji, baru isi ceritanya.**
   Bukan "bikin 4 SPK", tapi "bikin satu SPK per kondisi kartu": 0/4, 4/6, 3/3, 1/2.

4. **Langkah 3: Cari tahu bagaimana angka target dihitung.**
   Lihat `finishingTargetPcs()` → `calculateTotalSampleQuantity()`. Di sinilah jebakan
   terbesarnya (bagian §5).

5. **Langkah 4: Tulis migration Flyway baru**, jangan menyunting V43.

6. **Langkah 5: Terapkan & verifikasi lewat query agregat**, bukan lewat "kelihatannya jalan".

---

## 🧱 3. Bedah Blok per Blok

### Blok A: SPK dengan matriks ukuran yang *lengkap*

```sql
'[
   {"id":"sampling_qty_row","pomName":"Jumlah Sampel (pcs)","values":{"ALL SIZE":"","S":"2","M":"2","L":"2","XL":"",...}},
   {"id":"pom_lebar_dada","pomName":"Lebar Dada","values":{"ALL SIZE":"","S":"48","M":"51","L":"54","XL":"",...}},
   {"id":"pom_panjang_baju","pomName":"Panjang Baju","values":{"ALL SIZE":"","S":"56","M":"58","L":"60","XL":"",...}}
 ]'::jsonb
```

**Mengapa blok ini ditulis begini?**
- Target finishing **tidak** diambil dari kolom `sample_quantity`. Ia dihitung ulang dari baris
  `sampling_qty_row`, dan sebuah kolom ukuran hanya dihitung bila **seluruh baris POM** terisi
  untuk kolom itu (`isSizeColumnActive`).
- Artinya: satu sel POM kosong di kolom `L` → 2 pcs ukuran L hilang diam-diam dari target, dan
  kartu operator menampilkan `4 pcs` padahal maksudmu `6 pcs`. Gagalnya senyap, tidak ada error.
- Karena itu di seed ini kolom `S/M/L` diisi penuh di ketiga baris, dan kolom lain dibiarkan
  kosong **secara konsisten** di semua baris.

### Blok B: Setoran finishing sebagai tabel terpisah

```sql
INSERT INTO sampling_finishing_deposits (id, tenant_id, sampling_order_id, deposit_date,
                                         qty_pcs, weight_kg, scale_photo_key, ...)
VALUES ('fd-seed-0010-1', 'ten-demo-001', 'smp-seed-0010', CURRENT_DATE - 3,
        2, 0.86, 'finishing/smp-seed-0010/timbangan-01.jpg', ...);
```

**Mengapa blok ini ditulis begini?**
- Setoran **bukan kolom di `sampling_orders`**, melainkan baris tersendiri. Progres `4/6` adalah
  *derived state* — hasil `sum(qty_pcs)`, bukan angka yang disimpan. Tidak ada kolom
  `deposited_pcs` yang bisa melenceng dari riwayatnya.
- Alasan domainnya ada di [`FinishingDeposit.kt`](../../core/src/commonMain/kotlin/com/eventverse/app/domain/sampling/finishing/FinishingDeposit.kt):
  dokumen setoran dimiliki **operator pembuatnya** (`OPERATOR_EXEC`, `ScopeCapability.HIERARCHICAL`),
  sementara SPK dimiliki divisi sampling. Menyatukan keduanya memaksa satu cakupan data untuk
  dua kepemilikan berbeda.
- Yang disimpan adalah **KEY object storage**, bukan presigned URL. URL bertanda tangan punya
  masa kedaluwarsa; kalau ia yang tersimpan, foto bukti akan mati sendiri beberapa jam kemudian.

### Blok C: SPK yang sengaja dibiarkan tanpa setoran

```sql
-- Sengaja tidak ada setoran untuk SPK-SMP-0009: kartu "0 dari 4 pcs" adalah
-- state kosong yang juga perlu terlihat di layar operator.
```

**Mengapa blok ini ditulis begini?**
- Godaan terbesar saat menyeed adalah membuat semuanya "bagus". Padahal state yang paling sering
  rusak justru state nol: progress bar 0%, badge "Perlu Dikerjakan", dan daftar riwayat kosong.
- Komentar eksplisit dipasang supaya orang berikutnya tidak "memperbaiki" data ini dengan
  menambahkan setoran.

### Blok D: Idempotensi & penomoran SPK

```sql
ON CONFLICT (id) DO NOTHING;
```

**Mengapa blok ini ditulis begini?**
- `PostgresSamplingOrderRepository.nextSpkNumber()` = `COUNT(*) + 1`. DB dev sudah memuat
  `0001..0008`, jadi seed ini **wajib** mengisi `0009..0012` — kalau meloncat, nomor berikutnya
  yang diterbitkan aplikasi akan menabrak `UNIQUE (tenant_id, spk_number)`.
- `ON CONFLICT DO NOTHING` membuat file ini aman dijalankan dua kali: sekali manual ke DB dev,
  sekali lagi oleh Flyway saat server restart.

---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

| Pilihan | Alternatif | Mengapa Kita Memilih Ini? | Risiko Alternatif |
|---|---|---|---|
| Migration Flyway baru (V44) | Menyunting V43 | Checksum V43 sudah tercatat di `flyway_schema_history`; menyuntingnya membuat migrasi gagal di setiap mesin yang sudah menjalankannya | Server tim lain menolak start dengan *checksum mismatch* |
| Seed via SQL | Seed via endpoint/skrip Kotlin | Data demo adalah bagian dari skema lingkungan dev, ikut versi dan ikut `flyway migrate` — tidak perlu langkah manual terpisah | Mesin baru dapat DB kosong, dan setiap orang menyeed versinya sendiri |
| ID deterministik (`smp-seed-0010`) | ID acak/UUID | Bisa di-`ON CONFLICT DO NOTHING`, bisa dirujuk antar-INSERT, dan bisa dihapus bersih saat tidak dipakai | Seed ganda beranak-pinak tiap kali dijalankan |
| Progres dihitung dari setoran | Kolom `deposited_pcs` | Satu sumber kebenaran; angka tidak mungkin berbeda dari riwayatnya | Kolom dan riwayat bisa berselisih, dan tidak ada cara tahu mana yang benar |

---

## ⚠️ 5. Jebakan Pemula

1. **Jebakan: Mengisi `sample_quantity` dan mengira itu targetnya.**
   - *Kenapa bahaya*: target finishing dihitung dari `size_matrix`; `sample_quantity` hanya
     **cadangan** untuk SPK lama yang matriksnya belum terisi. Angka di layar akan berbeda dari
     angka yang kamu tulis, dan kamu akan mencari bug di tempat yang salah.
   - *Solusi*: isi baris `sampling_qty_row` **dan** seluruh baris POM untuk kolom yang dipakai.

2. **Jebakan: Menyeed SPK berjalur `MAKLOON_VENDOR` lalu heran layarnya tetap kosong.**
   - *Kenapa bahaya*: jalur vendor memang sengaja tidak masuk antrean meja — bukan bug, itu
     aturan domain ([`GetFinishingQueueUseCase.kt`](../../core/src/commonMain/kotlin/com/eventverse/app/domain/sampling/finishing/usecases/GetFinishingQueueUseCase.kt)).
   - *Solusi*: baca saringannya dulu (Langkah 0), baru tulis datanya.

3. **Jebakan: Menyimpan URL foto, bukan KEY object storage.**
   - *Kenapa bahaya*: presigned URL kedaluwarsa. Data seed akan tampak benar hari ini dan rusak
     minggu depan.
   - *Solusi*: simpan `finishing/<orderId>/timbangan-01.jpg`; URL dibuat saat dibutuhkan.

4. **Jebakan: Meloncati nomor SPK.**
   - *Kenapa bahaya*: `nextSpkNumber() = COUNT(*) + 1` akan menerbitkan nomor yang sudah dipakai.
   - *Solusi*: hitung dulu isi tabelnya, lanjutkan berurutan.

---

## 🧪 6. Membuktikan Seed-nya Bekerja

Seed tidak diuji dengan unit test — ia diuji dengan **query yang meniru saringan layar**:

```sql
select o.spk_number, o.pipeline_stage, coalesce(sum(d.qty_pcs),0) as disetor
from sampling_orders o
left join sampling_finishing_deposits d on d.sampling_order_id = o.id
where o.tenant_id='ten-demo-001' and o.finishing_path='INTERNAL'
  and o.pipeline_stage in ('LINKING_ASSEMBLY','FINISHING_QC')
group by 1,2 order by 1;
```

Hasil yang diharapkan: 5 baris dengan progres `0/3`, `0/4`, `4/6`, `3/3`, `1/2`.

Lalu — dan ini bagian yang tidak tergantikan — **buka layarnya dan lihat dengan mata**. Angka
benar di SQL tidak menjamin kartunya tidak saling menimpa.

Ada juga [`SeedTopologyConsistencyTest`](../../server/src/test/kotlin/com/eventverse/app/SeedTopologyConsistencyTest.kt)
yang menjaga seed tidak bertentangan dengan topologi pipeline — jalankan sebelum merge.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tambahkan satu SPK yang **over-deposit** (disetor 5 dari target 4).
      Lihat `FinishingProgress.overDepositedPcs` — kenapa kelebihan setor dicatat, bukan ditolak?
- [ ] **Tantangan 2**: Seed satu inspeksi QC (`sampling_qc_inspections`) ber-hasil `REWORK` untuk
      `SPK-SMP-0011` yang finishing-nya sudah tuntas. Apa yang berubah di layar QC vs layar finishing?
- [ ] **Tantangan 3**: Ubah satu sel POM `SPK-SMP-0010` kolom `L` menjadi string kosong, refresh
      layar, dan jelaskan kenapa targetnya turun jadi 4 pcs. Ini latihan membaca *derived state*.
