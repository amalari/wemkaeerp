# 🎓 Modul Pembelajaran: Builder Chat — Bertanya Dulu, Alur Penuh, Lalu Per Modul

> **Level Target**: Mid Developer (paham Kotlin, coroutine, dan dasar Ktor)
> **Topik Utama**: SSE (Server-Sent Events) di Ktor + klien KMP, chat berutas (thread), follow-up yang dihitung dari data, agent LLM sebagai *pengusul* (bukan penulis), validasi sebagai satu-satunya gerbang
> **Prasyarat**: tahu bedanya `DiscoveryDraft` (dokumen) dan `ScreenProposal` (isi satu layar); sudah baca `teaching-iv-wiring-end-to-end.md` untuk pola "agent opsional, jatuh ke deterministik"
> **Referensi**: [`PLAN-builder-interview-chat.md`](../plannings/PLAN-builder-interview-chat.md) · commit `46486b9` (Fase A), `5bf3c86` (Fase B), `9c47323`/`7b4a058` (Fase C)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Wizard lama memaksa pemilik usaha melewati lima layar berurutan sebelum melihat apa pun. Ia juga menebak bertahap: divisi dulu, lalu peran, lalu modul. Dua akibatnya: (1) pengguna menunggu lama tanpa melihat hasil, (2) tebakan awal yang salah merambat ke semua tahap berikutnya.

**Analogi.** Bayangkan konsultan arsitek.
- Pertemuan pertama: ia mendengar ceritamu. Kalau ceritanya kabur ("saya punya usaha kecil"), ia bertanya **sekali**, singkat. Kalau jelas, ia langsung menggambar denah seluruh rumah.
- Setelah denah jadi, kamu menunjuk satu ruangan. Baru di situ ia bertanya detail ruangan itu ("lemari di sini mau berapa pintu?") dan langsung mengubah gambarnya.

Itu persis dua fase kita: **fase alur penuh** (model besar, sekali) dan **fase per modul** (model kecil, berulang, murah).

**Hasil akhir.** Layar chat: kiri percakapan, kanan hasil. Di atasnya deretan tab: `Semua` + satu tab per modul. Membuka tab modul menampilkan pertanyaan lanjutan modul itu dan mem-fokuskan panel kanan ke modul tersebut.

```text
 [Semua · 3] [Antrean · 2] [Stok] [Tagihan]
 ┌─ chat (utas aktif) ─────┐ ┌─ hasil request ───────────┐
 │ Agent: ada yang ingin   │ │ Fokus: Antrean            │
 │ saya pastikan...        │ │  [ Papan Kanban modul ]   │
 │ [ ] Status boleh ...?   │ │                           │
 └─────────────────────────┘ └───────────────────────────┘
```

---

## 🧭 2. "Start dari Mana?" — Order of Operations

Urutan ini sengaja **dari domain murni ke luar**, dan tiap langkah punya tes sebelum lanjut.

1. **`core` — model utas.** `ChatMessage` mendapat `moduleId` (null = utas Semua), `kind` (TEXT/QUESTION), `questions`. Mengapa dulu? Semua lapisan lain bergantung pada bentuk pesan ini; mengubahnya belakangan menyentuh DB, route, dan UI sekaligus.
2. **`core` — fungsi murni utas & follow-up.** `inThread()`, `pendingFollowUps()`, `composeNarrative()`. Tanpa I/O, jadi ditest dalam milidetik.
3. **`core` — kontrak agent.** `NarrativeClarifier`, `ModuleEditor` berupa `fun interface`. Core tidak tahu LLM apa pun.
4. **`core` — use case.** `SendBuilderMessageUseCase` (diperluas), `AskModuleFollowUpsUseCase`, `EditModuleFromChat`.
5. **`core` — aturan data.** `ModuleGapAnalyzer` (celah dihitung dari data) dan `ProposalEdit` (sunting isian).
6. **`server` — migrasi V93** (kolom aditif), repository, lalu **registri run + rute SSE**.
7. **`server` — adaptor LLM** (`KoogNarrativeClarifier`, `KoogModuleEditor`) dan saklar env.
8. **`app/shared` — klien**: `startRun`/`runEvents`, tab utas, panel fokus, kolom ketik yang berubah saat ada pertanyaan.
9. **Uji hidup di browser** dengan model sungguhan. Langkah ini menemukan satu bug yang tidak tertangkap tes (bagian 5).

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A — Utas adalah *filter*, bukan tabel terpisah

