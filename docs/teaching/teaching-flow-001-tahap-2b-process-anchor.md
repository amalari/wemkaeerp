# 🎓 Modul Pembelajaran: Jangkar Proses Opsional ke `StageCode` (TRD-FLOW-001 Tahap 2b)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Mengganti tipe field data class, kompilator sebagai daftar kerja, parser warisan
> **Prasyarat**: [Tahap 2a — transfer](teaching-flow-001-tahap-2a-transfer-stagecode.md)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — Tahap 2, paket `process`

---

## 💡 1. Konsep Dasar

`TenantOptionalProcess.samplingAnchorAfter` menjawab "Bordir disisipkan **setelah** tahap apa?".
Selama tipenya enum rajut, jawaban yang mungkin hanya 12 tahap rajut. Setelah menjadi
`StageCode`, proses bisa berjangkar pada tahap template industri mana pun.

Berbeda dari Tahap 2a, ini **data tersimpan milik tenant** (kolom
`tenant_optional_processes.sampling_anchor_after`, dan JSON di wire REST). Karena kode rajut =
nama enum, isi kolom tidak berubah satu byte pun — **tanpa migrasi**.

---

## 🧭 2. "Start dari Mana?"

1. **Ganti tipenya dulu, jangan cari pemakainya dengan grep.** Begitu field menjadi
   `StageCode?`, setiap `it.samplingAnchorAfter == stage` (dengan `stage` enum) menjadi error
   kompilasi *"Operator '==' cannot be applied"*. Kompilator menghasilkan daftar kerja yang
   **lengkap**; grep tidak pernah bisa menjamin itu.
2. Perbaiki per lapisan: `core` → `server` → `app/shared` → test.
3. Pisahkan yang *belum* dipindah: `PhaseTaggableStage` dan `skippedSamplingStages` terikat erat
   pada `SamplingRoute`; mereka pindah bersama paket `sampling` (dan diganti trait
   `PHASE_TAGGABLE` di Tahap 3).

---

## 🧱 3. Bedah Kode

### Blok A — Parser warisan: satu fungsi, aturan lama

```kotlin
fun parseLegacyStageCodeOrNull(raw: String?): StageCode? =
    SamplingPipelineStage.parseOrNull(raw)?.toStageCode()
```

- Dipakai di tiga pintu masuk data: `ProcessCatalogCodec`, `PostgresTenantProcessRepository`,
  `TenantProcessRoutes`.
- Kenapa tidak `StageCode.parseOrNull`? Karena itu mengubah apa yang diterima: alias
  `FINISHING_QC` dulu **diterjemahkan** ke `CUCI_SOFTENER`, dan nama tak dikenal dulu **ditolak**.
  Parser baru yang "lebih bersih" akan menyimpan `FINISHING_QC` apa adanya — proses Bordir lalu
  berjangkar pada tahap yang tidak pernah ada di papan, dan diam-diam hilang dari kanban.

### Blok B — Urutan sortir yang harus identik

```kotlin
.sortedBy { it.samplingAnchorAfter?.toSamplingStageOrNull()?.order ?: Int.MAX_VALUE }
```

- `ResolveActiveProcessesUseCase` dulu memakai `enum.order`. Sementara, urutan tetap diambil dari
  enum lewat jembatan; di Tahap 3 urutannya datang dari `TenantStageFlow.indexOf(code)` —
  karena urutan adalah milik kerangka tenant, bukan milik kode tahap.

### Blok C — Mengubah file yang sedang diedit orang lain

`ProcessFlowViewModel.kt` dan `ProcessFlowAdjusterPanel.kt` punya perubahan lokal yang belum
di-commit. Perubahannya dibuat **satu token per baris** (`.toStageCode()`) + satu import di posisi
alfabetisnya, supaya diff mudah dipisahkan dari pekerjaan pemilik file.

---

## ⚠️ 4. Jebakan Pemula

1. **Regex massal di test.** Pola `samplingAnchorAfter = <variabel>` ikut menangkap literal
   `null` dan menghasilkan `null?.toStageCode()`. Selalu baca ulang diff setelah perubahan massal.
2. **Assertion yang berubah tipe.** `assertEquals(enum, field)` tetap *terkompilasi* (keduanya
   `Any?`) tapi **gagal saat runtime**. Satu assertion diubah menjadi
   `assertEquals(SamplingPipelineStage.CUCI_SOFTENER.toStageCode(), …)` — makna sama, tipe baru.
   Perubahan seperti ini wajib dilaporkan, bukan disembunyikan di antara perubahan fixture.

---

## 🧪 5. Pembuktian

- `core`: 128/128 kelas test lulus; `app:shared`: 24/24 kelas test lulus.
- Perubahan test: hanya **fixture** (argumen konstruktor `→ .toStageCode()`), kecuali satu
  assertion tipe di `TenantProcessCatalogTest` (Blok 4.2).
- Kompilasi `core` JVM/JS/wasm, `server` main+test, `app:shared` JVM/JS/wasm — hijau.
- Tidak ada migrasi DB: kolom menyimpan string yang sama.

---

## 🏆 6. Tantangan Mandiri

- [ ] Ubah `ProcessFlowUiEvent.anchorAfter` menjadi `StageCode` sehingga `.toStageCode()` di
      ViewModel hilang — di mana batas terakhir enum di lapisan presentasi?
- [ ] Rancang pengganti `parseLegacyStageCodeOrNull` untuk Tahap 3 yang memvalidasi terhadap `TenantStageFlow` tenant, bukan terhadap enum.
