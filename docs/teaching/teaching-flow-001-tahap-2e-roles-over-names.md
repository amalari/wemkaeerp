# 🎓 Modul Pembelajaran: Peran, Bukan Nama — Kerangka Beku per SPK (TRD-FLOW-001 R1 + R2)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Snapshot per agregat, lookup berbasis peran (archetype/trait), validasi saat membaca, bug senyap di lapisan persistensi
> **Prasyarat**: [Tahap 2d](teaching-flow-001-tahap-2d-sampling-order-stagecode.md)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — FR-5b, Tahap 2 R1/R2

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Setelah Tahap 2d, *data* tahap SPK sudah berupa `StageCode`. Tapi **aturan**-nya masih menyebut
nama tahap rajut: "vendor makloon mengembalikan barang ke `LINKING_ASSEMBLY`", "QC lolos →
`PENGEMASAN`", "meja operator = `MACHINE_KNITTING..PENGEMASAN`". Perusahaan bordir tidak punya
satu pun tahap bernama itu — tapi punya **peran** yang sama: meja perakitan, meja QC akhir, meja
kemas.

**Analogi**: SOP rumah sakit tidak menulis "bawa pasien ke Dr. Budi", tapi "bawa ke dokter jaga
IGD". Dr. Budi bisa diganti; perannya tetap. Nama tahap = nama dokter; archetype/trait = jabatan.

Dua keputusan produk (2026-09-29) membentuk desainnya:
1. **Kerangka beku per SPK** — SPK membawa salinan kerangkanya sendiri, supaya admin yang
   mengubah kerangka pabrik tidak me-rute ulang kartu yang sedang dikerjakan.
2. **Label ringkas & warna adalah data tahap** (`shortLabel`, `colorHex`) — bukan tabel `when` di UI.

---

## 🧭 2. "Start dari Mana?"

1. **Kategorikan dulu referensinya** sebelum menyentuh 60 file: anchor (A), tabel per tahap (B),
   rentang (C), aturan pada tahap tertentu (D). Hanya A yang mekanis; B–D butuh keputusan desain.
2. **Tanyakan keputusan yang berdampak produk** (beku vs selalu terkini; asal label/warna) —
   jangan diputuskan diam-diam oleh kode.
3. **R1 — fondasi**: field baru di `StageDefinition`, `SamplingOrder.frozenStageFlow`, helper
   `stageFrame` / `firstStageWith(archetype)` / `stagesWith(trait)` / `positionOf`.
4. **R2 — pembaca domain & server**, dibuktikan dengan satu test "jalan penuh" di kerangka bordir.

---

## 🧱 3. Bedah Kode

### Blok A — Peran menggantikan nama

```kotlin
internal val SamplingOrder.assemblyStage: StageCode get() = requireRole(ModuleArchetype.SEWING)
internal val SamplingOrder.finalQcStage:  StageCode get() = requireRole(ModuleArchetype.QUALITY_CONTROL)
internal val SamplingOrder.packingStage:  StageCode get() = requireRole(ModuleArchetype.FULFILLMENT)
```

- Rajut: SEWING = Linking, QUALITY_CONTROL = QC Finishing, FULFILLMENT (tahap **kerja**) =
  Pengemasan. Filter `kind == WORK` penting: Penyimpanan/Terkirim juga FULFILLMENT, tapi mereka
  jangkar keluar.
- Kerangka tanpa peran itu **gagal keras** (`checkNotNull`) — tidak menebak tahap pengganti.

### Blok B — Snapshot per agregat, titik beku tunggal

```kotlin
val withTags = command.tenantPhaseTags?.let { order.freezePhaseTags(it) } ?: order
return command.tenantStageFlow?.let { withTags.freezeStageFlow(it) } ?: withTags
```

Kerangka dibekukan **bersama** tag fase, di titik yang sama (kartu pertama kali menyentuh lantai).
Dua titik beku yang berbeda akan menghasilkan SPK yang tagnya beku tapi kerangkanya tidak —
keadaan yang tidak pernah bisa dijelaskan ke operator.

### Blok C — Bug senyap yang ditemukan di persistensi (yang terpenting di R2)

Dua bug akan muncul **pertama kali tenant bordir menyimpan SPK**, dan tidak satu pun test lama
bisa menangkapnya karena semua test memakai rajut:

1. Repository menulis `order.pipelineStage.name` → jembatan enum melempar untuk `MACHINE_EMBROIDERY`.
2. Repository & codec membaca lewat `SamplingPipelineStage.parseOrNull(...) ?: NEW_INTAKE` →
   SPK yang sedang dibordir **kembali sebagai SPK baru** setiap kali dimuat.