```kotlin
fun List<ChatMessage>.inThread(moduleId: String?): List<ChatMessage> =
    if (moduleId == null) this else filter { it.moduleId == moduleId }

fun List<ChatMessage>.pendingFollowUps(moduleId: String?): List<PendingFollowUp> =
    inThread(moduleId)
        .filter { it.kind == ChatMessageKind.QUESTION }
        .flatMap { m -> m.questions.filter { it.answer.isNullOrBlank() }.map { PendingFollowUp(m.id, m.moduleId, it) } }
```

**Mengapa begini?**
- Satu percakapan per tenant tetap satu tabel. Utas hanyalah *sudut pandang*: `null` = tanpa filter (melihat semuanya), terisi = hanya modul itu. Keputusan produk "utas Semua tanpa filter" jadi satu baris `if`.
- "Apakah ada follow-up?" **tidak disimpan**, melainkan dihitung setiap riwayat dimuat dari pertanyaan yang belum berjawaban. Tidak ada status yang bisa basi atau tidak sinkron.
- `moduleId` adalah **data** (kode modul pack), bukan enum. Pack klinik, bordir, atau garment memakai kolom yang sama (Uji Variabilitas).

### Blok B — SSE: progres lewat aliran, kebenaran tetap di DB

```kotlin
class BuilderRun(val id: String, val tenantId: TenantId) {
    private val state = MutableStateFlow(State())           // events + finished

    fun stream(afterId: Long = 0): Flow<RunEvent> = flow {
        var next = afterId.toInt()
        while (true) {
            val snapshot = state.first { it.events.size > next || it.finished }
            while (next < snapshot.events.size) emit(snapshot.events[next++])
            if (snapshot.finished && next >= snapshot.events.size) break
        }
    }
}
```

**Mengapa begini?**
- Peristiwa disimpan sebagai **daftar yang bertambah** di `MutableStateFlow`. Pelanggan baru, atau yang sambung ulang dengan `Last-Event-ID`, cukup mulai dari indeks tertentu. Tidak ada kondisi balapan "peristiwa lewat sebelum aku berlangganan".
- Alur: `POST /chat/runs` → `202 {runId}` → `GET /runs/{id}/events`. Setelah `done`/`error`, klien **memuat ulang riwayat**. Jadi kalau koneksi SSE putus di tengah, tidak ada data hilang: hasil sebenarnya sudah ada di DB. SSE hanyalah "sedang apa".
- Satu run aktif per tenant (`RunAlreadyActiveException` → 409) mencegah dua giliran agent saling menimpa draf yang sama.

### Blok C — Gerbang harus dijalankan *sebelum* aliran dimulai

```kotlin
route("/runs/{runId}/events") {
    intercept(ApplicationCallPipeline.Call) {
        if (call.gate() == null) return@intercept finish()       // 403/404 sebelum header terkirim
        val run = registry.find(call.parameters["runId"].orEmpty(), call.tenantContext.tenantId)
        if (run == null) { call.respond(HttpStatusCode.NotFound, "Run tidak ditemukan"); return@intercept finish() }
        call.attributes.put(RunKey, run)
    }
    sse { /* hanya mengalirkan */ }
}
```

**Mengapa begini?** Begitu aliran SSE mulai, header 200 sudah terkirim; kamu tidak bisa lagi membalas 403. Jadi pemeriksaan wewenang ditaruh di *interceptor* sebelum handler `sse`. Run milik tenant lain diperlakukan sebagai "tidak ada" (404, bukan 403) agar keberadaannya tidak bocor.

### Blok D — Penanya klarifikasi: satu putaran, bukan interogasi

```kotlin
val asker = clarifier
if (asker != null && moduleId == null && waiting.isEmpty() &&
    thread.none { it.kind == ChatMessageKind.QUESTION }) {
    val questions = runCatching { asker.clarify(composeNarrative(thread), existingModules) }
        .getOrDefault(emptyList()).take(InterviewLimits.CLARIFICATIONS)
    if (questions.isNotEmpty()) { /* simpan pesan QUESTION, hentikan giliran */ }
}
```

