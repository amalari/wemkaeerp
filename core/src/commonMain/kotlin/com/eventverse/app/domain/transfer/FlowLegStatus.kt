package com.eventverse.app.domain.transfer

/**
 * Sejauh mana sebuah [FlowTransferLeg] sudah benar-benar dijalani.
 *
 * Hanya tiga, karena hanya tiga yang mengubah keputusan: boleh dikerjakan di tujuan atau belum.
 * Rincian daur hidup dokumen tetap tinggal di [TransferStatus].
 */
enum class FlowLegStatus(val displayName: String) {
    /** Belum ada dokumen sah yang menyertai barang. Draft termasuk di sini. */
    BELUM_TERBIT("Surat Jalan belum terbit"),

    /** Barang sudah berangkat tapi belum dinyatakan diterima di tujuan. */
    DIKIRIM("Dalam perjalanan"),

    /** Sudah diterima di tujuan — pekerjaan di sana boleh dimulai. */
    DITERIMA("Diterima di tujuan");

    val isReceived: Boolean get() = this == DITERIMA
}

/**
 * Satu leg beserta dokumen yang melayaninya, bila ada.
 */
data class FlowLegView(
    val leg: FlowTransferLeg,
    val status: FlowLegStatus,
    val manifest: SuratJalanManifest? = null,
    /**
     * Dokumen dipasangkan lewat tebakan asal/tujuan, bukan lewat `legKey`.
     *
     * Hanya terjadi pada Surat Jalan yang terbit sebelum kolom `leg_key` ada. Ditandai supaya
     * UI bisa menyatakan bahwa pasangan ini tidak sepasti yang lain, alih-alih menyamarkan
     * tebakan sebagai fakta.
     */
    val isLegacyMatch: Boolean = false
)

/**
 * Hasil penggabungan leg turunan dengan dokumen tersimpan.
 *
 * [orphanManifests] sengaja dibawa keluar, bukan dibuang: Surat Jalan yang leg-nya sudah tidak
 * ada di alur berarti alurnya diubah setelah dokumen terbit. Itu keadaan yang perlu dilihat
 * orang, bukan disembunyikan.
 */
data class FlowLegBoard(
    val legs: List<FlowLegView> = emptyList(),
    val orphanManifests: List<SuratJalanManifest> = emptyList()
) {
    val hasPendingLeg: Boolean get() = legs.any { !it.status.isReceived }

    /** Leg yang menuju sebuah simpul — inilah yang menggerbangi pekerjaan di simpul itu. */
    fun legsInto(node: FlowNodeRef): List<FlowLegView> = legs.filter { it.leg.toNode == node }
}

/**
 * Memasangkan leg turunan dengan dokumen Surat Jalan yang tersimpan. Murni.
 */
object FlowLegStatusResolver {

    fun resolve(
        legs: List<FlowTransferLeg>,
        manifests: List<SuratJalanManifest>
    ): FlowLegBoard {
        val usable = manifests.filterNot { it.status == TransferStatus.CANCELLED }
        val claimed = mutableSetOf<SuratJalanId>()

        val views = legs.map { leg ->
            val exact = usable.filter { it.legKey == leg.legKey }
            val chosen = exact.latestOrNull()
                ?: usable.filter { it.legKey == null && it.matchesHeuristically(leg) }.latestOrNull()
            chosen?.let { claimed += it.id }
            FlowLegView(
                leg = leg,
                status = chosen.toLegStatus(),
                manifest = chosen,
                isLegacyMatch = chosen != null && chosen.legKey == null
            )
        }

        val knownKeys = legs.map { it.legKey }.toSet()
        val orphans = usable.filter { it.legKey != null && it.legKey !in knownKeys && it.id !in claimed }

        return FlowLegBoard(legs = views, orphanManifests = orphans)
    }

    /**
     * `DRAFT` sengaja dibaca sebagai belum terbit: draft adalah niat, bukan izin berangkat.
     * `PARTIAL_RECEIVED` dibaca sebagai masih dalam perjalanan — sebagian barang belum sampai,
     * dan gerbang tidak boleh terbuka untuk sebagian.
     */
    private fun SuratJalanManifest?.toLegStatus(): FlowLegStatus = when (this?.status) {
        null, TransferStatus.DRAFT, TransferStatus.CANCELLED -> FlowLegStatus.BELUM_TERBIT
        TransferStatus.DISPATCHED,
        TransferStatus.IN_TRANSIT,
        TransferStatus.PARTIAL_RECEIVED -> FlowLegStatus.DIKIRIM
        TransferStatus.RECEIVED -> FlowLegStatus.DITERIMA
    }

    /**
     * Dokumen terakhir untuk satu leg — bukan yang paling maju statusnya.
     *
     * Bedanya nyata saat leg dijalani dua kali (barang kembali untuk perbaikan lalu dikirim
     * ulang): yang berlaku adalah perjalanan terakhir, dan memilih "yang paling maju" akan
     * membuat gerbang terbuka karena perjalanan lama yang sudah selesai.
     */
    private fun List<SuratJalanManifest>.latestOrNull(): SuratJalanManifest? =
        maxWithOrNull(
            compareBy<SuratJalanManifest> { it.receivedAt ?: it.dispatchedAt }
                .thenBy { it.id.value }
        )

    /**
     * Pencocokan untuk dokumen tanpa `leg_key`. Sengaja dibiarkan kasar dan hanya dipakai
     * sebagai jalan terakhir — tuple asal/tujuan tidak bisa membedakan dua kunjungan ke gedung
     * yang sama dalam satu SPK, dan itulah alasan `leg_key` ada.
     */
    private fun SuratJalanManifest.matchesHeuristically(leg: FlowTransferLeg): Boolean {
        if (transferType != leg.transferType) return false
        return when (val destination = leg.destination) {
            is LegEndpoint.Site -> destinationLocationId == destination.locationId
            is LegEndpoint.Vendor -> vendorRef == destination.ref
            is LegEndpoint.Customer -> customerName == destination.name
        }
    }
}
