package com.eventverse.app

import com.eventverse.app.domain.costing.CostingBenchmarkRepository
import com.eventverse.app.domain.costing.CostingRateCardRepository
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.customfield.CustomFieldDefinitionRepository
import com.eventverse.app.domain.deal.DealRepository
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
    traceScanHost: String
) {
    val tenantProcessCatalogRepository: com.eventverse.app.domain.process.TenantProcessCatalogRepository =
        com.eventverse.app.infrastructure.PostgresTenantProcessRepository()

    samplingRoutes(
        repository = samplingOrderRepo,
        dealRepository = crmDealRepo,
        processCatalogRepository = tenantProcessCatalogRepository
    )
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
        scanHost = traceScanHost
    )

    fulfillmentTransferRoutes(
        transfers = transferRepo,
        containers = traceContainerRepo,
        imageStorage = benchmarkImageStorage,
        roleRepository = roleRepo
    )

    val workCardRepository: com.eventverse.app.domain.workqueue.WorkCardRepository =
        com.eventverse.app.infrastructure.PostgresWorkCardRepository()
    val workDepositRepository: com.eventverse.app.domain.workqueue.WorkDepositRepository =
        com.eventverse.app.infrastructure.PostgresWorkDepositRepository()
    val reworkTicketRepository: com.eventverse.app.domain.workqueue.ReworkTicketRepository =
        com.eventverse.app.infrastructure.PostgresReworkTicketRepository()
    val suratJalanRepository: com.eventverse.app.domain.transfer.SuratJalanRepository =
        com.eventverse.app.infrastructure.PostgresSuratJalanRepository()



    workQueueRoutes(
        cardRepository = workCardRepository,
        depositRepository = workDepositRepository,
        ticketRepository = reworkTicketRepository
    )

    suratJalanRoutes(
        suratJalanRepository = suratJalanRepository,
        cardRepository = workCardRepository
    )

    tenantProcessRoutes(repository = tenantProcessCatalogRepository)
}