**Mengapa begini?**
- **Aturan ditegakkan di kode, bukan di prompt.** Prompt boleh bilang "maksimal 3, sekali saja", tapi yang menjamin adalah `thread.none { QUESTION }` dan `.take(3)`. Model bisa lupa; `if` tidak.
- `runCatching { ... }.getOrDefault(emptyList())`: penanya mati/timeout/keluaran rusak berarti **tidak bertanya**. Chat tidak pernah macet karena komponen "bonus".
- Hanya di utas Semua. Utas modul punya mekanisme sendiri (Blok F).

### Blok E — Narasi gabungan: revisi dibaca bersama cerita awal

```kotlin
fun composeNarrative(thread: List<ChatMessage>, limit: Int = InterviewLimits.NARRATIVE): String {
    val answers = /* semua jawaban follow-up */
    val parts = thread.mapNotNull { m -> when {
        m.kind == QUESTION -> "Pertanyaan: ...\nJawaban: ..."      // pasangan tanya-jawab
        m.role == USER && m.text.trim() !in answers -> m.text.trim() // jangan ulang balasan yang sudah jadi jawaban
        else -> null } }
    ...
}
```

**Mengapa begini?** Agent penyusun draf (`DiscoveryBackedBuilderAgent`) memperlakukan pesan sebagai *narasi baru dan menyusun draf dari nol*. Tanpa penggabungan, "tambah modul pengiriman" menghasilkan draf yang mematikan 9 modul, karena agent hanya melihat satu kalimat itu. Dengan narasi gabungan, jawaban "klinik gigi..." tadi menghasilkan modul klinik. Bila terlalu panjang, bagian tengah dipotong: awal (cerita pokok) dan akhir (revisi terbaru) dipertahankan.

### Blok F — Celah dihitung dari data; model hanya menafsirkan

```kotlin
object ModuleGapAnalyzer {
    fun analyze(draft: DiscoveryDraft, moduleId: String, limit: Int = 3): List<ModuleGap> { ... }
    // NO_SCREEN, FEW_FIELDS, NO_REQUIRED, NO_STATUS_FLOW, GENERIC_SOURCE
    fun unasked(gaps: List<ModuleGap>, thread: List<ChatMessage>): List<ModuleGap> // lewati yang sudah pernah ditanyakan
}
```

**Mengapa begini?**
- Pertanyaan per modul **tidak perlu LLM**: "isian baru 2, apa lagi?" cukup dari `entity.fields.size`. Hemat biaya, deterministik, dan bisa dites.
- `ModuleGap.code` (`gap:few_fields:<screenId>`) dipakai sebagai `Clarification.id`. Jadi tab yang dibuka berkali-kali tidak menanyakan hal yang sama (`unasked`).
- `ModuleGapKind` adalah enum karena itu *mekanik platform* (macam kekurangan spesifikasi). Teks pertanyaan dirakit dari label milik tenant, tanpa kosakata industri.

### Blok G — Sunting isian: model mengusulkan, kode menerapkan, validator memutuskan

```kotlin
fun ScreenProposal.applyEdits(edits: List<ProposalEdit>, packModuleIds: Set<String>? = null): Result<ScreenProposal> = runCatching {
    var current = this
    edits.forEach { current = current.applyOne(it) }
    val issues = ScreenProposalValidator.validate(current, "$.proposal", null, packModuleIds)
    if (issues.isNotEmpty()) throw ProposalEditException(issues.joinToString("; ") { "${it.path}: ${it.message}" })
    current
}
```

**Mengapa begini?**
- Model tidak pernah menulis `DiscoveryDraft`. Ia mengembalikan JSON `{op: add|remove|replace, ...}`, kode menerjemahkannya ke `ProposalEdit`, menerapkannya ke **salinan**, dan **validator yang sama dengan semua pembuat layar lain** memutuskan sah atau tidak. Satu pintu, tidak ada aturan kedua.
- Galat validator bersifat *berpath* (`$.proposal.seed[0].tanggal: ...`), jadi bisa dikirim balik ke model sebagai umpan balik pada percobaan kedua (`MAX_ATTEMPTS = 2`).
- Hasil akhirnya **patch usulan** yang menunggu tombol Terapkan: manusia tetap yang memutuskan.

### Blok H — Serah-terima ke developer: brief membawa *mengapa*, bukan hanya *apa*

