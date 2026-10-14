# TRD: Data Flow Visualization & Prototype Simulation

**TRD-ID**: TRD-BUILDER-007  
**Tanggal**: 2026-10-14  
**Status**: Draft  
**Track**: Builder Chat Enhancement  

---

## 1. Overview

### 1.1 Tujuan

Meningkatkan UX panel Result di `/builder/chat` dengan:

1. **Tab Alur Data** — visualisasi rantai alur data per **fitur** yang mudah dipahami
2. **Tab Prototype** — kemampuan simulasi/testing prototype

### 1.2 Ruang Lingkup

- UI: Chat Builder Result Panel
- Tab: Alur Data & Prototype
- Tidak termasuk: Detail per modul (nanti)

---

## 2. Functional Requirements

### 2.1 Tab "Alur Data"

| ID | Kebutuhan | Prioritas |
|----|-----------|-----------|
| FR-01 | Tampilkan rantai alur data **per fitur** (bukan per modul) | Wajib |
| FR-02 | Format horizontal step-by-step dengan konektor | Wajib |
| FR-03 | Tampilkan input/output type per step | Wajib |
| FR-04 | Filter/sortir berdasarkan fitur | Opsional |

### 2.2 Tab "Prototype" Simulation

| ID | Kebutuhan | Prioritas |
|----|-----------|-----------|
| FR-05 | Tampilkan prototype form/table/card | Wajib |
| FR-06 | Panel input JSON untuk simulasi | Wajib |
| FR-07 | Tombol "Jalankan" untuk eksekusi | Wajib |
| FR-08 | Tampilkan output hasil simulasi | Wajib |

---

## 3. Non-Functional Requirements

| ID | Kebutuhan |
|----|-----------|
| NFR-01 | Responsive di desktop (min 1280px) |
| NFR-02 | Loading state untuk proses simulasi |
| NFR-03 | Error handling untuk input tidak valid |

---

## 4. User Flow

### 4.1 Alur Data Flow

```
User klik Tab "Alur Data"
    ↓
Sistem tampilkan list fitur dengan flow chain
    ↓
User klik expand pada satu fitur
    ↓
Sistem tampilkan detail step-by-step
```

### 4.2 Prototype Simulation Flow

```
User klik Tab "Prototype"
    ↓
Sistem tampilkan prototype preview + simulation panel
    ↓
User input JSON di simulation panel
    ↓
User klik "Jalankan"
    ↓
Sistem proses & tampilkan output
```

---

## 5. UI/UX Design

### 5.1 Layout Structure

```
┌──────────────────────────────────────────────────────────┐
│                    RESULT PANEL                          │
├──────────────────────────────────────────────────────────┤
│  [Alur Data] [Prototype]      ← Tab Switcher           │
├──────────────────────────────────────────────────────────┤
│                                                          │
│                    CONTENT AREA                          │
│                                                          │
│   (Alur Data: Flow Chain per Fitur)                     │
│   (Prototype: Preview + Simulation)                      │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### 5.2 Component Specifications

| Component | Spesifikasi |
|-----------|-------------|
| Tab Switcher | ClaySegmentedBar (existing) |
| Flow Chain Card | Horizontal layout, step connectors |
| Simulation Panel | Input textarea + Execute button + Output display |

---

## 6. Technical Notes

### 6.1 Data Structure

```kotlin
data class FeatureFlow(
    val featureId: String,
    val featureName: String,
    val steps: List<FlowStep>
)

data class FlowStep(
    val stepName: String,
    val inputType: String,
    val outputType: String,
    val description: String?
)
```

### 6.2 State Management

- Use existing MVI pattern di ViewModel
- Simulation state: `SimulationState.Idle | SimulationState.Running | SimulationState.Success | SimulationState.Error`

---

## 7. Acceptance Criteria

| ID | Kriteria |
|----|----------|
| AC-01 | Tab "Alur Data" menampilkan minimal 1 flow chain |
| AC-02 | Tab "Prototype" bisa input JSON dan dapat output |
| AC-03 | UI bisa dipahami user non-teknis |
| AC-04 | Tidak ada crash pada invalid input |

---

## 8. Out of Scope

- Detail alur per modul (FR lanjutan)
- Backend simulation engine (mock/demo dulu)
- Mobile responsive (desktop only)
