package com.eventverse.app

import com.eventverse.app.domain.costing.CostingBenchmarkRepository
import com.eventverse.app.domain.costing.CostingRateCardRepository
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.deal.DealRepository
import com.eventverse.app.domain.deal.storage.PoFileStorage
import com.eventverse.app.domain.invoicing.InvoiceIssuerProfileRepository
import com.eventverse.app.domain.invoicing.InvoicePaymentRepository
import com.eventverse.app.domain.invoicing.InvoiceRepository
import com.eventverse.app.domain.invoicing.InvoiceTemplateRepository
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.fulfillment.InternalTransferRepository
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.production.BulkWorkOrderRepository
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.techpack.TechPackRepository
import com.eventverse.app.domain.traceability.TraceContainerRepository
import com.eventverse.app.domain.traceability.TraceWorkOrderProvider
import com.eventverse.app.infrastructure.storage.BenchmarkImageStorage
import com.eventverse.app.infrastructure.traceability.KnitWorksheetBuilder
import com.eventverse.app.infrastructure.traceability.SpkCardBuilder
import com.eventverse.app.routes.*
import com.eventverse.app.services.DesignVisionAnalyzer
import com.eventverse.app.services.HistoricalCostingParser
import io.ktor.server.routing.Route

/**
 * Pemasangan rute modul operasional & keuangan.
 *
 * Diangkat keluar dari `Application.kt` yang sudah 698 baris — jauh di atas hard limit 500 untuk
 * lapisan server — sehingga menambah satu modul lagi di sana akan melanggar Aturan Ratchet. Batas
 * pemecahannya mengikuti tanggung jawab, bukan jumlah baris: yang pindah ke sini adalah modul yang
 * mengerjakan pesanan dan uangnya, sedangkan tata kelola (RBAC, organisasi, pipeline, admin) tetap
 * di tempatnya karena berubah karena alasan yang berbeda.
 */