```kotlin
fun briefContextOf(messages: List<ChatMessage>, included: Set<String>, narrativeLimit: Int = 4000): BriefContext?
// cerita asli | tanya-jawab terjawab | keputusan yang DITERAPKAN | pertanyaan yang BELUM jelas
```

**Mengapa begini?**
- Tujuan fitur ini mempercepat **prototype**; pekerjaan akhirnya tetap dikerjakan manusia. Percepatan hilang bila developer hanya menerima hasil akhir
  (field-field), lalu bertanya ulang apa yang sudah dijawab di chat. Brief lama berisi layar, entitas, harga, dan log prototype memori; ia **tidak** memuat
  cerita, tanya-jawab, atau keputusan.
- Hanya patch yang **sudah diterapkan** yang dihitung sebagai keputusan; usulan yang dibuang tidak boleh tampil sebagai kesepakatan.
- Bagian **Belum jelas** (follow-up yang masih menunggu) paling berguna bagi developer: ia menunjukkan batas pengetahuan kita.
- Kompatibel mundur: tanpa riwayat chat, `context` bernilai `null`, Markdown dan JSON identik byte-per-byte dengan sebelumnya (dikunci tes golden).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **SSE + riwayat di DB sebagai kebenaran** | WebSocket; polling | Satu arah (server → klien) cukup; HTTP biasa, lolos proxy dev; sambung ulang bawaan lewat `Last-Event-ID` | WebSocket: kerumitan 2 arah yang tidak dibutuhkan. Polling: boros dan terasa lambat |
| **Utas = filter atas satu tabel** | Tabel/percakapan terpisah per modul | Utas Semua otomatis memuat semuanya; satu tempat migrasi dan RLS | Pesan terpecah, utas Semua harus menggabung manual |
| **Follow-up dihitung saat dimuat** | Menyimpan status "ada follow-up" | Tidak pernah basi; mengikuti keadaan draf terkini | Status tersimpan bisa tidak cocok dengan data setelah Terapkan |
| **Celah dari data, model hanya menafsir** | Minta LLM menilai kekurangan | Murah, deterministik, bisa dites; model dipakai di tempat ia benar-benar unggul (memahami bahasa) | Biaya per tab dibuka, hasil berubah-ubah |
| **Model besar sekali, model kecil berulang** | Satu model untuk semuanya | Keputusan struktur (mahal bila salah) pakai pro; interaksi kecil pakai flash | Semua pro: lambat dan mahal. Semua flash: alur awal kurang matang |
| **Gagal = tidak bertanya / balasan jujur** | Melempar galat ke pengguna | Komponen AI adalah *peningkat*, bukan prasyarat | Chat macet saat provider LLM bermasalah |

**Pengaturan model (env)** — kedua fitur baru *mengikuti* saklar agent yang sudah ada (tidak perlu saklar baru):

| Tahap | Menyala bila | Model |
|---|---|---|
| Penanya klarifikasi (pro) | `DISCOVERY_AGENT=koog` | `BUILDER_CLARIFIER_MODEL` → `DISCOVERY_AGENT_MODEL_PLAN` → `DISCOVERY_AGENT_MODEL` |
| Penyusun draf | `DISCOVERY_AGENT=koog` | `DISCOVERY_AGENT_MODEL` |
| Penyunting modul (flash) | `INTERVIEW_AGENT=koog` | `BUILDER_MODULE_EDITOR_MODEL` → `INTERVIEW_AGENT_MODEL` |

`BUILDER_CLARIFIER=off` / `BUILDER_MODULE_EDITOR=off` hanya pengecualian untuk mematikan satu fitur.

Tanpa kunci API atau saklar agent induk, chat tetap jalan lewat jalur deterministik.

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Membalas 403 setelah aliran SSE dimulai.**
   *Bahaya*: header 200 sudah terkirim; klien menerima aliran kosong, bukan penolakan. *Solusi*: gerbang di interceptor sebelum handler `sse` (Blok C).

2. **Memercayai prompt untuk aturan keras** ("tanya maksimal sekali").
   *Bahaya*: model sesekali melanggar, dan kamu baru tahu di produksi. *Solusi*: tegakkan di kode (`thread.none { QUESTION }`, `.take(3)`), prompt hanya mengarahkan.

