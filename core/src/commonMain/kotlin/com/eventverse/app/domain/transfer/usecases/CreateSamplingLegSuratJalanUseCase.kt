package com.eventverse.app.domain.transfer.usecases

import com.eventverse.app.domain.transfer.FlowTransferLeg
import com.eventverse.app.domain.transfer.LegEndpoint
import com.eventverse.app.domain.transfer.SuratJalanId
import com.eventverse.app.domain.transfer.SuratJalanItem
import com.eventverse.app.domain.transfer.SuratJalanManifest
import com.eventverse.app.domain.transfer.SuratJalanNumber
import com.eventverse.app.domain.transfer.SuratJalanRepository
import com.eventverse.app.domain.transfer.TransferStatus
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.domain.workqueue.WorkSubjectKind
import com.eventverse.app.domain.workqueue.WorkSubjectRef
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Alokasi jumlah sampel per ukuran, sumber rincian barang di Surat Jalan sampling. */
data class SamplingLegQuantity(val sizeLabel: String, val qtyPcs: Int)

data class CreateSamplingLegSuratJalanCommand(
    val tenantId: String,
    val subject: WorkSubjectRef,
    val leg: FlowTransferLeg,
    val quantities: List<SamplingLegQuantity>,
    val sjNumber: SuratJalanNumber? = null,
    val carrierName: String? = null,
    val driverName: String? = null,
    val vehiclePlate: String? = null,
    val unitServiceFeeIdr: Long = 0L,
    val expectedReturnDate: LocalDate? = null,
    val notes: String = "",
    val now: Instant
)

/**
 * Menerbitkan Surat Jalan untuk sebuah leg pada SPK **sampling**.
 *
 * Berdiri terpisah dari [CreateInternalTransferSuratJalanUseCase] dan
 * [CreateMakloonOutboundSuratJalanUseCase] karena keduanya bertumpu pada `WorkCard`, dan SPK
 * sampling tidak punya satu pun: kartu kerja lahir dari meja potong produksi masal, sementara
 * sampel berjumlah beberapa potong dan tidak pernah melewatinya. Memaksakan kartu di jalur ini
 * berarti mengarang bundel yang secara fisik tidak ada.
 *
 * Karena itu rincian barangnya dibangun dari alokasi ukuran SPK, dan jenis perpindahannya
 * diambil dari leg — bukan dipilih pemanggil, supaya dokumen tidak bisa menyimpang dari alur
 * yang menuntutnya.
 */
class CreateSamplingLegSuratJalanUseCase(
    private val suratJalanRepository: SuratJalanRepository
) {
    suspend operator fun invoke(
        command: CreateSamplingLegSuratJalanCommand
    ): Result<SuratJalanManifest> = runCatching {
        require(command.subject.kind == WorkSubjectKind.SAMPLING_ORDER) {
            "Use case ini khusus SPK sampling; untuk produksi masal pakai jalur berbasis WorkCard"
        }
        val positive = command.quantities.filter { it.qtyPcs > 0 }
        require(positive.isNotEmpty()) {
            "Surat Jalan tidak bisa diterbitkan tanpa satu pun potong sampel"
        }

        val leg = command.leg
        val sjNumber = command.sjNumber ?: SuratJalanNumber(
            "SJ-${leg.transferType.prefix()}-${command.now.toEpochMilliseconds()}"
        )

        val items = positive.mapIndexed { index, quantity ->
            SuratJalanItem(
                id = "item-smp-${sjNumber.value}-$index",
                sizeLabel = quantity.sizeLabel,
                qtyPcs = quantity.qtyPcs,
                notes = "Sampel ${quantity.sizeLabel}"
            )
        }

        val manifest = SuratJalanManifest(
            id = SuratJalanId("sj-smp-${command.now.toEpochMilliseconds()}"),
            tenantId = command.tenantId,
            sjNumber = sjNumber,
            transferType = leg.transferType,
            subject = command.subject,
            originLocationId = (leg.origin as? LegEndpoint.Site)?.locationId,
            destinationLocationId = (leg.destination as? LegEndpoint.Site)?.locationId,
            vendorRef = leg.vendorRef(),
            customerName = (leg.destination as? LegEndpoint.Customer)?.name,
            carrierName = command.carrierName,
            driverName = command.driverName,
            vehiclePlate = command.vehiclePlate,
            // Langsung DISPATCHED: penerbitan dari panel alur adalah pernyataan bahwa barang
            // berangkat sekarang. Draft di sini hanya akan menjadi dokumen yang tidak pernah
            // ditindaklanjuti, sementara gerbang tetap tertutup tanpa ada yang tahu kenapa.
            status = TransferStatus.DISPATCHED,
            legKey = leg.legKey,
            items = items,
            unitServiceFeeIdr = command.unitServiceFeeIdr,
            expectedReturnDate = command.expectedReturnDate,
            dispatchedAt = command.now,
            notes = command.notes
        )

        suratJalanRepository.save(manifest)
        manifest
    }

    /** Vendor bisa ada di salah satu ujung: berangkat menuju vendor, atau pulang darinya. */
    private fun FlowTransferLeg.vendorRef(): String? =
        (destination as? LegEndpoint.Vendor)?.ref ?: (origin as? LegEndpoint.Vendor)?.ref

    private fun TransferType.prefix(): String = when (this) {
        TransferType.INTERNAL_SITE_TRANSFER -> "INT"
        TransferType.SUBCONTRACT_OUTBOUND -> "MKO"
        TransferType.SUBCONTRACT_INBOUND -> "MKI"
        TransferType.CUSTOMER_DISPATCH -> "CUS"
    }
}