@Suppress("LongParameterList")
fun Route.operationalModuleRoutes(
    samplingOrderRepo: SamplingOrderRepository,
    crmDealRepo: DealRepository,
    bulkWorkOrderRepo: BulkWorkOrderRepository,
    materialRepo: MaterialItemRepository,
    materialPriceRepo: MaterialPriceRepository,
    customFieldRepo: CustomFieldDefinitionRepository,
    techPackRepo: TechPackRepository,
    roleRepo: RoleRepository,
    assignmentRepo: ModuleAssignmentRepository,
    invoiceRepo: InvoiceRepository,
    invoiceTemplateRepo: InvoiceTemplateRepository,
    invoicePaymentRepo: InvoicePaymentRepository,
    invoiceIssuerProfileRepo: InvoiceIssuerProfileRepository,
    costingSheetRepo: CostingSheetRepository,
    costingRateCardRepo: CostingRateCardRepository,
    costingBenchmarkRepo: CostingBenchmarkRepository,
    pipeRepo: TenantPipelineRepository,
    historicalCostingParser: HistoricalCostingParser,
    designVisionAnalyzer: DesignVisionAnalyzer,
    benchmarkImageStorage: BenchmarkImageStorage,
    traceContainerRepo: TraceContainerRepository,
    transferRepo: InternalTransferRepository,
    traceWorkOrderProvider: TraceWorkOrderProvider,
    knitWorksheetBuilder: KnitWorksheetBuilder,
    traceScanHost: String,
    poFileStorage: PoFileStorage? = null
) {
    val tenantProcessCatalogRepository: com.eventverse.app.domain.process.TenantProcessCatalogRepository =
        com.eventverse.app.infrastructure.PostgresTenantProcessRepository()
    val phaseTagsRepository: com.eventverse.app.domain.process.TenantStagePhaseTagsRepository =
        com.eventverse.app.infrastructure.PostgresTenantStagePhaseTagsRepository()

    // Dideklarasikan di sini, bukan di dekat rute Surat Jalan di bawah, karena gerbang
    // perpindahan tahap pada samplingRoutes membutuhkan keduanya.
    val suratJalanRepository: com.eventverse.app.domain.transfer.SuratJalanRepository =
        com.eventverse.app.infrastructure.PostgresSuratJalanRepository()
    val tenantLocationRepository: com.eventverse.app.domain.transfer.TenantLocationConfigRepository =
        com.eventverse.app.infrastructure.PostgresTenantLocationRepository()
    val flowLegsUseCase = com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsUseCase(
        locationConfigRepository = tenantLocationRepository,
        suratJalanRepository = suratJalanRepository
    )

    samplingRoutes(
        repository = samplingOrderRepo,
        dealRepository = crmDealRepo,
        processCatalogRepository = tenantProcessCatalogRepository,
        poFileStorage = poFileStorage,
        flowLegsUseCase = flowLegsUseCase,
        storageRepository = com.eventverse.app.infrastructure.PostgresSampleStorageRecordRepository(),
        phaseTagsRepository = phaseTagsRepository
    )
    samplingStageWorkRoutes(samplingOrderRepo)
            productionRoutes(
                workOrderRepository = bulkWorkOrderRepo,
                dealRepository = crmDealRepo,
                samplingOrderRepository = samplingOrderRepo
            )
            masterDataRoutes(
                materialRepository = materialRepo,
                priceRepository = materialPriceRepo,
                customFieldRepository = customFieldRepo
            )
            techPackRoutes(
                techPackRepository = techPackRepo,
                samplingOrderRepository = samplingOrderRepo,
                materialRepository = materialRepo,
                materialPriceRepository = materialPriceRepo,
                roleRepository = roleRepo,
                moduleAssignmentRepository = assignmentRepo
            )
            invoicingRoutes(
                invoiceRepository = invoiceRepo,
                templateRepository = invoiceTemplateRepo,
                paymentRepository = invoicePaymentRepo,
                issuerProfileRepository = invoiceIssuerProfileRepo,
                samplingOrderRepository = samplingOrderRepo
            )
            costingRoutes(
                sheetRepository = costingSheetRepo,
                rateCardRepository = costingRateCardRepo,
                techPackRepository = techPackRepo,
                materialRepository = materialRepo,
                materialPriceRepository = materialPriceRepo,
                roleRepository = roleRepo,
                moduleAssignmentRepository = assignmentRepo,
                benchmarkRepository = costingBenchmarkRepo,
                tenantPipelineRepository = pipeRepo,
                historicalCostingParser = historicalCostingParser,
                designVisionAnalyzer = designVisionAnalyzer,
                benchmarkImageStorage = benchmarkImageStorage
            )

    traceabilityScanRoutes(
        containers = traceContainerRepo,
        workOrders = traceWorkOrderProvider
    )
    traceabilityPrintRoutes(
        containers = traceContainerRepo,
        workOrders = traceWorkOrderProvider,
        worksheets = knitWorksheetBuilder,
        spkCards = SpkCardBuilder(samplingOrderRepo),
        scanHost = traceScanHost
    )

    val fulfillmentRouteConfigRepository: com.eventverse.app.domain.fulfillment.FulfillmentRouteConfigRepository =
        com.eventverse.app.infrastructure.PostgresFulfillmentRouteConfigRepository()

    fulfillmentTransferRoutes(
        transfers = transferRepo,
        containers = traceContainerRepo,
        routeConfigRepository = fulfillmentRouteConfigRepository,
        imageStorage = benchmarkImageStorage,
        roleRepository = roleRepo
    )

    val workCardRepository: com.eventverse.app.domain.workqueue.WorkCardRepository =
        com.eventverse.app.infrastructure.PostgresWorkCardRepository()
    val workDepositRepository: com.eventverse.app.domain.workqueue.WorkDepositRepository =
        com.eventverse.app.infrastructure.PostgresWorkDepositRepository()
    val reworkTicketRepository: com.eventverse.app.domain.workqueue.ReworkTicketRepository =
        com.eventverse.app.infrastructure.PostgresReworkTicketRepository()
    val washingBatchRepository: com.eventverse.app.domain.workqueue.WashingBatchRepository =
        com.eventverse.app.infrastructure.PostgresWashingBatchRepository()

    workQueueRoutes(
        cardRepository = workCardRepository,
        depositRepository = workDepositRepository,
        ticketRepository = reworkTicketRepository
    )

    washingBatchRoutes(
        cardRepository = workCardRepository,
        washingBatchRepository = washingBatchRepository
    )

    suratJalanRoutes(
        suratJalanRepository = suratJalanRepository,
        cardRepository = workCardRepository,
        locationConfigRepository = tenantLocationRepository
    )

    tenantProcessRoutes(repository = tenantProcessCatalogRepository)
    tenantPhaseTagRoutes(repository = phaseTagsRepository)
    tenantStageFlowRoutes(repository = com.eventverse.app.infrastructure.PostgresTenantStageFlowRepository())

    tenantLocationRoutes(
        repository = tenantLocationRepository,
        roleRepository = roleRepo,
        moduleAssignmentRepository = assignmentRepo
    )

    vendorRoutes(
        vendorRepository = com.eventverse.app.infrastructure.PostgresVendorRepository(),
        assignmentRepository = com.eventverse.app.infrastructure.PostgresVendorAssignmentRepository(),
        flowGateway = com.eventverse.app.infrastructure.SamplingSubcontractFlowGateway(
            orderRepository = samplingOrderRepo,
            processCatalogRepository = tenantProcessCatalogRepository,
            flowLegsUseCase = flowLegsUseCase,
            phaseTagsRepository = phaseTagsRepository
        ),
        roleRepository = roleRepo,
        moduleAssignmentRepository = assignmentRepo
    )
}

