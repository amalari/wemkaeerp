# MVP Tasks Summary - WeMade ERP

**Tanggal**: 2026-10-12

## Jalur A - ERP Konveksi

### Selesai ✅
| # | Task | Status | Commit |
|---|---|---|---|
| A1 | Header kanvas dari data tenant | ✅ | 3987c73b |
| A3 | EdgeFlowProvider + 6 panah ready | ✅ | 3987c73b |
| A4 | Kolom rujukan (source_sampling_order_id, dll) | ✅ | 3987c73b |
| A5 | Kolom kanvas phase data + Sampling di ENGINEERING | ✅ | 3987c73b |
| A6 | PipelineBlueprint + validator | ✅ | 3987c73b |
| A7 | AiFlowTranslator (LLM-based) | ✅ | 3987c73b |
| A8a | CRM + TechPack telemetry | ✅ | cd39608d |
| A8b | Skema ID node pipeline | ✅ | dokumentasi |
| A8c | FK restore-presets | ✅ | preemptif |
| A8d | GET locations fail-open | ✅ | sudah ada |
| A8e | Editor tahap RBAC | ✅ | sudah ada |

### Belum ⏳
| # | Task | Priority | Plan |
|---|---|---|---|
| **A2** | Node aktif = entitlement + aktivasi tenant | **TINGGI** | [`PLAN-factory-flow-a2-entitlement-activation.md`](PLAN-factory-flow-a2-entitlement-activation.md) (TRD format) |

---

## Jalur B - Platform General

### Discovery Done, Implementation Pending
| # | Task | Status |
|---|---|---|
| B0 | Domain Pack concept | Discovery done |
| B4 | Blueprint (menggantikan GarmentBusinessPreset) | Discovery done |
| B7 | Tenant Pack Dinamis | Discovery done |

---

## Catatan

- **Inventory/QC/Fulfillment telemetry**: butuh modul/repository lebih dulu
- **A2**: satu-satunya MVP yang belum dimulai
