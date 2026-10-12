# PLAN-K3: Builder Wizard Self-Service (Follow-up UI)

**Tanggal**: 2026-10-13
**Rencana induk**: [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §4 (K3)
**Status**: Plan · Mulai implementasi

---

## 1. Overview

**K3**: Builder Wizard Self-Service — UI untuk guided self-service pembuatan pack baru via AI chat, dengan popup follow-up untuk clarify data yang kurang.

### Masalah

Backend sudah mendukung:
- Chat API (`POST /api/builder/chat/runs`)
- Follow-up questions (`AskModuleFollowUpsUseCase`, `Clarification`)
- Response `followUp` di `GET /api/builder/chat`

Yang belum: **UI layer** untuk:
1. Render popup/modal dari response `followUp`
2. Input form untuk menjawab pertanyaan
3. Submit jawaban → lanjut chat flow

### Solusi

Tambah UI layer di `app/shared/presentation/builder/chat/`:
- `FollowUpModal.kt` — popup modal untuk tampilkan & jawab pertanyaan
- Integrasi ke `BuilderChatPane.kt` — trigger modal saat ada `pendingFollowUps`
- Wizard shell (opsional) — stepper untuk Fase A→B→C

---

## 2. Kondisi Sekarang (As-Is)

### 2.1 Backend: Follow-up sudah ada

**File**: `core/.../domain/builder/ModuleChat.kt:131-154`

```kotlin
class AskModuleFollowUpsUseCase(
    private val chats: BuilderChatRepository,
    private val drafts: DiscoveryDraftRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(tenantId: TenantId, moduleId: String): Result<Boolean> = runCatching {
        // ...
        questions = gaps.map { Clarification(it.code, it.question) }
    }
}
```

### 2.2 API: `followUp` di response

**File**: `server/.../routes/BuilderRoutes.kt:239-242`

```kotlin
"scope" to jsonOf(if (moduleId == null) "all" else "module"),
```

Response JSON:
```json
{
  "followUp": {
    "scope": "module",
    "moduleId": "klinik_poli",
    "questions": [
      {"id": "c1", "question": "Siapa yang mengisi?", "messageId": "2", "moduleId": "klinik_poli"}
    ]
  }
}
```

### 2.3 Frontend: Parse sudah ada

**File**: `app/shared/.../presentation/builder/chat/ChatModel.kt:26-30`

```kotlin
internal fun parseFollowUp(raw: JsonValue): List<PendingQuestion> =
    (((raw as? JsonValue.Obj)?.get("followUp") as? JsonValue.Obj)?.get("questions") ...
```

**Gap**: Tidak ada UI untuk render `PendingQuestion` sebagai modal.

---

## 3. Solusi (To-Be)

### 3.1 K3a: FollowUpModal Component

Buat komponen modal baru:

```
app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/builder/chat/
├── FollowUpModal.kt        // Modal popup untuk follow-up
└── FollowUpForm.kt         // Form per pertanyaan
```

**Desain** ( Clay + Neo-Brutalism):
- Modal dengan `ClayCard` (outline 3dp, hard shadow)
- List pertanyaan follow-up dari `PendingQuestion`
- Setiap pertanyaan: label + input field (text/select/radio)
- Tombol "Kirim Jawaban" → POST ke chat dengan jawaban
- Tombol "Lewati" → abaikan dan lanjut chat

### 3.2 K3b: Integrasi ke ChatPane

**File**: `app/shared/.../presentation/builder/chat/BuilderChatPane.kt`

Tambah state:
```kotlin
data class BuilderChatUiState(
    // ... existing
    val pendingFollowUps: List<PendingQuestion> = emptyList(),
    val showFollowUpModal: Boolean = false,
)
```

Trigger: setelah `GET /api/builder/chat`返回, cek `followUp.questions`.Kalau ada yang belum dijawab → show modal.

### 3.3 K3c: Submit Jawaban

POST ke `POST /api/builder/chat/runs` dengan body:
```json
{
  "text": "Jawab pertanyaan c1: Admin klinik",
  "moduleId": "klinik_poli",
  "answered": [["c1", "Admin klinik"]]
}
```

Lihat `ModuleChat.kt:53-54`:
```kotlin
message: String,
answered: List<Pair<String, String>>,
```

### 3.4 K3d: Wizard Shell (Optional)

Fase A (Interview) → Fase B (Blueprint) → Fase C (Module Editor)
- Stepper UI di top bar
- Progress indicator
- Skip/Back navigation

---

## 4. Task Breakdown

| Task | Deskripsi | File Target |
|---|---|---|
| K3a.1 | Buat `FollowUpModal.kt` dengan Clay design | `presentation/builder/chat/` |
| K3a.2 | Buat `FollowUpForm.kt` — input per tipe pertanyaan | `presentation/builder/chat/` |
| K3b.1 | Tambah state `pendingFollowUps` di `BuilderChatViewModel` | `presentation/builder/` |
| K3b.2 | Trigger modal dari state | `BuilderChatPane.kt` |
| K3c.1 | POST jawaban ke chat API | `BuilderChatViewModel` |
| K3c.2 | Handle response — lanjut chat | `BuilderChatViewModel` |
| K3d.1 | (Optional) Wizard stepper shell | TBD |

---

## 5. Estimasi & Prioritas

| Task | Effort | Priority |
|---|---|---|
| K3a | 1 hari | **TINGGI** |
| K3b | 0.5 hari | **TINGGI** |
| K3c | 0.5 hari | **TINGGI** |
| K3d | 2 hari | SEDANG |

Total: **2 hari** (K3a–c), K3d opsional.

---

## 6. Acceptance Criteria

- [ ] Ada modal yang muncul saat `GET /api/builder/chat` return ada `followUp.questions` yang belum dijawab
- [ ] User bisa isi jawaban per pertanyaan di modal
- [ ] Submit jawaban → POST ke chat API → AI response baru
- [ ] Tidak ada regression di chat flow yang sudah ada
- [ ] UI sesuai Claymorphism design system

---

## 7. Referensi

- [`PLAN-builder-interview-chat.md`](../features/PLAN-builder-interview-chat.md) — konteks chat architecture
- `core/.../domain/builder/ModuleChat.kt` — AskModuleFollowUpsUseCase
- `app/shared/.../presentation/builder/chat/ChatModel.kt` — parseFollowUp
- [`design-system-rules.md`](../../rules/design-system-rules.md) — Claymorphism specs
