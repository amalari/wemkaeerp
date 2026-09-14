package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetId
import com.eventverse.app.domain.costing.CostingSheetRepository
import com.eventverse.app.domain.costing.CostingSnapshot
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Menyetujui lembar HPP — membekukan angka menjadi komitmen komersial.
 *
 * Kontrol permission (APPROVE_COSTING) dilakukan di route layer, bukan di sini.
 * Domain hanya memvalidasi state: hanya PENDING_APPROVAL yang bisa disetujui.
 *
 * @param sheetRepository Repository lembar HPP.
 * @param inputFingerprintFn Fungsi untuk menghasilkan fingerprint dari input kalkulasi.
 *   Default: timestamp-based (untuk produksi gunakan SHA-256 dari formulaParameters JSON).
 */
class ApproveCostingSheetUseCase(
    private val sheetRepository: CostingSheetRepository,
    private val clock: Clock = Clock.System,
    private val snapshotIdGenerator: () -> String = { "snap-${clock.now().toEpochMilliseconds()}" },
    private val inputFingerprintFn: (CostingSheet) -> String = { sheet ->
        // Default fingerprint: formulaParameters JSON hash (simplified)
        sheet.latestResult?.formulaParameters?.entries
            ?.sortedBy { it.key }
            ?.joinToString(",") { "${it.key}=${it.value}" }
            ?.let { "fp-${it.hashCode()}" }
            ?: "fp-empty"
    }
) {
    suspend operator fun invoke(
        tenantId: TenantId,
        sheetId: CostingSheetId,
        approvedByUserId: String
    ): Result<CostingSheet> = runCatching {
        require(approvedByUserId.isNotBlank()) { "approvedByUserId tidak boleh kosong" }

        val sheet = sheetRepository.findById(tenantId, sheetId)
            ?: error("Lembar HPP tidak ditemukan: ${sheetId.value}")

        val now = clock.now()
        val fingerprint = inputFingerprintFn(sheet)

        val approved = sheet.approve(
            approvedByUserId = approvedByUserId,
            inputFingerprint = fingerprint,
            snapshotId = snapshotIdGenerator(),
            now = now
        ).getOrThrow()

        sheetRepository.save(approved)
        approved
    }
}
