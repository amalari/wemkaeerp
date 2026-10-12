# TRD-K3: Builder Wizard (Template Selection + Follow-Up UI)

## 1. Konteks dan Administrasi

- **ID**: TRD-K3 — Builder Wizard (Template + Follow-Up)
- **Status**: **Diusulkan; siap implementasi** (2026-10-13)
- **Rujukan**: 
  - [`PLAN-builder-interview-chat.md`](../../plannings/features/PLAN-builder-interview-chat.md) — arsitektur chat
  - [`PLAN-K3-builder-wizard-self-service.md`](../../plannings/mvp/PLAN-K3-builder-wizard-self-service.md) — plan
  - `module-integration-rules.md` §5 (modul operasional)
  - `design-system-rules.md` — Claymorphism specs
  - `field-component-rules.md` — komponen input

### Masalah

User mau buat pack baru, tapi:
1. Dari scratch — terlalu kompleks untuk user biasa
2. Template-based — belum ada UI pilih template
3. Follow-up questions — belum ada UI untuk input jawaban divisi

### Solusi MVP

Dua metode pembuatan pack:
- **Template-based (disarankan)**: Pilih template garment → tanya divisi → auto-configure
- **From Scratch**: Chat AI tanpa template

### Bukti (As-Is)

**Backend support** (`core/.../domain/builder/ModuleChat.kt:131-154`):
```kotlin
class AskModuleFollowUpsUseCase(...) {
    suspend operator fun invoke(tenantId: TenantId, moduleId: String): Result<Boolean>
    // questions = gaps.map { Clarification(it.code, it.question) }
}
```

**API response** (`GET /api/builder/chat`):
```json
{
  "messages": [...],
  "followUp": {
    "scope": "module",
    "moduleId": "klinik_poli",
    "questions": [
      {"id": "c1", "question": "Siapa yang mengisi?", "messageId": "2", "moduleId": "klinik_poli"}
    ]
  }
}
```

**Frontend parse** (`app/shared/.../presentation/builder/chat/ChatModel.kt:26-30`):
```kotlin
internal fun parseFollowUp(raw: JsonValue): List<PendingQuestion> = ...
```

---

## 2. Persyaratan

### FR (Functional Requirements)

#### Template Selection (K3e)

- **FR-1**: Tampil halaman pemilihan metode: "Template-based" vs "From Scratch"
- **FR-2**: Template-based menampilkan grid card template garment:
  - Konveksi FOB
  - Konveksi CMT
  - Sablon
  - Bordir
  - Rajut
  - Custom/Lainnya
- **FR-3**: Klik template → mulai chat dengan blueprint/template tersebut
- **FR-4**: Klik "From Scratch" → mulai chat tanpa template

#### Follow-Up Questions (K3a-c)

- **FR-5**: Modal popup muncul saat `GET /api/builder/chat` mengembalikan `followUp.questions` yang belum dijawab
- **FR-6**: Modal menampilkan list pertanyaan follow-up dengan label dan input field per pertanyaan
- **FR-7**: User bisa submit jawaban via tombol "Kirim Jawaban"
- **FR-8**: Jawaban dikirim ke `POST /api/builder/chat/runs` dengan format:
  ```json
  {
    "text": "Jawab pertanyaan c1: [jawaban]",
    "moduleId": "klinik_poli",
    "answered": [["c1", "[jawaban]"]]
  }
  ```
- **FR-9**: Setelah submit, modal menutup dan chat flow lanjut normal (AI response baru)
- **FR-10**: User bisa skip/lewati pertanyaan via tombol "Lewati"

### NFR (Non-Functional Requirements)

- **NFR-1**: UI sesuai Claymorphism + Neo-Brutalism design system (`design-system-rules.md`)
- **NFR-2**: Komponen input pakai `FieldInput` dari `presentation/discovery/fields/` — tidak buat yang baru
- **NFR-3**: State management di `BuilderChatViewModel` — tidak buat ViewModel baru
- **NFR-4**: Tidak ada regression di chat flow yang sudah ada
- **NFR-5**: Kompilasi 5 target KMP tetap hijau

---

## 3. Keputusan

### K1 — Arsitektur UI

