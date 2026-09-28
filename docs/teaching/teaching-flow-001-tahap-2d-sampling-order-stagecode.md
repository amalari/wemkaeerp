# 🎓 Modul Pembelajaran: `SamplingOrder.stageCode` sebagai Sumber Kebenaran (TRD-FLOW-001 Tahap 2d)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Memisahkan tulis dari baca, properti turunan sebagai jembatan, Aturan Ratchet
> **Prasyarat**: [Tahap 2c](teaching-flow-001-tahap-2c-sampling-route.md)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — Tahap 2, `sampling` bagian 2/3

---

## 💡 1. Konsep Dasar

`SamplingOrder.pipelineStage` adalah field paling sering dibaca di seluruh sistem:
**111 referensi di 31 file**, kebanyakan di layar (kanban, meja operator, timeline, dialog SPK).
Mengganti tipenya sekaligus = satu PR raksasa yang menyentuh file berisi perubahan lokal orang
lain, plus `DealDetailDialog.kt` (3006 baris, di bawah Aturan Ratchet).

Pengamatan kuncinya: dari 111 referensi, hanya **±20 yang menulis**. Sisanya membaca.

**Analogi**: memindahkan kantor pusat. Alamat surat-menyurat resmi (tempat *menulis*) dipindah
hari ini; kantor lama dijadikan *penerus surat* supaya semua pengirim lama (pembaca) tetap
sampai, lalu mereka diberi tahu alamat baru satu per satu.

---

## 🧭 2. "Start dari Mana?"

1. **Hitung tulis vs baca** (`grep "pipelineStage\s*="`). Keputusan arsitekturnya lahir dari angka ini.
2. Jadikan konstruktor menyimpan `stageCode: StageCode`.
3. Jadikan `pipelineStage` **properti turunan read-only** — pembaca tidak berubah sama sekali.
4. Perbaiki hanya penulis: domain (`movedTo`, `requestRevision`), use case publish, codec,
   repository, satu baris ViewModel, dan fixture test.

---

## 🧱 3. Bedah Kode

### Blok A — Field tersimpan & jembatan baca

```kotlin
val stageCode: StageCode = SamplingPipelineStage.NEW_INTAKE.toStageCode(),
...
val pipelineStage: SamplingPipelineStage
    get() = checkNotNull(stageCode.toSamplingStageOrNull()) { "Tahap ${stageCode.value} belum didukung jalur enum" }
```

- `copy(pipelineStage = …)` kini **error kompilasi** — justru itu yang kita mau: kompilator
  menemukan semua penulis, tidak ada satu pun yang lolos menulis ke jalur lama.
- **Gagal keras, bukan fallback.** Kode non-rajut (`MACHINE_EMBROIDERY`) melempar
  `IllegalStateException`. Alternatifnya — jatuh ke `NEW_INTAKE` — akan membuat kartu yang sedang
  dibordir muncul sebagai SPK baru. Test `bridge_withNonKnitCode_shouldFailLoudlyNotFallBack`
  mengunci keputusan ini, dan constraint TRD melarang template kedua aktif sebelum bagian 3.

### Blok B — Aturan Ratchet di file yang sudah kegemukan

`PostgresSamplingOrderRepository.kt` (619 baris, di atas hard limit server 500) wajib **tidak
bertambah panjang**. Import baru akan menambah satu baris — tapi file itu sudah memakai
`import com.eventverse.app.domain.sampling.*`, jadi `toStageCode()` sudah terjangkau. Perubahan
satu baris, 619 → 619.

`SamplingOrder.kt` sendiri 381 → 387 baris (hard limit `core` 400). Tambahannya dijaga minimal:
satu field berkomentar satu baris, satu properti tiga baris, satu import.

### Blok C — Yang sengaja ditunda

`SamplingSnapshot.pipelineStage` (arsip revisi) dan `StageTransitionAudit` (riwayat perpindahan)
masih enum. Keduanya **catatan sejarah** dengan codec sendiri — pindah di bagian 3 bersama
codec/repository, supaya satu PR tidak mengubah bentuk data hidup *dan* data arsip sekaligus.

---

## ⚠️ 4. Jebakan Pemula

1. **Mengganti tipe field yang dibaca 90 kali.** Pisahkan tulis dari baca dulu.
2. **Fallback diam-diam pada jembatan.** Lihat Blok A.
3. **Percaya "BUILD SUCCESSFUL in 1s".** Cek stempel waktu XML hasil test terhadap waktu mulai
   perintah; di sini hasil `core` ternyata ditulis perintah sebelumnya — tetap sah karena sesudah
   perubahan, tapi harus dibuktikan, bukan diasumsikan.
4. **Error "Unresolved reference 'Department'" di `AccountCreationPolicyTest`** muncul lagi pada
   kompilasi test inkremental. Bukan kode — cache Gradle. `./gradlew :core:compileTestKotlinJvm --rerun`.

---

## 🧪 5. Pembuktian

- `core`: **889 test, 0 gagal** (129 kelas); `app:shared`: **141 test, 0 gagal** (24 kelas).
- Perubahan test lama: hanya **13 baris fixture** (`pipelineStage = X` → `stageCode = X.toStageCode()`),
  nol assertion.
- Test baru `SamplingOrderStageCodeTest` (4): default, advance menulis kode, gagal keras untuk
  kode non-rajut, round-trip codec.
- Kompilasi `core`/`app:shared` JVM/JS/wasm, `server` main+test — hijau.
- Tidak ada migrasi DB: kolom `pipeline_stage` tetap berisi string yang sama.

---

## 🏆 6. Tantangan Mandiri

- [ ] Pilih satu pembaca (mis. `SamplingKanbanCard.kt`, 6 referensi) dan pindahkan ke `stageCode`.
      Apa yang harus menggantikan `pipelineStage.displayName`?
- [ ] Rancang bagaimana `StageTransitionAudit` menyimpan `fromStage`/`toStage` sebagai kode
      tanpa merusak riwayat lama.
