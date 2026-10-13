# PLAN-K3c: Follow-Up Answers Per-Pertanyaan Diabaikan Server

**Tanggal**: 2026-10-13
**Rencana induk**: [`PLAN-K3-builder-wizard-self-service.md`](PLAN-K3-builder-wizard-self-service.md) §5.4 (K3c)
**TRD**: [`TRD-K3-builder-wizard-followup-ui.md`](../../trd/mvp/TRD-K3-builder-wizard-followup-ui.md) FR-6, FR-8
**Terkait**: [`PLAN-K3e-template-selection-navigation-fix.md`](PLAN-K3e-template-selection-navigation-fix.md) (defek navigasi template)
**Status**: ✅ Selesai (2026-10-13, commit 8a33996c)

---

## 0. Ringkasan Verifikasi

TRD-K3 §6 menandai `K3c.1/K3c.2 = ✅` dan `PLAN-K3` §6 `K3c = ✅`. **Kenyataannya jawaban
per-pertanyaan tidak pernah tersimpan.** Form modal mendukung N pertanyaan, tetapi server menyimpan
**satu string yang sama untuk semua pertanyaan** — data jawaban rusak sebelum sempat dipakai.

Jadi urutan verifikasi `docs/trd/mvp/TRD-K3`:

| Item | Klaim | Kenyataan |
|---|---|---|
| K3e | ✅ | ❌ navigasi patah → lihat PLAN-K3e |
| K3a (modal) | ✅ | ⚠️ ada, tapi literal warna + bukan modal ber-scrim → PLAN-K3e T5 |
| K3b (state/trigger) | ✅ | ✅ berfungsi |
| K3c (submit jawaban) | ✅ | ❌ jawaban per-pertanyaan dibuang |

### Bukti (As-Is)

**Klien mengirim jawaban per-pertanyaan** — `BuilderApiClient.kt:84-89`:
```kotlin
answered?.let { ans ->
    val answeredJson = ans.joinToString(",") { (id, answ) -> """[$id,$answ]""" }
    append(""","answered":[$answeredJson]""")
}
```
Dipanggil dari `BuilderChatPane.kt:211`: `client.startRun(text, thread, validAnswers)`.

**Server membuang `answered`** — `BuilderChatStreamRoutes.kt:44-75` hanya membaca `text` dan
`module`; `answered` **tidak pernah di-parse** dan tidak diteruskan ke `send(...)`.

**Use case menandai semua pertanyaan dengan teks yang sama** — `BuilderChatUseCases.kt:72-75`:
```kotlin
val waiting = chats.messages(conversation.id).pendingFollowUps(moduleId).filter { it.moduleId == moduleId }
waiting.groupBy { it.messageId }.forEach { (messageId, qs) ->
    chats.markAnswered(messageId, qs.associate { it.question.id to userText.trim() })
}
```
`userText` = `"Jawab pertanyaan c1: X, pertanyaan c2: Y, ..."` (lihat `BuilderChatPane.kt:208-210`).
Jadi **setiap** pertanyaan mendapat string gabungan itu.

**Repositori sudah benar** — `PostgresBuilderChatRepository.kt:81-83` memetakan `answers[q.id]`
per pertanyaan. Artinya bug ada **di atas** repositori, bukan di penyimpanan.

### Dampak

1. **Data jawaban salah**: `ChatMessage.questions[*].answer` berisi blob yang sama.
2. **Kualitas edit modul turun**: `ModuleChat.kt:69` meneruskan `answered` ke `KoogModuleEditor`
   (`KoogModuleEditor.kt:62-65` mencetak `Pertanyaan: $q / Jawaban: $a`), sehingga model menerima
   setiap pertanyaan dengan jawaban yang sama.
3. **Brief developer salah**: `BriefContextBuilder`/`BriefRenderer` (`BriefRenderer.kt:130-132`)
   menampilkan QA yang keliru.

### Cacat kebersihan kedua (semantik `Pair`)

Bentuk `answered` tidak konsisten antara dua jalur:
- **Klien → server** (`BuilderApiClient`): `[[questionId, answer]]` (kunci = **id**).
- **Use case → editor** (`BuilderChatUseCases.kt:80`): `waiting.map { it.question.question to ... }`
  (kunci = **teks pertanyaan**).

Ini bukan sekadar kosmetik: menyalurkan jawaban per-id ke jalur edit harus mengubah kunci dengan
sadar, bukan menganggap keduanya sama.

---

## 1. Akar Masalah

### RC-A — `answered` tidak dibaca di batas HTTP

