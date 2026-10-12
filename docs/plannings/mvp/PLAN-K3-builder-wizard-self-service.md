# PLAN-K3: Builder Wizard Self-Service (Template + Follow-Up)

**Tanggal**: 2026-10-13
**Rencana induk**: [`PLAN-dual-track-garment-and-general-platform.md`](PLAN-dual-track-garment-and-general-platform.md) §4 (K3)
**Status**: Plan · Mulai implementasi

---

## 1. Overview

**K3**: Builder Wizard Self-Service — UI untuk guided self-service pembuatan pack baru via AI chat.

### Dua Metode Pembuatan Pack

| Metode | Deskripsi | Target User |
|---|---|---|
| **Template-based (disarankan)** | Pilih template garment → tanya divisi → auto-configure modules | Pabrik garment umum |
| **From Scratch** | Chat AI tanpa template, buat pack dari awal | Custom/universal |

### Masalah

Backend sudah mendukung:
- Chat API (`POST /api/builder/chat/runs`)
- Follow-up questions (`AskModuleFollowUpsUseCase`, `Clarification`)
- Response `followUp` di `GET /api/builder/chat`

Yang belum: **UI layer** untuk:
1. Pemilihan metode (template vs scratch)
2. Render popup/modal dari response `followUp` (divisi, dll)
3. Submit jawaban → lanjut chat flow

### Solusi

1. **Halaman Pemilihan Metode** — template vs scratch
2. **Template Garment** — konveksi, sablon, bordir, rajut, dll
3. **Follow-up Questions** — tanya divisi (Cutting, Sewing, Finishing, dll)
4. **UI Modal** — render + submit jawaban

---

## 2. Template-Based Pack Creation (MVP)

### 2.1 Template Garment

| Template | Divisi Default | Modules |
|---|---|---|
| Konveksi FOB | Cutting, Sewing, Finishing, QC | Full BOM, Cutting SPK, Sewing Kanban, QC, Fulfillment |
| Konveksi CMT | Cutting, Sewing, Finishing, QC | (tanpa procurement) |
| Sablon | Cutting, Sablon, Finishing, QC | Screen Printing, Curing |
| Bordir | Cutting, Bordir, Finishing, QC | Embroidery |
| Rajut | Knitting, Cutting, Finishing, QC | Circular Knitting |
| Custom | Tanya divisi → generate | Berdasarkan jawaban |

### 2.2 Follow-Up Questions (Division-Based)

Setelah pilih template, AI bertanya:

1. **"Apa saja divisi di pabrik Anda?"**
   - Jawaban: Cutting, Sewing, Finishing, QC, Sablon, Bordir, Rajut, Printing, etc.
   - Sistem auto-enable modules berdasarkan divisi

2. **"Pakai mesin otomatis atau manual?"**
   - Jawaban: Otomatis / Manual / Campuran
   - Pengaruh: fitur auto-cut, auto-sew, dll

3. **"Pakai QC 100% atau sampling AQL?"**
   - Jawaban: 100% / AQL 2.5 / AQL 4.0
   - Pengaruh: QC inspection rules

### 2.3 From Scratch (Tetap Ada)

User yang mau custom 100% bisa pilih "From Scratch":
- Tidak ada template/blueprint awal
- Chat AI tanpa constrain
- Bisa tambah modul satu-satu

---

## 4. Kondisi Sekarang (As-Is)

### 4.1 Backend: Follow-up sudah ada

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

### 4.2 API: `followUp` di response

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

### 4.3 Frontend: Parse sudah ada

**File**: `app/shared/.../presentation/builder/chat/ChatModel.kt:26-30`

```kotlin
internal fun parseFollowUp(raw: JsonValue): List<PendingQuestion> =
    (((raw as? JsonValue.Obj)?.get("followUp") as? JsonValue.Obj)?.get("questions") ...
```

**Gap**: Tidak ada UI untuk render `PendingQuestion` sebagai modal.

---

## 5. Solusi (To-Be)

### 5.1 K3e: Halaman Pemilihan Metode (Template vs Scratch)

**File**: `app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/builder/BuilderNewPackScreen.kt`

**UI Flow**:
```
┌─────────────────────────────────────────┐
│  Buat Pack Baru                           │
├─────────────────────────────────────────┤
│                                          │
│  🎯 Template-based (disarankan)         │
│  ┌─────────────────────────────────┐    │
│  │  Konveksi FOB                   │    │
│  │  Cutting → Sewing → Finishing   │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │  Konveksi CMT                   │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │  Sablon                         │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │  Bordir                         │    │
│  └─────────────────────────────────┘    │
│                                          │
│  ✨ From Scratch                         │
│  │  Buat pack sendiri dari awal      │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

**Action**:
- Klik template → mulai chat dengan blueprint/template tersebut
- Klik Scratch → mulai chat tanpa template

### 5.2 K3a: FollowUpModal Component

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

### 5.3 K3b: Integrasi ke ChatPane

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

### 5.4 K3c: Submit Jawaban

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

### 5.5 K3d: Wizard Shell (Optional)

Fase A (Interview) → Fase B (Blueprint) → Fase C (Module Editor)
- Stepper UI di top bar
- Progress indicator
- Skip/Back navigation

---

## 6. Task Breakdown

| Task | Deskripsi | File Target | Status |
|---|---|---|---|
| K3e | Halaman pemilihan metode (Template vs Scratch) | `BuilderNewPackScreen.kt` | ⏳ |
| K3a.1 | Buat `FollowUpModal.kt` dengan Clay design | `presentation/builder/chat/` | ✅ |
| K3a.2 | Buat `FollowUpForm.kt` — input per tipe pertanyaan | `presentation/builder/chat/` | ✅ |
| K3b.1 | Tambah state `pendingFollowUps` di ChatPane | `BuilderChatPane.kt` | ✅ |
| K3b.2 | Trigger modal dari state | `BuilderChatPane.kt` | ✅ |
| K3c.1 | POST jawaban ke chat API | `BuilderApiClient.kt` | ✅ |
| K3c.2 | Handle response — lanjut chat | `BuilderChatPane.kt` | ✅ |
| K3d | Wizard shell (opsional) | TBD | ❌ |

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

- [ ] User bisa pilih metode: Template-based atau From Scratch
- [ ] Template-based menampilkan grid template garment (Konveksi, Sablon, Bordir, Rajut, dll)
- [ ] Dari template → masuk ke chat dengan blueprint/template tersebut
- [ ] From Scratch → masuk ke chat tanpa template
- [ ] Ada modal yang muncul saat `GET /api/builder/chat` return ada `followUp.questions` yang belum dijawab
- [ ] User bisa isi jawaban per pertanyaan di modal (terutama: divisi apa saja)
- [ ] Submit jawaban → POST ke chat API → AI response baru
- [ ] Tidak ada regression di chat flow yang sudah ada
- [ ] UI sesuai Claymorphism design system

---

## 7. Referensi

- [`PLAN-builder-interview-chat.md`](../features/PLAN-builder-interview-chat.md) — konteks chat architecture
- `core/.../domain/builder/ModuleChat.kt` — AskModuleFollowUpsUseCase
- `app/shared/.../presentation/builder/chat/ChatModel.kt` — parseFollowUp
- [`design-system-rules.md`](../../rules/design-system-rules.md) — Claymorphism specs