Perbaikannya membaca **terhadap kerangka SPK itu sendiri**:

```kotlin
fun resolveStoredStageCode(raw: String?, frame: List<StageDefinition>): StageCode? {
    fun inFrame(code: StageCode?) = code?.takeIf { c -> frame.any { it.code == c } }
    return inFrame(parseLegacyStageCodeOrNull(raw)) ?: inFrame(StageCode.parseOrNull(raw))
}
```

Kenapa tidak cukup `StageCode.parseOrNull(raw)`? Karena itu mengubah perilaku untuk baris rusak:
`"FOO"` dulu jatuh ke fallback, sekarang akan diterima lalu meledak saat dibaca. Validasi
terhadap kerangka membuat baris rajut — termasuk alias `FINISHING_QC` — dibaca **persis** seperti
sebelumnya.

### Blok D — Nilai yang dipertahankan persis, dibuktikan dari git

`remainingWorkFactor` (bobot urgensi) dipindah dari tabel `when` ke template. Test lama hanya
mengunci *sifat* (monoton turun), jadi test paritas baru membandingkan dengan nilai di
`git HEAD` — bukan dengan angka yang diketik ulang dari ingatan.

---

## ⚖️ 4. The "Why"

| Keputusan | Alternatif | Alasan |
|---|---|---|
| Beku per SPK | Selalu kerangka terkini | Kartu di lantai tidak ter-rute ulang oleh perubahan admin |
| Lookup peran (archetype/trait) | Tabel pemetaan nama→nama per template | Template baru tidak butuh kode baru |
| Validasi baca terhadap kerangka | Terima kode apa pun | Baris rusak tetap diperlakukan seperti dulu |
| Overload enum sebagai **ekstensi** | Overload member | `SamplingOrder.kt` 395/400 baris; ekstensi dipilih bila member tak cocok — asal di-*import* |
| Enum `isOperatorDesk` lama **dipertahankan** | Turunkan dari template | Ia pembanding test paritas; menurunkannya dari template membuat test jadi tautologi |

---

## ⚠️ 5. Jebakan Pemula

1. **Hanya menguji dengan rajut.** Semua test lama hijau sementara dua bug persistensi di Blok C
   hidup. `NonKnitStageFlowWalkTest` ada justru untuk ini: setiap pembacaan enum di jalur bordir
   melempar, jadi aturan yang diam-diam masih memakai nama rajut langsung ketahuan.
2. **Ekstensi tanpa import.** "Argument type mismatch" pada overload enum = ekstensinya ada, tapi
   file pemanggil di package lain belum meng-import-nya.
3. **Helper import yang terlalu pintar.** Wildcard `domain.sampling.*` tidak mencakup
   `domain.stageflow.StageCode` — jangan lewati import hanya karena ada wildcard.
4. **Jar kosong.** `core-jvm.jar` sempat terkemas 261 byte (hanya manifest) setelah paksa
   `jvmJar --rerun`; server lalu "tidak mengenal" seluruh `com.eventverse.app.domain`. Cek ukuran
   jar sebelum mencurigai kode.

---

## 🧪 6. Pembuktian

- `core` **910 test**, `app:shared` **142 test**, 0 gagal (hasil segar, dicek stempel waktunya).
- Test lama: hanya **10 baris fixture** berubah (konstruksi command/SPK), **nol assertion**.
- Paritas dengan kode lama: warna (`samplingStageTint`), `OPERATOR_DESK` (`isOperatorDesk`),
  bobot urgensi (nilai `git HEAD`).
- `NonKnitStageFlowWalkTest` (8): maju tahap + audit, klaim/lepas/rework via trait, tolak klaim
  di meja non-operator, makloon & kembali vendor via peran SEWING, QC lolos → peran FULFILLMENT,
  jejak R&D, round-trip codec (SPK + riwayat + klaim), validasi baca terhadap kerangka.
- Migrasi V73 diuji di Postgres dev dalam transaksi yang di-rollback.

---

## 🏆 7. Tantangan Mandiri

- [ ] `OperatorDeskAccess.deskStageForCode` masih memvalidasi kode meja terhadap enum. Rancang
      validasinya terhadap kerangka **tenant** (bukan SPK) — kenapa bukan kerangka SPK?
- [ ] Gerbang lembar CAM (`SamplingStageGate`) masih khas rajut. Rancang "lembar wajib per tahap"
      sebagai data `StageDefinition` untuk Tahap 3.