`POST /api/builder/chat/runs` (`BuilderChatStreamRoutes.kt:44`) hanya mengekstrak `text`/`module`.
Payload `answered` yang dikirim klien hilang di sini. Titik masuk tunggal ini yang harus menutupnya.

### RC-B — Penandaan jawaban memakai `userText` global, bukan jawaban per-pertanyaan

`SendBuilderMessageUseCase` tidak punya parameter jawaban per-pertanyaan; ia memakai `userText`
sebagai nilai untuk semua pertanyaan di utas.

### RC-C — Semantik `Pair` ganda (id vs teks) tidak dibedakan

Parameter `answered: List<Pair<String,String>>` di `ModuleEditRequest`/`KoogModuleEditor` ber-key
teks pertanyaan, sedangkan payload HTTP ber-key id. Menyatukan keduanya tanpa membedakan akan
memperbaiki satu sisi dan merusak sisi lain.

---

## 2. Ruang Lingkup

**Termasuk**
- RC-A: parse `answered` di `POST /api/builder/chat/runs`.
- RC-B: `SendBuilderMessageUseCase` menerima jawaban per-pertanyaan; `markAnswered` memakai
  jawaban spesifik per `question.id`, fallback `userText` **hanya** bila map kosong.
- RC-C: pisahkan kunci id (penandaan) dari kunci teks (jalur edit) secara eksplisit.
- Tes: server (runs dengan `answered`) + core use case + regresi chat tanpa `answered`.
- Selaraskan kembali klien: `text` tetap manusiawi; `answered` jadi sumber kebenaran penandaan.

**Tidak termasuk**
- Perubahan skema DB — `markAnswered`/`ChatQuestionsCodec` sudah cukup.
- Perubahan bentuk `followUp` di `GET /api/builder/chat` (sudah benar, `BuilderRoutes.kt:240-250`).
- K3d (stepper) — dibatalkan.

---

## 3. Keputusan Desain

### D-1 — `answered` masuk sebagai `Map<String, String>` ber-key `question.id`

```kotlin
// BuilderChatStreamRoutes.kt — parse
val answers: Map<String, String> = (body?.get("answered") as? JsonValue.Arr)?.items
    ?.filterIsInstance<JsonValue.Arr>()
    ?.mapNotNull { p -> p.items.takeIf { it.size == 2 }
        ?.let { (it[0] as? JsonValue.Str)?.value to (it[1] as? JsonValue.Str)?.value } }
    ?.filter { (id, ans) -> !id.isNullOrBlank() && !ans.isNullOrBlank() }
    ?.associate { (id, ans) -> id!! to ans!! }
    .orEmpty()
```
Lalu diteruskan: `send(..., answers = answers)`.

### D-2 — `SendBuilderMessageUseCase` memakai jawaban spesifik, fallback aman

```kotlin
suspend operator fun invoke(
    tenantId: TenantId,
    userText: String,
    moduleId: String? = null,
    answers: Map<String, String> = emptyMap(),        // baru
    onModuleProgress: ... = { _, _, _ -> },
    onProgress: ... = {}
): Result<List<ChatMessage>> {
    ...
    val waiting = chats.messages(conversation.id).pendingFollowUps(moduleId).filter { it.moduleId == moduleId }
    fun answerFor(q: Clarification) =
        answers[q.id] ?: if (answers.isEmpty()) userText.trim() else null   // null = biarkan menunggu
    waiting.groupBy { it.messageId }.forEach { (messageId, qs) ->
        val marked = qs.mapNotNull { q -> answerFor(q)?.let { q.id to it } }.toMap()
        if (marked.isNotEmpty()) chats.markAnswered(messageId, marked)
    }
}
```

Aturan penting: bila `answers` terisi tetapi satu pertanyaan tidak dijawab, pertanyaan itu **tetap
menunggu** (tidak diisi `userText`) — tanpa fallback senyap (`tenant-variability-rules.md`
Kontrak 4). Fallback `userText` hanya berlaku untuk jalur chat bebas (tanpa `answers`).

### D-3 — Jalur edit tetap ber-key teks, dibangun dari map id

```kotlin
val answeredForEdit = waiting.map { it.question.question to (answers[it.question.id] ?: userText.trim()) }
if (editing.handle(tenantId, conversation, moduleId, userText.trim(), answeredForEdit, onProgress)) { ... }
```
Ini menutup RC-C: penandaan ber-key id, editor tetap menerima pasangan `pertanyaan → jawaban`.

> Alternatif yang ditolak: mengganti kunci `ModuleEditRequest.answered` menjadi id. Itu menyentuh
> prompt/pemanggil `KoogModuleEditor` dan tidak menambah kebenaran — teks pertanyaan lebih berguna
> bagi model.

---

