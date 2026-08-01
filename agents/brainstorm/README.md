# EventVerse Feature Brainstorm Agent

Agent interaktif berbasis **Google Antigravity SDK** untuk brainstorm fitur EventVerse
dan langsung mempublishnya sebagai **GitHub Issue** yang terstruktur (dengan DDD context, 
acceptance criteria, label, milestone, dan GitHub Project card).

---

## Prerequisites

- Python 3.11+
- `GEMINI_API_KEY` — dari [Google AI Studio](https://aistudio.google.com/app/api-keys)
- `GITHUB_TOKEN` — Personal Access Token dengan scope:
  - `public_repo` atau `repo` — untuk membuat issues
  - `project` — untuk GitHub Projects v2
  - Buat di: https://github.com/settings/tokens?type=beta

---

## Setup

```bash
# 1. Masuk ke folder brainstorm
cd .agents/agents/brainstorm

# 2. Buat virtual environment & install dependencies
#    (hanya perlu dilakukan sekali)
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# 3. Salin dan isi .env
cp .env.example .env
# Edit .env dengan API key dan token kamu

# 4. Jalankan agent
#    (setiap kali buka terminal baru, aktifkan venv dulu)
source .venv/bin/activate
python3 agent.py
```

---

## Cara Pakai

```
You: saya mau bikin fitur notifikasi ketika tiket hampir habis

Agent: Ide yang bagus! Mari kita explore lebih dalam.
       Beberapa pertanyaan untuk memahami fiturnya:
       1. Notifikasi ini untuk siapa — organizer yang melihat tiket acara mereka hampir habis,
          atau attendee yang mau diberi tahu kalau tiket tersisa sedikit?
       ...
```

Agent akan:
1. 🧠 Mengajukan pertanyaan klarifikasi (siapa, apa, platform, DDD layer, dll)
2. 📋 Menampilkan draft GitHub Issue yang terstruktur
3. ✅ Meminta konfirmasi sebelum publish
4. 🚀 Membuat issue di GitHub + menambahkan ke Project board (jika dikonfigurasi)

---

## Output Issue Template

```markdown
## 🎯 Feature: Notifikasi Tiket Hampir Habis

### 📖 User Story
As an organizer, I want to receive a notification when ticket inventory
is below 10% so that I can take action (e.g., add more seats or close sales).

### ✅ Acceptance Criteria
- [ ] Notifikasi dikirim ketika stok tiket < 10% kapasitas total
- [ ] ...

### 🏗️ DDD Context
| Field | Value |
|---|---|
| Domain | `Ticket` |
| Layer | `Application` |
| Suggested Use Cases | `NotifyLowTicketInventoryUseCase` |
| Entities / Value Objects | `TicketInventory`, `TicketThreshold` |

### 📱 Platform Target
- [x] Android
- [x] iOS
...
```

---

## File Structure

```
.agents/agents/brainstorm/
├── agent.py               ← Entry point utama
├── requirements.txt       ← Python dependencies
├── .env.example           ← Template environment variables
├── .env                   ← (gitignored) API keys kamu
└── tools/
    ├── github_tools.py    ← GitHub REST + GraphQL API tools
    └── brainstorm_tools.py← Issue structuring tool
```

---

## GitHub PAT Scopes

| Scope | Kebutuhan |
|---|---|
| `public_repo` | Membuat issues di repo public |
| `repo` | Membuat issues di repo private |
| `project` | Menambahkan ke GitHub Projects v2 |

> **Tip:** Gunakan Fine-grained PAT untuk keamanan lebih baik.
> Cukup grant akses ke repo `amalari/event-verse` saja.
