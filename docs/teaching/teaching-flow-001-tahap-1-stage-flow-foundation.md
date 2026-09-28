# 🎓 Modul Pembelajaran: Fondasi Kerangka Tahap Dinamis (TRD-FLOW-001 Tahap 1)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Strangler Fig Migration, Parity Testing, Value Object sebagai key tersimpan, JSONB + RLS
> **Prasyarat**: Paham enum `SamplingPipelineStage`, pola repository JSONB (`PostgresTenantStagePhaseTagsRepository`)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — Tahap 1

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: kerangka alur sampling adalah `enum` khas pabrik rajut. Perusahaan bordir
  atau konveksi jahit tidak bisa memakai sistem tanpa melewati "Rajut Turun Mesin". Tapi enum
  itu dipakai di **71 file / ±230 referensi** — mengganti semuanya dalam satu PR berarti PR yang
  tidak bisa direview dan tidak bisa di-rollback.
- **Analogi — pohon ara pencekik (*Strangler Fig*)**: pohon ara tumbuh *di sekeliling* pohon
  lama, perlahan mengambil alih, sampai pohon lama bisa dilepas tanpa ada yang roboh. Tahap 1
  adalah menanam bibitnya: struktur baru berdiri **di samping** enum, belum ada yang bersandar
  padanya.
- **Hasil akhir Tahap 1**: paket `domain/stageflow`, template `KNIT_SWEATER`, tabel V72,
  `GET /api/tenant/stage-flow`. **Nol perubahan perilaku** — dan itu dibuktikan test, bukan diklaim.

---

## 🧭 2. "Start dari Mana?"

1. **Langkah 0 — Tetapkan syarat keberhasilan dulu: paritas.** Sebelum menulis entity, tentukan
   bagaimana membuktikan "tidak ada yang berubah". Jawabannya: template rajut harus identik
   dengan enum di setiap sumbu yang dibaca logika (kode, nama, urutan, `next`, sifat-sifat).
2. **Langkah 1 — Value object `StageCode`.** Karena kode ini akan menjadi key yang tersimpan di
   DB, validasinya ditentukan paling awal.
3. **Langkah 2 — Agregat `TenantStageFlow`** dengan invarian anchor.
4. **Langkah 3 — Template seed** `IndustryStageTemplates.KNIT_SWEATER`.
5. **Langkah 4 — Test paritas** (ditulis sebelum menyentuh server).
6. **Langkah 5 — Jembatan** `SamplingStageCodeBridge` untuk Tahap 2.
7. **Langkah 6 — Codec, migrasi, repository, route** meniru pola V71 persis.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A — `StageCode`: kode yang sama dengan nama enum

```kotlin
@JvmInline
value class StageCode(val value: String) {
    init { require(PATTERN.matches(value)) { ... } }
    companion object { private val PATTERN = Regex("^[A-Z][A-Z0-9_]{1,47}$") }
}
```

- Huruf besar + underscore **bukan selera**: itu syarat agar `MACHINE_KNITTING` sebagai
  `StageCode` identik dengan `SamplingPipelineStage.MACHINE_KNITTING.name`. Akibatnya
  `sampling_orders.current_stage` dan key `STAGE:MACHINE_KNITTING` di leg transfer tetap valid
  — **migrasi data nol baris**.
- Regex menolak `:` karena `FlowNodeRef.key` memakai `:` sebagai pemisah.
- `import kotlin.jvm.JvmInline` wajib di `commonMain` — tanpa itu target JS/wasm gagal kompilasi
  meskipun JVM hijau (ini terjadi saat pengerjaan; kompilasi satu target tidak cukup).

### Blok B — Invarian anchor lewat urutan enum `StageKind`

```kotlin
enum class StageKind { ENTRY_ANCHOR, WORK, EXIT_ANCHOR }

private fun kindsAreGrouped(): Boolean =
    stages.zipWithNext().all { (a, b) -> a.kind.ordinal <= b.kind.ordinal }
```

- Satu baris menjamin: semua tahap masuk di depan, tahap kerja di tengah, tahap keluar di
  belakang. Tenant nanti bebas menyusun **isi tengah**, tapi tidak bisa meletakkan "Bordir"
  setelah "ACC Produksi" — yang akan merusak invoice & surat jalan.

### Blok C — Sifat tahap sebagai label, bukan rentang

```kotlin
// lama (enum):
val isOnFinishingFloor get() = order in LINKING_ASSEMBLY.order..PENGEMASAN.order
// baru (template):
stage("LINKING_ASSEMBLY", ..., FINISHING_FLOOR)
```

