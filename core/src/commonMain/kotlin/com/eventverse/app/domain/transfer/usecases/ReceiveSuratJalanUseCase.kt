package com.eventverse.app.domain.transfer.usecases

import com.eventverse.app.domain.transfer.SuratJalanId
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.workqueue.WorkCardRepository
import com.eventverse.app.domain.workqueue.WorkCardStatus
import kotlinx.datetime.Instant

data class ReceiveSuratJalanCommand(
    val manifestId: SuratJalanId,
    val receiverName: String,
    val notes: String = "",
    val now: Instant
)

/**
 * Memvalidasi dan menyelesaikan serah terima barang di lokasi tujuan (Gedung baru, pabrik dari vendor, atau buyer).
 */
class ReceiveSuratJalanUseCase(
    private val suratJalanRepository: SuratJalanRepository,
    private val cardRepository: WorkCardRepository
) {
    suspend operator fun invoke(command: ReceiveSuratJalanCommand): Result<SuratJalanManifest> = runCatching {
        val manifest = suratJalanRepository.findById(command.manifestId)
            ?: error("SuratJalanManifest not found with id: ${command.manifestId.value}")

        val receivedManifest = manifest.markReceived(command.now)

        if (manifest.transferType == TransferType.INTERNAL_SITE_TRANSFER) {
            // Aktifkan kembali kartu bundle di stasiun tujuan
            val cardIds = manifest.items.mapNotNull { it.workCardId }
            val cards = cardIds.mapNotNull { cardRepository.findById(it) }
            val activatedCards = cards.map { card ->
                if (card.status == WorkCardStatus.IN_PROGRESS || card.status == WorkCardStatus.QUEUED) {
                    card.copy(status = WorkCardStatus.QUEUED)
                } else card
            }
            cardRepository.saveAll(activatedCards)
        }

        suratJalanRepository.save(receivedManifest)
        receivedManifest
    }
}