**Pakai komponen yang sudah ada**, tidak buat baru:
- Modal: `ClayCard` + `ClayActionSurface` (bukan `AlertDialog` Material)
- Input: `FieldInput` dari `presentation/discovery/fields/FieldInput.kt`
- Styling: token dari `WeMadeColors`, `ClayShapes`, `ClaySpacing`

### K2 — State Management

Tambah state di `BuilderChatUiState`:
```kotlin
data class BuilderChatUiState(
    // ... existing
    val pendingFollowUps: List<PendingQuestion> = emptyList(),
    val showFollowUpModal: Boolean = false,
)
```

Trigger: setelah fetch chat history, parsing `parseFollowUp(response)` → kalau ada `pendingFollowUps` yang belum dijawab → `showFollowUpModal = true`.

### K3 — Submit Flow

1. User isi jawaban di form
2. ViewModel POST ke `POST /api/builder/chat/runs` dengan body:
   ```json
   {
     "text": "Jawab pertanyaan ${questionId}: ${answer}",
     "moduleId": "${moduleId}",
     "answered": [["${questionId}", "${answer}"]]
   }
   ```
3. Response: chat message baru dari AI
4. Modal close, chat pane refresh dengan message baru

### K4 — Error Handling

- **Network error**: Tampilkan error di modal, user bisa retry
- **Validation error**: Validasi input sebelum submit (required field)
- **API error 4xx/5xx**: Tampilkan pesan error, modal tetap terbuka

---

## 4. Desain UI (Wireframe Teks)

```
┌─────────────────────────────────────────────────┐
│  ⚠️ Pertanyaan Clarification                   │
├─────────────────────────────────────────────────┤
│                                                 │
│  📋 klinik_poli                                │
│                                                 │
│  ─────────────────────────────────────────────  │
│                                                 │
│  c1: "Siapa yang mengisi?"                     │
│  ┌─────────────────────────────────────────┐   │
│  │ [Text input: Admin klinik              ] │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  ─────────────────────────────────────────────  │
│                                                 │
│              [ Lewati ]    [ Kirim Jawaban ]    │
│                                                 │
└─────────────────────────────────────────────────┘
```

**Specs**:
- Modal width: 480dp max
- Padding: `ClaySpacing.Md` (16dp)
- Border: `ClayBorder.Thick` (3dp)
- Shadow: `ClayOffset.Rest` (6dp)
- Font: Nunito body, Fredoka header

---

## 5. File Targets

| Komponen | Lokasi |
|---|---|
| `BuilderNewPackScreen.kt` | `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/builder/` |
| `TemplateCard.kt` | `app/shared/.../presentation/builder/components/` |
| `FollowUpModal.kt` | `app/shared/.../presentation/builder/chat/` |
| `FollowUpForm.kt` (opsional, bisa inline) | `app/shared/.../presentation/builder/chat/` |
| State update | `app/shared/.../presentation/builder/chat/BuilderChatPane.kt` |
| Trigger modal | `app/shared/.../presentation/builder/chat/BuilderChatPane.kt` |

---

## 6. Testing

- **Unit test**: `BuilderChatViewModel` — verify state update saat followUp ada
- **Integration test**: `BuilderChatRunsRoutesTest` — verify submit answered questions
- **Visual check**: Render modal di tenant `klinik-uji`, lihat follow-up popup

---

## 7. Risiko & Mitigasi

| Risiko | Mitigasi |
|---|---|
| Input type tidak jelas (text vs number vs date) | Pakai `FieldInput` generik, biarkan AI clarify |
| Banyak pertanyaan sekaligus | Scrollable list di modal |
| User skip semua | Tombol "Lewati" wajib ada |

---

## 8. Definition of Done

### Template Selection (K3e)
- [ ] Tampil halaman pemilihan metode
- [ ] Grid template garment muncul dengan benar
- [ ] Klik template → masuk ke chat dengan blueprint
- [ ] Klik From Scratch → masuk ke chat tanpa template

### Follow-Up Questions (K3a-c)
- [ ] Modal muncul saat ada `pendingFollowUps` dari API
- [ ] Form input per pertanyaan render dengan benar
- [ ] Submit jawaban → POST ke API → AI response baru
- [ ] Tombol "Lewati" works
- [ ] UI sesuai Clay design system
- [ ] Kompilasi 5 target hijau
- [ ] Visual check di browser (Wasm Compose)