- Rentang `order` hanya benar selama tahap tetap di kode. Begitu tenant menyisipkan tahap,
  "rentang" tidak punya arti. Label ikut melekat pada tahapnya ke mana pun ia dipindah.

### Blok D — Test paritas: kontrak antara dunia lama dan baru

```kotlin
SamplingPipelineStage.entries.forEach { legacy ->
    assertEquals(legacy.nextStage?.name, knit.next(legacy.toStageCode())?.code?.value)
    assertEquals(legacy.isOnFinishingFloor, stage.has(StageTrait.FINISHING_FLOOR))
    ...
}
```

- Test ini **mengiterasi enum**, bukan daftar yang ditulis ulang tangan. Kalau seseorang
  menambah entri enum tanpa memperbarui template, test gagal. Selama Tahap 2 berjalan, inilah
  jaring pengaman yang membuat pemindahan pembaca satu per satu aman.

### Blok E — Dekode ketat di repository

```kotlin
// Baris yang tidak bisa didekode melempar, tidak dianggap kosong
```

- Kalau baris rusak dianggap "tidak ada", `GetTenantStageFlowUseCase` akan mem-provision ulang
  dan **menimpa** kerangka yang sudah diubah tenant. Error yang keras lebih murah daripada data
  yang hilang diam-diam. (Bandingkan `StagePhaseTagsCodec` yang toleran — di sana nilai yang
  hilang aman jatuh ke default "kedua fase".)

---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

| Pendekatan | Alternatif | Kenapa ini | Risiko alternatif |
|---|---|---|---|
| Strangler Fig 4 tahap | Big-bang ganti enum | Tiap PR bisa direview & di-rollback | 71 file dalam satu PR |
| Kode = nama enum lama | Kode baru + migrasi | Nol `UPDATE` ke tabel lama | Migrasi lintas 6+ tabel, rollback sulit |
| Test paritas iterasi enum | Test kasus per kasus | Menangkap entri enum baru yang terlupa | Test lulus padahal template tertinggal |
| JSONB satu baris per tenant | Tabel baris-per-tahap | Tulis atomik, pola sama V71 & pipeline | Reorder = banyak UPDATE, urutan ganda |
| Tunda `CUSTODY_NODE` | Tebak tahap mana simpul custody | Hari ini pemanggil `FlowLegDerivation` yang menentukan, bukan tahap | Mengarang semantik yang tak bisa diuji paritasnya |

---

## ⚠️ 5. Jebakan Pemula

1. **Hanya kompilasi JVM.** `@JvmInline` tanpa import lolos di JVM, gagal di JS/wasm.
2. **Menambah semantik yang tidak ada padanannya.** Setiap trait harus punya properti enum yang
   bisa dibandingkan; kalau tidak, ia belum layak masuk Tahap 1.
3. **Me-restart server bersama tanpa izin.** Server dev dipakai sesi lain; verifikasi migrasi
   dilakukan dalam `BEGIN … ROLLBACK` di Postgres nyata alih-alih mematikan proses orang.
4. **Fallback diam-diam di codec.** Lihat Blok E.

---

## 🧪 6. Pembuktian

- `core/src/commonTest/.../stageflow/IndustryStageTemplatesTest.kt` — 9 test: paritas kode/nama/
  urutan, `next`, 3 trait, alias `FINISHING_QC`, invarian anchor & duplikat, regex kode, codec
  round-trip, provisioning sekali.
- Migrasi V72 dijalankan di Postgres dev dalam transaksi yang di-rollback: tabel terbentuk,
  default `KNIT_SWEATER`, policy RLS ada.
- Kompilasi: `core` JVM/JS/wasm, `server` main+test, `app:shared` JVM — hijau. Seluruh 127
  kelas test `core` lulus.
- **Belum**: panggilan `GET /api/tenant/stage-flow` di server hidup (butuh restart server).

---

## 🏆 7. Tantangan Mandiri

- [ ] Pindahkan `FlowLegDerivation.resolveNodes` agar menerima `List<StageDefinition>` —
      paket pertama Tahap 2 — dan buktikan test transfer tetap hijau tanpa mengubah assertion.
- [ ] Tulis integration test `PostgresTenantStageFlowRepository` yang membuktikan RLS: tenant A
      tidak bisa membaca baris tenant B.
- [ ] Rancang template `EMBROIDERY` di atas kertas: tahap mana yang ber-`FINISHING_FLOOR`?
