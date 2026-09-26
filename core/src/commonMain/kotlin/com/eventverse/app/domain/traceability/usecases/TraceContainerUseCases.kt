package com.eventverse.app.domain.traceability.usecases

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.*
import kotlinx.datetime.Instant

/** Menyiapkan kode untuk seluruh kartu yang akan dicetak saat SPK terbit. */
class PlanTraceAllocationUseCase(
    private val containers: TraceContainerRepository,
    private val workOrders: TraceWorkOrderProvider
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        ref: TraceWorkOrderRef,
        setsPerBundle: Int = TraceAllocationPlan.DEFAULT_SETS_PER_BUNDLE,
        pcsPerSack: Int = TraceAllocationPlan.DEFAULT_PCS_PER_SACK
    ): Result<TraceAllocationPlan> = runCatching {
        val base = workOrders.snapshot(tenantId, ref) ?: error("SPK ${ref.id} tidak ditemukan.")
        require(base.sizes.isNotEmpty()) {
            "SPK ${base.spkNumber} belum punya rincian size — isi dulu jumlah per ukuran sebelum mencetak kartu."
        }
        val snapshot = base.copy(
            ordinal = containers.ensureWorkOrderOrdinal(tenantId, ref),
            tenantOrdinal = containers.tenantOrdinal(tenantId)
        )
        TraceAllocationPlan.plan(snapshot, setsPerBundle, pcsPerSack)
    }
}

/**
 * Membuat baris wadah saat kartu pra-cetak di-scan pertama kali.
 *
 * Idempoten: memanggilnya sepuluh kali menghasilkan satu baris dan sepuluh jawaban yang sama.
 */
class OpenTraceContainerUseCase(
    private val containers: TraceContainerRepository,
    private val workOrders: TraceWorkOrderProvider
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        rawPayload: String,
        colorway: String,
        now: Instant
    ): Result<TraceContainer> = runCatching {
        val code = TraceCodec.fromScanPayload(rawPayload) ?: error("Kode tidak dikenali.")
        val parts = TraceCodec.parse(code.value) ?: error("Kode tidak sah.")

        containers.findByCode(tenantId, code)?.let { return@runCatching it }

        val ref = containers.findWorkOrderByOrdinal(tenantId, parts.workOrderOrdinal, parts.workOrderKind)
            ?: error("SPK untuk kartu ${TraceCodec.grouped(code)} belum terdaftar. Cetak ulang kartunya dari SPK.")
        val snapshot = workOrders.snapshot(tenantId, ref) ?: error("SPK ${ref.id} tidak ditemukan.")
        val size = snapshot.sizeAt(parts.sizeIndex)
            ?: error("Size ke-${parts.sizeIndex + 1} tidak ada lagi pada SPK ${snapshot.spkNumber}.")

        containers.openIfAbsent(
            TraceContainer(
                id = TraceContainerId("trc_${code.value}"),
                tenantId = tenantId,
                code = code,
                workOrder = ref,
                tier = parts.tier,
                sizeLabel = size.sizeLabel,
                colorway = colorway,
                state = TraceContainerState.OPENED,
                recordedAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}

/** Mencatat hitungan lembar panel sebuah bundel di akhir shift. */
class RecordBundleTallyUseCase(
    private val containers: TraceContainerRepository
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        code: TraceCode,
        tallies: List<PanelTally>,
        operatorName: String,
        shift: ShiftLabel,
        recordedAt: Instant,
        now: Instant,
        notes: String = ""
    ): Result<TraceContainer> = runCatching {
        val bundle = containers.findByCode(tenantId, code)
            ?: error("Bundel ${TraceCodec.grouped(code)} belum dibuka. Scan kartunya dulu.")
        containers.save(
            bundle.recordTally(tallies, operatorName, shift, recordedAt, now, notes)
        )
    }
}

/**
 * Menutup karung: menuang bundel-bundel ke dalamnya dan mengunci jumlahnya.
 *
 * Keseragaman diperiksa sebelum apa pun ditulis, dan penolakannya menyebut nilai yang bertabrakan —
 * operator perlu tahu apa yang harus dipisahkan, bukan sekadar bahwa dia salah.
 */
class CloseTraceSackUseCase(
    private val containers: TraceContainerRepository,
    private val workOrders: TraceWorkOrderProvider
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        sackCode: TraceCode,
        bundleCodes: List<TraceCode>,
        declaredPcs: Int,
        weightKg: Double,
        operatorName: String,
        recordedAt: Instant,
        now: Instant,
        notes: String = ""
    ): Result<TraceSackClosed> = runCatching {
        val sack = containers.findByCode(tenantId, sackCode)
            ?: error("Karung ${TraceCodec.grouped(sackCode)} belum dibuka. Scan kartunya dulu.")
        require(sack.isSack) { "Kartu ${TraceCodec.grouped(sackCode)} adalah kartu bundel, bukan karung." }
        require(sack.state != TraceContainerState.CLOSED) {
            "Karung ${TraceCodec.grouped(sackCode)} sudah ditutup sebelumnya."
        }
        require(bundleCodes.isNotEmpty()) { "Pilih dulu bundel yang dituang ke karung ini." }

        val bundles = bundleCodes.map { code ->
            containers.findByCode(tenantId, code)
                ?: error("Bundel ${TraceCodec.grouped(code)} tidak ditemukan.")
        }

        val blockers = sack.missingUniformityReasons(bundles)
        require(blockers.isEmpty()) { blockers.joinToString("; ") }

        val snapshot = workOrders.snapshot(tenantId, sack.workOrder)
        val requirements = snapshot?.panelRequirements ?: emptyList()

        val closed = sack.closeSack(declaredPcs, weightKg, operatorName, recordedAt, now, notes)
        val links = bundles.map { bundle ->
            TraceContainerLink(
                tenantId = tenantId,
                parentId = closed.id,
                childId = bundle.id,
                consumedPcs = bundle.completeSets(requirements).coerceAtLeast(1),
                linkedAt = now
            )
        }
        containers.consumeIntoSack(closed, links, bundles.map { it.markConsumed(now) })

        TraceSackClosed(
            code = closed.code,
            declaredPcs = closed.declaredPcs,
            consumedBundleCount = bundles.size,
            shrinkagePcs = links.sumOf { it.consumedPcs } - closed.declaredPcs,
            occurredAt = now
        )
    }
}

/** Rekapitulasi susut per size untuk satu SPK. */
class GetTraceReconciliationUseCase(
    private val containers: TraceContainerRepository,
    private val workOrders: TraceWorkOrderProvider
) {
    suspend operator fun invoke(tenantId: TenantId, ref: TraceWorkOrderRef): Result<TraceReconciliation> =
        runCatching {
            val snapshot = workOrders.snapshot(tenantId, ref) ?: error("SPK ${ref.id} tidak ditemukan.")
            TraceReconciliation.build(
                snapshot = snapshot,
                containers = containers.findByWorkOrder(tenantId, ref),
                links = containers.linksForWorkOrder(tenantId, ref)
            )
        }
}
