# 🎓 Modul Pembelajaran: TRD-BLDR-003 F0 — Kontrak Evidence & Fingerprint Builder

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Provenance data, idempotensi hash, codec versioning tanpa DDL, backward compatibility, contract-first testing
> **Prasyarat**: DDD dasar (value object, entity), Kotlin data class, paham bentuk JSON codec repo ini (`JsonValue`), Uji Variabilitas (tenant-variability-rules.md)
> **Referensi Task**: [`TRD-BLDR-003`](../trd/features/TRD-BLDR-003-builder-llm-eval-loop.md) · [`PLAN-builder-llm-evals.md`](../plannings/mvp/PLAN-builder-llm-evals.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata**: Builder AI menawarkan modul, fitur, tahap, dan skenario hasil "ngobrol" dengan pemilik usaha. Ketika user bertanya *"kenapa ada modul QC?"*, sistem saat ini tidak bisa menjawab dengan bukti — dan yang lebih berbahaya: dua konsep berbeda di kode **sama-sama bernama `basisRef`** padahal isinya beda total:

1. Di **wawancara**: `BasisRef` = *bukti* (kutipan narasi / id jawaban) — bisa divalidasi.
2. Di **brief builder**: `basisRef` = *hash idempotensi* (FNV-1a atas narasi+input+versi agent) — sekadar penanda "apakah brief perlu dibuat ulang".

Nama sama, makna beda = bug menunggu waktu. Engineer baru hampir pasti mengira hash itu bukti.

**Analogi sederhana**: fingerprint di pintu absensi ≠ alasan kamu masuk ruangan. Fingerprint cuma menyatakan "ini kamu, sudah absen hari ini" (idempotensi). Alasan masuk = "ada rapat evaluasi jam 10" (evidence). Menyimpan fingerprint lalu mengaku itu alasan = karangan.

**Hasil akhir F0**: satu kosakata bukti (`EvidenceSource`/`EvidenceRef` + validator `EvidenceRules`), satu kosakata "kenapa bertanya" (`GapRef`), nama jujur `inputFingerprint` untuk hash, dan butir brief/skenario jadi objek bertipe yang membawa buktinya sendiri — semuanya **tanpa satu pun DDL** dan data lama tetap terbaca.

---

## 🧭 2. "Start dari Mana?" — Order of Operations

Kalau mengetik ulang dari nol:

1. **Langkah 0: Kontrak evidence murni di `core/domain/evidence/`** (`Evidence.kt`, `GapRef.kt`). Kenapa duluan? Karena ini *kosakata* yang akan dipakai semua lapisan — menulis brief sebelum kosakata beku = refactor dua kali.
2. **Langkah 1: Codec JSON-nya** (`shared/evidence/EvidenceRefCodec.kt`) — ketat, unknown source ditolak, ikuti pola `InterviewBasisCodec`.
3. **Langkah 2: Adaptor dari konsep lama** (`interview/InterviewEvidenceAdaptor.kt`) — jembatan `BasisRef` → `EvidenceRef` **tanpa mengubah byte dokumen wawancara lama**.
4. **Langkah 3: Rename menipu** — `BriefKeys.basisRef(...)` → `inputFingerprint(...)`; rumus `b1:` + FNV-1a **dibekukan** (idempotensi brief lama pecah kalau rumus berubah).
5. **Langkah 4: Butir bertipe** — `BriefFeature(text, evidence)`, `StageSketch.evidence`, `ScenarioStep.evidence`, `contractVersion`.
6. **Langkah 5: Codec v1/v2** — brief pakai *bentuk* sebagai penanda versi; skenario pakai *kunci eksplisit*.
7. **Langkah 6: Propagasi ke fallback deterministik** — `RoleFeature` membawa bukti sampai ke penyusun; yang tanpa dasar **dihilangkan**, bukan dikarang buktinya.
8. **Langkah 7: Baru pikirkan prompt LLM** (Track B) — model boleh tahu kosakata bukti setelah validator & codec siap menolak karangan.

---

## 🧱 3. Bedah Blok per Blok

### Blok A: Validator berpath, bukan konstruktor galah

```kotlin
object EvidenceRules : EvidenceValidator {
    override fun validate(ref: EvidenceRef, context: EvidenceContext, path: String): List<EvidenceIssue> = buildList {
        when (ref.source) {
            EvidenceSource.NARRATIVE -> {
                val q = ref.quote
                when {
                    q.isNullOrBlank() -> add(EvidenceIssue("$path.quote", "kutipan wajib untuk NARRATIVE"))
                    q.length > EvidenceLimits.QUOTE_MAX_CHARS -> add(...)
                    context.narratives.none { it.contains(q) } ->
                        add(EvidenceIssue("$path.quote", "kutipan tidak ada di narasi mana pun: '${q.take(40)}'"))
                }
            }
            EvidenceSource.INTERVIEW_ANSWER -> requireRef(ref.referenceId, context.interviewAnswerIds, path, ...)
            ...
        }
    }
}
```

**Mengapa begini?**
- **Galat berpath, bukan exception**: keluaran LLM ditolak dan *umpan baliknya dikirim balik ke model* untuk percobaan kedua. Path `features[2].evidence.quote` memberi tahu model persis mana yang salah — pola yang sama dengan `InterviewValidator` (pembelajaran TRD-FLOW-001).
- **`EvidenceContext` dibawa pemanggil**: validator tidak boleh me query DB. Use case merakitnya dari data tenant yang sama (`narratives` = cerita wawancara + narasi chat), sehingga **bukti lintas tenant mustahil lulus** — id yang diklaim tidak ada di konteks.

### Blok B: Dua strategi versioning — dua keputusan berbeda, dua alasan berbeda

**Brief** (kolom DB `features` & `stages` terpisah, tidak ada amplop):

```kotlin
// Bentuk ELEMEN = penanda versi. Tidak ada heuristik: string = v1, objek = v2.
val shapes = arr.items.map { it::class }.distinct()
require(shapes.size <= 1) { "features mencampur bentuk v1 (teks) dan v2 (objek)" }
```

**Skenario** (satu objek skenario = amplop alami):

```kotlin
// Kunci eksplisit, ditulis HANYA bila ≠ 1 → JSON v1 lama byte-identik.
"contractVersion" to jsonOf(sc.contractVersion) // bila != 1
```

**Mengapa beda?** Track 0 harus memilih tanpa DDL. Brief tersimpan dalam dua kolom terpisah sehingga tidak ada tempat alami untuk kunci versi — sedangkan *tipe elemen* sudah membedakan dengan tegas. Skenario punya satu objek amplop, jadi kunci eksplisit lebih jujur. Keduanya memenuhi FR-7.2: "dapat dibedakan tanpa heuristik ambigu". Campuran v1+v2 dalam satu array = **korup → ditolak keras**, bukan didiamkan.

### Blok C: Fallback jujur — hapus klaim, jangan karang bukti

```kotlin
// DeterministicModuleBriefer.of(request)
val features = request.roleFeatures.filter { it.evidence != null }  // FR-3.5
    .map { BriefFeature(it.label.trim().take(L.FEATURE_MAX_CHARS), it.evidence) }
```

**Mengapa butir tanpa dasar dihapus?** Karena kontrak v2 mewajibkan bukti. Dua pilihan: melanggar kontrak sendiri, atau menghasilkan klaim lebih sedikit tapi jujur. TRD memilih yang kedua — dan ringkasan pun hanya merangkum butir berevidence (FR-3.6), supaya summary tidak menyelundupkan klaim yang fiturnya barusan dibuang.

### Blok D: Propagasi provenance, jangan turun jadi string lebih awal

```kotlin
// ModuleBriefInputs.roleFeatures
val evidence = link.basisRef.toEvidenceRef()   // adaptor interview → evidence
if (link.features.isEmpty()) listOf(RoleFeature(role, evidence))
else link.features.map { RoleFeature("$role: $it", evidence) }
```

**Mengapa `RoleFeature` bukan `List<String>`?** Karena begitu tautan diratakan jadi string, buktinya **hilang selamanya** — fallback tidak mungkin mewarisi apa yang sudah dibuang. FR-3.4 menulis aturan ini persis: "tidak mengubahnya menjadi string sebelum fallback deterministik memakainya".

---

## ⚖️ 4. The "Why" — Keputusan vs Alternatif

| Keputusan | Alternatif | Mengapa ini | Risiko alternatif |
|---|---|---|---|
| `require`/`error` berpath di codec & validator | Return `Result` berlapis | Konsisten pola codec repo; umpan balik ke model sederhana | Result berlapis membuat retry loop use case verbose |
| Rename `basisRef`→`inputFingerprint`, kolom DB tetap `basis_ref` | Migrasi DB rename kolom | Nol DDL, nol risiko deployment; KDoc mapper menjelaskan | Nama kolom "bohong" sesaat — dimitigasi dokumentasi & rename bertahap |
| Rumus hash `b1:`+FNV-1a dibekukan | Perbaiki rumus sambil jalan | ID pesan lama & idempotensi brief pecah kalau berubah | Brief lama dibuat ulang massal, pertanyaan AI terulang |
| Adapter `BasisRef→EvidenceRef`, interview tak disentuh | Refactor interview pakai EvidenceRef | Dokumen wawancara lama tetap byte-identik; risiko nol | Refactor besar = risiko kehilangan parity yang sudah diuji 100% |
| `LLM-as-judge` ditolak sejak TRD | Nilai mutu teks pakai model kedua | Grader Kotlin deterministik: murah, repeatable, bisa mutation-test | Judge LLM tidak stabil antar-run; skor tak bisa dipercaya |

---

## ⚠️ 5. Jebakan Pemula

1. **Mengira hash = bukti.** Nama `basisRef` lama sengaja dibunuh. Kalau kamu menemukan diri menulis `message.basisRef` sebagai alasan fitur — itu bug.
2. **Validasi di konstruktor.** `BriefFeature` hanya cek `text` tidak kosong; kewajiban evidence ditegakkan **validator** (`ModuleBriefRules.itemEvidence`) — supaya keluaran model yang salah bisa dikoreksi via umpan balik berpath, bukan crash.
3. **Menambah `else ->` saat decode versi.** Unknown `contractVersion` / unknown `source` **wajib gagal keras** (Kontrak 4 tenant-variability). `else -> v1` = data baru terbaca sebagai data lama tanpa jejak.
4. **Mengubah fixture yang dibekukan test lain.** `InterviewFixtures.klinikSession` adalah sesi legacy v1 yang byte-nya dijaga `InterviewBasisTest`. Butuh sesi berbasis cerita? Buat **varian baru** (`klinikSessionBerdasarCerita`), jangan edit yang lama.
5. **Alias import Kotlin**: `import ...ModuleBriefLimits as L` membuat nama panjang `ModuleBriefLimits` **tidak terikat** di file itu — pakai `L.` saja. Error "unresolved reference" di baris yang kelihatan benar hampir selalu ini.

---

## 🧪 6. Cara Membuktikan Kode Bekerja

- **Paritas fingerprint**: `inputFingerprint` diuji == `"b1:" + fingerprint("cerita\u001Ea\u001Fb\u001Ebriefer/v1")` — kalau ada yang mengubah rumus, test ini merah sebelum idempotensi produksi pecah (`ModuleBriefCodecTest`).
- **Kompat v1**: fixture JSON lama (fitur string, stage tanpa evidence) tetap didecode; `ScenarioSketchCodec` menjanjikan JSON v1 **byte-identik** (kunci versi hanya ditulis bila ≠ 1).
- **Round-trip v2**: butir berevidence di-encode lalu di-decode, `assertEquals` objek utuh termasuk `EvidenceRef`.
- **Korup ditolak**: campuran bentuk, kunci asing, source tak dikenal, `text` kosong — masing-masing `assertFailsWith`.
- **Fallback jujur**: dua test di `GenerateModuleBriefsUseCaseTest` — sesi berdasar cerita mewarisi bukti; sesi legacy tanpa dasar menghasilkan **fitur kosong** + summary jujur, bukan fitur karangan.
- **Gerbang**: `:core:jvmTest`, `:server:test`, `:app:shared:jvmTest`, kompilasi JVM/WasmJs/Js — hijau 2026-10-17 (Android: SDK tidak terpasang di mesin).

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tambah sumber evidence baru `ACCEPTED_SUGGESTION`-style, mis. `PACK_DEFAULT` untuk tahap bawaan template — daftar semua titik yang harus disentuh (kosakata, codec, context builder di use case, grader F1 nanti) dan buktikan unknown handling tetap gagal keras.
- [ ] **Tantangan 2**: `ScenarioReplyParser` (server) masih v1. Rancang prompt v2 yang meminta bukti per langkah DAN test yang membuktikan parser menolak kutipan palsu sebelum menyimpan.
- [ ] **Tantangan 3**: Tulis draf `BuilderBriefEvalGrader` dengan satu kriteria `tertelusur` (numerator/denominator) + mutation test yang membuktikan kriteria itu bisa gagal sendiri (prasyarat F1).
