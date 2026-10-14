# PLAN: Data Flow Visualization + Prototype Simulation

**Status**: Diusulkan  
**Tanggal**: 2026-10-14  
**Track**: Builder Chat Enhancement  

---

## 1. Ringkasan

Meningkatkan panel Result di `/builder/chat` dengan:

1. **Tab Alur Data** — visualisasi rantai alur data **per fitur** (bukan per modul) yang mudah dipahami
2. **Tab Prototype** — simulasi/input testing untuk mencoba prototype

---

## 2. Layout Saat Ini

```
┌──────────────────┬─────────────────────────────┐
│    CHAT AREA    │      RESULT PANEL           │
│                 │  [Prototype] [Fitur & Spek] │
│                 │  [Alur Data]                │
└─────────────────┴─────────────────────────────┘
```

---

## 3. Layout Baru (Target)

```
┌──────────────────┬─────────────────────────────┐
│    CHAT AREA    │      RESULT PANEL           │
│                 │  [Alur Data] [Prototype]    │
│                 ├─────────────────────────────┤
│                 │                             │
│                 │   (isi tab yang dipilih)    │
│                 │                             │
└─────────────────┴─────────────────────────────┘
```

---

## 4. Detail Per Tab

### 4.1 Tab "Alur Data" (Data Flow Chain per Fitur)

**Tujuan**: Tampilkan peta rantai alur data berdasarkan **fitur** (bukan modul), mudah dipahami.

**Struktur Visual**:

```
┌─────────────────────────────────────────────────────────────┐
│  FITUR: Order Processing                                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  User Input ──▶ NLP Parse ──▶ Intent Detect ──▶ Action   │
│       │              │              │               │       │
│       ▼              ▼              ▼               ▼       │
│  [Text/Rich]   [Entities]    [Intent:order]   [Response]  │
│                                                             │
│  ═══════════════════════════════════════════════════════    │
│  FITUR: Inventory Check                                     │
│  User Input ──▶ SKU Lookup ──▶ Stock Verify ──▶ Response │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Komponen**:
- **Fitur Header**: Nama fitur + ringkasan
- **Flow Chain**: Horizontal step-by-step dengan konektor
- **Step Detail**: Input/Output type per step

### 4.2 Tab "Prototype" + Simulasi

**Tujuan**: Prototype bisa dicoba/di-test dengan input/output.

**Struktur Visual**:

```
┌─────────────────────────────────────────────────────────────┐
│  PROTOTYPE: Order Form                                      │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Field: Product (ENUM: SKU-001, SKU-002, ...)     │   │
│  │  Field: Quantity (NUMBER: 1-100)                    │   │
│  │  Field: Shipping Address (TEXT)                    │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  SIMULASI / TEST                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ Input: {"product":"SKU-001","qty":5}              │   │
│  │ ▶ Jalankan                                         │   │
│  │ Output: {"stock":50,"price":250000,"valid":true}  │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**Komponen**:
- **Prototype Preview**: Form/Table/Card sesuai tipe
- **Simulation Panel**: Input JSON + Tombol "Jalankan" + Output JSON

---

## 5. Tahap Implementasi

| Tahap | Deskripsi |
|-------|-----------|
| **Phase 1** | Refactor tab order: "Alur Data" → "Prototype" |
| **Phase 2** | Redesain Tab "Alur Data" per fitur (bukan per modul) |
| **Phase 3** | Tambah Simulation Panel di Tab "Prototype" |
| **Phase 4** | Integrasi dengan backend (mock/test data) |

---

## 6. Definition of Done

- [ ] Tab "Alur Data" menampilkan flow chain per fitur
- [ ] Tab "Prototype" memiliki simulation input/output
- [ ] UI mudah dipahami non-teknis
- [ ] Responsif di desktop

---

## 7. File yang Likely Affected

- `ChatResultPanel.kt` — tab order & layout
- `DataFlowPane.kt` — redesign per fitur
- `PrototypeRenderer.kt` — tambah simulation panel
- Design system components baru (bila perlu)