3. **Mengabaikan efek samping pada data contoh (seed).** *Ini bug nyata yang ditemukan di uji hidup.*
   Menambah field **wajib** membuat setiap baris contoh melanggar aturan "field wajib harus terisi di setiap baris". Flash sudah benar, tapi validator menolak dan percobaan ulang tidak menolong (model tak bisa memperbaiki seed). *Solusi*: `reconcileFor` mengisi/menyesuaikan seed sesuai tipe (DATE → `2026-01-01`, ENUM → opsi pertama, dst.) sebagai bagian dari penerapan sunting. **Pelajaran: tes dengan model tiruan tidak menemukan ini — hanya uji hidup yang menemukan.**

4. **Menyimpan "ada follow-up" sebagai kolom.** Akan basi setelah draf berubah. Hitung dari data.

5. **Satu run berjalan ganda untuk satu tenant.** Dua giliran agent bisa menimpa draf yang sama. Registri menolak dengan 409.

6. **Teks tampilan memakai karakter di luar font** (mis. `→`). Font Nunito tidak punya glyph itu; tampil sebagai kotak. Pakai ASCII/Latin-1 untuk teks yang dilihat pengguna.

7. **Menjalankan perintah tes dengan `timeout` di macOS.** `timeout` tidak ada; perintahnya gagal diam-diam dan tes tidak pernah jalan, sementara hasil "lulus" yang kamu baca berasal dari run lama. Selalu baca hasil dari berkas XML/`BUILD SUCCESSFUL`, bukan dari kode keluar pipeline.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

**Prinsip:** agent LLM diganti `ScriptedPromptExecutor` (menjawab dari skrip, merekam prompt) sehingga seluruh jalur produksi diuji **tanpa jaringan dan tanpa biaya**; yang diganti hanya jawaban model.

| Lapisan | Apa yang dibuktikan | Contoh |
|---|---|---|
| Core murni | utas, follow-up, narasi gabungan, celah, sunting | `BuilderThreadsTest`, `ModuleGapAnalyzerTest` (pack **klinik**, non-garment), `ProposalEditTest` |
| Use case + fake repo | bertanya sekali, jawaban mengisi pertanyaan, gagal = tidak bertanya, utas modul tidak bertanya | `BuilderClarifyFlowTest`, `ModuleChatTest` |
| Registri run | satu run aktif per tenant, isolasi tenant, replay `Last-Event-ID`, pembersihan | `BuilderRunRegistryTest` |
| Rute + SSE | 202 → aliran berurutan, **403/401/404**, utas terfilter, follow-up per utas | `BuilderChatRunsRoutesTest` (klien SSE sungguhan di dalam proses) |
| Adaptor LLM | parsing ketat, batas 3 pertanyaan, prompt memuat konteks | `KoogNarrativeClarifierTest`, `KoogModuleEditorTest` |
| Klien | URL `?module=`, body run, parse follow-up, fokus modul | `BuilderChatThreadsTest` |
| **Uji hidup** | SSE di browser Wasm; pro bertanya; flash menyunting; bug seed | Dicek di browser dengan model sungguhan |

Contoh asersi kunci (dari `ModuleChatTest`): sunting yang ditolak dicoba ulang **dengan umpan balik**.

```kotlin
assertEquals(2, feedbacks.size)
assertNull(feedbacks[0]); assertTrue(feedbacks[1]!!.contains("sudah ada"), "percobaan kedua membawa galat")
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1 — Progres per modul yang jujur.** Sekarang peristiwa SSE hanya `status` (`planning`, `drafting`, `editing`). Rancang langkah hidrasi per modul (satu panggilan flash per modul) sehingga peristiwa `module_ready` benar-benar muncul satu per satu. Apa yang harus diubah di `BuilderRun` dan di `SendBuilderMessageUseCase`?
- [ ] **Tantangan 2 — Celah baru.** Tambahkan `ModuleGapKind.NO_VIEW_FIELDS` (field ada tapi tidak satu pun tampil di tabel). Tulis tesnya dulu di pack **bengkel**, bukan klinik. Mengapa tes di tenant kedua penting?
- [ ] **Tantangan 3 — Edit lintas field.** `ProposalEdit` belum punya `SetTransitions` (urutan perpindahan status Kanban). Rancang edit itu dan aturan sinkron ke `ViewProposal`; aturan validator mana yang harus tetap menjadi satu-satunya penentu?
