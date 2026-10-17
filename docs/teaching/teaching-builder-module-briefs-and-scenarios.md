# 🎓 Modul Pembelajaran: Ringkasan per Modul (Brief) & Skenario Lintas Modul di Chat Builder

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Utas chat per modul, idempotensi lewat fingerprint, fallback deterministik di belakang model AI, validator sebagai kontrak, simulasi tanpa menyimpan data, verifikasi dengan model sungguhan
> **Prasyarat**: Paham `DiscoveryDraft`, `InterviewSession`, use case DDD di repo ini
> **Referensi Task**: [`PLAN-builder-module-briefs.md`](../plannings/features/PLAN-builder-module-briefs.md) · [`TRD-BLDR-001`](../trd/features/TRD-BLDR-001-module-briefs.md) · [`TRD-BLDR-002`](../trd/features/TRD-BLDR-002-scenario-simulation.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: chat Builder awalnya satu utas untuk semua modul. Klarifikasi muncul di setiap modul, dan berulang tiap pengguna pindah modul. Pengguna jadi tidak tahu apa yang sudah diputuskan untuk modul mana.
- **Analogi**: satu grup WhatsApp untuk seluruh kantor versus satu grup per divisi. Di grup per divisi, ringkasan dan pertanyaan lanjutan menempel pada pemiliknya.
- **Hasil akhir**:
  - **Fase 1 (Brief)**: tiap modul punya riwayat chat, ringkasan (summary + fitur/Tahap), dan follow-up sendiri, diturunkan dari cerita awal.
  - **Fase 2 (Skenario)**: alur lintas modul ("pasien daftar, periksa, bayar") yang bisa **disimulasikan** sebagai tampilan, tanpa menyimpan record apa pun.

---

## 🧭 2. "Start dari Mana?" — Order of Operations

1. **Kontrak data dulu** (`core`): `ChatMessage` dapat `moduleId` + `kind` (`TEXT`/`QUESTION`/`BRIEF`). Tanpa ini, UI tidak punya cara menyaring utas.
2. **Kunci identitas** (`BriefKeys`): `followUpId`, `basisRef`, `fingerprint` (FNV-1a). Semua idempotensi bergantung pada ini.
3. **Pembuat brief**: `GenerateModuleBriefsUseCase` dengan fallback `DeterministicModuleBriefer`. Fallback ditulis **sebelum** adapter model.
4. **Adapter model** (`server`): `KoogModuleBriefer`. Model hanya boleh mengisi teks, bukan id/sumber.
5. **Pemicu**: setelah **Terapkan** (`ApplyDraftPatchUseCase`), di latar. Bukan saat chat dikirim.
6. **Skenario**: `ScenarioSketch` + `ScenarioRules` (validator) lebih dulu, baru `DiscoveryScenarioService`, pengusul model, rute, lalu klien.
7. **Klien terakhir**: `ScenarioRun` murni, lalu picker/host UI.

Alasan urutan ini: tiap lapisan bisa diuji tanpa lapisan di atasnya. Validator skenario punya puluhan tes sebelum satu baris Compose ditulis.

---

## 🧱 3. Bedah Blok per Blok

### Blok A: Id follow-up memuat `moduleId`
```kotlin
// BriefKeys: id pesan follow-up = turunan (moduleId, followUpId); AI_ID_PREFIX = "ai:", gap = "gap:"
```
- Id pertama hanya nomor urut. Dua modul bisa menghasilkan id yang sama, dan `chat_messages` bertabrakan di PK.
- Tes awal tidak menangkapnya karena fake repository menempelkan nomor urut sendiri. Perbaikannya: `StrictChats` (fake yang menolak id ganda) + `FollowUpMessageIdTest`.

### Blok B: Pertanyaan yang tampil = pesan AI terbaru per modul
```kotlin
// BuilderThreads.pendingFollowUps: gap (template) disembunyikan bila sudah ada pertanyaan AI;
// hanya pesan AI TERBARU per modul yang dihitung.
```
- Pertanyaan template ("Siapa yang mengisi data X?") adalah cadangan, bukan pesan tetap. Begitu AI menjawab, template mundur.
- Tanpa aturan "terbaru saja", regenerasi menumpuk pertanyaan lama dan badge klarifikasi membengkak.

### Blok C: Validator sebagai kontrak, `eligibleModules`
```kotlin
fun eligibleModules(draft: DiscoveryDraft): List<ModuleDefinition> = draft.pack.modules.filter {
    when (it.kind) {
        ModuleKind.OPERATIONAL -> it.id.value in draft.blueprint.activeModuleCodes
        ModuleKind.FOUNDATION -> it.description.isNotBlank()
        ModuleKind.GOVERNANCE -> false
    }
}
```
- Satu fungsi dipakai **dua pihak**: perakit tawaran ke model dan validator. Yang ditawarkan selalu sama dengan yang diterima.
- `when` tanpa `else`: tipe `ModuleKind` baru memaksa keputusan di sini (aturan Kontrak 6).
- Kasus nyata (temuan 5): "invoice DP" dipetakan model ke `costing_hpp` karena `invoicing` (FOUNDATION, tak ada di blueprint) tidak pernah ditawarkan. Setelah perubahan, model memilih `invoicing` dan langkahnya lolos validator.
- `DeterministicScenarioBuilder` sengaja tidak diubah: fallback tanpa model tetap persis seperti sebelumnya (paritas).

### Blok D: Wawancara basi dan Terapkan yang 409 (temuan 11)
```kotlin
val stale = interview != null && InterviewValidator.validate(interview, proposed.pack).isNotEmpty()
// basi: rencanakan ulang; gagal: buang wawancara dan laporkan, jangan tawarkan patch yang pasti ditolak
```
- Gejala: `POST /chat/apply` membalas 409 setelah revisi struktur modul. Penyebab ditemukan dari log `patch_apply_rejected`, bukan dugaan: port handoff lama (`ANTREAN`) tidak cocok slot baru (`PASIEN`).
- Akar: adapter agent menyalin wawancara lama bila `pack.code` sama, padahal slot/port modul sudah berubah.
- Pelajaran: sebelumnya plan menulis "tidak terbukti". Itu benar pada data saat itu, tetapi log yang ditambahkan kemudian yang akhirnya menjawabnya.

---

### Blok E: Pertanyaan skenario (SC3 / FR-9)
```kotlin
val fresh = scenario.questions.map { Clarification(ScenarioKeys.questionId(scenario.id, it), it.trim()) }
    .filter { known.add(it.id) && knownText.add(BriefKeys.normalize(it.question)) }
```
- Layanan skenario tidak punya akses chat (sengaja), jadi pesan dibuat use case terpisah yang dipanggil route **setelah** gerbang lolos dan propose sukses.
- Dua lapis dedup: per id (idempoten) dan per teks (dua skenario menanyakan hal yang sama, ditemukan lewat UI).
- Pelajaran: opsi jawaban bawaan UI untuk pertanyaan celah modul tampil juga untuk pertanyaan skenario dan jawabannya masuk narasi. Tes tak menangkapnya; layar yang dilihat mata menangkapnya.

---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

| Pendekatan | Alternatif | Alasan | Risiko alternatif |
|---|---|---|---|
| Brief dipicu setelah **Terapkan** | Saat pesan dikirim | Draf yang diterapkan baru punya wawancara dan modul final | Brief dibuat dari draf tanpa wawancara, salah momen (terjadi di iterasi awal) |
| Fingerprint FNV-1a untuk idempoten | Bandingkan teks penuh | Murah, deterministik, multiplatform | Regenerasi berulang tanpa perubahan input = biaya model sia-sia |
| Fallback deterministik | Gagal bila model mati | Pengguna tetap dapat brief/skenario | Fitur mati total saat model bermasalah |
| Simulasi = tampilan/memori | Simpan record contoh | Keputusan pemilik; tak mencemari data tenant | Data contoh tercampur data nyata |
| Field wajib tak tercakup diisi nilai contoh, ditandai "contoh" | Berhenti di langkah itu | Alur tetap bisa dilihat; jujur soal asal nilai | Simulasi macet di langkah dengan field baru, atau nilai contoh tampak seperti data |
| Model tidak menentukan `source`/`confirmed` | Percaya keluaran model | Status konfirmasi hak pengguna | Skenario "terkonfirmasi" tanpa persetujuan |

---

## ⚠️ 5. Jebakan Pemula

1. **Mengira bug ada di model, padahal di konteks yang kita beri.** Perencana wawancara gagal 3 putaran di handoff; dugaan awal "model mengarang port". Akarnya: katalog tak memuat `input/output` per modul. Setelah katalog diperbaiki, model sendiri benar. Selalu cek apa yang model **lihat** sebelum menyalahkannya.
2. **Log terpotong.** Pesan penolakan dipotong 300 karakter sehingga penyebab hilang. Kini `DETAIL_MAX_CHARS = 900`.
3. **Prompt "bantu" yang merugikan.** Kalimat tambahan "lebih baik tanpa handoff" membuat model menghasilkan sedikit handoff. Dibuktikan lewat A/B model sungguhan, lalu dihapus.
4. **Tes yang lulus karena fake terlalu baik** (id pesan). Buat fake yang menolak pelanggaran kontrak.
5. **Mencentang di plan tanpa bukti.** `SC3` sempat tercentang padahal belum dikerjakan; dikoreksi. Hanya centang yang terbukti (CLAUDE.md §18).
6. **Menyimpulkan dari notifikasi latar.** "Completed" hanya berarti shell peluncur selesai, bukan servernya siap. Cek port/hasil.
7. **Baris tak tampil di papan.** Baris hasil simulasi tanpa status awal tidak masuk kolom kanban mana pun. Tes tidak menangkapnya; hanya mata yang melihat. Solusi: `SampleValues.initialState`.

---

## 🧪 6. Cara Membuktikan Kodingan Bekerja

- **Merah-hijau**: tulis tes, lihat gagal, baru perbaiki (mis. `StaleInterviewReplanTest`: dua tes merah sebelum perbaikan).
- **Tes paritas** yang mengiterasi enum (`ScenarioSource.entries`) dan pack garment (`ScenarioFoundationModuleTest`).
- **Tenant kedua**: fixture klinik (non-konveksi), bukan hanya garment.
- **Model sungguhan**: DB scratch + server lokal + skrip siklus. Hasil 11: 2 siklus revisi, 4 Terapkan, semua 200, 0 `patch_apply_rejected`. Skenario FOUNDATION: model memakai `invoicing` untuk "invoice DP", langkahnya tampil di UI sebagai kerangka. Keduanya sampel kecil: catat batasnya.
- **Mata**: UI Compose-Wasm berupa canvas; periksa lewat screenshot dan klik koordinat.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: kerjakan `SC3` (pertanyaan tingkat skenario di utas Semua, maksimal 2 per skenario). Mulai dari mana, dan kenapa dari kontrak data dulu?
- [ ] **Tantangan 2**: skenario sekarang semuanya kerangka bila model tak menunjuk layar. Rancang cara pengusul diberi layar berproposal modul tanpa melanggar paritas.
- [ ] **Tantangan 3**: tulis tes yang membuktikan `eligibleModules` tidak pernah menawarkan modul GOVERNANCE di pack mana pun yang terdaftar.