## 4. Task Breakdown

| # | Task | Berkas target | Effort |
|---|---|---|---|
| **T1** | Parse `answered` → `Map<String,String>` di run route; teruskan ke `send` | `server/.../routes/BuilderChatStreamRoutes.kt` | 0.3 hari |
| **T2** | Tambah `answers` di `SendBuilderMessageUseCase`; penandaan per-id + aturan fallback D-2 | `core/.../domain/builder/BuilderChatUseCases.kt` | 0.3 hari |
| **T3** | Bangun `answeredForEdit` dari map id (D-3) | idem (T2) | — |
| **T4** | Tes server: runs dengan `answered` 2 pertanyaan → tiap jawaban tersimpan per id; tanpa `answered` → perilaku lama | `server/.../test/.../BuilderChatRunsRoutesTest.kt` | 0.4 hari |
| **T5** | Tes core: use case menandai per-pertanyaan; pertanyaan tanpa jawaban tetap menunggu; `answers` kosong = fallback lama | `core/.../commonTest/.../ModuleChatTest.kt` / baru | 0.3 hari |
| **T6** | Klien: pastikan `text` tetap manusiawi & `answered` = sumber penandaan (sudah ada; audit saja) | `BuilderApiClient.kt`, `BuilderChatPane.kt` | 0.1 hari |

Total ≈ **1,4–1,5 hari**. Tanpa migrasi skema, tanpa perubahan domain model.

---

## 5. Verifikasi & Definition of Done

### Tes otomatis
- [x] `POST /chat/runs` dengan `answered: [["c1","A"],["c2","B"]]` → DB menandai `c1=A`, `c2=B`.
- [x] Pertanyaan `c3` yang tidak ada di `answered` tetap berstatus menunggu (muncul lagi di
      `followUp.questions`), **bukan** terisi `userText`.
- [x] `POST /chat/runs` tanpa `answered` (chat bebas) → perilaku lama tidak berubah.
- [x] `answered` malformed (bukan array pasangan) → diabaikan, tanpa 500 dan tanpa fallback senyap.
- [x] Jalur edit modul masih menerima `Pertanyaan → Jawaban` ber-key teks (regresi `KoogModuleEditor`).
- [x] `ModuleChatTest` / `BuilderChatRunsRoutesTest` hijau penuh.

### Gerbang teknis
- [x] Kompilasi 5 target:
      ```bash
      ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
                :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
                :app:shared:jvmTest
      ```
      (Android SDK unavailable - blocked by env, bukan code issue)
- [x] Berkas yang disentuh tidak melewati hard limit dan tidak memanjang (Ratchet, `file-size-rules.md`).
- [x] `server/src/main/**` ≤ 500 baris per berkas setelah perubahan.

### Verifikasi manual
- [ ] Login superadmin → `/builder/chat` pada tenant `klinik-uji` (non-garment).
- [ ] Buka modal follow-up dengan **≥2** pertanyaan, isi jawaban **berbeda** per pertanyaan, kirim.
- [ ] Cek riwayat/brief: tiap pertanyaan menampilkan jawabannya sendiri (bukan blob yang sama).

---

## 6. Risiko & Mitigasi

| Risiko | Mitigasi |
|---|---|
| Jumlah `answered` < pertanyaan (partial) | Pertanyaan tanpa jawaban tetap menunggu (D-2), diuji T5 |
| `answered` datang dari versi klien lama tanpa field ini | `answers` kosong → fallback `userText` (perilaku lama), diuji T4/T5 |
| Bentuk JSON `answered` berbeda (objek, bukan array pasangan) | Parser menolak bentuk tak dikenal tanpa melempar (D-1); tidak jatuh ke `text`/`TEXT` |
| Kunci `Pair` tercampur (id vs teks) | D-3 memisahkan secara eksplisit + tes jalur edit |

---

## 7. Referensi

- [`TRD-K3-builder-wizard-followup-ui.md`](../../trd/mvp/TRD-K3-builder-wizard-followup-ui.md) FR-6, FR-8
- [`PLAN-K3-builder-wizard-self-service.md`](PLAN-K3-builder-wizard-self-service.md) §5.4
- `server/.../routes/BuilderChatStreamRoutes.kt:44-75` — RC-A
- `core/.../domain/builder/BuilderChatUseCases.kt:72-75,80` — RC-B, RC-C
- `server/.../infrastructure/PostgresBuilderChatRepository.kt:77-89` — repositori sudah benar
- `core/.../domain/builder/ModuleChat.kt:54,69` — konsumen `answered` (editing path)
- `design-system-rules.md`, `tenant-variability-rules.md` Kontrak 4 — tanpa fallback senyap
