package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.sampling.usecases.AdvanceSamplingStageCommand
import com.eventverse.app.domain.sampling.usecases.AdvanceSamplingStageUseCase
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.domain.transfer.LocationId
import com.eventverse.app.domain.transfer.PhysicalLocation
import com.eventverse.app.domain.transfer.SuratJalanId
import com.eventverse.app.domain.transfer.SuratJalanItem
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.SuratJalanNumber
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TenantLocationConfig
import com.eventverse.app.domain.transfer.TenantLocationConfigRepository
import com.eventverse.app.domain.transfer.TransferStatus
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.transfer.usecases.GetFlowTransferLegsUseCase
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gerbang perpindahan tahap: pekerjaan tidak boleh dimulai di gedung tujuan sebelum barangnya
 * benar-benar sampai di sana.
 */
class AdvanceSamplingStageGateTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private val tenantId = TenantId("demo-tenant")
    private val gedungA = LocationId("loc-rajut-01")
    private val gedungB = LocationId("loc-finishing-01")

    private fun order() = SamplingOrder(
        id = SamplingOrderId("smp_001"),
        tenantId = tenantId,
        spkNumber = SpkNumber("SPK-SMP-0002"),
        clientName = "BIANCA",
        styleName = "FLORAL CARDIGAN",
        status = SamplingStatus.IN_PROGRESS,
        pipelineStage = SamplingPipelineStage.MACHINE_KNITTING,
        createdAt = now,
        updatedAt = now
    )

    private class FakeLocationRepo(private val config: TenantLocationConfig?) :
        TenantLocationConfigRepository {
        override suspend fun findByTenantId(tenantId: String) = config
        override suspend fun save(config: TenantLocationConfig) = Unit
    }

    private class FakeSuratJalanRepo(private val manifests: List<SuratJalanManifest>) :
        SuratJalanRepository {
        override suspend fun findById(id: SuratJalanId) = manifests.firstOrNull { it.id == id }
        override suspend fun findByNumber(tenantId: String, sjNumber: SuratJalanNumber) =
            manifests.firstOrNull { it.sjNumber == sjNumber }
        override suspend fun findByTenant(tenantId: String, transferType: TransferType?) = manifests
        override suspend fun findBySubject(tenantId: String, subjectId: String) = manifests
        override suspend fun save(manifest: SuratJalanManifest) = Unit
    }

    private fun multiSiteConfig() = TenantLocationConfig(
        tenantId = tenantId.value,
        isMultiSiteEnabled = true,
        requireCustomerDispatchSj = false,
        locations = listOf(
            PhysicalLocation(gedungA, "Gedung A"),
            PhysicalLocation(gedungB, "Gedung B")
        ),
        nodeLocations = mapOf(
            FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING) to gedungA,
            FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY) to gedungB
        )
    )

    private fun manifest(status: TransferStatus): SuratJalanManifest {
        val legKey = com.eventverse.app.domain.transfer.FlowTransferLeg.keyFor(
            FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING),
            FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY),
            TransferType.INTERNAL_SITE_TRANSFER
        )
        return SuratJalanManifest(
            id = SuratJalanId("sj-1"),
            tenantId = tenantId.value,
            sjNumber = SuratJalanNumber("SJ-INT-1"),
            transferType = TransferType.INTERNAL_SITE_TRANSFER,
            subject = WorkSubjectRef(
                kind = WorkSubjectKind.SAMPLING_ORDER,
                subjectId = "smp_001",
                orderNumber = "SPK-SMP-0002",
                articleName = "FLORAL CARDIGAN"
            ),
            originLocationId = gedungA,
            destinationLocationId = gedungB,
            status = status,
            legKey = legKey,
            // Tanpa bundleNo — SPK sampling memang tidak punya kartu bundel.
            items = listOf(SuratJalanItem(id = "i1", sizeLabel = "M", qtyPcs = 2)),
            dispatchedAt = now,
            receivedAt = if (status == TransferStatus.RECEIVED) now else null
        )
    }

    private fun useCase(
        config: TenantLocationConfig? = multiSiteConfig(),
        manifests: List<SuratJalanManifest> = emptyList()
    ) = AdvanceSamplingStageUseCase(
        GetFlowTransferLegsUseCase(FakeLocationRepo(config), FakeSuratJalanRepo(manifests))
    )

    private fun command(
        useCaseOrder: SamplingOrder = order(),
        overrideReason: String? = null
    ) = AdvanceSamplingStageCommand(
        order = useCaseOrder,
        target = SamplingPipelineStage.LINKING_ASSEMBLY,
        stages = SamplingPipelineStage.entries,
        processes = emptyList(),
        actorEmail = "ppic@pabrik.id",
        actorRole = "PPIC",
        overrideReason = overrideReason,
        now = now
    )

    @Test
    fun `advance when surat jalan not issued should fail and name the destination`() = runTest {
        val result = useCase()(command())

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("Gedung B"), "Pesan harus menyebut gedung tujuan: $message")
        assertTrue(message.contains("Surat Jalan"), "Pesan harus menyebut dokumennya: $message")
    }

    @Test
    fun `advance when goods still in transit should still fail`() = runTest {
        val result = useCase(manifests = listOf(manifest(TransferStatus.DISPATCHED)))(command())

        assertTrue(
            result.isFailure,
            "Barang di jalan bukan barang yang bisa dikerjakan — gerbang harus tetap tertutup"
        )
        assertTrue(result.exceptionOrNull()?.message?.contains("perjalanan") == true)
    }

    @Test
    fun `advance when goods received should pass`() = runTest {
        val result = useCase(manifests = listOf(manifest(TransferStatus.RECEIVED)))(command())

        assertTrue(result.isSuccess, "Gagal padahal barang sudah diterima: ${result.exceptionOrNull()}")
        assertEquals(SamplingPipelineStage.LINKING_ASSEMBLY, result.getOrThrow().pipelineStage)
    }

    @Test
    fun `advance when single site should pass without any document`() = runTest {
        val singleSite = TenantLocationConfig(
            tenantId = tenantId.value,
            isMultiSiteEnabled = false,
            requireCustomerDispatchSj = false,
            locations = multiSiteConfig().locations,
            nodeLocations = multiSiteConfig().nodeLocations
        )
        val result = useCase(config = singleSite)(command())

        assertTrue(result.isSuccess, "Pabrik satu atap tidak boleh terhambat gerbang ini")
    }

    @Test
    fun `advance when tenant has no location config should pass`() = runTest {
        val result = useCase(config = null)(command())
        assertTrue(result.isSuccess, "Gagal terbuka: tenant tanpa pemetaan tidak boleh terkunci")
    }

    @Test
    fun `advance when gate use case absent should pass unchanged`() = runTest {
        val result = AdvanceSamplingStageUseCase(legsUseCase = null)(command())
        assertTrue(result.isSuccess, "Gerbang yang tidak dipasang harus berperilaku seperti sebelumnya")
    }

    @Test
    fun `advance when override given should pass and record the reason in history`() = runTest {
        val result = useCase()(command(overrideReason = "Mobil pickup rusak, barang diantar manual"))

        assertTrue(result.isSuccess)
        val audit = result.getOrThrow().stageHistory.last()
        assertTrue(
            audit.actorRole.contains("lewat gerbang"),
            "Override wajib meninggalkan jejak, bukan lewat diam-diam: ${audit.actorRole}"
        )
        assertTrue(audit.actorRole.contains("Mobil pickup rusak"))
    }

    @Test
    fun `advance when cam worksheet incomplete should keep reporting the cam gate`() = runTest {
        val atCam = order().copy(pipelineStage = SamplingPipelineStage.CAM_PROGRAMMING)
        val result = useCase(config = null)(
            command(useCaseOrder = atCam).copy(target = SamplingPipelineStage.MACHINE_KNITTING)
        )

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Program CAM") == true,
            "Gerbang lokasi tidak boleh menelan pesan gerbang CAM: ${result.exceptionOrNull()?.message}"
        )
    }
}
