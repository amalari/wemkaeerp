# Peta Titik Ekstensi WeMade ERP

Singkatan: `core/…` = `core/src/commonMain/kotlin/com/eventverse/app/`,
`app/…` = `app/shared/src/commonMain/kotlin/com/eventverse/app/`,
`server/…` = `server/src/main/kotlin/com/eventverse/app/`, migrasi = `server/src/main/resources/db/migration/`.

## Butuh… → extend di… → contoh nyata

| Butuh | Extend di | Contoh yang ditiru |
|---|---|---|
| Modul operasional baru | `core/…/domain/rbac/BusinessModule.kt`, `core/…/domain/pipeline/OperationalModuleCatalog.kt`, `ModuleArchetype` di `OperationalModuleContract.kt` | `QualityControlModule` di `OperationalModuleCatalog.kt`; migrasi `V27__register_master_data_module.sql`, `V64__create_vendor_contacts.sql` |
| Modul governance | `BusinessModule` (`kind = GOVERNANCE`), `GovernanceModuleGate` di `App.kt` | `V18__add_governance_modules.sql`, `V19__backfill_governance_role_permissions.sql` |
| Tipe port (I/O) | `core/…/domain/contracts/ModulePortPayload.kt` (`PortDataTypeRegistry`), `PortCompatibility.kt` | port `CutPiecesBundle` |
| Kerangka tahap / template industri | `core/…/domain/stageflow/IndustryStageTemplates.kt`, `TenantStageFlow.kt` | template `EMBROIDERY`; editor `app/…/presentation/sampling/components/StageFlowEditorPanel.kt` |
| Aturan yang mencari tahap berdasarkan peran | `core/…/domain/sampling/SamplingOrderStageFrame.kt` (`firstStageWith`, `stagesWith`) | `assemblyStage`, `finalQcStage`, `packingStage` |
| Proses sisipan (bordir, sablon) | `core/…/domain/process/TenantProcessCatalog.kt`, `TenantOptionalProcess.kt`, `WorkStationCatalog.optionalStations()` | proses Bordir di panel Penentuan Alur |
| Stasiun lini produksi massal | `core/…/domain/workqueue/WorkStationCatalog.kt` | `WASHING`, `STEAM` |
| Konfigurasi JSONB per tenant | domain VO + repository interface + `server/…/infrastructure/Postgres*Repository.kt` + codec `core/…/shared/**` + migrasi | tag fase V71 (`PostgresTenantStagePhaseTagsRepository.kt`), kerangka tahap V72 (`PostgresTenantStageFlowRepository.kt`) |
| Snapshot beku per dokumen | kolom JSONB nullable + fungsi `freeze…()` idempoten | `frozenStageFlow` V73, `freezeStageFlow` di `SamplingOrderStageFrame.kt` |
| Parser kunci tersimpan | value object + parser tunggal yang tidak fallback | `StageCode`, `resolveStoredStageCode` di `SamplingStageCodeBridge.kt` |
| Route bergerbang RBAC (baca) | `server/…/routes/ModuleAccessGuard.kt` (`moduleDecision`, `requireModuleAccess`) | `VendorRoutes.kt` |
| Route tulis fail-closed | `mayEditWithoutDecision` di `server/…/routes/TenantStageFlowRoutes.kt` | `TenantStageFlowRoutes.kt`, `TenantLocationRoutes.kt` (PUT) |
| Satu use case per operasi | `core/…/domain/<fitur>/usecases/` + helper `internal` bersama | `core/…/domain/stageflow/usecases/` (Add/Remove/Move/Rename/Reset) |
| API client | `app/…/infrastructure/api/*ApiClient.kt` + interface `*RemoteDataSource` (method baru ber-default agar fake test tak rusak) | `StageFlowApiClient.kt`, `ProcessCatalogApiClient.kt` |
| ViewModel + editor | `app/…/presentation/<fitur>/*ViewModel.kt` (MVI: UiState + UiEvent) | `StageFlowEditorViewModel.kt` |
| Layar / komponen | `app/…/presentation/designsystem/` (Clay*) — baca `design-system-rules.md` | `ClayCard`, `ClayButton`, `ClayStatusBanner` |
| Menu & navigasi | `app/…/presentation/navigation/AppNavScreen.kt`, `NavMenu.kt`, cabang `App.kt` | `TRACEABILITY` (fitur di bawah `OPERATOR_EXEC`) |
| Kanvas Factory Flow | `core/…/domain/pipeline/` (`TenantPipelineProjector`, `PipelineCatalogReconciler`, `PipelineGraph`), `app/…/presentation/pipeline/` | lihat TRD-FLOW-002 |

## Paket domain yang ada (`core/…/domain/`)

audit, auth, common, contracts, costing, crm, customfield, deal, fulfillment, invoicing, masterdata,
moduledev, orgchart, pipeline, printing, process, production, prospect, rbac, sampling, stageflow,
techpack, tenant, traceability, transfer, vendor, workqueue.

## Jebakan yang sudah pernah terjadi

- Kunci **NAME** (`QUALITY_CONTROL`) vs **code** (`quality_control`) tertukar di migrasi.
- Route fitur hanya mengecek "ada tenant" — lupa gate modul induk.
- Cabang `App.kt` hanya mengecek login, tidak `accessDecisions`.
- `hideBypassedNodes = true` menyembunyikan modul baru di kanvas.
- Cache Gradle basi / `core-jvm.jar` kosong — lihat `wemade-feature-workflow/references/verification-recipes.md`.

## Kanvas Factory Flow (TRD-FLOW-002)

| Ingin… | Sentuh | Otomatis |
|---|---|---|
| Node modul baru | spec di `OperationalModuleCatalog.all` (port!) | `CatalogPortWiring` → `CatalogPipelineBuilder` → reconciler tenant lama |
| Fitur di dalam modul | entri `pipeline/ModuleFeature.kt` + `routePrefixes` | level 2 panel node; `RouteOwnershipTest` menjaga route |
| Angka nyata di node | `ModuleTelemetryProvider` + daftar di `pipelineTelemetryRoutes` (`ServerRouteWiring`) | tag "estimasi" hilang; `stageWip` → angka per tahap di level 2 |

Contoh: `domain/sampling/SamplingTelemetryProvider.kt`, `domain/pipeline/ExistingTelemetryProviders.kt`.
Bacaan: `docs/teaching/teaching-catalog-driven-factory-flow.md`.
