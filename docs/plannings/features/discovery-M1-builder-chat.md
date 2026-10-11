# Discovery Note — M1 Builder (Chat tersimpan + `propose_patch` + panes)

**Tanggal**: 2026-09-30 · **Penulis**: Achmad Jamaludin (dibantu Claude) · **Jalur**: B
**Rencana induk**: [`PLAN-builder-console.md`](PLAN-builder-console.md) fase M1 · TRD:
[`TRD-PLAT-002-builder.md`](../trd/TRD-PLAT-002-builder.md) FR-M1-1 s.d. FR-M1-5.
**Lingkup**: chat tersimpan, agent mengusulkan patch (`propose_patch`), Terapkan/Buang,
pane Modules / Data Flow / Prototype (reuse), preview dalam builder.

## 1. Kebutuhan
- **Siapa memakai**: pemilik project (TENANT_ADMIN) & superadmin — bercerita lewat chat, melihat
  usulan patch (diff), menerapkan atau membuangnya, lalu melihat hasilnya di Modules/Data Flow/Prototype.
- **Data milik**: percakapan & pesan milik **tenant** (schema `builder`, RLS). Draf tetap milik
  tenant (`ops.discovery_drafts.tenant_id`, dari M0) — pesan hanya *menaut* `appliedDraftId`.
- **Berubah kapan**: percakapan bertambah per sesi; draf berubah **hanya** saat user menekan "Terapkan".

## 2. Fitur serupa
- Perintah: `scripts/find-similar-feature.sh chat agent conversation`
- Temuan: tidak ada fitur chat. Pola terdekat: `DiscoveryAgent` + `KoogDiscoveryAgent`
  (tool `platform_modules`/`validate_draft`, loop koreksi diri, fallback deterministik) — teaching
  `teaching-discovery-a8-koog-agent.md`; renderer: `ModuleMapPane`/`DataFlowPane`/`PrototypeRenderer`
  yang semuanya menerima `DiscoveryDraftUi.fromJson`.
- Keputusan: **Baru → tiru pola terdekat** (kontrak agent di domain, impl Koog + fallback deterministik
  di infra; renderer dipakai ulang apa adanya).

## 3. Jenis
**Governance-type platform screen** (lanjutan M0) — bukan `BusinessModule`; gate tetap `MANAGE_BUILDER`.

## 4. Uji Variabilitas
| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|
| `ChatRole {USER, AGENT, SYSTEM}` | tidak (sistem) | tidak | tidak | **kode** | — |
| Isi patch (pack/blueprint/screens) | **ya** | **ya** | **ya** (terapkan) | **data** (`DiscoveryDraft`) | draf beku saat lock; patch diterapkan = draf DRAFT diganti di tempat |
| Teks balasan agent | **ya** | ya | tidak | data (pesan) | — |

## 5. Core & extend
- **Core baru**: `core/domain/builder/chat/` — `BuilderConversation`, `ChatMessage`, `BuilderAgentReply`,
  interface `BuilderAgent` (metode `proposePatch`), `BuilderChatRepository`.
- **Titik extend**: `DiscoveryDraftRepository.findByTenant` (baru, untuk draf kerja tenant);
  `BuilderRoutes` diperluas argumennya (nol baris baru di `Application.kt` — ratchet);
  `BuilderApiClient` + pane baru.
- **Contoh yang ditiru**: `DiscoveryAgent.kt` (kontrak + `agentRef`), `KoogDiscoveryAgent`
  (loop koreksi diri maks 3×), `DiscoveryDraftValidator` (gerbang patch), `DemandLedgerScreen`
  (pola layar + ApiClient).
- **Jangan disentuh**: `KoogDiscoveryAgent`/tools funnel (dipakai via komposisi, bukan diubah),
  `Application.kt` (664 baris — tidak bertambah), `UpdateDiscoveryDraftUseCase` (gerbang owner,
  bukan gerbang tenant).

## 6. I/O & kanvas
- Port kanvas: tidak ada (governance). Patch = dokumen `DiscoveryDraft` penuh yang **divalidasi**
  `DiscoveryDraftValidator` + codec ketat sebelum boleh diterapkan; agent tidak pernah menulis draf.
- Telemetri: tidak ada.

## 7. Governance
| Operasi | Level minimum | Ditolak 403 (dites) |
|---|---|---|
| Baca chat & draf | `MANAGE_BUILDER` | tanpa izin, tenant lain |
| Kirim pesan (agent menjawab) | `MANAGE_BUILDER` | idem |
| **Terapkan patch** | `MANAGE_BUILDER` | idem |
- Agent **tidak punya** akses tulis; `Terapkan` = aksi manusia (plan §4).
- Fallback deterministik wajib: agent mati → chat tetap menjawab, tanpa patch.

## 8. Ukuran → TRD?
- Agregat baru: 2 (`BuilderConversation`, `ChatMessage`). Migrasi **V82**
  (`builder.conversations`, `builder.chat_messages`). TRD M1 sudah ada di TRD-PLAT-002.
