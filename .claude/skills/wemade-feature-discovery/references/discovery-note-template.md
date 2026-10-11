# Discovery Note — <nama fitur/modul>

**Tanggal**: YYYY-MM-DD · **Penulis**: …

## 1. Kebutuhan
- Siapa memakai:
- Data milik:
- Berubah kapan:

## 2. Fitur serupa
- Perintah: `scripts/find-similar-feature.sh …`
- Temuan (file/teaching doc):
- Keputusan: **Sudah ada → extend** / **Mirip → tiru pola `<file>`** / **Baru**

## 3. Jenis
**Modul operasional / Governance / Foundation / Fitur dalam modul `<MODUL_INDUK>`** — alasan:
**Mungkin dipakai tenant lain?** ya (→ pack bersama sejak awal) / tidak (→ khusus tenant) — alasan: *(hanya untuk modul baru)*

## 4. Uji Variabilitas
| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|

## 5. Core & extend
- Core:
- Titik extend:
- Contoh yang ditiru (file:baris):
- Jangan disentuh:

## 6. I/O & kanvas
- Port masuk: `<Tipe>` dari …
- Port keluar: `<Tipe>` ke …
- Kanvas: level 1 node / level 2 di bawah `<MODUL_INDUK>` sebagai tahap/proses/stasiun/alat
- Telemetri:

## 7. Governance
| Operasi | Level minimum | Peran yang ditolak (dites 403) |
|---|---|---|
- Gate: · ScopeCapability: · Entitlement:

## 8. Ukuran, Effort & Rencana Track
- **Agregat baru**: · **Migrasi DB**: · **TRD perlu**: Ya / Tidak
- **Estimasi Effort**: Low / Medium
- **Reasoning Effort**: <alasan: kompleksitas logika, jumlah file, skema data, risiko>
- **Rencana Track**:
  - Track 0 (Contract Phase — Serial / Prasyarat, bukan A0)
  - Track A (Core & Domain — Fully Coding)
  - Track B (Server & Persistence — Fully Coding)
  - Track C (Client & UI Implementation — Fully Coding)
  - Track D (UI Visual Check & Verification — Terpisah dari Track C)
